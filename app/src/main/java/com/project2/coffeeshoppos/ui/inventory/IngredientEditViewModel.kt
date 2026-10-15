package com.project2.coffeeshoppos.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.domain.IngredientDraft
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.domain.formatUnitCost
import com.project2.coffeeshoppos.domain.milliToInput
import com.project2.coffeeshoppos.domain.parseQuantityMilli
import com.project2.coffeeshoppos.ui.navigation.IngredientEditRoute
import com.project2.coffeeshoppos.util.parsePesoToCentavos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the owner is typing. Quantities stay as text until saved. */
data class IngredientForm(
    val name: String = "",
    val category: String = "",
    val baseUnit: MeasureUnit = MeasureUnit.GRAM,
    val reorderText: String = "",
    val reorderUnit: MeasureUnit = MeasureUnit.GRAM.displayUnit,
    // Opening stock is only asked for when creating an ingredient.
    val openingText: String = "",
    val openingUnit: MeasureUnit = MeasureUnit.GRAM.displayUnit,
    val openingCostText: String = "",
)

data class IngredientFormErrors(
    val name: String? = null,
    val reorder: String? = null,
    val opening: String? = null,
    val openingCost: String? = null,
) {
    val hasErrors: Boolean get() = listOf(name, reorder, opening, openingCost).any { it != null }
}

data class IngredientEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val form: IngredientForm = IngredientForm(),
    val savedForm: IngredientForm = IngredientForm(),
    val canChangeUnit: Boolean = true,
    val isArchived: Boolean = false,
    /** e.g. "₱900.00/kg", or null for a new ingredient or one with no cost yet. */
    val averageCostLabel: String? = null,
    val errors: IngredientFormErrors = IngredientFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val isFinished: Boolean = false,
) {
    val hasUnsavedChanges: Boolean get() = form != savedForm
}

class IngredientEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: InventoryRepository,
) : ViewModel() {
    val ingredientId = savedStateHandle.toRoute<IngredientEditRoute>().ingredientId
    private var showErrors = false

    private val _uiState = MutableStateFlow(IngredientEditUiState(isNew = ingredientId == 0L))
    val uiState: StateFlow<IngredientEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (ingredientId == 0L) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val ingredient = repository.getIngredient(ingredientId)
        if (ingredient == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        val unit = ingredient.baseUnit
        val form = IngredientForm(
            name = ingredient.name,
            category = ingredient.category,
            baseUnit = unit,
            reorderText = if (ingredient.reorderLevelMilli > 0) milliToInput(ingredient.reorderLevelMilli, unit.displayUnit) else "",
            reorderUnit = unit.displayUnit,
            openingUnit = unit.displayUnit
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                form = form,
                savedForm = form,
                canChangeUnit = repository.canChangeUnit(ingredientId),
                isArchived = ingredient.isArchived,
                averageCostLabel = ingredient.averageCostCentavosPerUnit.takeIf { cost -> cost.signum() > 0 }
                    ?.let { cost -> formatUnitCost(cost, unit) }
            )
        }
    }

    private fun updateForm(transform: (IngredientForm) -> IngredientForm) {
        _uiState.update { state ->
            val form = transform(state.form)
            state.copy(form = form, errors = if (showErrors) validate(form, state.isNew).first else IngredientFormErrors())
        }
    }

    fun onNameChange(value: String) = updateForm { it.copy(name = value) }
    fun onCategoryChange(value: String) = updateForm { it.copy(category = value) }
    fun onReorderChange(value: String) = updateForm { it.copy(reorderText = value) }
    fun onReorderUnitChange(unit: MeasureUnit) = updateForm { it.copy(reorderUnit = unit) }
    fun onOpeningChange(value: String) = updateForm { it.copy(openingText = value) }
    fun onOpeningUnitChange(unit: MeasureUnit) = updateForm { it.copy(openingUnit = unit) }
    fun onOpeningCostChange(value: String) = updateForm { it.copy(openingCostText = value) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun showMessage(text: String) = _uiState.update { it.copy(message = text) }

    /** Switching between weight, volume and count resets the quantity units to match. */
    fun onBaseUnitChange(unit: MeasureUnit) {
        if (!_uiState.value.canChangeUnit) return
        updateForm { it.copy(baseUnit = unit, reorderUnit = unit.displayUnit, openingUnit = unit.displayUnit) }
    }

    /** Checks the form; returns the errors and, when there are none, the draft to save. */
    private fun validate(form: IngredientForm, isNew: Boolean): Pair<IngredientFormErrors, IngredientDraft?> {
        val reorder = if (form.reorderText.isBlank()) 0L else parseQuantityMilli(form.reorderText, form.reorderUnit, form.baseUnit)
        val opening = if (!isNew || form.openingText.isBlank()) 0L else parseQuantityMilli(form.openingText, form.openingUnit, form.baseUnit)
        val openingCost = if (!isNew || form.openingCostText.isBlank()) 0L else parsePesoToCentavos(form.openingCostText)
        val errors = IngredientFormErrors(
            name = if (form.name.isBlank()) "Enter an ingredient name" else null,
            reorder = if (reorder == null) "Enter a number, e.g. 500" else null,
            opening = if (opening == null) "Enter a number, e.g. 2.5" else null,
            openingCost = when {
                openingCost == null -> "Enter a valid amount, e.g. 900 or 899.50"
                openingCost > 0 && opening == 0L -> "Enter the opening stock this cost is for"
                else -> null
            }
        )
        if (errors.hasErrors) return errors to null
        return errors to IngredientDraft(
            id = ingredientId,
            name = form.name,
            category = form.category,
            baseUnit = form.baseUnit,
            reorderLevelMilli = requireNotNull(reorder),
            openingQuantityMilli = requireNotNull(opening),
            openingTotalCostCentavos = requireNotNull(openingCost)
        )
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        showErrors = true
        val (errors, draft) = validate(state.form, state.isNew)
        if (draft == null) {
            _uiState.update { it.copy(errors = errors, message = "Please fix the highlighted fields") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errors = errors) }
        viewModelScope.launch {
            try {
                if (state.isNew) repository.addIngredient(draft) else repository.updateIngredient(draft)
                _uiState.update { it.copy(isSaving = false, savedForm = it.form, isFinished = true) }
            } catch (e: UserFacingException) {
                _uiState.update { it.copy(isSaving = false, message = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = "Could not save: ${e.message}") }
            }
        }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch {
            try {
                repository.setIngredientArchived(ingredientId, archived)
                _uiState.update {
                    if (archived) it.copy(isArchived = true, isFinished = true)
                    else it.copy(isArchived = false, message = "Ingredient restored")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Could not update the ingredient") }
            }
        }
    }
}
