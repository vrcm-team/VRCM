import SwiftUI
import ComposeApp
@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(ScanAppDelegate.self) private var appDelegate
    init(){
        MainViewControllerKt.StartKoin()
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

final class ScanAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: session.role)
        configuration.delegateClass = ScanSceneDelegate.self
        return configuration
    }
}

final class ScanSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        if options.shortcutItem?.type == "io.github.vrcmteam.vrcm.scan" {
            MainViewControllerKt.RequestQrScanner()
        }
    }

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem,
                     completionHandler: @escaping (Bool) -> Void) {
        let handled = shortcutItem.type == "io.github.vrcmteam.vrcm.scan"
        if handled { MainViewControllerKt.RequestQrScanner() }
        completionHandler(handled)
    }
}
