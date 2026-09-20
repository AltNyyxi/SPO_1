package main

import kotlin.system.exitProcess

fun charToValue(c: Char): Int {
    return when {
        c in '0'..'9' -> c - '0'
        c in 'A'..'Z' -> 10 + (c - 'A')
        c in 'a'..'z' -> 10 + (c - 'a')
        else -> -1
    }
}

fun valueToChar(value: Int): Char {
    return when {
        value in 0..9 -> '0' + value
        value in 10..35 -> 'A' + (value - 10)
        else -> '?'
    }
}

fun validateNumber(number: String, base: Int): Boolean {
    if (number.isEmpty()) return false
    if (base < 2 || base > 36) return false
    for (c in number) {
        val v = charToValue(c)
        if (v < 0 || v >= base) return false
    }
    return true
}

fun convert(number: String, fromBase: Int, toBase: Int): String {
    if (!validateNumber(number, fromBase)) return ""

    var result = 0
    for (c in number) {
        result = result * fromBase + charToValue(c)
    }

    if (result == 0) return "0"

    var output = ""
    var temp = result
    while (temp > 0) {
        val digit = temp % toBase
        output = valueToChar(digit) + output
        temp = temp / toBase
    }
    return output
}

fun main() {
    println("Введите число:")
    val number = readLine() ?: exitProcess(1)

    println("Исходное основание:")
    val fromBase = readLine()?.toIntOrNull() ?: exitProcess(1)

    println("Целевое основание:")
    val toBase = readLine()?.toIntOrNull() ?: exitProcess(1)

    val converted = convert(number, fromBase, toBase)
    if (converted.isEmpty()) {
        println("Ошибка: неверное число или основание")
    } else {
        println("Результат: $converted")
    }

    val check = number.toIntOrNull()
    if (check != null && check > 0) {
        println("Проверка: $check")
    } else {
        println("Проверка не удалась")
    }
}