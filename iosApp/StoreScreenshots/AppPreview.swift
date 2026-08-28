import XCTest

/// Drives the app for the App Store preview video while `simctl` films it.
///
/// The division of labour is forced: only XCUITest can tap a simulator and only `simctl` can record
/// one, so the recording is `.github/ios-previews.sh` and this is the finger. What connects them is
/// one file — this writes the instant the first inhale begins, in epoch seconds, and the script
/// trims the capture to it. The simulator shares the host clock, so the two agree to the
/// millisecond, and neither has to guess how long an install took or how slow a boot was.
///
/// It photographs nothing. A separate class from `StoreScreenshots` so `-only-testing` can ask for
/// one without the other; the screenshot run would otherwise film 22 seconds of nothing and the
/// preview run would take nine pictures it throws away.
final class AppPreview: XCTestCase {

    private var app: XCUIApplication!
    private var marker: URL!

    override func setUpWithError() throws {
        continueAfterFailure = false
        let dir = try XCTUnwrap(
            ProcessInfo.processInfo.environment["PREVIEW_DIR"], "PREVIEW_DIR is not set")
        let url = URL(fileURLWithPath: dir, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        marker = url.appendingPathComponent("inhale-began")
        app = XCUIApplication()
    }

    func testFilmTheBreathingCue() throws {
        // No demo log, unlike the screenshots. A fresh install is on the default preset with waves
        // on every phase, which is exactly the sound PreviewAudio renders — so the video and its
        // soundtrack are the same app, not an approximation of it. It does mean the first-run
        // question is in the way, which is one tap.
        app.launch()
        let start = element("Start")
        if element("Continue").waitForExistence(timeout: 10) {
            element("Continue").tap()
        }
        XCTAssertTrue(start.waitForExistence(timeout: 10), "no Start button to begin a sitting")
        start.tap()

        // Get ready counts down for four seconds before the first breath, and a preview should open
        // on the app doing the thing rather than on a countdown.
        let inhale = element("Breathe in")
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline && !inhale.exists {
            Thread.sleep(forTimeInterval: 0.05)
        }
        guard inhale.exists else {
            print("::error title=The sitting never started::no 'Breathe in' within 30s of tapping Start")
            return XCTFail("the sitting never reached an inhale")
        }

        // written after the fact rather than before, so the number is when the phase actually
        // changed and not when this test hoped it would
        let began = Date().timeIntervalSince1970
        try String(format: "%.3f", began).write(to: marker, atomically: true, encoding: .utf8)
        print("inhale began at \(began)")

        // Long enough for the script to take its 22 seconds — two whole breaths — with room for a
        // recorder that takes a moment to come up. The app is left breathing; nothing is tapped.
        Thread.sleep(forTimeInterval: 34)
    }

    private func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", label))
            .firstMatch
    }
}
