package com.mysecunion.app

/** Simple dotted-numeric semver comparison for FR-501/503. Non-numeric parts sort as 0. */
object VersionUtils {
    fun isLower(current: String, other: String): Boolean = compare(current, other) < 0

    private fun compare(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
