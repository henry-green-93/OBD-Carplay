package com.henryg.obdcarplay.shared

/**
 * Data model for the OBD2 PIDs displayed on all platforms.
 *
 * Fields:
 * - waterTemp: engine coolant temperature (Celsius)
 * - oilTemp: engine oil temperature (Celsius)
 * - afr: air/fuel ratio (dimensionless, stoichiometric = ~14.7)
 * - boostKpa: boost pressure / vacuum in kPa (negative = vacuum)
 */
data class ObdData(
    val waterTemp: Int = 0,
    val oilTemp: Int = 0,
    val afr: Double = 0.0,
    val boostKpa: Int = 0
) {
    /** Human-readable combined AFR + Boost string for display. */
    val afrBoostDisplay: String
        get() = "%.2f, %d kPa".format(afr, boostKpa)
}
