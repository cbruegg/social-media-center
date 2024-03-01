package util

fun String.isNumeric(): Boolean = all { it.isDigit() }