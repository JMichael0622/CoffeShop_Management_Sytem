package com.project2.coffeeshoppos.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.repository.LoyaltyRepository
import com.project2.coffeeshoppos.domain.LoyaltyRules
import com.project2.coffeeshoppos.util.centavosToInput
import com.project2.coffeeshoppos.util.parsePesoToCentavos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the owner is typing; saved together with the Save button. */
data class LoyaltyForm(
    val isEnabled: Boolean,
    val stampsText: String,
    val capText: String,
    val error: String? = null,
    val isSaving: Boolean = false,
)

data class LoyaltySettingsUiState(
    val isLoading: Boolean = true,
    val saved: LoyaltyRules = LoyaltyRules.DEFAULT,
    val form: LoyaltyForm? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val message: String? = null,
) {
    val hasChanges: Boolean
        get() = form != null && (
            form.isEnabled != saved.isEnabled ||
                form.stampsText.trim() != saved.stampsForReward.toString() ||
                parsePesoToCentavos(form.capText) != saved.rewardCapCentavos
            )
}

class LoyaltySettingsViewModel(private val repository: LoyaltyRepository) : ViewModel() {
    private val form = MutableStateFlow<LoyaltyForm?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LoyaltySettingsUiState> = combine(
        repository.observeRules(), repository.observeCategories(), form, message
    ) { rules, categories, form, message ->
        LoyaltySettingsUiState(false, rules, form, categories, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoyaltySettingsUiState())

    init {
        viewModelScope.launch {
            val rules = repository.observeRules().first()
            form.value = LoyaltyForm(rules.isEnabled, rules.stampsForReward.toString(), centavosToInput(rules.rewardCapCentavos))
        }
    }

    fun onMessageShown() { message.value = null }
    fun onEnabledChange(value: Boolean) = form.update { it?.copy(isEnabled = value, error = null) }
    fun onStampsChange(value: String) = form.update { it?.copy(stampsText = value.filter(Char::isDigit).take(2), error = null) }
    fun onCapChange(value: String) = form.update { it?.copy(capText = value, error = null) }

    fun save() {
        val current = form.value ?: return
        if (current.isSaving) return
        val stamps = current.stampsText.toIntOrNull()
        val cap = parsePesoToCentavos(current.capText)
        if (stamps == null) {
            form.value = current.copy(error = "Enter how many stamps earn a free drink")
            return
        }
        if (cap == null) {
            form.value = current.copy(error = "Enter the most a free drink can be worth, e.g. 150")
            return
        }
        form.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                repository.saveRules(LoyaltyRules(current.isEnabled, stamps, cap))
                form.update { it?.copy(isSaving = false) }
                message.value = "Loyalty card saved"
            } catch (e: Exception) {
                form.update { it?.copy(isSaving = false, error = e.message ?: "Could not save") }
            }
        }
    }

    fun setCategoryEarnsStamps(category: CategoryEntity, earns: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setCategoryEarnsStamps(category.id, earns) }
                .onFailure { message.value = it.message ?: "Could not update ${category.name}" }
        }
    }
}
