package com.example.keeper

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * AutoBackup — set-and-forget periodic backup to a user-chosen folder.
 *
 * This is device-local config (like the reminder presets in Prefs), NOT part of
 * the DB export/import. Deliberately no cloud SDK: the user picks a folder once
 * via the Storage Access Framework; we write a timestamped byte copy of the DB
 * there on a weekly schedule and prune to the newest [Prefs.backupKeep] files.
 * If that folder lives inside the Drive/Dropbox/Nextcloud app's DocumentsProvider
 * the backup syncs to the cloud for free — this app still makes no network call.
 *
 * The byte copy reuses [Db.backup]; because WAL is disabled the DB file is always
 * consistent, so a backup is a plain file copy (same as the manual "Export DB").
 */
object AutoBackup {
    private const val WORK = "auto_backup"
    private const val PREFIX = "keeper-"
    private const val SUFFIX = ".db"

    /** Bumped after every backup run so an open SettingsDialog re-reads status. */
    val version = mutableIntStateOf(0)

    /** Outcome of one run; maps to a WorkManager result and a UI status. */
    enum class Outcome { OK, SKIPPED, RETRY }

    /** (Re)schedule or cancel the unique weekly worker from current prefs.
     *  Safe to call on every app start — WorkManager persists across reboot. */
    fun sync(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (Prefs.backupEnabled(ctx) && Prefs.backupTreeUri(ctx) != null) {
            val req = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    /** Perform one backup now (also the worker body). Records status in [Prefs]
     *  and bumps [version]. Never throws. */
    fun runOnce(ctx: Context): Outcome {
        Db.init(ctx)
        val outcome = try {
            doBackup(ctx)
        } catch (e: IOException) {
            fail(ctx, R.string.backup_error_io)
            Outcome.RETRY
        } catch (e: Exception) {
            fail(ctx, R.string.backup_error_io)
            Outcome.SKIPPED        // programmer/SAF error — don't retry-storm
        }
        version.intValue++
        return outcome
    }

    private fun doBackup(ctx: Context): Outcome {
        val uriStr = Prefs.backupTreeUri(ctx) ?: run {
            fail(ctx, R.string.backup_error_no_folder); return Outcome.SKIPPED
        }
        val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(uriStr))
        if (tree == null || !tree.canWrite()) {     // folder deleted or permission lost
            fail(ctx, R.string.backup_error_no_folder); return Outcome.SKIPPED
        }
        val name = PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + SUFFIX
        val file = tree.createFile("application/octet-stream", name)
            ?: run { fail(ctx, R.string.backup_error_io); return Outcome.RETRY }
        val ok = ctx.contentResolver.openOutputStream(file.uri)?.use { Db.backup(it); true } ?: false
        if (!ok) { file.delete(); fail(ctx, R.string.backup_error_io); return Outcome.RETRY }

        prune(tree, Prefs.backupKeep(ctx))
        Prefs.setLastBackup(ctx, System.currentTimeMillis())   // also clears the error
        return Outcome.OK
    }

    /** Keep the newest [keep] backups; delete older ones. Filenames carry an
     *  ASCII-sortable timestamp (Locale.US), so name order == time order. */
    private fun prune(tree: DocumentFile, keep: Int) {
        tree.listFiles()
            .filter { (it.name ?: "").startsWith(PREFIX) && (it.name ?: "").endsWith(SUFFIX) }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { it.delete() }
    }

    private fun fail(ctx: Context, msgRes: Int) = Prefs.setBackupError(ctx, msgRes)

    /** Human-readable name of the chosen folder for the settings row, or null. */
    fun folderLabel(ctx: Context): String? {
        val uri = Prefs.backupTreeUri(ctx) ?: return null
        return runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(uri))?.name }.getOrNull()
            ?: Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
    }
}

/** WorkManager body: weekly (and on-demand) trigger for [AutoBackup.runOnce]. */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = when (AutoBackup.runOnce(applicationContext)) {
        AutoBackup.Outcome.RETRY -> Result.retry()
        else -> Result.success()
    }
}
