//
//  ContentView.swift
//  iosApp
//
//  Created by 卡莫 SAMA on 2024/2/27.
//


import SwiftUI
import SwiftData
//
//struct ContentView: View {
//    var body: some View {
//        VStack{
//            Text("Greeting().greet()")
//        }.padding()
//    }
//}
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
    }
}
