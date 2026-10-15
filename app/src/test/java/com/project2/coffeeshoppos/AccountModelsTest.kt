package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.domain.AccessRules
import com.project2.coffeeshoppos.domain.BackupFrequency
import com.project2.coffeeshoppos.domain.BackupReminder
import com.project2.coffeeshoppos.domain.Permission
import com.project2.coffeeshoppos.domain.PinHasher
import com.project2.coffeeshoppos.domain.UserRole
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountModelsTest {
    @Test
    fun adminsCanDoEverything() {
        Permission.entries.forEach { assertTrue(it.name, AccessRules.allows(UserRole.ADMIN, it)) }
    }

    @Test
    fun staffCanOnlyDoTheirJobs() {
        val allowed = Permission.entries.filter { AccessRules.allows(UserRole.STAFF, it) }.toSet()
        assertEquals(
            setOf(Permission.TAKE_ORDERS, Permission.INVENTORY, Permission.CUSTOMERS, Permission.EXPORT, Permission.SAVE_BACKUP),
            allowed
        )
    }

    @Test
    fun pinsAreHashedWithASaltAndChecked() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)
        assertNotEquals("4821", hash)
        assertTrue(PinHasher.matches("4821", salt, hash))
        assertFalse(PinHasher.matches("4822", salt, hash))
        // The same PIN with another salt gives another hash.
        assertNotEquals(hash, PinHasher.hash("4821", PinHasher.newSalt()))
    }

    @Test
    fun weakPinsAreRejected() {
        listOf("123", "1234567", "12a4", "0000", "1234", "4321").forEach { assertNotNull(it, PinHasher.problemWith(it)) }
        listOf("4821", "907315").forEach { assertNull(it, PinHasher.problemWith(it)) }
    }

    @Test
    fun backupIsDueOnceOlderThanTheChosenFrequency() {
        val now = Instant.parse("2026-10-15T10:00:00Z")
        assertFalse("Nothing to lose yet", BackupReminder.isDue(null, now, BackupFrequency.WEEKLY, hasData = false))
        assertTrue("Never backed up", BackupReminder.isDue(null, now, BackupFrequency.WEEKLY, hasData = true))
        assertFalse(BackupReminder.isDue(now.minusSeconds(6 * 86_400), now, BackupFrequency.WEEKLY, hasData = true))
        assertTrue(BackupReminder.isDue(now.minusSeconds(7 * 86_400), now, BackupFrequency.WEEKLY, hasData = true))
        assertTrue(BackupReminder.isDue(now.minusSeconds(86_400), now, BackupFrequency.DAILY, hasData = true))
        assertEquals("9 days ago", BackupReminder.ageLabel(now.minusSeconds(9 * 86_400), now))
        assertEquals("yesterday", BackupReminder.ageLabel(now.minusSeconds(86_400), now))
    }
}
