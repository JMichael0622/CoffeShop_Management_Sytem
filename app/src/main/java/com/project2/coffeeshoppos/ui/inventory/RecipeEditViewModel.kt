package com.project2.coffeeshoppos.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.domain.InventoryMath
import com.project2.coffeeshoppos.domain.RecipeLine
import com.project2.coffeeshoppos.domain.RecipeTarget
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.domain.milliToInput
import com.project2.coffeeshoppos.domain.parseQuantityMilli
import com.project2.coffeeshoppos.ui.navigation.RecipeEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One ingredient row as typed. The quantity is in the ingredient's base unit (g, ml or pc). */
data class RecipeLineForm(val key: Long, val ingredientId: Long?, val quantityText: String)

data class RecipeEditUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val priceCentavos: Long = 0,
    val lines: List<RecipeLineForm> = emptyList(),
    val savedLines: List<RecipeLineForm> = emptyList(),
    val ingredients: List<IngredientEntity> = emptyList(),
    /** Error per row, keyed by [RecipeLineForm.key]. */
    val lineErrors: Map<Long, String> = emptyMap(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val isFinished: Boolean = false,
) {
    val hasUnsavedChanges: Boolean get() = lines.map { it.ingredientId to it.quantityText } != savedLines.map { it.ingredientId to it.quantityText }
    val ingredientsById: Map<Long, IngredientEntity> get() = ingredients.associateBy { it.id }

    /** Active ingredients, plus archived ones this recipe already uses. */
    val selectableIngredients: List<IngredientEntity>
        get() = ingredients.filter { !it.isArchived || lines.any { line -> line.ingredientId == it.id } }

    /** Cost of one row in centavos, or null while it is incomplete. */
    fun lineCost(line: RecipeLineForm): Long? {
        val ingredient = line.ingredientId?.let(ingredientsById::get) ?: return null
        val milli = parseQuantityMilli(line.quantityText, ingredient.baseUnit, ingredient.baseUnit) ?: return null
        return InventoryMath.costOf(milli, ingredient.averageCostCentavosPerUnit)
    }

    val totalCostCentavos: Long get() = lines.sumOf { lineCost(it) ?: 0L }
}

class RecipeEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: InventoryRepository,
) : ViewModel() {
    private val target: RecipeTarget = savedStateHandle.toRoute<RecipeEditRoute>().let {
        if (it.addOnId != 0L) RecipeTarget.AddOn(it.addOnId) else RecipeTarget.Variation(it.variationId)
    }
    private var nextKey = 1L
    private var showErrors = false

    private val _uiState = MutableStateFlow(RecipeEditUiState())
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllIngredients().collect { all -> _uiState.update { it.copy(ingredients = all) } }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val data = repository.getRecipeForEdit(target)
        if (data == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        val lines = data.lines.map { line ->
            val unit = repository.getIngredient(line.ingredientId)?.baseUnit
            RecipeLineForm(
                key = nextKey++,
                ingredientId = line.ingredientId,
                quantityText = unit?.let { milliToInput(line.quantityMilli, it) } ?: ""
            )
        }
        _uiState.update {
            it.copy(isLoading = false, title = data.title, priceCentavos = data.priceCentavos, lines = lines, savedLines = lines)
        }
    }

    private fun updateLines(transform: (List<RecipeLineForm>) -> List<RecipeLineForm>) {
        _uiState.update { state ->
            val lines = transform(state.lines)
            val updated = state.copy(lines = lines)
            updated.copy(lineErrors = if (showErrors) validate(updated).first else emptyMap())
        }
    }

    fun onAddLine() = updateLines { it + RecipeLineForm(nextKey++, ingredientId = null, quantityText = "") }
    fun onRemoveLine(key: Long) = updateLines { lines -> lines.filterNot { it.key == key } }
    fun onIngredientChange(key: Long, ingredientId: Long) =
        updateLines { lines -> lines.map { if (it.key == key) it.copy(ingredientId = ingredientId) else it } }
    fun onQuantityChange(key: Long, value: String) =
        updateLines { lines -> lines.map { if (it.key == key) it.copy(quantityText = value) else it } }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /** Checks every row; returns the errors and, when there are none, the lines to save. */
    private fun validate(state: RecipeEditUiState): Pair<Map<Long, String>, List<RecipeLine>?> {
        val errors = mutableMapOf<Long, String>()
        val seen = mutableSetOf<Long>()
        val result = state.lines.mapNotNull { line ->
            val ingredient = line.ingredientId?.let(state.ingredientsById::get)
            val milli = ingredient?.let { parseQuantityMilli(line.quantityText, it.baseUnit, it.baseUnit) }
            val error = when {
                ingredient == null -> "Choose an ingredient"
                !seen.add(ingredient.id) -> "${ingredient.name} is already listed"
                milli == null || milli <= 0 -> "Enter how much is used, e.g. 18"
                else -> null
            }
            if (error != null) {
                errors[line.key] = error
                null
            } else RecipeLine(requireNotNull(ingredient).id, requireNotNull(milli))
        }
        return errors to if (errors.isEmpty()) result else null
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        showErrors = true
        val (errors, lines) = validate(state)
        if (lines == null) {
            _uiState.update { it.copy(lineErrors = errors, message = "Please fix the highlighted rows") }
            return
        }
        _uiState.update { it.copy(isSaving = true, lineErrors = errors) }
        viewModelScope.launch {
            try {
                repository.saveRecipe(target, lines)
                _uiState.update { it.copy(isSaving = false, savedLines = it.lines, isFinished = true) }
            } catch (e: UserFacingException) {
                _uiState.update { it.copy(isSaving = false, message = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = "Could not save: ${e.message}") }
            }
        }
    }
}
