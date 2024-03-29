//
//  ContentView.swift
//  iosApp
//
//  Created by 卡莫 SAMA on 2024/2/27.
//


import SwiftUI
import SwiftData
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
                .ignoresSafeArea(.all) // Compose has own keyboard handler
    }
}
