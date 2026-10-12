package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.domain.LoyaltyMath
import com.project2.coffeeshoppos.domain.LoyaltyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoyaltyMathTest {
    private val rules = LoyaltyRules(isEnabled = true, stampsForReward = 10, rewardCapCentavos = 15_000)

    @Test
    fun normalizesTheUsualWaysToWriteAMobileNumber() {
        listOf("09171234567", "0917 123 4567", "0917-123-4567", "+63 917 123 4567", "639171234567", "9171234567")
            .forEach { assertEquals(it, "09171234567", LoyaltyMath.normalizePhone(it)) }
    }

    @Test
    fun rejectsNumbersThatAreNotMobileNumbers() {
        listOf("", "1234", "0917123456", "091712345678", "02 8123 4567", "08171234567")
            .forEach { assertNull(it, LoyaltyMath.normalizePhone(it)) }
    }

    @Test
    fun formatsPhoneForDisplay() {
        assertEquals("0917 123 4567", LoyaltyMath.formatPhone("09171234567"))
    }

    @Test
    fun theFreeDrinkDoesNotEarnAStamp() {
        assertEquals(3, LoyaltyMath.stampsEarned(eligibleItemCount = 3, usedReward = false))
        assertEquals(2, LoyaltyMath.stampsEarned(eligibleItemCount = 3, usedReward = true))
        assertEquals(0, LoyaltyMath.stampsEarned(eligibleItemCount = 1, usedReward = true))
        assertEquals(0, LoyaltyMath.stampsEarned(eligibleItemCount = 0, usedReward = true))
    }

    @Test
    fun countsRewardsAndStampsToGo() {
        assertEquals(0, LoyaltyMath.rewardsAvailable(9, rules))
        assertEquals(1, LoyaltyMath.rewardsAvailable(10, rules))
        assertEquals(2, LoyaltyMath.rewardsAvailable(25, rules))
        assertEquals(0, LoyaltyMath.rewardsAvailable(-3, rules))
        assertEquals(3, LoyaltyMath.stampsToNextReward(7, rules))
        assertEquals(10, LoyaltyMath.stampsToNextReward(10, rules))
        assertEquals(10, LoyaltyMath.stampsToNextReward(0, rules))
    }

    @Test
    fun rewardIsTheDrinkPriceUpToTheCap() {
        // ₱120 drink under a ₱150 cap is fully free; a ₱180 drink gets ₱150 off.
        assertEquals(12_000, LoyaltyMath.rewardAmount(12_000, rules))
        assertEquals(15_000, LoyaltyMath.rewardAmount(18_000, rules))
        assertEquals(0, LoyaltyMath.rewardAmount(-5, rules))
    }
}
