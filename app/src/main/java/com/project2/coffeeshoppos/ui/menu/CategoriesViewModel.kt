package com.project2.coffeeshoppos.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add/rename dialog. [category] is null when adding a new one. */
data class CategoryDialogState(
    val category: CategoryEntity?,
    val name: String,
    val error: String? = null,
    val isSaving: Boolean = false,
)

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val active: List<CategoryEntity> = emptyList(),
    val archived: List<CategoryEntity> = emptyList(),
    val dialog: CategoryDialogState? = null,
    val message: String? = null,
)

class CategoriesViewModel(private val repository: CatalogRepository) : ViewModel() {
    private val dialog = MutableStateFlow<CategoryDialogState?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.observeAllCategories(), dialog, message
    ) { categories, dialog, message ->
        CategoriesUiState(
            isLoading = false,
            active = categories.filterNot { it.isArchived },
            archived = categories.filter { it.isArchived },
            dialog = dialog,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun openAdd() { dialog.value = CategoryDialogState(category = null, name = "") }
    fun openEdit(category: CategoryEntity) { dialog.value = CategoryDialogState(category, category.name) }
    fun closeDialog() { dialog.value = null }
    fun onDialogNameChange(name: String) = dialog.update { it?.copy(name = name, error = null) }
    fun onMessageShown() { message.value = null }

    fun saveDialog() {
        val current = dialog.value ?: return
        if (current.isSaving) return
        dialog.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                if (current.category == null) {
                    repository.addCategory(current.name)
                    message.value = "Category added"
                } else {
                    repository.renameCategory(current.category.id, current.name)
                }
                dialog.value = null
            } catch (e: Exception) {
                dialog.update { it?.copy(isSaving = false, error = e.message ?: "Could not save") }
            }
        }
    }

    fun setArchived(category: CategoryEntity, archived: Boolean) {
        viewModelScope.launch {
            try {
                repository.setCategoryArchived(category.id, archived)
                dialog.value = null
                message.value = "\"${category.name}\" ${if (archived) "archived" else "restored"}"
            } catch (e: Exception) {
                dialog.value = null
                message.value = e.message ?: "Could not update the category"
            }
        }
    }

    fun move(category: CategoryEntity, up: Boolean) {
        viewModelScope.launch {
            runCatching { repository.moveCategory(category.id, up) }
                .onFailure { message.value = it.message ?: "Could not reorder" }
        }
    }
}
