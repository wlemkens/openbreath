package io.github.wlemkens.openbreath

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.URLByAppendingPathComponent
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
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

    override val canPickAudio = true

    /**
     * The chosen file is **copied into the app's own container**, and the handle is its name
     * there. Android keeps a `content://` URI alive with a persistable permission grant; iOS has
     * bookmarks for the same job, and this does neither.
     *
     * Copying is the better trade here. A bookmark still points at someone else's file, so the
     * marker goes silent the day they tidy their Downloads, move it, or leave it in an iCloud
     * folder that is not on the phone at the moment a phase ends — and a breathing app that is
     * silent for a reason on another screen is indistinguishable from one that is broken. A copy
     * is a few megabytes and always there.
     */
    override fun pickAudio(onPicked: (String?) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = audioTypes())
        present(picker, Delegate(
            onPicked = { url ->
                delegate = null
                onPicked(url?.let { take(it) })
            },
            onCancel = { delegate = null; onPicked(null) },
        ))
    }

    /** The name it was picked under, which is the only thing a reader would recognise. */
    override fun audioName(handle: String): String = handle

    private fun audioTypes(): List<UTType> = listOfNotNull(UTType.typeWithIdentifier("public.audio"))

    /**
     * Copies the picked file in and hands back its name, or null if it could not be read. The
     * security-scoped access has to be opened for the read and given back after, or the grant
     * leaks for the life of the process.
     */
    private fun take(url: NSURL): String? {
        val name = url.lastPathComponent ?: return null
        val opened = url.startAccessingSecurityScopedResource()
        val data = try {
            NSData.dataWithContentsOfURL(url)
        } finally {
            if (opened) url.stopAccessingSecurityScopedResource()
        }
        val destination = pickedMarkerPath(name) ?: return null
        NSFileManager.defaultManager.createDirectoryAtPath(
            pickedMarkerDir() ?: return null,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return if (data != null && data.writeToFile(destination, true)) name else null
    }

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

/**
 * Where a picked marker lives once it is ours: a directory beside the practice log, in Documents.
 *
 * Documents rather than Caches deliberately, and for the same reason the log is there — the
 * system may empty Caches whenever it likes, and a preset pointing at a sound the phone quietly
 * deleted is the silent failure this whole arrangement exists to avoid.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun pickedMarkerDir(): String? {
    val documents = NSFileManager.defaultManager
        .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        .firstOrNull() as? NSURL
    return documents?.path?.let { "$it/markers" }
}

/** The full path of a handle from [Files.pickAudio], which is a bare file name. */
internal fun pickedMarkerPath(name: String): String? = pickedMarkerDir()?.let { "$it/$name" }
