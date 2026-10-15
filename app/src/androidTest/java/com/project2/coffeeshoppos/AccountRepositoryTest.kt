package com.project2.coffeeshoppos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AccountRepository
import com.project2.coffeeshoppos.data.repository.AuditEntityType
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.SessionManager
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.domain.UserRole
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A clock the test can move forward. */
private class MovableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

/** Runs on the phone against a temporary in-memory database (your real data is not touched). */
@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest {
    private lateinit var db: CoffeeShopDatabase
    private lateinit var accounts: AccountRepository
    private lateinit var session: SessionManager
    private lateinit var audit: AuditRepository
    private val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CoffeeShopDatabase::class.java).build()
        audit = AuditRepository(db, clock) { session.current.value }
        accounts = AccountRepository(db, audit, clock)
        session = SessionManager(accounts, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun firstAccountIsAnAdminAndOnlyOnce() = runBlocking<Unit> {
        assertFalse(accounts.observeIsSetUp().first())
        val id = accounts.createFirstAdmin("Mika", "4821")
        assertTrue(accounts.observeIsSetUp().first())
        assertEquals(UserRole.ADMIN, accounts.observeAccounts().first().single().role)
        assertThrows(UserFacingException::class.java) { runBlocking { accounts.createFirstAdmin("Someone", "4821") } }

        session.signIn(id, "4821")
        assertEquals("Mika", session.current.value?.name)
    }

    @Test
    fun pinsAreNeverStoredAndWrongPinsLockTheAccount() = runBlocking<Unit> {
        val id = accounts.createFirstAdmin("Mika", "4821")
        val stored = db.accountDao().get(id)!!
        assertFalse(stored.pinHash.contains("4821"))

        repeat(AccountRepository.MAX_FAILURES) {
            assertThrows(UserFacingException::class.java) { runBlocking { session.signIn(id, "9999") } }
        }
        // Locked: even the right PIN is refused for now.
        assertThrows(UserFacingException::class.java) { runBlocking { session.signIn(id, "4821") } }
        assertNull(session.current.value)

        clock.now = clock.now.plus(AccountRepository.LOCKOUT).plusSeconds(1)
        session.signIn(id, "4821")
        assertEquals(id, session.current.value?.id)
    }

    @Test
    fun theLastAdminCanNotBeRemoved() = runBlocking<Unit> {
        val admin = accounts.createFirstAdmin("Mika", "4821")
        val staff = accounts.addAccount("Ben", UserRole.STAFF, "7392")

        assertThrows(UserFacingException::class.java) { runBlocking { accounts.setActive(admin, false, actingUserId = staff) } }
        assertThrows(UserFacingException::class.java) { runBlocking { accounts.setRole(admin, UserRole.STAFF, actingUserId = staff) } }
        assertThrows(UserFacingException::class.java) { runBlocking { accounts.setActive(admin, false, actingUserId = admin) } }

        // With a second admin, the first can be made staff by someone else.
        accounts.setRole(staff, UserRole.ADMIN, actingUserId = admin)
        accounts.setRole(admin, UserRole.STAFF, actingUserId = staff)
        assertEquals(UserRole.STAFF, db.accountDao().get(admin)!!.role)
    }

    @Test
    fun deactivatedAccountsCanNotSignIn() = runBlocking<Unit> {
        val admin = accounts.createFirstAdmin("Mika", "4821")
        val staff = accounts.addAccount("Ben", UserRole.STAFF, "7392")
        accounts.setActive(staff, false, actingUserId = admin)
        assertThrows(UserFacingException::class.java) { runBlocking { session.signIn(staff, "7392") } }

        assertThrows(UserFacingException::class.java) { runBlocking { accounts.addAccount("ben", UserRole.STAFF, "5813") } }
        assertThrows(UserFacingException::class.java) { runBlocking { accounts.addAccount("Cara", UserRole.STAFF, "1111") } }
    }

    @Test
    fun auditRecordsSayWhoMadeTheChange() = runBlocking<Unit> {
        val admin = accounts.createFirstAdmin("Mika", "4821")
        session.signIn(admin, "4821")
        val staff = accounts.addAccount("Ben", UserRole.STAFF, "7392")

        val record = audit.observeFor(AuditEntityType.ACCOUNT, staff).first().single()
        assertTrue(record.details, record.details.contains("By Mika (Admin)"))
    }

    @Test
    fun leavingTheAppForAWhileSignsOut() = runBlocking<Unit> {
        val admin = accounts.createFirstAdmin("Mika", "4821")
        session.signIn(admin, "4821")

        session.onAppBackgrounded()
        clock.now = clock.now.plusSeconds(60)
        session.onAppForegrounded()
        assertEquals("A short break keeps you signed in", admin, session.current.value?.id)

        session.onAppBackgrounded()
        clock.now = clock.now.plus(SessionManager.AUTO_SIGN_OUT).plus(Duration.ofSeconds(1))
        session.onAppForegrounded()
        assertNull(session.current.value)
    }
}
