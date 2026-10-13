package com.ibkrtax.mobile.manual

import com.ibkrtax.mobile.backup.BackupFileFormat
import com.ibkrtax.mobile.backup.BackupFolder
import com.ibkrtax.mobile.backup.BackupResult
import com.ibkrtax.mobile.backup.FolderAccessLostException
import com.ibkrtax.mobile.importer.StoredTransaction
import com.ibkrtax.mobile.storage.ManualEntry
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The manual journal payload (mobile-manual-operations Decision 6): every manual entry with its form and rows,
 * as UTF-8 JSON. The format is documented in `mobile/fixtures/manual/README.md` and shared with iOS.
 */
object ManualJournal {
    const val KIND = "manual-journal"
    const val VERSION = 1

    data class Decoded(val sequence: Long, val entries: List<ManualEntry>)

    fun encode(entries: List<ManualEntry>, sequence: Long, savedAt: String): String = JSONObject()
        .put("kind", KIND)
        .put("version", VERSION)
        .put("sequence", sequence)
        .put("savedAt", savedAt)
        .put(
            "entries",
            JSONArray(
                entries.map { entry ->
                    JSONObject()
                        .put("entryId", entry.entryId)
                        .put("type", entry.type)
                        .put("enteredAt", entry.enteredAt)
                        .put("fields", JSONObject(entry.fields))
                        .put("rows", JSONArray(entry.rows.map(::rowJson)))
                },
            ),
        )
        .toString()

    /** Null when [bytes] are not a journal this version can read (a report backup, or a newer journal). */
    fun decode(bytes: ByteArray): Decoded? {
        val text = bytes.decodeToString().trimStart()
        if (!text.startsWith("{")) return null
        return try {
            val json = JSONObject(text)
            if (json.optString("kind") != KIND || json.optInt("version") != VERSION) return null
            val entries = json.getJSONArray("entries")
            Decoded(
                sequence = json.getLong("sequence"),
                entries = (0 until entries.length()).map { i ->
                    val entry = entries.getJSONObject(i)
                    val rows = entry.getJSONArray("rows")
                    ManualEntry(
                        entryId = entry.getString("entryId"),
                        type = entry.getString("type"),
                        fields = entry.getJSONObject("fields").toString(),
                        enteredAt = entry.getString("enteredAt"),
                        rows = (0 until rows.length()).map { rowFrom(rows.getJSONObject(it)) },
                    )
                },
            )
        } catch (e: JSONException) {
            null
        }
    }

    private fun rowJson(row: StoredTransaction) = JSONObject()
        .put("sourceKey", row.sourceKey)
        .put("date", row.date)
        .put("eventType", row.eventType)
        .put("ticker", row.ticker)
        .put("quantity", row.quantity)
        .put("price", row.price)
        .put("currency", row.currency)
        .put("amount", row.amount)
        .put("fee", row.fee)
        .put("description", row.description)
        .put("splitRatio", row.splitRatio ?: JSONObject.NULL)
        .put("isin", row.isin)
        .put("conid", row.conid)
        .put("instrumentDescription", row.instrumentDescription)
        .put("listingExchange", row.listingExchange)

    private fun rowFrom(json: JSONObject) = StoredTransaction(
        sourceKey = json.getString("sourceKey"),
        date = json.getString("date"),
        eventType = json.getString("eventType"),
        ticker = json.getString("ticker"),
        quantity = json.getString("quantity"),
        price = json.getString("price"),
        currency = json.getString("currency"),
        amount = json.getString("amount"),
        fee = json.getString("fee"),
        description = json.getString("description"),
        splitRatio = if (json.isNull("splitRatio")) null else json.getString("splitRatio"),
        isin = json.getString("isin"),
        conid = json.getString("conid"),
        instrumentDescription = json.getString("instrumentDescription"),
        listingExchange = json.getString("listingExchange"),
    )
}

/**
 * Where the journal stands on this device. [sequence] is the last journal built; [file] is the verified journal
 * in the backup folder; [pending] is an encrypted journal waiting in app-private storage; [stale] means entries
 * changed since the last journal was built (for example while backups were not set up).
 */
data class JournalState(val sequence: Long = 0, val file: String? = null, val pending: String? = null, val stale: Boolean = false)

interface JournalStore {
    fun entries(): List<ManualEntry>

    fun journalState(): JournalState

    fun setJournalState(state: JournalState)

    /** Stores restored entries as they are; they were checked when first saved. */
    fun restoreEntries(entries: List<ManualEntry>)
}

/**
 * Backs up manual entries as one encrypted journal (Decision 6): after every change a new journal is built,
 * written and verified, and only then is the previous one deleted. Without a folder it stays pending and is
 * retried with the report backups.
 */
class ManualJournalBackup(
    private val dataKey: () -> ByteArray?,
    private val folder: () -> BackupFolder?,
    private val store: JournalStore,
    private val pendingDir: File,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    /** True when a journal still has to reach the folder; false while backups are not set up. */
    val isPending: Boolean get() = dataKey() != null && store.journalState().let { it.pending != null || it.stale }

    /** Call after every add, edit or delete of a manual entry. */
    fun changed(): BackupResult = sync(changed = true)

    fun sync(changed: Boolean = false): BackupResult {
        var state = store.journalState()
        if (changed && !state.stale) {
            state = state.copy(stale = true)
            store.setJournalState(state)
        }
        val key = dataKey() ?: return BackupResult.PENDING
        if (state.stale) {
            val sequence = state.sequence + 1
            val payload = ManualJournal.encode(store.entries(), sequence, clock.instant().toString()).toByteArray(Charsets.UTF_8)
            val name = randomFileName()
            pendingDir.mkdirs()
            File(pendingDir, name).writeBytes(BackupFileFormat.encrypt(key, payload, random))
            state.pending?.let { File(pendingDir, it).delete() }
            state = state.copy(sequence = sequence, pending = name, stale = false)
            store.setJournalState(state)
        }
        val name = state.pending ?: return BackupResult.BACKED_UP
        val target = folder() ?: return BackupResult.PENDING
        val encrypted = File(pendingDir, name).takeIf { it.exists() }?.readBytes()
            ?: return BackupResult.PENDING.also { store.setJournalState(state.copy(pending = null, stale = true)) }
        try {
            target.write(name, encrypted)
            val readBack = target.read(name)
            if (readBack == null || !sha256(readBack).contentEquals(sha256(encrypted))) {
                runCatching { target.delete(name) }
                return BackupResult.PENDING
            }
        } catch (e: FolderAccessLostException) {
            return BackupResult.ACCESS_LOST
        } catch (e: IOException) {
            return BackupResult.PENDING
        }
        // The new journal is verified; only now is the previous one removed.
        state.file?.takeIf { it != name }?.let { old -> runCatching { target.delete(old) } }
        store.setJournalState(state.copy(file = name, pending = null))
        File(pendingDir, name).delete()
        return BackupResult.BACKED_UP
    }

    /** Restores the newest of [journals] (file name and decrypted bytes); returns how many entries came back. */
    fun restore(journals: List<Pair<String, ManualJournal.Decoded>>): Int {
        val (name, newest) = journals.maxByOrNull { it.second.sequence } ?: return 0
        store.restoreEntries(newest.entries)
        store.setJournalState(JournalState(sequence = newest.sequence, file = name))
        return newest.entries.size
    }

    private fun randomFileName(): String = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) } + ".bin"

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
