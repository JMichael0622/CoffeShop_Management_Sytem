package com.project2.coffeeshoppos.ui.menu

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.project2.coffeeshoppos.data.local.entity.AddOnEntity
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.domain.DEFAULT_SIZE_NAME
import com.project2.coffeeshoppos.domain.ProductDraft
import com.project2.coffeeshoppos.domain.ProductFormErrors
import com.project2.coffeeshoppos.domain.ProductFormValidator
import com.project2.coffeeshoppos.domain.SizeFormInput
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.ui.navigation.ProductEditRoute
import com.project2.coffeeshoppos.util.centavosToInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the owner is typing. Compared with the saved version to detect unsaved changes. */
data class ProductForm(
    val name: String = "",
    val categoryId: Long? = null,
    val description: String = "",
    val isAvailable: Boolean = true,
    val sizes: List<SizeFormInput> = emptyList(),
    /** Includes links to archived add-ons, so saving never silently drops them. */
    val addOnIds: Set<Long> = emptySet(),
)

data class ProductEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val form: ProductForm = ProductForm(),
    val savedForm: ProductForm = ProductForm(),
    val isArchived: Boolean = false,
    val categories: List<CategoryEntity> = emptyList(),
    val addOns: List<AddOnEntity> = emptyList(),
    val errors: ProductFormErrors = ProductFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    /** Set when the screen should close (after saving or archiving). */
    val isFinished: Boolean = false,
) {
    val hasUnsavedChanges: Boolean get() = form != savedForm

    /** Active categories, plus the current one even if it was archived. */
    val selectableCategories: List<CategoryEntity>
        get() = categories.filter { !it.isArchived || it.id == form.categoryId }

    /** Active add-ons, plus any already linked that are now archived (shown so they can be unlinked). */
    val selectableAddOns: List<AddOnEntity>
        get() = addOns.filter { !it.isArchived || it.id in form.addOnIds }
}

class ProductEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: CatalogRepository,
) : ViewModel() {
    private val productId = savedStateHandle.toRoute<ProductEditRoute>().productId
    private var nextSizeKey = 1L
    private var showErrors = false

    private val _uiState = MutableStateFlow(ProductEditUiState(isNew = productId == 0L))
    val uiState: StateFlow<ProductEditUiState> = _uiState.asStateFlow()

    init {
        // Keep the category and add-on choices up to date.
        viewModelScope.launch {
            combine(repository.observeAllCategories(), repository.observeAllAddOns(), ::Pair)
                .collect { (categories, addOns) ->
                    _uiState.update { it.copy(categories = categories, addOns = addOns) }
                }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (productId == 0L) {
            val form = ProductForm(sizes = listOf(newSizeRow()))
            _uiState.update { it.copy(isLoading = false, form = form, savedForm = form) }
            return
        }
        val data = repository.getProductForEdit(productId)
        if (data == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        val form = ProductForm(
            name = data.product.name,
            categoryId = data.product.categoryId,
            description = data.product.description,
            isAvailable = data.product.isAvailable,
            sizes = data.sizes.map {
                SizeFormInput(key = nextSizeKey++, id = it.id, name = it.name, priceText = centavosToInput(it.priceCentavos))
            },
            addOnIds = data.addOnIds
        )
        _uiState.update {
            it.copy(isLoading = false, form = form, savedForm = form, isArchived = data.product.isArchived)
        }
    }

    private fun newSizeRow() = SizeFormInput(key = nextSizeKey++, id = 0, name = "", priceText = "")

    private fun updateForm(transform: (ProductForm) -> ProductForm) {
        _uiState.update { state ->
            val form = transform(state.form)
            state.copy(form = form, errors = if (showErrors) validate(form) else ProductFormErrors())
        }
    }

    private fun validate(form: ProductForm) = ProductFormValidator.validate(form.name, form.sizes)

    fun onNameChange(value: String) = updateForm { it.copy(name = value) }
    fun onCategoryChange(categoryId: Long?) = updateForm { it.copy(categoryId = categoryId) }
    fun onDescriptionChange(value: String) = updateForm { it.copy(description = value) }
    fun onAvailableChange(value: Boolean) = updateForm { it.copy(isAvailable = value) }

    fun onAddSize() = updateForm { form ->
        // When going from one size to two, give the first one a visible name to edit.
        val sizes = form.sizes.map { if (form.sizes.size == 1 && it.name.isBlank()) it.copy(name = DEFAULT_SIZE_NAME) else it }
        form.copy(sizes = sizes + newSizeRow())
    }

    fun onRemoveSize(key: Long) = updateForm { form ->
        if (form.sizes.size <= 1) form else form.copy(sizes = form.sizes.filterNot { it.key == key })
    }

    fun onSizeNameChange(key: Long, value: String) = updateForm { form ->
        form.copy(sizes = form.sizes.map { if (it.key == key) it.copy(name = value) else it })
    }

    fun onSizePriceChange(key: Long, value: String) = updateForm { form ->
        form.copy(sizes = form.sizes.map { if (it.key == key) it.copy(priceText = value) else it })
    }

    fun onToggleAddOn(addOnId: Long) = updateForm { form ->
        form.copy(addOnIds = if (addOnId in form.addOnIds) form.addOnIds - addOnId else form.addOnIds + addOnId)
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        showErrors = true
        val errors = validate(state.form)
        if (errors.hasErrors) {
            _uiState.update { it.copy(errors = errors, message = errors.general ?: "Please fix the highlighted fields") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errors = errors) }
        viewModelScope.launch {
            val form = state.form
            val draft = ProductDraft(
                id = productId,
                name = form.name,
                categoryId = form.categoryId,
                description = form.description,
                isAvailable = form.isAvailable,
                sizes = ProductFormValidator.toSizeInputs(form.sizes),
                addOnIds = form.addOnIds
            )
            try {
                repository.saveProduct(draft)
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
                repository.setProductArchived(productId, archived)
                _uiState.update {
                    if (archived) it.copy(isArchived = true, isFinished = true)
                    else it.copy(isArchived = false, message = "Product restored to the menu")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Could not update the product") }
            }
        }
    }
}
