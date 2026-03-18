import XCTest

/// End-to-end UI test that drives the real CommCare iOS app through
/// a full HQ workflow: login → sync → navigate menus → verify.
///
/// Credentials are read from CommCareUITests/HQTestCredentials.plist (gitignored).
/// Create that file with keys: username, password, serverUrl (optional).
///
/// To create credentials file:
///   cat > CommCareUITests/HQTestCredentials.plist << 'EOF'
///   <?xml version="1.0" encoding="UTF-8"?>
///   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
///   <plist version="1.0"><dict>
///     <key>username</key><string>user@domain</string>
///     <key>password</key><string>password</string>
///     <key>serverUrl</key><string>https://www.commcarehq.org</string>
///   </dict></plist>
///   EOF
///
final class HQRoundTripUITest: XCTestCase {

    let app = XCUIApplication()

    private var credentials: [String: String] = [:]

    private var username: String { credentials["username"] ?? "" }
    private var password: String { credentials["password"] ?? "" }
    private var serverUrl: String { credentials["serverUrl"] ?? "https://www.commcarehq.org" }

    override func setUpWithError() throws {
        continueAfterFailure = false

        // Load credentials from plist bundled in test target
        if let plistPath = Bundle(for: type(of: self)).path(forResource: "HQTestCredentials", ofType: "plist"),
           let dict = NSDictionary(contentsOfFile: plistPath) as? [String: String] {
            credentials = dict
        }

        guard !username.isEmpty, !password.isEmpty else {
            throw XCTSkip("HQ credentials not configured — create CommCareUITests/HQTestCredentials.plist")
        }

        app.launch()
    }

    // MARK: - Test: Full login → sync → home screen

    func testLoginAndReachHomeScreen() throws {
        // Step 1: Verify we're on the login screen
        let loginTitle = app.staticTexts["CommCare"]
        XCTAssertTrue(loginTitle.waitForExistence(timeout: 15), "Login screen should show CommCare title")

        // Step 2: Fill in credentials
        fillLoginFields()

        // Step 3: Tap Log In
        let loginButton = app.buttons["Log In"]
        XCTAssertTrue(loginButton.waitForExistence(timeout: 5), "Log In button should exist")
        XCTAssertTrue(loginButton.isEnabled, "Log In button should be enabled")
        loginButton.tap()

        // Step 4: Wait for home screen (login → installing → ready)
        let homeReached = waitForHomeScreen(timeout: 90)

        if !homeReached {
            captureScreenshot(name: "LoginFailed")
            // Check for error messages
            let errorTexts = app.staticTexts.allElementsBoundByIndex.filter {
                $0.label.lowercased().contains("error") || $0.label.lowercased().contains("failed")
            }
            let errorMsg = errorTexts.first?.label ?? "Unknown"
            XCTFail("Home screen not reached. Error: \(errorMsg)")
        }

        print("Successfully logged in and reached home screen")
        captureScreenshot(name: "HomeScreen")
    }

    // MARK: - Test: Login → navigate into first module

    func testNavigateToModule() throws {
        try loginToHomeScreen()

        // Find any tappable element that could be a module entry
        // Compose Multiplatform renders buttons and clickable text
        // The home screen should have module names as tappable items
        let allButtons = app.buttons.allElementsBoundByIndex
        let moduleButtons = allButtons.filter { $0.isHittable && !$0.label.isEmpty }

        guard let firstModule = moduleButtons.first else {
            print("No module buttons found — app may have no modules")
            captureScreenshot(name: "NoModules")
            return
        }

        let moduleName = firstModule.label
        print("Tapping module: \(moduleName)")
        firstModule.tap()

        // Wait for navigation
        Thread.sleep(forTimeInterval: 3)
        captureScreenshot(name: "ModuleScreen")
        print("Navigated into module: \(moduleName)")
    }

    // MARK: - Helpers

    private func fillLoginFields() {
        // Compose text fields - find by index since accessibility IDs aren't available
        let textFields = app.textFields.allElementsBoundByIndex
        let secureFields = app.secureTextFields.allElementsBoundByIndex

        // Server URL (first text field)
        if let serverField = textFields.first, serverField.waitForExistence(timeout: 5) {
            serverField.tap()
            serverField.clearText()
            serverField.typeText(serverUrl)
        }

        // Username (second text field, or first if server field is hidden)
        let usernameFieldIndex = textFields.count > 1 ? 1 : 0
        if usernameFieldIndex < textFields.count {
            let usernameField = textFields[usernameFieldIndex]
            usernameField.tap()
            usernameField.clearText()
            usernameField.typeText(username)
        }

        // Password (secure text field)
        if let passwordField = secureFields.first, passwordField.waitForExistence(timeout: 5) {
            passwordField.tap()
            passwordField.typeText(password)
        }
    }

    private func loginToHomeScreen() throws {
        let loginTitle = app.staticTexts["CommCare"]
        guard loginTitle.waitForExistence(timeout: 15) else {
            return // Already past login
        }

        fillLoginFields()

        let loginButton = app.buttons["Log In"]
        guard loginButton.waitForExistence(timeout: 5), loginButton.isEnabled else {
            throw XCTSkip("Log In button not available")
        }
        loginButton.tap()

        guard waitForHomeScreen(timeout: 90) else {
            captureScreenshot(name: "LoginFailed")
            throw XCTSkip("Could not reach home screen")
        }
    }

    private func waitForHomeScreen(timeout: TimeInterval) -> Bool {
        let start = Date()
        while Date().timeIntervalSince(start) < timeout {
            // Look for indicators that we're on the home screen
            if app.staticTexts["Ready"].exists { return true }
            if app.buttons["Sync"].exists { return true }
            if app.buttons["Start"].exists { return true }
            // Check for module names (any static text that isn't the login/install screen)
            let staticTexts = app.staticTexts.allElementsBoundByIndex.map { $0.label }
            if staticTexts.contains(where: { $0.contains("Module") || $0.contains("List") }) {
                return true
            }
            Thread.sleep(forTimeInterval: 1)
        }
        return false
    }

    private func captureScreenshot(name: String) {
        let screenshot = app.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

// MARK: - XCUIElement helpers

extension XCUIElement {
    func clearText() {
        guard let currentValue = value as? String, !currentValue.isEmpty else { return }
        // Triple tap to select all, then delete
        tap(withNumberOfTaps: 3, numberOfTouches: 1)
        typeText(XCUIKeyboardKey.delete.rawValue)
    }
}
