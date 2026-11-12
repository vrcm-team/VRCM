//
//  ContentView.swift
//  iosApp
//
//  Created by 卡莫 SAMA on 2024/2/27.
//


import SwiftUI
import SwiftData
import ComposeApp
import AVFoundation

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        QrScannerBridge.shared.presenter = NativeQrScannerPresenter()
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Creates an embedded scanner; Compose owns navigation and all page transitions.
final class NativeQrScannerPresenter: NSObject, QrScannerPresenter {
    func create(title: String, hint: String, backLabel: String, myCodeLabel: String, albumLabel: String,
               chooseHint: String, rescanLabel: String, selectCodeLabel: String,
               tintArgb: Int64, onScanned: @escaping (String) -> Void,
               onMyCode: @escaping () -> Void, onAlbum: @escaping () -> Void,
               onDismiss: @escaping () -> Void, onFailure: @escaping () -> Void) -> UIViewController {
        let scanner = QrCaptureController()
        scanner.title = title
        scanner.hint = hint
        scanner.backLabel = backLabel
        scanner.myCodeLabel = myCodeLabel
        scanner.albumLabel = albumLabel
        scanner.chooseHint = chooseHint
        scanner.rescanLabel = rescanLabel
        scanner.selectCodeLabel = selectCodeLabel
        scanner.onMyCode = { onMyCode() }
        scanner.onAlbum = { onAlbum() }
        let argb = UInt32(truncatingIfNeeded: tintArgb)
        scanner.scanTint = UIColor(
            red: CGFloat((argb >> 16) & 0xff) / 255,
            green: CGFloat((argb >> 8) & 0xff) / 255,
            blue: CGFloat(argb & 0xff) / 255,
            alpha: 1
        )
        scanner.complete = { value, failed in
            if failed { onFailure() }
            else if let value = value { onScanned(value) }
            else { onDismiss() }
        }
        return scanner
    }

    func stop(controller: UIViewController) {
        (controller as? QrCaptureController)?.cancelWithoutCallback()
    }
}

