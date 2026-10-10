package com.ibkrtax.mobile.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Paging, filters and speed of the Operations list against the real SQLCipher database (synthetic rows). */
@RunWith(AndroidJUnit4::class)
class OperationsRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "operations-test.db"
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var repository: OperationsRepository

    private val types = listOf("BUY", "SELL", "DIVIDEND", "TAX", "SPLIT")
    private val tickers = listOf("AAPL", "MSFT", "BRK B", "SAP", "T_X")

    @Before
    fun setUp() {
        EncryptedDatabase.delete(context, dbName)
        helper = EncryptedDatabase.open(context, dbName)
        repository = OperationsRepository(helper)
    }

    @After
    fun tearDown() {
        helper.close()
        EncryptedDatabase.delete(context, dbName)
    }

    /** Row i: ticker i % 5, type i % 5 shifted by i / 5 so every ticker gets every type; one row per day from 2000. */
    private fun insertRows(count: Int) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("INSERT INTO reports (id, fingerprint, imported_at, inserted_count, skipped_count) VALUES (1, 'f', '2026-01-01', $count, 0)")
            for (i in 0 until count) {
                val date = java.time.LocalDate.of(2000, 1, 1).plusDays(i.toLong()).toString()
                db.execSQL(
                    "INSERT INTO transactions (report_id, source_key, date, event_type, ticker, quantity, price, currency, amount, fee, description) " +
                        "VALUES (1, ?, ?, ?, ?, '1', '10', 'USD', '10', '0', '')",
                    arrayOf<Any?>("k$i", date, types[(i + i / 5) % 5], tickers[i % 5]),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Test
    fun emptyDatabaseHasNoOperations() {
        assertFalse(repository.hasAny())
        assertTrue(repository.page(emptySet(), "", 100, 0).isEmpty())
    }

    @Test
    fun pagesAreNewestFirstAndDoNotOverlap() {
        insertRows(250)
        val first = repository.page(emptySet(), "", 100, 0)
        val second = repository.page(emptySet(), "", 100, 100)
        val third = repository.page(emptySet(), "", 100, 200)

        assertTrue(repository.hasAny())
        assertEquals(listOf(100, 100, 50), listOf(first.size, second.size, third.size))
        val all = first + second + third
        assertEquals(all.map { it.date }.sortedDescending(), all.map { it.date })
        assertEquals(250, all.map { it.id }.toSet().size)
    }

    @Test
    fun searchAndFiltersCombine() {
        insertRows(500)
        val dividendsOfAapl = repository.page(setOf(OperationFilter.DIVIDENDS), "aap", 1000, 0)
        assertEquals(20, dividendsOfAapl.size)
        assertTrue(dividendsOfAapl.all { it.ticker == "AAPL" && it.eventType == "DIVIDEND" })

        val other = repository.page(setOf(OperationFilter.OTHER, OperationFilter.SELLS), "", 1000, 0)
        assertEquals(setOf("SPLIT", "SELL"), other.map { it.eventType }.toSet())
        assertEquals(200, other.size)
    }

    @Test
    fun wildcardsInTheSearchAreLiteral() {
        insertRows(50)
        assertEquals(setOf("T_X"), repository.page(emptySet(), "_", 100, 0).map { it.ticker }.toSet())
        assertEquals(setOf("BRK B"), repository.page(emptySet(), "k b", 100, 0).map { it.ticker }.toSet())
        assertTrue(repository.page(emptySet(), "%", 100, 0).isEmpty())
    }

    @Test
    fun aPageOfTwentyThousandRowsLoadsQuickly() {
        insertRows(20_000)
        repository.page(emptySet(), "", 100, 0) // warm up the connection and statement cache
        val unfiltered = timeMillis { repository.page(emptySet(), "", 100, 0) }
        val filtered = timeMillis { repository.page(setOf(OperationFilter.DIVIDENDS), "ms", 100, 0) }
        val deep = timeMillis { repository.page(emptySet(), "", 100, 19_000) }
        assertTrue("unfiltered page took $unfiltered ms", unfiltered < 200)
        assertTrue("filtered page took $filtered ms", filtered < 200)
        assertTrue("deep page took $deep ms", deep < 200)
    }

    private fun timeMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
