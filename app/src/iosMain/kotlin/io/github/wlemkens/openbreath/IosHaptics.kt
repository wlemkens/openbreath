package io.github.wlemkens.openbreath

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * The nudge at a phase boundary.
 *
 * `UIImpactFeedbackGenerator` and not CoreHaptics, which would be an engine to start, keep alive
 * and tear down for a single tap. The interface takes a duration and this ignores it: the Taptic
 * Engine plays one fixed impact, and 45 ms is about what a medium one already feels like. If a
 * boundary ever wants a shape rather than a tap, that is the moment to reach for CHHapticEngine,
 * and not before.
 *
 * `prepare()` before each one because the engine is otherwise idle and the first tap after a quiet
 * stretch arrives late — on a breath cue, a nudge landing after the turn is worse than none. A
 * device with no Taptic Engine does nothing here, which is the right amount of noise about it.
 */
class IosHaptics : Haptics {

    private val generator =
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)

    /**
     * True on every iPhone, including the handful without a Taptic Engine: the generator is a no-op
     * there rather than an error, and hiding the setting on a device we cannot ask about would take
     * it away from everyone. The flag exists for the desktop, which has nothing to vibrate at all.
     */
    override val supported = true

    override fun buzz(ms: Long) {
        generator.prepare()
        generator.impactOccurred()
    }
}
