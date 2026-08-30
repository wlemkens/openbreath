import XCTest

/// The App Store screenshots, taken by driving a simulator.
///
/// The twin of `docs/store/screenshots.py`, which does the same job on an Android emulator through
/// uiautomator, and the two take deliberately the same nine pictures of the same screens in the
/// same order. Read that file alongside this one; where they differ, the difference is a fact about
/// the platform and is commented here.
///
/// Two things it needs from the environment, both set by `.github/ios-screenshots.sh`:
///
///   SCREENSHOT_DIR  where to write the PNGs — a path on the *host*, which a simulator process can
///                   write to because a simulator is not a virtual machine.
///   DEMO_JSON       the generated practice log, handed to the app in its launch environment. The
///                   Android script imports it by driving the file picker; on iOS the picker would
///                   need the file somewhere it can browse to, so the app takes it directly.
///
/// It is not run on every push. Screenshots are retaken when a screen changes, which is a decision
/// someone makes, so the job is workflow_dispatch — the same shape as the Android command.
final class StoreScreenshots: XCTestCase {

    private var app: XCUIApplication!
    private var shots: URL!
    private var demo: String!

    override func setUpWithError() throws {
        continueAfterFailure = false

        let env = ProcessInfo.processInfo.environment
        let dir = try XCTUnwrap(env["SCREENSHOT_DIR"], "SCREENSHOT_DIR is not set")
        shots = URL(fileURLWithPath: dir, isDirectory: true)
        try FileManager.default.createDirectory(at: shots, withIntermediateDirectories: true)

        let json = try XCTUnwrap(env["DEMO_JSON"], "DEMO_JSON is not set")
        demo = try String(contentsOf: URL(fileURLWithPath: json), encoding: .utf8)

        app = XCUIApplication()
    }

    /// One test method rather than nine, because the order is the point: the first-run question can
    /// only be photographed before anything is stored, and the milestone only in the moment the
    /// imported log crosses a hundred days.
    func testTakeTheStoreScreenshots() throws {
        // A fresh install, so nothing has ever been stored and the first-run question appears. The
        // shell script erases the device for the same reason.
        //
        // Waited for, and that is not belt-and-braces. `launch()` returns when the process is up,
        // which is well before Compose has drawn anything, and this was the one shot in the run
        // taken without a wait in front of it — every other one follows a `tap` or a `waitUntil`
        // that blocks until the screen is there. So it photographed an empty black rectangle, and
        // it did so silently: the script checks that nine files exist at the size Apple accepts,
        // which a blank screenshot passes. A blank one reached the App Store listing that way.
        app.launch()
        let welcome = element("Continue")
        XCTAssertTrue(
            welcome.waitForExistence(timeout: 30),
            "the first-run question never appeared — was the device erased?"
        )
        shot("firstrun")
        welcome.tap()

        // Relaunched with the log in the environment. Android taps Settings ▸ Advanced ▸ Import
        // here; the effect is the same and the wait is for the import to land.
        app.terminate()
        app.launchEnvironment["OPENBREATH_IMPORT_JSON"] = demo
        app.launch()

        // a hundred days of practice has just arrived, so the milestone shows itself
        let onwards = element("Onwards")
        XCTAssertTrue(onwards.waitForExistence(timeout: 30), "the milestone never appeared — did the import fail?")
        shot("milestone")
        onwards.tap()

        shot("session-idle")

        // The only shot that is a moment rather than a screen, and it is synchronised on the phase
        // rather than counted in seconds. The Android script waits 4.2 + 5.5 + 5.5 — the countdown
        // plus a breath and a half — which lands within a fifth of a second of the boundary between
        // one breath and the next, where the sphere is at its very smallest. It photographs a cue
        // that appears to be doing nothing. Waiting for an inhale to *begin* and then letting it
        // run most of its length is immune to that, to how long a tap takes to be delivered, and to
        // someone retiming the preset.
        tap("Start")
        let inhale = element("Breathe in")
        waitUntil(inhale, exists: true, "the sitting never reached an inhale")
        waitUntil(inhale, exists: false, "the first inhale never ended")
        waitUntil(inhale, exists: true, "the second inhale never came")
        Thread.sleep(forTimeInterval: 3.6)   // of the 5.5: the sphere is near full stretch
        shot("session-breathe-in")
        tap("Reset")

        // Reminders is Android's alone, so this is three items where the Android run has four —
        // and that absence is the reason the App Store copy may not promise them either.
        for screen in ["Log", "Achievements", "Goals"] {
            tap("⋮")
            tap(screen)
            // The log is the one that visibly flickers: LogScreen collects its history with an
            // empty initial value, so "Nothing yet." is on screen for a frame or two before the
            // entries arrive, and a shot taken in that window claims the app has no history at
            // all. The iPad caught exactly that on one run and missed it on the run before — the
            // same code, half an hour apart, which is what a race looks like from outside.
            //
            // The summary line is the signal because it exists only once the history has: the
            // other two screens have no static text worth waiting on, and no run has caught them
            // half-drawn.
            if screen == "Log" {
                waitForLabel(containing: "in all", "the log never filled in")
            }
            shot(screen.lowercased())
            tap("Done")
        }

        tap("⋮")
        tap("Settings")
        tap("Advanced")
        // scrolled to rather than swiped a counted number of times: XCUITest's swipe has its own
        // velocity and would not land where adb's does, and a screenshot of the wrong part of a
        // list is not obviously wrong in a diff
        let marker = scroll(to: "Marker")
        marker.tap()
        // And then scrolled on, because `scroll(to:)` stops the instant a label becomes hittable
        // — at the bottom edge — so anchoring on the first thing in a section frames everything
        // *above* it. This shot was three quarters Preset and Timing with "Sound per phase" as a
        // sliver along the bottom, where Android's is the section itself: Breathe in set to
        // Marker, the mp3 row, and the bowl/bell/tick choice under it. That choice is what the
        // picture is selling, and it was not in it.
        //
        // "Tick" is the anchor because it is unique — the tone chips exist only for a phase in
        // Marker mode, which is the one just tapped — and because stopping with it at the bottom
        // puts the section header and every chip above it on screen.
        _ = scroll(to: "Tick")
        shot("sound-per-phase")

        // Scrolled to, for the reason the comment above already gives and this line used to
        // ignore: two bare swipes land wherever the list happens to be that day, and the list grew
        // when the cue gained its two sliders — so this shot drifted onto the sound chips,
        // duplicating the one above it and missing the section it is named for.
        //
        // "Point size" rather than "Breath cue", which is the section this frames: `scroll(to:)`
        // stops the moment a label is on screen, so asking for the header would stop with it at
        // the bottom edge and the chips and both sliders still below the fold. Asking for the last
        // row of the section puts the whole of it on screen — which is Android's framing, where
        // the chips and the two sliders under them read as one section.
        _ = scroll(to: "Point size")
        shot("settings-options")
    }

