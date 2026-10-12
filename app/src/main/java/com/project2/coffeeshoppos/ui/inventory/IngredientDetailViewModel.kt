package com.project2.coffeeshoppos.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.local.entity.InventoryMovementEntity
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.domain.InventoryMath
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.PurchaseDraft
import com.project2.coffeeshoppos.domain.formatQuantity
import com.project2.coffeeshoppos.domain.formatQuantityChange
import com.project2.coffeeshoppos.domain.formatUnitCost
import com.project2.coffeeshoppos.domain.parseQuantity
import com.project2.coffeeshoppos.ui.navigation.IngredientDetailRoute
import com.project2.coffeeshoppos.util.centavosToInput
import com.project2.coffeeshoppos.util.parsePesoToCentavos
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StockAction(val title: String, val confirmLabel: String) {
    PURCHASE("Record purchase", "Add stock"),
    WASTAGE("Record wastage", "Remove stock"),
    COUNT("Stock count", "Save count"),
    CORRECT_COST("Correct cost", "Save cost"),
}

/** The open stock dialog. Only the fields for [action] are shown. */
data class StockDialogState(
    val action: StockAction,
    val quantityText: String = "",
    val unit: MeasureUnit,
    val costText: String = "",
    val supplier: String = "",
    val reference: String = "",
    /** Wastage or cost correction reason, or an optional note for a stock count or purchase. */
    val note: String = "",
    val purchasedOn: LocalDate,
    val error: String? = null,
    val isSaving: Boolean = false,
)

data class IngredientDetailUiState(
    val isLoading: Boolean = true,
    val ingredient: IngredientEntity? = null,
    val movements: List<InventoryMovementEntity> = emptyList(),
    val usedIn: List<String> = emptyList(),
    val dialog: StockDialogState? = null,
    val message: String? = null,
    val today: LocalDate = LocalDate.MIN,
)

class IngredientDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: InventoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val ingredientId = savedStateHandle.toRoute<IngredientDetailRoute>().ingredientId
    private val dialog = MutableStateFlow<StockDialogState?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<IngredientDetailUiState> = combine(
        repository.observeIngredient(ingredientId),
        repository.observeMovements(ingredientId),
        repository.observeRecipeUsage(ingredientId),
        dialog,
        message,
    ) { ingredient, movements, usedIn, dialog, message ->
        IngredientDetailUiState(
            isLoading = false,
            ingredient = ingredient,
            movements = movements,
            usedIn = usedIn.sorted(),
            dialog = dialog,
            message = message,
            today = LocalDate.now(clock)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientDetailUiState())

    fun openDialog(action: StockAction) {
        val ingredient = uiState.value.ingredient ?: return
        // Purchases are usually in big units (kg, L); wastage and counts often in small ones.
        val unit = if (action == StockAction.PURCHASE) ingredient.baseUnit.displayUnit else ingredient.baseUnit
        // A cost correction starts from today's cost, so a typo only needs a small fix.
        val costText = if (action == StockAction.CORRECT_COST && ingredient.averageCostCentavosPerUnit.signum() > 0) {
            centavosToInput(InventoryMath.displayPriceCentavos(ingredient.averageCostCentavosPerUnit, ingredient.baseUnit))
        } else ""
        dialog.value = StockDialogState(action = action, unit = unit, costText = costText, purchasedOn = LocalDate.now(clock))
    }

    fun closeDialog() { dialog.value = null }
    fun onMessageShown() { message.value = null }

    private fun edit(transform: (StockDialogState) -> StockDialogState) =
        dialog.update { it?.let { current -> transform(current).copy(error = null) } }

    fun onQuantityChange(value: String) = edit { it.copy(quantityText = value) }
    fun onUnitChange(unit: MeasureUnit) = edit { it.copy(unit = unit) }
    fun onCostChange(value: String) = edit { it.copy(costText = value) }
    fun onSupplierChange(value: String) = edit { it.copy(supplier = value) }
    fun onReferenceChange(value: String) = edit { it.copy(reference = value) }
    fun onNoteChange(value: String) = edit { it.copy(note = value) }
    fun onDateChange(date: LocalDate) = edit { it.copy(purchasedOn = date) }

    /** "₱450.00/kg" for what is typed in the purchase dialog, or null until it is valid. */
    fun purchaseUnitCostLabel(state: StockDialogState, ingredient: IngredientEntity): String? {
        val quantity = parseQuantity(state.quantityText) ?: return null
        val cost = parsePesoToCentavos(state.costText) ?: return null
        val milli = state.unit.toMilli(quantity, ingredient.baseUnit)
        if (milli <= 0) return null
        return formatUnitCost(InventoryMath.unitCost(cost, milli), ingredient.baseUnit)
    }

    fun saveDialog() {
        val current = dialog.value ?: return
        val ingredient = uiState.value.ingredient ?: return
        if (current.isSaving) return
        if (current.action == StockAction.CORRECT_COST) {
            saveCostCorrection(current, ingredient)
            return
        }
        val quantity = parseQuantity(current.quantityText)
        val milli = quantity?.let { current.unit.toMilli(it, ingredient.baseUnit) }
        val cost = parsePesoToCentavos(current.costText)
        val error = when {
            milli == null -> "Enter a quantity, e.g. 2.5"
            milli <= 0 && current.action != StockAction.COUNT -> "Enter a quantity above zero"
            current.action == StockAction.PURCHASE && cost == null -> "Enter the total you paid, e.g. 900"
            current.action == StockAction.WASTAGE && current.note.isBlank() -> "Say what happened, e.g. spoiled"
            else -> null
        }
        if (error != null) {
            dialog.value = current.copy(error = error)
            return
        }
        dialog.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val done = when (current.action) {
                    StockAction.PURCHASE -> {
                        repository.recordPurchase(
                            PurchaseDraft(
                                ingredientId = ingredient.id,
                                enteredQuantity = requireNotNull(quantity),
                                enteredUnit = current.unit,
                                totalCostCentavos = requireNotNull(cost),
                                supplier = current.supplier,
                                reference = current.reference,
                                notes = current.note,
                                purchasedOn = current.purchasedOn
                            )
                        )
                        "Added ${formatQuantity(requireNotNull(milli), ingredient.baseUnit)}"
                    }
                    StockAction.WASTAGE -> {
                        repository.recordWastage(ingredient.id, requireNotNull(milli), current.note)
                        "Removed ${formatQuantity(milli, ingredient.baseUnit)}"
                    }
                    StockAction.COUNT -> {
                        val change = repository.recordStockCount(ingredient.id, requireNotNull(milli), current.note)
                        if (change == 0L) "Count matches the recorded stock. Nothing changed."
                        else "Stock adjusted by ${formatQuantityChange(change, ingredient.baseUnit)}"
                    }
                    StockAction.CORRECT_COST -> error("Handled by saveCostCorrection")
                }
                dialog.value = null
                message.value = done
            } catch (e: Exception) {
                dialog.update { it?.copy(isSaving = false, error = e.message ?: "Could not save") }
            }
        }
    }

    private fun saveCostCorrection(current: StockDialogState, ingredient: IngredientEntity) {
        val price = parsePesoToCentavos(current.costText)
        val error = when {
            price == null || price <= 0 -> "Enter the correct cost, e.g. 900"
            current.note.isBlank() -> "Say what was wrong, e.g. mistyped purchase"
            else -> null
        }
        if (error != null) {
            dialog.value = current.copy(error = error)
            return
        }
        dialog.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val cost = InventoryMath.unitCostFromDisplayPrice(requireNotNull(price), ingredient.baseUnit)
                repository.correctCost(ingredient.id, cost, current.note)
                dialog.value = null
                message.value = "Cost corrected to ${formatUnitCost(cost, ingredient.baseUnit)}"
            } catch (e: Exception) {
                dialog.update { it?.copy(isSaving = false, error = e.message ?: "Could not save") }
            }
        }
    }
}
