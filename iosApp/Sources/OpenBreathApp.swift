import SwiftUI
import UIKit
import OpenBreath

/// The whole of the Swift in OpenBreath.
///
/// Everything the app does — the session engine, the cue, the log, the goals — is Kotlin in the
/// OpenBreath framework and is shared with Android. This wraps the view controller that framework
/// exports and gets out of the way. If this file ever grows, something has been written twice.
@main
struct OpenBreathApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                // Compose draws its own background and handles safe areas through
                // safeDrawingPadding, so the host must not inset it a second time
                .ignoresSafeArea()
                // the app is dark by design — Ink, near-black so the cue's glow has somewhere to
                // fade into. Letting iOS light-mode it would wash the cue out entirely.
                .preferredColorScheme(.dark)
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // MainViewControllerKt is how Kotlin/Native exports a top-level function: the file name,
        // suffixed Kt. It provides the store and the platform, then hands over to the shared App.
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
