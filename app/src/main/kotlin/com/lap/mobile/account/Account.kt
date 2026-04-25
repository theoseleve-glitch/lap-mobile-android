package com.lap.mobile.account

/**
 * Type-safe wrapper for an account identifier (T187).
 *
 * Inline `value class` so it costs nothing at runtime — JVM bytecode
 * passes the underlying [String] directly. The invariant
 * (`isNotBlank`) catches partial-state bugs at construction rather
 * than 200 lines downstream when the empty string would match no
 * account / produce a 400 from the server / silently key into a
 * shared row.
 *
 * v1 uses the placeholder value `"active"` for the single resident
 * account because the real server-side account ID isn't known until
 * T189's `/me` sync. The placeholder is documented in
 * [AccountRepository] — calling code should treat it as opaque.
 */
@JvmInline
value class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "AccountId value must be non-blank" }
    }
}

/**
 * Account record (T187 scaffold).
 *
 * [displayLabel] is the user-facing handle (email / display name)
 * when known — null until T189's `/me` sync populates it. Keeping
 * the slot here means T189 doesn't have to add a column or break
 * the [Account] shape; it just fills in what was reserved.
 */
data class Account(
    val id: AccountId,
    val displayLabel: String?,
)
