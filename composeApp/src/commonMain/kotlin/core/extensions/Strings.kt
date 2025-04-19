package io.github.vrcmteam.vrcm.core.extensions

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun String.capitalizeFirst() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }


fun CharSequence.isDigitsOnly(): Boolean = this.all{ char -> char.isDigit() }

fun String.omission(maxLength: Int)= this.takeIf { it.length < maxLength }
    ?: "${this.substring(0,maxLength)}..."

fun String.toLocalDateTime() = Instant.parse(this).toLocalDateTime(TimeZone.currentSystemDefault())
fun String.toLocalDate() = Instant.parse(this).toLocalDateTime(TimeZone.currentSystemDefault()).date

fun String.pretty() = this.let { string ->
    var indentLevel = 0
    val indentWidth = 4
    fun padding() = "".padStart(indentLevel * indentWidth)
    buildString {
        var ignoreSpace = false
        string.onEach { char ->
            when (char) {
                '(', '[', '{' -> {
                    indentLevel++
                    appendLine(char)
                    append(padding())
                }

                ')', ']', '}' -> {
                    indentLevel--
                    appendLine()
                    append(padding())
                    append(char)
                }

                ',' -> {
                    appendLine(char)
                    append(padding())
                    ignoreSpace = true
                }
                ' ' -> {
                    if (!ignoreSpace) append(char)
                    ignoreSpace = false
                }

                else -> append(char)
            }
        }
    }
}