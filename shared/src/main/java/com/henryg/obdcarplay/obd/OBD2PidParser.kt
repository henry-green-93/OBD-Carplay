package com.henryg.obdcarplay.obd

import android.util.Log

/**
 * Parser for ELM327 OBD2 PID responses.
 *
 * Handles the standard ELM327 response format:
 * - "41 XX YY" - Mode 01, PID 0xXX, data YY
 * - "7E8 01 XX YY" - Mode 01 response from EC
 * - "NO DATA", "BUSY", etc.
 *
 * Decodes:
 * - Engine RPM: ((256*A)+B)/4
 * - Coolant temp: A-40
 * - AFR: (100/200) * A/B
 * - Boost/Vacuum: A-40 (in kPa, negative = vacuum)
 */
class OBD2PidParser {

    companion object {
        private const val TAG = "OBD2PidParser"
        private const val ELM327_PROMPT = ">"
    }

    /**
     * Parse an ELM327 response for a given PID.
     *
     * @param pid The PID command sent (e.g., "01 0C" for RPM)
     * @param response The raw response from the ELM327
     * @return The parsed value as a string, or null if unparseable
     */
    fun parse(pid: String, response: String): String? {
        // Clean response - remove prompt, spaces, "NO DATA", etc.
        val clean = response
            .replace(ELM327_PROMPT, "")
            .trim()

        // Handle "NO DATA" / "BUSY" responses
        if (clean.contains("NO DATA", ignoreCase = true) ||
            clean.contains("BUSY", ignoreCase = true)) {
            return "N/A"
        }

        // Extract hex values from Mode 01 response (41 XX YY...)
        val values = extractHexValues(clean)
        if (values.isEmpty()) {
            Log.w(TAG, "Could not parse response: $response")
            return null
        }

        // Parse based on the PID
        val pidByte = pid.split(" ").getOrNull(1)?.toIntOrNull(16)
            ?: return null

        return when (pidByte) {
            0x05 -> decodeCoolantTemp(values)       // PID 05 - Engine Coolant Temp
            0x0C -> decodeRpm(values)               // PID 0C - Engine RPM
            0x0B -> decodeManifoldPressure(values)  // PID 0B - Manifold Pressure (boost/vacuum)
            0x44 -> decodeAirFuelRatio(values)      // PID 44 - Air/Fuel Ratio
            0x0D -> decodeVehicleSpeed(values)      // PID 0D - Vehicle Speed
            else -> decodeGeneric(values)           // Generic hex fallback
        }
    }

    /**
     * Decode engine coolant temperature.
     * Formula: A - 40 (where A is first data byte)
     * Valid range: -40°C to 215°C
     */
    private fun decodeCoolantTemp(values: List<Int>): String? {
        val a = values[0]
        val tempCelsius = a - 40
        return "${tempCelsius}°C"
    }

    /**
     * Decode engine oil temperature (same formula as coolant temp).
     * Formula: A - 40
     */
    fun decodeOilTemp(values: List<Int>): String? {
        val a = values[0]
        val tempCelsius = a - 40
        return "${tempCelsius}°C"
    }

    /**
     * Decode engine RPM.
     * Formula: ((256 * A) + B) / 4
     */
    private fun decodeRpm(values: List<Int>): String? {
        val a = values[0].toLong()
        val b = values.getOrNull(1)?.toLong() ?: 0L
        val rpm = ((256 * a) + b) / 4.0
        return if (rpm < 0) "0" else "%.0f RPM".format(rpm)
    }

    /**
     * Decode manifold pressure / boost / vacuum.
     * Formula: A - 40 (result in kPa, negative = vacuum)
     * Range: -40 kPa to 215 kPa (absolute pressure)
     */
    private fun decodeManifoldPressure(values: List<Int>): String? {
        val a = values[0]
        val kpa = a - 40
        return if (kpa < 0) {
            "$kpa kPa (vacuum)"
        } else {
            "$kpa kPa (boost)"
        }
    }

    /**
     * Decode air/fuel ratio.
     * Formula: (200 * A) / B
     * Stoichiometric = ~14.7
     */
    private fun decodeAirFuelRatio(values: List<Int>): String? {
        if (values.size < 2) return null
        val a = values[0].toDouble()
        val b = values[1].toDouble()
        if (b == 0.0) return "N/A"
        val afr = (200.0 * a) / b
        return "%.2f".format(afr)
    }

    /**
     * Decode vehicle speed.
     * Formula: A (already in km/h)
     */
    private fun decodeVehicleSpeed(values: List<Int>): String? {
        val a = values[0]
        return "${a} km/h"
    }

    /**
     * Decode generic hex response.
     */
    private fun decodeGeneric(values: List<Int>): String? {
        return values.joinToString(", ") { "0x${it.toString(16).uppercase().padStart(2, '0')}" }
    }

    /**
     * Extract hex byte values from an ELM327 response.
     *
     * Handles responses like:
     * - "41 0C 1A2B" → [0x0C, 0x1A, 0x2B]
     * - "4105 52" → [0x05, 0x52]
     * - "7E8 41 05 52" → [0x41, 0x05, 0x52]
     */
    private fun extractHexValues(response: String): List<Int> {
        val values = mutableListOf<Int>()
        val hexParts = response.trim().split(Regex("\\s+"))

        // Skip the first byte if it's a mode/response identifier (e.g., "41", "7E8")
        var startIdx = 0
        if (hexParts.isNotEmpty() && hexParts[0].matches(Regex("^[0-9A-Fa-f]{2,4}$"))) {
            // Check if it's a valid hex mode byte (2 chars) or response ID (3-4 chars)
            val firstByte = hexParts[0]
            if (firstByte.length <= 2) {
                // Likely a mode byte (41 = Mode 01 response)
                try {
                    val mode = firstByte.toInt(16)
                    if (mode in 0x41..0x4F) {
                        startIdx = 1 // Skip the mode byte
                    }
                } catch (e: NumberFormatException) {
                    // Not a simple mode byte
                }
            }
        }

        // Parse remaining hex bytes (pairs)
        for (i in startIdx until hexParts.size) {
            val part = hexParts[i].trim()
            if (part.length == 2 && part.matches(Regex("^[0-9A-Fa-f]{2}$"))) {
                try {
                    values.add(part.toInt(16))
                } catch (e: NumberFormatException) {
                    // Skip invalid hex
                }
            }
        }

        return values
    }
}
