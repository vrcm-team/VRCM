//
//  ContentView.swift
//  iosApp
//
//  Created by 卡莫 SAMA on 2024/2/27.
//

import SwiftUI
import SwiftData
import shared
struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var items: [Item]

    var body: some View {
        VStack{
            Text(Greeting().greet())
        }.padding()
    }

  
}

#Preview {
    ContentView()
        .modelContainer(for: Item.self, inMemory: true)
}
