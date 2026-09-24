package com.printylan.model

/** The settings the user picked in the system print dialog. */
data class JobTicket(
    val copies: Int,
    val media: MediaSize,
    val resolution: Resolution,
    val colorMode: ColorMode,
    val duplexMode: DuplexMode,
)
