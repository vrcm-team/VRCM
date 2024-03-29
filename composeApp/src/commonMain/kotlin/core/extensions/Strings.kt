package io.github.vrcmteam.vrcm.core.extensions

fun String.capitalizeFirst() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }


fun CharSequence.isDigitsOnly(): Boolean = this.any { char -> char.isDigit() }