package com.project2.coffeeshoppos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.project2.coffeeshoppos.domain.LoyaltyEntryType
import java.time.Instant

// Loyalty card: customers collect stamps for drinks and trade them for a free one.

/**
 * A regular customer, found at the counter by mobile number or name.
 * Deleting a customer clears their name and number but keeps the row, so past orders and
 * stamp history still add up.
 */
@Entity(
    tableName = "customers",
    indices = [Index(value = ["phone"], unique = true), Index("name")]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Normalized to "09171234567". Null only after the customer is deleted. */
    val phone: String?,
    val note: String = "",
    val isDeleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * The stamp ledger. Every change to a customer's stamps writes exactly one row here,
 * so a customer's stamp count is always the sum of their entries.
 */
@Entity(
    tableName = "loyalty_entries",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("customerId"), Index("orderId"), Index("createdAt")]
)
data class LoyaltyEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val type: LoyaltyEntryType,
    /** Positive adds stamps, negative removes them. */
    val stampsChange: Int,
    val reason: String,
    val orderId: Long? = null,
    val createdAt: Instant,
)

/**
 * The owner's loyalty rules. There is only ever one row (id = 1); until the owner saves
 * settings it does not exist and [com.project2.coffeeshoppos.domain.LoyaltyRules.DEFAULT] applies.
 */
@Entity(tableName = "loyalty_settings")
data class LoyaltySettingsEntity(
    @PrimaryKey val id: Long = SINGLE_ROW_ID,
    @ColumnInfo(defaultValue = "1") val isEnabled: Boolean = true,
    /** Stamps a customer must collect for one free drink. */
    val stampsForReward: Int,
    /** The most a free drink can take off the bill; a pricier drink is discounted by this much. */
    val rewardCapCentavos: Long,
    val updatedAt: Instant,
) {
    companion object {
        const val SINGLE_ROW_ID = 1L
    }
}