final class QrCaptureController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var complete: ((String?, Bool) -> Void)?
    var hint = ""
    var backLabel = ""
    var myCodeLabel = ""
    var albumLabel = ""
    var chooseHint = ""
    var rescanLabel = ""
    var selectCodeLabel = ""
    var onMyCode: (() -> Void)?
    var onAlbum: (() -> Void)?
    var scanTint = UIColor.systemBlue
    private let session = AVCaptureSession()
    private let cameraQueue = DispatchQueue(label: "io.github.vrcmteam.vrcm.qr-camera")
    private var preview: AVCaptureVideoPreviewLayer?
    private var finished = false
    private var configured = false
    private var visible = false
    private let backButton = UIButton(type: .system)
    private let hintLabel = UILabel()
    private let footer = UIView()
    private let topShade = CAGradientLayer()
    private let sweep = CAGradientLayer()
    private let scanLine = CAGradientLayer()
    private let modeButton = UIButton(type: .system)
    private let qrOutput = AVCaptureMetadataOutput()
    private var choices: [(value: String, metadata: AVMetadataMachineReadableCodeObject)] = []
    private var choiceButtons: [UIButton] = []

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    override func viewDidLoad() {
        super.viewDidLoad()
        overrideUserInterfaceStyle = .dark
        view.backgroundColor = .systemBackground
        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        self.preview = preview
        view.layer.addSublayer(preview)
        configureControls()
        NotificationCenter.default.addObserver(self, selector: #selector(background), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(cameraFailed), name: .AVCaptureSessionRuntimeError, object: session)
        NotificationCenter.default.addObserver(self, selector: #selector(updateSweep), name: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil)
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configureCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] allowed in
                DispatchQueue.main.async {
                    guard let self = self, !self.finished else { return }
                    if allowed { self.configureCamera() }
                    else { self.finish(nil, failed: true) }
                }
            }
        default: finish(nil, failed: true)
        }
    }

    private func configureCamera() {
        cameraQueue.async { [weak self] in
            guard let self = self else { return }
            self.session.beginConfiguration()
            guard let camera = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: camera), self.session.canAddInput(input) else {
                self.session.commitConfiguration()
                DispatchQueue.main.async { self.finish(nil, failed: true) }
                return
            }
            self.session.addInput(input)
            let output = self.qrOutput
            guard self.session.canAddOutput(output) else {
                self.session.commitConfiguration()
                DispatchQueue.main.async { self.finish(nil, failed: true) }
                return
            }
            self.session.addOutput(output)
            guard output.availableMetadataObjectTypes.contains(.qr) else {
                self.session.commitConfiguration()
                DispatchQueue.main.async { self.finish(nil, failed: true) }
                return
            }
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]
            self.session.commitConfiguration()
            DispatchQueue.main.async {
                guard !self.finished else { return }
                self.configured = true
                self.view.setNeedsLayout()
                self.foreground()
            }
        }
    }

    private func configureControls() {
        let background = UIColor.systemBackground.resolvedColor(with: view.traitCollection)
        topShade.colors = [background.withAlphaComponent(0.55).cgColor, UIColor.clear.cgColor]
        view.layer.addSublayer(topShade)
        sweep.colors = [UIColor.clear.cgColor, scanTint.withAlphaComponent(0.22).cgColor]
        view.layer.addSublayer(sweep)
        scanLine.colors = [UIColor.clear.cgColor, scanTint.cgColor, scanTint.cgColor, UIColor.clear.cgColor]
        scanLine.startPoint = CGPoint(x: 0, y: 0.5)
        scanLine.endPoint = CGPoint(x: 1, y: 0.5)
        sweep.addSublayer(scanLine)

        backButton.setImage(UIImage(systemName: "chevron.left", withConfiguration: UIImage.SymbolConfiguration(pointSize: 22)), for: .normal)
        backButton.tintColor = .label
        backButton.accessibilityLabel = backLabel
        backButton.addTarget(self, action: #selector(close), for: .touchUpInside)
        hintLabel.text = hint
        hintLabel.textColor = .label
        hintLabel.font = .preferredFont(forTextStyle: .subheadline)
        hintLabel.adjustsFontForContentSizeCategory = true
        hintLabel.numberOfLines = 0
        hintLabel.textAlignment = .center
        footer.backgroundColor = .systemBackground
        modeButton.setTitle(title, for: .normal)
        modeButton.setTitleColor(scanTint, for: .normal)
        modeButton.setTitleColor(scanTint, for: .disabled)
        modeButton.titleLabel?.font = .preferredFont(forTextStyle: .headline)
        modeButton.titleLabel?.adjustsFontForContentSizeCategory = true
        modeButton.titleLabel?.numberOfLines = 0
        modeButton.titleLabel?.textAlignment = .center
        modeButton.isEnabled = false
        modeButton.addTarget(self, action: #selector(rescan), for: .touchUpInside)
        let myCode = footerAction(title: myCodeLabel, symbol: "qrcode", action: #selector(showMyCode))
        let album = footerAction(title: albumLabel, symbol: "photo", action: #selector(chooseImage))
        let menu = UIStackView(arrangedSubviews: [myCode, modeButton, album])
        menu.axis = .horizontal
        menu.distribution = .fill
        menu.alignment = .top
        menu.spacing = 8
        [backButton, hintLabel, footer].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview($0)
        }
        menu.translatesAutoresizingMaskIntoConstraints = false
        footer.addSubview(menu)
        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            backButton.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 8),
            backButton.topAnchor.constraint(equalTo: safe.topAnchor, constant: 8),
            // A 44 pt target keeps the bare chevron easy to tap without a visible container.
            backButton.widthAnchor.constraint(equalToConstant: 44),
            backButton.heightAnchor.constraint(equalToConstant: 44),
            footer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            footer.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            footer.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            menu.topAnchor.constraint(equalTo: footer.topAnchor, constant: 20),
            menu.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 16),
            menu.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -16),
            menu.bottomAnchor.constraint(equalTo: safe.bottomAnchor, constant: -20),
            myCode.widthAnchor.constraint(equalTo: album.widthAnchor),
            modeButton.widthAnchor.constraint(equalTo: myCode.widthAnchor, multiplier: 1.3),
            modeButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 48),
            hintLabel.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 24),
            hintLabel.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -24),
            hintLabel.bottomAnchor.constraint(equalTo: footer.topAnchor, constant: -36),
            hintLabel.topAnchor.constraint(greaterThanOrEqualTo: backButton.bottomAnchor, constant: 12),
        ])
    }

    private func footerAction(title: String, symbol: String, action: Selector) -> UIView {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 24)), for: .normal)
        button.tintColor = .label
        button.backgroundColor = .secondarySystemBackground
        button.layer.cornerRadius = 24
        button.accessibilityLabel = title
        button.addTarget(self, action: action, for: .touchUpInside)
        let label = UILabel()
        label.text = title
        label.textColor = .secondaryLabel
        label.font = .preferredFont(forTextStyle: .footnote)
        label.adjustsFontForContentSizeCategory = true
        label.textAlignment = .center
        label.numberOfLines = 0
        label.isAccessibilityElement = false
        let column = UIStackView(arrangedSubviews: [button, label])
        column.axis = .vertical
        column.alignment = .center
        column.spacing = 8
        // Match the shared footer's 48 pt circular action and let its label grow freely.
        button.widthAnchor.constraint(equalToConstant: 48).isActive = true
        button.heightAnchor.constraint(equalToConstant: 48).isActive = true
        label.widthAnchor.constraint(equalTo: column.widthAnchor).isActive = true
        return column
    }

    @objc private func showMyCode() { finishAction(onMyCode) }
    @objc private func chooseImage() { finishAction(onAlbum) }

    private func finishAction(_ action: (() -> Void)?) {
        guard !finished else { return }
        complete = nil
        finishScanner(action)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
        topShade.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: backButton.frame.maxY + 24)
        updateSweep()
        if let connection = preview?.connection, connection.isVideoOrientationSupported {
            switch view.window?.windowScene?.interfaceOrientation {
            case .landscapeLeft: connection.videoOrientation = .landscapeLeft
            case .landscapeRight: connection.videoOrientation = .landscapeRight
            case .portraitUpsideDown: connection.videoOrientation = .portraitUpsideDown
            default: connection.videoOrientation = .portrait
            }
        }
        if let preview = preview, configured {
            let region = CGRect(x: 0, y: backButton.frame.maxY, width: view.bounds.width,
                                height: max(0, footer.frame.minY - backButton.frame.maxY))
            qrOutput.rectOfInterest = preview.metadataOutputRectConverted(fromLayerRect: region)
            for (index, button) in choiceButtons.enumerated() {
                guard let code = preview.transformedMetadataObject(for: choices[index].metadata) else { continue }
                button.frame = CGRect(x: code.bounds.midX - 24, y: code.bounds.midY - 24, width: 48, height: 48)
            }
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        visible = true
        foreground()
    }

    override func viewWillDisappear(_ animated: Bool) {
        visible = false
        background()
        super.viewWillDisappear(animated)
    }

    @objc private func foreground() {
        guard visible && !finished && choices.isEmpty else { return }
        updateSweep()
        cameraQueue.async { [weak self] in
            guard let self = self, self.configured, !self.session.isRunning else { return }
            self.session.startRunning()
        }
    }

    @objc private func background() {
        sweep.removeAnimation(forKey: "scan")
        cameraQueue.async { [weak self] in self?.session.stopRunning() }
    }

    @objc private func updateSweep() {
        sweep.isHidden = !choices.isEmpty
        let top = backButton.frame.maxY
        let height = max(0, hintLabel.frame.minY - top)
        let trail = min(96, height * 0.16)
        let previousBounds = sweep.bounds
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        sweep.bounds = CGRect(x: 0, y: 0, width: view.bounds.width, height: trail)
        sweep.position = CGPoint(x: view.bounds.midX, y: top + height * (0.08 + 0.4 * 0.68) - trail / 2)
        scanLine.frame = CGRect(x: 0, y: max(0, trail - 1.5), width: view.bounds.width, height: 1.5)
        CATransaction.commit()
        guard visible, !finished, choices.isEmpty, UIApplication.shared.applicationState == .active,
              !UIAccessibility.isReduceMotionEnabled else {
            sweep.removeAnimation(forKey: "scan")
            return
        }
        if previousBounds == sweep.bounds && sweep.animation(forKey: "scan") != nil { return }
        let animation = CABasicAnimation(keyPath: "position.y")
        animation.fromValue = top + height * 0.08 - trail / 2
        animation.toValue = top + height * 0.76 - trail / 2
        animation.duration = 2.6
        animation.repeatCount = .infinity
        sweep.add(animation, forKey: "scan")
    }

    @objc private func cameraFailed() {
        DispatchQueue.main.async { [weak self] in self?.finish(nil, failed: true) }
    }

    @objc private func close() { finish(nil, failed: false) }

    func cancelWithoutCallback() {
        complete = nil
        finish(nil, failed: false)
    }

    private func finish(_ value: String?, failed: Bool) {
        let callback = complete
        finishScanner { callback?(value, failed) }
    }

    private func finishScanner(_ callback: (() -> Void)?) {
        guard !finished else { return }
        finished = true
        background()
        complete = nil
        onMyCode = nil
        onAlbum = nil
        callback?()
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard !finished, choices.isEmpty, let preview = preview else { return }
        let detected = metadataObjects.compactMap { object -> (String, AVMetadataMachineReadableCodeObject)? in
            guard let code = object as? AVMetadataMachineReadableCodeObject, let value = code.stringValue,
                  let transformed = preview.transformedMetadataObject(for: code) else { return nil }
            let center = CGPoint(x: transformed.bounds.midX, y: transformed.bounds.midY)
            guard view.bounds.contains(center), center.y > backButton.frame.maxY && center.y < footer.frame.minY else { return nil }
            return (value, code)
        }
        if detected.count == 1 {
            finish(detected[0].0, failed: false)
        } else if detected.count > 1 {
            choices = detected
            background()
            hintLabel.text = chooseHint
            modeButton.setTitle(rescanLabel, for: .normal)
            modeButton.isEnabled = true
            for index in choices.indices {
                let button = UIButton(type: .custom)
                button.tag = index
                button.accessibilityLabel = selectCodeLabel.replacingOccurrences(of: "%d", with: String(index + 1))
                button.addTarget(self, action: #selector(selectCode(_:)), for: .touchUpInside)
                // The visible dot is 24 pt inside a 48 pt touch target.
                let dot = UIView(frame: CGRect(x: 12, y: 12, width: 24, height: 24))
                dot.backgroundColor = scanTint
                dot.layer.cornerRadius = 12
                dot.layer.borderWidth = 3
                dot.layer.borderColor = UIColor.label.resolvedColor(with: view.traitCollection).cgColor
                dot.isUserInteractionEnabled = false
                button.addSubview(dot)
                view.addSubview(button)
                choiceButtons.append(button)
            }
            view.setNeedsLayout()
        }
    }

    @objc private func selectCode(_ sender: UIButton) {
        guard choices.indices.contains(sender.tag) else { return }
        finish(choices[sender.tag].value, failed: false)
    }

    @objc private func rescan() {
        choices.removeAll()
        choiceButtons.forEach { $0.removeFromSuperview() }
        choiceButtons.removeAll()
        hintLabel.text = hint
        modeButton.setTitle(title, for: .normal)
        modeButton.isEnabled = false
        foreground()
    }

    deinit { NotificationCenter.default.removeObserver(self) }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea(.all) // Compose has own keyboard handler
    }
}
