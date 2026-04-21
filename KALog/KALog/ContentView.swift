//
//  ContentView.swift
//  KALog
//
//  Created by Бурмич Даниил on 26.03.2026.
//

import UIKit
import SwiftUI
import KALogIos

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
