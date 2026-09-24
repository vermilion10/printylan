package com.printylan.model

/**
 * An IEEE 1284 Device ID, the `KEY:value;` string a USB printer returns from GET_DEVICE_ID.
 * Example: `MFG:HP;MDL:LaserJet P1102;CMD:PJL,PCL,PWGRaster;CLS:PRINTER;`
 */
data class DeviceId(val fields: Map<String, String>) {
    val manufacturer: String? get() = field("MFG", "MANUFACTURER")
    val model: String? get() = field("MDL", "MODEL")
    val serialNumber: String? get() = field("SN", "SERIALNUMBER")
    val description: String? get() = field("DES", "DESCRIPTION")

    /** Page description languages the printer accepts, upper cased (for example `PCL`, `PDF`). */
    val commandSet: List<String>
        get() = field("CMD", "COMMAND SET", "COMMANDSET")
            ?.split(',')
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    val displayName: String?
        get() = when {
            manufacturer != null && model != null && !model!!.startsWith(manufacturer!!, ignoreCase = true) ->
                "$manufacturer $model"
            else -> model ?: description
        }

    private fun field(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { fields[it] }?.takeIf { it.isNotBlank() }

    companion object {
        fun parse(raw: String): DeviceId {
            val fields = raw.split(';')
                .mapNotNull { entry ->
                    val colon = entry.indexOf(':')
                    if (colon <= 0) return@mapNotNull null
                    entry.substring(0, colon).trim().uppercase() to entry.substring(colon + 1).trim()
                }
                .toMap()
            return DeviceId(fields)
        }
    }
}
