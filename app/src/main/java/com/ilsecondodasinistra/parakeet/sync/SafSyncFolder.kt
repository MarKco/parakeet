package com.ilsecondodasinistra.parakeet.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * A folder picked with the system file access (OpenDocumentTree): works with Syncthing-Fork, Drive, Dropbox…
 * Only the files of the folder itself are used.
 */
class SafSyncFolder(context: Context, private val tree: Uri) : SyncFolder {
    private val resolver = context.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(tree)
    private val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)

    /** name → document id of every file (not folder) in the folder. */
    private fun children(): Map<String, String> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val out = LinkedHashMap<String, String>()
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                out[c.getString(1)] = c.getString(0)
            }
        }
        return out
    }

    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    override fun list(): List<String> = children().keys.toList()

    override fun read(name: String): String? {
        val id = children()[name] ?: return null
        return resolver.openInputStream(docUri(id))?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override fun write(name: String, text: String) {
        val existing = children()[name]
        val uri = if (existing != null) docUri(existing)
        else DocumentsContract.createDocument(resolver, rootUri, "application/json", name) ?: error("Cannot create $name")
        // "wt": write and truncate, so a shorter text doesn't leave the end of the old one
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Cannot write $name")
    }

    override fun rename(name: String, newName: String) {
        val id = children()[name] ?: return
        DocumentsContract.renameDocument(resolver, docUri(id), newName) ?: error("Cannot rename $name")
    }

    override fun delete(name: String) {
        val id = children()[name] ?: return
        DocumentsContract.deleteDocument(resolver, docUri(id))
    }
}
