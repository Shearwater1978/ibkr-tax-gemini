package com.ibkrtax.mobile.ui.common

/**
 * Year choices on income screens (mobile-year-selection spec): the newest year with data and
 * the years with data up to two years before it are shown directly, newest first; all other
 * years go into a selector.
 */
data class YearChoices(val direct: List<Int>, val older: List<Int>) {
    companion object {
        fun of(years: Collection<Int>): YearChoices {
            val newestFirst = years.distinct().sortedDescending()
            val newest = newestFirst.firstOrNull() ?: return YearChoices(emptyList(), emptyList())
            val direct = newestFirst.filter { it >= newest - 2 }
            return YearChoices(direct, newestFirst - direct.toSet())
        }
    }
}
