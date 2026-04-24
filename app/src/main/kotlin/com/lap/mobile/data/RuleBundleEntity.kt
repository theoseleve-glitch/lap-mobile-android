package com.lap.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Cache of the compiled coaching-rules bundle the rules-engine-lite
 * (T211/T214) evaluates against session features. Fetched at login by
 * T212, validated at read time per T213 (TTL 7 days via [fetchedAt],
 * HMAC/ECDSA check against [signature]).
 *
 * [bundleJson] is opaque JSON — T211 specifies a rule-engine interface
 * whose exact payload shape is backend-owned and may change
 * independently. Keeping the payload as text isolates Room from that
 * churn; only the envelope (version, signature, fetched_at) is
 * SQL-level.
 *
 * [id] is the bundle identifier (typically the calver version);
 * duplicate fetches overwrite. No FK — rules bundles are global, not
 * per-driver (T216 filters server-side by `applicable_to: mobile_real_life`
 * tag before serving the subset to mobile).
 */
@Entity(tableName = "rule_bundle")
data class RuleBundleEntity(
    @PrimaryKey
    val id: String,
    val version: String,
    @ColumnInfo(name = "bundle_json")
    val bundleJson: String,
    val signature: String,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Instant,
)
