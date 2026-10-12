package com.project2.coffeeshoppos.domain

/** The owner's loyalty card rules. */
data class LoyaltyRules(
    val isEnabled: Boolean,
    /** Stamps needed for one free drink. */
    val stampsForReward: Int,
    /** The most a free drink can take off the bill. */
    val rewardCapCentavos: Long,
) {
    companion object {
        /** Used until the owner saves their own rules: 10 stamps for a free drink worth up to ₱150. */
        val DEFAULT = LoyaltyRules(isEnabled = true, stampsForReward = 10, rewardCapCentavos = 15_000)

        val STAMPS_RANGE = 2..50
        val CAP_RANGE_CENTAVOS = 100L..1_000_000L
    }
}

/** Stamp math for the loyalty card. Pure functions so they are easy to test. */
object LoyaltyMath {
    /**
     * Cleans up a Philippine mobile number to "09171234567", accepting the usual ways people say
     * it: "0917 123 4567", "+63 917-123-4567", "639171234567" or "9171234567".
     * Returns null when it is not a valid mobile number.
     */
    fun normalizePhone(input: String): String? {
        val digits = input.filter { it.isDigit() }
        val local = when {
            digits.length == 12 && digits.startsWith("63") -> "0" + digits.drop(2)
            digits.length == 10 && digits.startsWith("9") -> "0$digits"
            else -> digits
        }
        return local.takeIf { it.length == 11 && it.startsWith("09") }
    }

    /** "09171234567" -> "0917 123 4567", for display. */
    fun formatPhone(phone: String): String =
        if (phone.length == 11) "${phone.take(4)} ${phone.substring(4, 7)} ${phone.drop(7)}" else phone

    /**
     * Stamps an order earns: one per item that earns stamps. The free drink itself, if one was
     * used, does not earn a stamp.
     */
    fun stampsEarned(eligibleItemCount: Int, usedReward: Boolean): Int =
        (eligibleItemCount - if (usedReward) 1 else 0).coerceAtLeast(0)

    /** Free drinks a customer can claim with [stamps]. */
    fun rewardsAvailable(stamps: Int, rules: LoyaltyRules): Int =
        if (rules.stampsForReward <= 0) 0 else (stamps / rules.stampsForReward).coerceAtLeast(0)

    /** Stamps still needed for the next free drink, e.g. 3 when a customer has 7 of 10. */
    fun stampsToNextReward(stamps: Int, rules: LoyaltyRules): Int =
        rules.stampsForReward - (stamps.coerceAtLeast(0) % rules.stampsForReward)

    /**
     * What a free drink takes off the bill: the price of one of the drink's size, up to the cap.
     * Add-ons on the free drink are still charged.
     */
    fun rewardAmount(drinkPriceCentavos: Long, rules: LoyaltyRules): Long =
        drinkPriceCentavos.coerceIn(0, rules.rewardCapCentavos)
}
