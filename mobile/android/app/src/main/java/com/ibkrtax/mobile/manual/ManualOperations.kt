package com.ibkrtax.mobile.manual

import com.ibkrtax.mobile.importer.StoredTransaction
import com.ibkrtax.mobile.storage.ManualEntry
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import org.json.JSONObject

/** A stored operation; [entryId] is null for imported rows. */
data class StoredRow(val entryId: String?, val row: StoredTransaction)

/** Where manual entries live; implemented by the encrypted database. */
interface ManualStore {
    /** Every operation, imported and manual, by date and then insertion order. */
    fun allRows(): List<StoredRow>

    fun get(entryId: String): ManualEntry?

    fun save(entry: ManualEntry)

    fun delete(entryId: String): Boolean
}

sealed interface ManualSaveResult {
    data class Saved(val entryId: String) : ManualSaveResult

    data class Problems(val problems: List<ManualProblem>) : ManualSaveResult

    /** A split of shares that were not held before its date. */
    data class NothingHeld(val ticker: String, val date: String) : ManualSaveResult

    data class Oversold(val oversell: Oversell) : ManualSaveResult

    /** Not saved: the rows look like existing operations; save again with `confirmDuplicate` to keep both. */
    data class PossibleDuplicate(val matches: List<Pair<StoredTransaction, StoredTransaction>>) : ManualSaveResult
}

sealed interface ManualDeleteResult {
    data object Deleted : ManualDeleteResult

    data object NotFound : ManualDeleteResult

    data class Oversold(val oversell: Oversell) : ManualDeleteResult
}

/** Adds, edits and deletes manual entries with the checks of Decisions 3–5 (mobile-manual-operations). */
class ManualOperations(
    private val store: ManualStore,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Runs after every saved or deleted entry; the app backs up the manual journal here. */
    private val onChanged: () -> Unit = {},
) {
    /** Adds an entry, or replaces [entryId]'s entry. Numbers in [fields] are plain decimal strings. */
    fun save(type: ManualType, fields: Map<String, String>, entryId: String? = null, confirmDuplicate: Boolean = false): ManualSaveResult {
        val typed = fields.filterKeys { it in type.fields && it != "heldBefore" }.mapValues { it.value.trim() }
        val all = store.allRows()
        val others = all.filter { entryId == null || it.entryId != entryId }.map { it.row }
        // heldBefore is the app's, not the user's; it is checked once the typed fields are valid.
        val problems = ManualValidation.check(type, if (type == ManualType.SPLIT) typed + ("heldBefore" to "0") else typed, LocalDate.now(clock))
        if (problems.isNotEmpty()) return ManualSaveResult.Problems(problems)
        val complete = if (type == ManualType.SPLIT) withHeldBefore(typed, others) ?: return nothingHeld(typed) else typed

        val id = entryId ?: newId()
        val imported = all.filter { it.entryId == null }.map { it.row }
        val rows = ManualRows.build(id, type, complete, ManualInstruments.lookup(imported))
        ManualFifo.oversell(ManualFifo.events(all.map { it.row }), ManualFifo.events(others + rows))?.let { return ManualSaveResult.Oversold(it) }
        if (!confirmDuplicate) {
            val matches = ManualDuplicates.find(rows, others)
            if (matches.isNotEmpty()) return ManualSaveResult.PossibleDuplicate(matches)
        }
        store.save(ManualEntry(id, type.name, JSONObject(complete).toString(), clock.instant().toString(), rows))
        onChanged()
        return ManualSaveResult.Saved(id)
    }

    fun delete(entryId: String): ManualDeleteResult {
        val all = store.allRows()
        if (all.none { it.entryId == entryId }) return ManualDeleteResult.NotFound
        val remaining = all.filter { it.entryId != entryId }.map { it.row }
        ManualFifo.oversell(ManualFifo.events(all.map { it.row }), ManualFifo.events(remaining))?.let { return ManualDeleteResult.Oversold(it) }
        if (!store.delete(entryId)) return ManualDeleteResult.NotFound
        onChanged()
        return ManualDeleteResult.Deleted
    }

    /** Manual rows that match imported ones, each with the imported row; shown after an import. */
    fun duplicatesOfImports(): List<Pair<StoredTransaction, StoredTransaction>> {
        val all = store.allRows()
        return ManualDuplicates.find(all.filter { it.entryId != null }.map { it.row }, all.filter { it.entryId == null }.map { it.row })
    }

    /** A split needs the shares held before it; null when none were held. */
    private fun withHeldBefore(fields: Map<String, String>, others: List<StoredTransaction>): Map<String, String>? {
        val ticker = fields.getValue("ticker")
        val date = fields.getValue("date")
        // Additions on the split date count after it (FIFO applies splits first), so only earlier days.
        val held = ManualFifo.heldBefore(ManualFifo.events(others.filter { it.date < date }), ticker, date)
        return if (held.signum() > 0) fields + ("heldBefore" to held.toString()) else null
    }

    private fun nothingHeld(fields: Map<String, String>) = ManualSaveResult.NothingHeld(fields.getValue("ticker"), fields.getValue("date"))
}
