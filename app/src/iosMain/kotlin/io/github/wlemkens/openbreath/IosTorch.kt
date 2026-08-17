package io.github.wlemkens.openbreath

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isTorchAvailable
import platform.AVFoundation.setTorchMode
import platform.AVFoundation.setTorchModeOnWithLevel

/**
 * The flashlight on iOS, brightening with the inhale.
 *
 * The quantising is [torchLevel]'s, shared with Android to the step, so the two platforms light
 * the same breath the same way. Only the writing is here.
 *
 * **Wants no camera permission, and must keep not wanting one.** Torch control is configuration
 * on the capture device, not capture: no `AVCaptureSession` is created, nothing is recorded, and
 * so `NSCameraUsageDescription` is not required and no prompt appears. That mirrors Android,
 * where `setTorchMode` sits deliberately outside the CAMERA permission. If a prompt ever does
 * appear here, something has started a session and the cause is that, not this.
 *
 * Every call is guarded the same way Android's is. Another app holding the camera makes the lock
 * fail, and a meditation must not end because the torch could not.
 *
 * The opt-in is for the two calls that take an `NSError **`: Kotlin/Native models an out-pointer
 * as a CPointer, and every CPointer is behind ExperimentalForeignApi. Passing null for it is the
 * normal way to say "no error out-param wanted", and the calls report failure by return value
 * anyway, which is what is actually checked here.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTorch : TorchLight {

    private val device: AVCaptureDevice? =
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)

    override val available: Boolean get() = device?.hasTorch() == true

    /**
     * Sixteen steps. iOS takes a float where Android takes a device-reported integer, so the
     * number is ours to pick: fine enough that the ramp reads as smooth, coarse enough that a
     * breath locks the device a few dozen times rather than sixty times a second.
     */
    private var written = 0

    override fun follow(state: PhaseState) = write(torchLevel(state, STEPS))

    private fun write(want: Int) {
        if (want == written) return
        val cam = device ?: return
        if (!cam.hasTorch() || !cam.isTorchAvailable()) return

        // only a call that landed counts as written, so a failed off() is retried next frame
        if (!cam.lockForConfiguration(null)) return
        val ok = if (want == 0) {
            cam.setTorchMode(AVCaptureTorchModeOff)
            true
        } else {
            // the level is exclusive of 0 and inclusive of 1; want is 1..STEPS, so it cannot be 0
            cam.setTorchModeOnWithLevel(want.toFloat() / STEPS, null)
        }
        cam.unlockForConfiguration()
        if (ok) written = want
    }

    /** Safe when never lit, and idempotent — a torch left burning outlives the app that lit it. */
    override fun off() = write(0)

    private companion object {
        const val STEPS = 16
    }
}
