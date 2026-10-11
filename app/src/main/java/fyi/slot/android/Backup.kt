package fyi.slot.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File

/**
 * Keeps a small copy of your Apps & games choices and settings inside the ROM folder you picked, so they survive
 * an uninstall: when you pick the folder again slot finds the copy and puts everything back. (An ordinary update
 * keeps the app's own files anyway; this is for the times Android makes you uninstall first.)
 */
object Backup {
    private const val NAME = ".slot-backup.json"
    private const val MIME = "application/json"

    private fun files(ctx: Context) = listOf("apps.json", "settings.json").map { File(ctx.filesDir, it) }

    private fun treeDoc(root: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))

    private fun findCopy(ctx: Context, root: Uri): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
        ctx.contentResolver.query(
            children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == NAME) return DocumentsContract.buildDocumentUriUsingTree(root, c.getString(0))
            }
        }
        return null
    }

    /** Writes the current choices and settings next to the games. Quietly does nothing if the folder isn't writable. */
    fun write(ctx: Context) {
        val root = Persist.loadRoots(ctx).firstOrNull()?.let(Uri::parse) ?: return
        val (apps, settings) = files(ctx).map { f -> f.takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } }
        if (apps == null && settings == null) return
        val body = JSONObject().put("version", 1).apply {
            apps?.let { put("apps", it) }
            settings?.let { put("settings", it) }
        }.toString()
        runCatching {
            val target = findCopy(ctx, root) ?: DocumentsContract.createDocument(ctx.contentResolver, treeDoc(root), MIME, NAME) ?: return
            ctx.contentResolver.openOutputStream(target, "wt")?.use { it.write(body.toByteArray()) }
        }
    }

    /** After the folder is picked on a fresh install: puts back whatever the copy holds and the app doesn't have yet. */
    fun restore(ctx: Context, root: Uri): Boolean = runCatching {
        val copy = findCopy(ctx, root) ?: return false
        val saved = ctx.contentResolver.openInputStream(copy)?.use { JSONObject(it.readBytes().decodeToString()) } ?: return false
        var restored = false
        for ((key, file) in listOf("apps" to File(ctx.filesDir, "apps.json"), "settings" to File(ctx.filesDir, "settings.json"))) {
            val part = saved.optJSONObject(key) ?: continue
            if (!file.exists()) { file.writeText(part.toString()); restored = true }
        }
        restored
    }.getOrDefault(false)
}
