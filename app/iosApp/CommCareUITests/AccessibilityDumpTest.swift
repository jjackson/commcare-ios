import XCTest

/// Debug test to dump what XCTest can see in the accessibility tree.
final class AccessibilityDumpTest: XCTestCase {

    func testDumpAccessibilityTree() throws {
        let app = XCUIApplication()
        app.launch()

        // Wait for app to render
        Thread.sleep(forTimeInterval: 5)

        print("=== ACCESSIBILITY TREE DUMP ===")
        print("Windows: \(app.windows.count)")
        print("Static Texts: \(app.staticTexts.count)")
        for i in 0..<min(app.staticTexts.count, 20) {
            let el = app.staticTexts.element(boundBy: i)
            print("  StaticText[\(i)]: '\(el.label)' frame=\(el.frame)")
        }
        print("Text Fields: \(app.textFields.count)")
        for i in 0..<min(app.textFields.count, 10) {
            let el = app.textFields.element(boundBy: i)
            print("  TextField[\(i)]: '\(el.label)' placeholder='\(el.placeholderValue ?? "nil")'")
        }
        print("Secure Text Fields: \(app.secureTextFields.count)")
        print("Buttons: \(app.buttons.count)")
        for i in 0..<min(app.buttons.count, 10) {
            let el = app.buttons.element(boundBy: i)
            print("  Button[\(i)]: '\(el.label)' enabled=\(el.isEnabled)")
        }
        print("Other Elements: \(app.otherElements.count)")
        print("=== END DUMP ===")

        // Dump the full debug description
        print("\n=== FULL DEBUG DESCRIPTION ===")
        print(app.debugDescription)
    }
}
