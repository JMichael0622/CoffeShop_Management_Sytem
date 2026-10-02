package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.domain.ProductFormValidator
import com.project2.coffeeshoppos.domain.SizeFormInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFormValidatorTest {
    private fun size(key: Long, name: String, price: String) = SizeFormInput(key, id = 0, name = name, priceText = price)

    @Test
    fun singleUnnamedSizeIsValidAndBecomesRegular() {
        val sizes = listOf(size(1, "", "120"))
        val errors = ProductFormValidator.validate("Latte", sizes)
        assertFalse(errors.hasErrors)
        val saved = ProductFormValidator.toSizeInputs(sizes).single()
        assertEquals("Regular", saved.name)
        assertEquals(12_000L, saved.priceCentavos)
    }

    @Test
    fun requiresProductName() {
        val errors = ProductFormValidator.validate("  ", listOf(size(1, "", "120")))
        assertNotNull(errors.name)
    }

    @Test
    fun requiresAtLeastOneSize() {
        val errors = ProductFormValidator.validate("Latte", emptyList())
        assertNotNull(errors.general)
    }

    @Test
    fun flagsBadPrice() {
        val errors = ProductFormValidator.validate("Latte", listOf(size(1, "", "abc")))
        assertTrue(1L in errors.sizes)
    }

    @Test
    fun multipleSizesNeedDistinctNames() {
        val errors = ProductFormValidator.validate(
            "Latte",
            listOf(size(1, "12 oz", "120"), size(2, "12 OZ ", "140"), size(3, "", "160"))
        )
        assertFalse(1L in errors.sizes)
        assertTrue(2L in errors.sizes)
        assertTrue(3L in errors.sizes)
    }
}
