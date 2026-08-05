package com.henryg.obdcarplay.shared

/**
 * Immutable data model for OBD2 telemetry.
 *
 * Contains engine metrics read from the ECU:
 * - waterTemp: engine coolant temperature in Celsius
 * - oilTemp: engine oil temperature in Celsius
 * - afr: air/fuel ratio (dimensionless; stoichiometric ≈ 14.7)
 * - boostKpa: absolute manifold pressure in kPa (negative = vacuum)
 *
 * This class is shared across all platform modules (mobile, automotive, etc.).
 */
data class ObdData(
    val waterTemp: Int = 0,
    val oilTemp: Int = 0,
    val afr: Double = 0.0,
    val boostKpa: Int = 0
) {
    /** Human-readable combined AFR + Boost string for display. */
    val afrBoostDisplay: String
        get() = "AFR: %.2f | Boost: %d kPa".format(afr, boostKpa)

    companion object {
        /** Zero-filled default instance used when no connection exists. */
        val Empty = ObdData()
    }
}
