//
//  Item.swift
//  iosApp
//
//  Created by 卡莫 SAMA on 2024/2/27.
//

import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    
    init(timestamp: Date) {
        self.timestamp = timestamp
    }
}
