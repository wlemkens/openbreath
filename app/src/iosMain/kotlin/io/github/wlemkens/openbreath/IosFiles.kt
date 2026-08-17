package io.github.wlemkens.openbreath

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.URLByAppendingPathComponent
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

/**
 * iOS's file picking, which for now is the backup and only the backup.
 *
 * The two halves are not symmetrical, because UIKit's are not. Exporting hands the picker a file
 * that already exists and lets the reader say where it should end up; importing hands back a URL
 * that has to be opened inside a security-scoped access pair, because a document chosen from
 * another app's container is not otherwise readable at all.
 *
 * **The delegate is held in a property on purpose.** UIKit keeps only a weak reference to it, so
 * a delegate that lives no longer than the function which presented the picker is collected
 * before the reader has finished choosing, and the callback simply never arrives. It reads as a
 * picker that does nothing, with no error anywhere.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFiles : Files {

    /**
     * What a backup may be opened from. JSON is what it is; plain text is here because a file
     * that has been mailed to itself and back often loses the type on the way, and a picker that
     * greys out the reader's own backup is indistinguishable from one that is broken.
     */
    private fun backupTypes(): List<UTType> = listOfNotNull(
        UTType.typeWithIdentifier("public.json"),
        UTType.typeWithIdentifier("public.plain-text"),
    )

    /** The last picker's delegate, kept alive for exactly as long as its picker is on screen. */
    private var delegate: Delegate? = null

    private fun present(controller: UIDocumentPickerViewController, handler: Delegate) {
        delegate = handler
        controller.delegate = handler
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root == null) {
            delegate = null
            handler.cancelled()
            return
        }
        root.presentViewController(controller, animated = true, completion = null)
    }

    override fun exportText(suggestedName: String, text: String, onDone: (Boolean) -> Unit) {
        // written to the temporary directory first: the picker moves a file that exists, it does
        // not take bytes. The copy left behind is the system's to clean up
        val temporary = NSURL.fileURLWithPath(NSTemporaryDirectory())
            .URLByAppendingPathComponent(suggestedName)
        if (temporary == null) {
            onDone(false)
            return
        }
        val wrote = (text as NSString).writeToURL(temporary, true, NSUTF8StringEncoding, null)
        if (!wrote) {
            onDone(false)
            return
        }

        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(temporary))
        present(picker, Delegate(
            onPicked = { url -> delegate = null; onDone(url != null) },
            onCancel = { delegate = null; onDone(false) },
        ))
    }

    override fun importText(onResult: (String?) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = backupTypes())
        present(picker, Delegate(
            onPicked = { url ->
                delegate = null
                onResult(url?.let { read(it) })
            },
            onCancel = { delegate = null; onResult(null) },
        ))
    }

    /**
     * A file from another app's container is unreadable until asked for and must be given back,
     * or the grant leaks for the lifetime of the process.
     */
    private fun read(url: NSURL): String? {
        val opened = url.startAccessingSecurityScopedResource()
        return try {
            NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)
        } finally {
            if (opened) url.stopAccessingSecurityScopedResource()
        }
    }

    /**
     * Not implemented, and honest about it rather than opening a picker that leads nowhere. The
     * sound would have to be decoded and played too, and IosMarkers cannot do that yet — offering
     * the choice first would let someone pick a file and hear silence.
     */
    override val canPickAudio = false

    override fun pickAudio(onPicked: (String?) -> Unit) = onPicked(null)

    override fun audioName(handle: String): String =
        NSURL.URLWithString(handle)?.lastPathComponent ?: "sound"

    /**
     * One delegate class for both directions. `didPickDocumentsAtURLs` is the modern callback and
     * fires for a single pick too; the older single-URL one is not overridden, since UIKit calls
     * whichever is implemented and implementing both would double every result.
     */
    internal class Delegate(
        private val onPicked: (NSURL?) -> Unit,
        private val onCancel: () -> Unit,
    ) : NSObject(), UIDocumentPickerDelegateProtocol {

        fun cancelled() = onCancel()

        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            onPicked(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onCancel()
        }
    }
}
