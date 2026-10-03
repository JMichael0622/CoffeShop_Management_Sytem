package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.data.repository.shortOrderNumber
import com.project2.coffeeshoppos.domain.CartLine
import com.project2.coffeeshoppos.domain.CartMath
import com.project2.coffeeshoppos.domain.Discount
import com.project2.coffeeshoppos.domain.PosAddOn
import com.project2.coffeeshoppos.domain.PosMenu
import com.project2.coffeeshoppos.domain.PosProduct
import com.project2.coffeeshoppos.domain.PosSize
import com.project2.coffeeshoppos.domain.priceCartLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartMathTest {
    private val latte = PosProduct(
        id = 1, name = "Iced Latte", categoryId = null,
        sizes = listOf(PosSize(10, "12 oz", 12_000), PosSize(11, "16 oz", 15_000)),
        addOns = listOf(PosAddOn(100, "Extra shot", 3_000), PosAddOn(101, "Oat milk", 4_000))
    )
    private val cookie = PosProduct(2, "Cookie", null, listOf(PosSize(20, "Regular", 6_500)), emptyList())
    private val menu = PosMenu(emptyList(), listOf(latte, cookie))

    @Test
    fun pricesLinesFromTheLiveMenu() {
        // 2 × (16 oz ₱150 + 2 extra shots ₱60 + oat milk ₱40) = ₱500
        val line = priceCartLine(CartLine(1, 1, 11, mapOf(100L to 2, 101L to 1), quantity = 2), menu)
        assertEquals(10_000L, line.addOnsUnitPriceCentavos)
        assertEquals(50_000L, line.lineTotalCentavos)
        assertEquals("Iced Latte · 16 oz", line.title)
        assertNull(line.unavailableReason)

        val single = priceCartLine(CartLine(2, 2, 20, emptyMap(), quantity = 1), menu)
        assertEquals("Cookie", single.title)
    }

    @Test
    fun flagsItemsThatLeftTheMenu() {
        assertNotNull(priceCartLine(CartLine(1, 1, 99, emptyMap(), 1), menu).unavailableReason)
        assertNotNull(priceCartLine(CartLine(1, 3, 30, emptyMap(), 1), menu).unavailableReason)
        assertNotNull(priceCartLine(CartLine(1, 1, 10, mapOf(555L to 1), 1), menu).unavailableReason)
    }

    @Test
    fun discountsRoundAndNeverExceedTheSubtotal() {
        assertEquals(0L, CartMath.discountAmount(12_345, Discount.None))
        assertEquals(2_469L, CartMath.discountAmount(12_345, Discount.Percent(20))) // 2,469.0
        assertEquals(617L, CartMath.discountAmount(12_345, Discount.Percent(5))) // 617.25 -> 617
        assertEquals(12_345L, CartMath.discountAmount(12_345, Discount.Amount(99_999)))
        assertEquals(9_876L, CartMath.total(12_345, Discount.Percent(20)))
    }

    @Test
    fun suggestsExactAndRoundCashAmounts() {
        assertEquals(listOf(18_500L, 20_000L, 50_000L, 100_000L), CartMath.suggestedCash(18_500))
        assertEquals(listOf(10_000L, 50_000L, 100_000L), CartMath.suggestedCash(10_000))
        assertTrue(CartMath.suggestedCash(0).isEmpty())
    }

    @Test
    fun shortOrderNumberIsTheDailyCounter() {
        assertEquals("#007", shortOrderNumber("261003-007"))
        assertFalse(shortOrderNumber("261003-007").contains("261003"))
    }
}
