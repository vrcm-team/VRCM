package io.github.vrcmteam.vrcm.core.extensions

fun String.capitalizeFirst() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }


fun CharSequence.isDigitsOnly(): Boolean = this.all{ char -> char.isDigit() }

fun String.omission(maxLength: Int)= this.takeIf { it.length < maxLength }
    ?: "${this.substring(0,maxLength)}..."

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