    // MARK: - the five things this needs

    /// Polls for any element whose label *contains* `text`. [element(_:)] matches exactly, which
    /// cannot express "the log's summary line, whatever the numbers in it happen to be".
    private func waitForLabel(
        containing text: String, _ what: String, timeout: TimeInterval = 20
    ) {
        let match = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", text))
            .firstMatch
        guard match.waitForExistence(timeout: timeout) else {
            print("::error title=Waited \(Int(timeout))s for nothing::\(what). On screen: \(visible())")
            return XCTFail(what)
        }
    }

    /// A screenshot of the whole screen, at the simulator's own pixel size. `XCUIScreen` rather
    /// than `app.screenshot()`: the app's own bounds exclude the status bar, and Apple wants the
    /// device's full resolution or it refuses the upload.
    private func shot(_ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        let file = shots.appendingPathComponent("\(name).png")
        XCTAssertNoThrow(try png.write(to: file), "could not write \(file.path)")
        print("wrote \(name).png (\(png.count) bytes)")
    }

    /// Compose exposes its semantics to iOS accessibility, so a label is findable — but as whatever
    /// element type Compose chose, which is why this asks for any descendant with the label rather
    /// than for a button. Matching on `label` alone would also match a longer string containing it,
    /// hence the exact predicate.
    private func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", label))
            .firstMatch
    }

    private func tap(_ label: String, timeout: TimeInterval = 10) {
        let el = element(label)
        guard el.waitForExistence(timeout: timeout) else {
            // an annotation rather than a bare failure: a job log needs a token to read, and this
            // is the failure that will actually happen — a renamed button breaks the run, not the
            // app, and the fix is to rename it here too
            print("::error title=No '\(label)' on screen::Renamed? The screenshot driver taps by label. On screen: \(visible())")
            return XCTFail("no '\(label)' on screen")
        }
        el.tap()
    }

    /// Polls until an element is there, or gone. `waitForExistence` covers only the appearing half,
    /// and watching a label leave is how a phase boundary is found without knowing the timings.
    private func waitUntil(
        _ el: XCUIElement, exists: Bool, _ what: String, timeout: TimeInterval = 20
    ) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if el.exists == exists { return }
            Thread.sleep(forTimeInterval: 0.1)
        }
        print("::error title=Waited \(Int(timeout))s for nothing::\(what)")
        XCTFail(what)
    }

    /// Swipes up until the label is on screen. Ten is generous for the longest list in the app and
    /// short enough to fail rather than swipe forever.
    private func scroll(to label: String) -> XCUIElement {
        let el = element(label)
        for _ in 0..<10 {
            if el.exists && el.isHittable { return el }
            app.swipeUp()
        }
        print("::error title=Never scrolled to '\(label)'::On screen: \(visible())")
        XCTFail("never scrolled to '\(label)'")
        return el
    }

    private func visible() -> String {
        app.descendants(matching: .any).allElementsBoundByIndex
            .compactMap { $0.exists ? $0.label : nil }
            .filter { !$0.isEmpty }
            .prefix(40)
            .joined(separator: " | ")
    }
}
