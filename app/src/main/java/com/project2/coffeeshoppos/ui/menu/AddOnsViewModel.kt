package com.project2.coffeeshoppos.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.AddOnEntity
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.domain.AddOnDraft
import com.project2.coffeeshoppos.util.centavosToInput
import com.project2.coffeeshoppos.util.parsePesoToCentavos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add/edit dialog. [addOn] is null when adding a new one. */
data class AddOnDialogState(
    val addOn: AddOnEntity?,
    val name: String,
    val priceText: String,
    val isAvailable: Boolean,
    val error: String? = null,
    val isSaving: Boolean = false,
)

data class AddOnsUiState(
    val isLoading: Boolean = true,
    val active: List<AddOnEntity> = emptyList(),
    val archived: List<AddOnEntity> = emptyList(),
    val dialog: AddOnDialogState? = null,
    val message: String? = null,
)

class AddOnsViewModel(private val repository: CatalogRepository) : ViewModel() {
    private val dialog = MutableStateFlow<AddOnDialogState?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AddOnsUiState> = combine(
        repository.observeAllAddOns(), dialog, message
    ) { addOns, dialog, message ->
        AddOnsUiState(
            isLoading = false,
            active = addOns.filterNot { it.isArchived },
            archived = addOns.filter { it.isArchived },
            dialog = dialog,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddOnsUiState())

    fun openAdd() { dialog.value = AddOnDialogState(addOn = null, name = "", priceText = "", isAvailable = true) }

    fun openEdit(addOn: AddOnEntity) {
        dialog.value = AddOnDialogState(addOn, addOn.name, centavosToInput(addOn.priceCentavos), addOn.isAvailable)
    }

    fun closeDialog() { dialog.value = null }
    fun onNameChange(value: String) = dialog.update { it?.copy(name = value, error = null) }
    fun onPriceChange(value: String) = dialog.update { it?.copy(priceText = value, error = null) }
    fun onAvailableChange(value: Boolean) = dialog.update { it?.copy(isAvailable = value) }
    fun onMessageShown() { message.value = null }

    fun saveDialog() {
        val current = dialog.value ?: return
        if (current.isSaving) return
        val price = parsePesoToCentavos(current.priceText)
        if (price == null) {
            dialog.value = current.copy(error = "Enter a valid price, e.g. 20 or 25.50")
            return
        }
        dialog.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                repository.saveAddOn(
                    AddOnDraft(
                        id = current.addOn?.id ?: 0,
                        name = current.name,
                        priceCentavos = price,
                        isAvailable = current.isAvailable
                    )
                )
                dialog.value = null
                if (current.addOn == null) message.value = "Add-on created"
            } catch (e: Exception) {
                dialog.update { it?.copy(isSaving = false, error = e.message ?: "Could not save") }
            }
        }
    }

    fun setAvailable(addOn: AddOnEntity, available: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.saveAddOn(AddOnDraft(addOn.id, addOn.name, addOn.priceCentavos, available))
            }.onFailure { message.value = it.message ?: "Could not update ${addOn.name}" }
        }
    }

    fun setArchived(addOn: AddOnEntity, archived: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setAddOnArchived(addOn.id, archived) }
                .onSuccess {
                    dialog.value = null
                    message.value = "\"${addOn.name}\" ${if (archived) "archived" else "restored"}"
                }
                .onFailure { message.value = it.message ?: "Could not update ${addOn.name}" }
        }
    }
}
