package io.github.wlemkens.openbreath

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * The desktop's file picking: the backup that leaves the machine and the sound that comes onto it.
 *
 * `java.awt.FileDialog` and not Swing's `JFileChooser`, which is the one real choice in this file.
 * FileDialog is the operating system's own dialog — Explorer on Windows, the Finder sheet on macOS —
 * where JFileChooser is a Java drawing of one that looks foreign on all three. For a dialog whose
 * whole job is to look like the machine it is on, that settles it.
 *
 * Every method here is synchronous, unlike its Android and iOS counterparts: a modal FileDialog
 * pumps the event queue and returns when it is dismissed, so the callbacks the [Files] interface
 * asks for are simply invoked before returning. The interface is shaped for the two platforms that
 * genuinely hand the choosing to a system picker and hear back later; satisfying it early is not a
 * violation of it.
 */
class DesktopFiles(private val owner: Frame?) : Files {

    override fun exportText(suggestedName: String, text: String, onDone: (Boolean) -> Unit) {
        val target = ask(FileDialog.SAVE, "Save the backup", suggestedName)
        if (target == null) {
            onDone(false)
            return
        }
        // a disk that is full or a directory that is not writable, which is the reader's to see as
        // a failed export rather than ours to crash a meditation over
        onDone(runCatching { target.writeText(text) }.isSuccess)
    }

    override fun importText(onResult: (String?) -> Unit) {
        val source = ask(FileDialog.LOAD, "Open a backup")
        onResult(source?.let { runCatching { it.readText() }.getOrNull() })
    }

    override val canPickAudio = true

    /**
     * **The handle is the file's own path**, where iOS copies the file into its container and
     * Android keeps a `content://` URI alive with a persistable permission grant. Both of those
     * exist to survive a sandbox; there is no sandbox here, and a path a reader chose in their own
     * filesystem stays valid until they move the file.
     *
     * The day they do move it, [DesktopMarkers] finds nothing to decode and the phase falls back to
     * its tone — the same behaviour, from the same rule, as on the other two.
     */
    override fun pickAudio(onPicked: (String?) -> Unit) {
        onPicked(ask(FileDialog.LOAD, "Choose a sound")?.path)
    }

    /** The name, not the path: nobody recognises their own file by its directory. */
    override fun audioName(handle: String): String = File(handle).name

    /**
     * One dialog for all three. Returns null when the reader cancelled, which FileDialog signals by
     * leaving `file` null — there is no separate answer for it and none is wanted, since a cancelled
     * pick and an unreadable one both mean nothing happened.
     */
    private fun ask(mode: Int, title: String, suggested: String? = null): File? {
        val dialog = FileDialog(owner, title, mode)
        if (suggested != null) dialog.file = suggested
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory ?: ".", name)
    }
}
