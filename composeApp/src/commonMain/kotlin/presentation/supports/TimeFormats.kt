package io.github.vrcmteam.vrcm.presentation.supports

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char

object TimeFormats {
    val ISO_LOCAL_DATE_TIME by lazy {
        // 2020-08-30 18:43:13
        LocalDateTime.Format {
            date(LocalDate.Formats.ISO); char(' '); hour(); char(':'); minute(); char(':'); second()
        }
    }
}