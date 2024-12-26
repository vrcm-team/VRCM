package io.github.vrcmteam.vrcm.presentation.extensions

import kotlinx.datetime.*
import kotlinx.datetime.format.char

val LocalDateTime.ignoredFormat: String
    get() = format(
        LocalDateTime.Format {
            val nowTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            if (nowTime.year != this@ignoredFormat.year) {
                year(); char('/')
            }
            if (nowTime.month != this@ignoredFormat.month || nowTime.dayOfMonth != this@ignoredFormat.dayOfMonth) {
                monthNumber(); char('/'); dayOfMonth(); char(' ')
            }
            hour(); char(':'); minute()
        })

