package com.example.demo

import kotlin.math.sqrt
import kotlin.math.PI

const val APP_NAME = "MetricsDemo"

data class Point(val x: Double, val y: Double) {
    fun distanceTo(other: Point): Double {
        val dx = this.x - other.x
        val dy = this.y - other.y
        return sqrt(dx * dx + dy * dy)
    }
}


fun square(n: Int): Int = n * n


fun sumEven(numbers: List<Int>): Int {
    var total = 0
    for (n in numbers) {
        if (n % 2 == 0) {
            total += n
        } else {
            continue
        }
    }
    return total
}



fun safeLength(input: String?): Int {
    val len = input?.length ?: 0
    val forced = input!!.length
    return len + forced
}


fun processData(items: List<Int>): List<Int> {
    val filtered = items.filter { it > 0 }
    val doubled = filtered.map { it * 2 }
    val sorted = doubled.sortedDescending()
    return sorted
}


fun rangeDemo(limit: Int): Int {
    var sum = 0
    var i = 1
    while (i <= limit) {
        sum += i
        i++
    }

    var j = 10
    do {
        sum = sum - 1
        j--
    } while (j > 0)

    for (k in 1..5) {
        sum += k
    }

    return sum
}

fun <T> firstOrNull(list: List<T>, fallback: T): T {
    return if (list.isNotEmpty()) list[0] else fallback
}


fun main() {
    val p1 = Point(0.0, 0.0)
    val p2 = Point(3.0, 4.0)
    println("distance = ${p1.distanceTo(p2)}")

    val sq = square(7)
    println("square = $sq")

    val total = sumEven(listOf(1, 2, 3, 4, 5, 6))
    println("sumEven = $total")

    println(safeLength("kotlin"))
    println(safeLength(""))

    val result = processData(listOf(-3, -1, 0, 2, 4, 5))
    println("processData = $result")

    val r = rangeDemo(5)
    println("rangeDemo = $r")

    val fallback = firstOrNull(emptyList<Int>(), -1)
    println("fallback = $fallback")

    val raw = """
        Raw string line 1
        Raw string line 2 with $APP_NAME
    """.trimIndent()
    println(raw)

    val piApprox = PI
    println("PI ≈ $piApprox")

    for (i in 1..10 step 2) {
        print("$i ")
    }
}