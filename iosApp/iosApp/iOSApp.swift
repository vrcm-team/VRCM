import SwiftUI
import ComposeApp
@main
struct iOSApp: App {
    init(){
        MainViewControllerKt.StartKoin()
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
