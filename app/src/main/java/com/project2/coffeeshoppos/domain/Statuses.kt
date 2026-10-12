package com.project2.coffeeshoppos.domain

// Room stores these enums in the database by their name (e.g. "COMPLETED").
// Never rename an existing value: old rows would no longer match. Adding new values is fine.

enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    REFUNDED("Refunded"),
}

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    GCASH("GCash"),
    CARD("Card"),
    OTHER("Other"),
}

/** Why stock changed. Every change to an ingredient's quantity gets one movement record. */
enum class MovementType(val label: String) {
    OPENING_BALANCE("Opening balance"),
    PURCHASE("Purchase"),
    SALE("Sale"),
    WASTAGE("Wastage / spoilage"),
    ADJUSTMENT("Manual adjustment"),
    REFUND_RETURN("Returned from refund"),
    /** Fixes a wrongly entered cost. Revalues the stock on hand; the quantity does not change. */
    COST_CORRECTION("Cost correction"),
}

/** Why a customer's stamps changed. Every change gets one loyalty entry. */
enum class LoyaltyEntryType(val label: String) {
    EARNED("Earned"),
    REDEEMED("Free drink"),
    EARNED_REVERSED("Taken back (refund)"),
    REDEEM_RETURNED("Free drink given back"),
    ADJUSTMENT("Manual adjustment"),
}

enum class ExpenseCategory(val label: String) {
    RENT("Rent"),
    ELECTRICITY("Electricity"),
    WATER("Water"),
    WIFI("Wi-Fi"),
    SALARIES("Salaries"),
    SUPPLIES("Supplies"),
    MAINTENANCE("Maintenance"),
    TRANSPORT("Transport"),
    MARKETING("Marketing"),
    OTHER("Other"),
}

enum class PaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PAID("Paid"),
}

enum class RecurrenceFrequency(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
}

enum class AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    STATUS_CHANGE,
    ARCHIVE,
    RESTORE,
}
