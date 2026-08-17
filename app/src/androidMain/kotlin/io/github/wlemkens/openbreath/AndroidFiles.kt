package io.github.wlemkens.openbreath

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Android's file picking, for the backup and for a sound of the reader's own.
 *
 * **Constructed as a field of the Activity and not on demand.** `registerForActivityResult` has
 * to be called before the Activity is started — it is re-registering across process death that
 * makes a result survive the system killing the app mid-pick — and a launcher created later
 * throws. That is also why the callbacks are held in `var`s here rather than passed at launch:
 * the registration happens once, long before anyone knows what to do with the answer.
 *
 * A pending callback is cleared before it is invoked, so a picker dismissed twice cannot deliver
 * twice, and the launchers are single-use in practice because the screen only offers one at once.
 */
class AndroidFiles(private val activity: ComponentActivity) : Files {

    private var onExported: ((Boolean) -> Unit)? = null
    private var onImported: ((String?) -> Unit)? = null
    private var onAudio: ((String?) -> Unit)? = null

    /** Held until the picker answers: CreateDocument tells us where, not what to put there. */
    private var pendingText: String? = null

    private val createDocument =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val text = pendingText
            val done = onExported
            pendingText = null
            onExported = null
            if (uri == null || text == null) {
                done?.invoke(false)
            } else {
                val ok = runCatching {
                    activity.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        ?: error("nothing would open that file for writing")
                }.isSuccess
                done?.invoke(ok)
            }
        }

    private val openDocument =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val result = onImported
            onImported = null
            result?.invoke(
                uri?.let {
                    runCatching {
                        activity.contentResolver.openInputStream(it)?.use { stream ->
                            stream.readBytes().decodeToString()
                        }
                    }.getOrNull()
                }
            )
        }

    private val openAudio =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val picked = onAudio
            onAudio = null
            if (uri == null) {
                picked?.invoke(null)
            } else {
                // without a persisted grant the URI dies when the process does, and the preset
                // that points at it would be silent on the next launch with nothing to explain it
                runCatching {
                    activity.contentResolver
                        .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                picked?.invoke(uri.toString())
            }
        }

    override fun exportText(suggestedName: String, text: String, onDone: (Boolean) -> Unit) {
        pendingText = text
        onExported = onDone
        runCatching { createDocument.launch(suggestedName) }
            .onFailure { pendingText = null; onExported = null; onDone(false) }
    }

    override fun importText(onResult: (String?) -> Unit) {
        onImported = onResult
        // application/json alone hides backups on the phones whose file picker types them
        // octet-stream, which is most of them once a file has been off the device and back
        runCatching { openDocument.launch(arrayOf("application/json", "text/plain", "*/*")) }
            .onFailure { onImported = null; onResult(null) }
    }

    override val canPickAudio = true

    override fun pickAudio(onPicked: (String?) -> Unit) {
        onAudio = onPicked
        runCatching { openAudio.launch(arrayOf("audio/*")) }
            .onFailure { onAudio = null; onPicked(null) }
    }

    override fun audioName(handle: String): String = activity.displayName(Uri.parse(handle))
}
