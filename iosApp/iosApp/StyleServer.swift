import Foundation

/// Simple local HTTP server that serves MapLibre style JSON for offline pack creation.
/// MapLibre's offline download engine needs HTTP URLs for styles (file:// doesn't work reliably).
class StyleServer {
    static let shared = StyleServer()

    private var serverSocket: Int32 = -1
    private var isRunning = false
    private var styles: [String: String] = [:]
    private let lock = NSLock()
    let port: UInt16 = 8766

    func setStyle(name: String, json: String) {
        lock.lock()
        styles[name] = json
        lock.unlock()
        startIfNeeded()
    }

    func styleURL(for name: String) -> URL {
        return URL(string: "http://127.0.0.1:\(port)/styles/\(name)-style.json")!
    }

    private func startIfNeeded() {
        guard !isRunning else { return }
        isRunning = true

        serverSocket = Darwin.socket(AF_INET, SOCK_STREAM, 0)
        guard serverSocket >= 0 else {
            NSLog("[StyleServer] Failed to create socket")
            isRunning = false
            return
        }

        var reuse: Int32 = 1
        setsockopt(serverSocket, SOL_SOCKET, SO_REUSEADDR, &reuse, socklen_t(MemoryLayout<Int32>.size))

        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = port.bigEndian
        addr.sin_addr.s_addr = inet_addr("127.0.0.1")

        let bindResult = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { ptr in
                Darwin.bind(serverSocket, ptr, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }

        guard bindResult == 0 else {
            NSLog("[StyleServer] Failed to bind to port \(port): errno=\(errno)")
            Darwin.close(serverSocket)
            serverSocket = -1
            isRunning = false
            return
        }

        Darwin.listen(serverSocket, 5)
        NSLog("[StyleServer] Started on port \(port)")

        DispatchQueue.global(qos: .utility).async { [weak self] in
            self?.acceptLoop()
        }
    }

    private func acceptLoop() {
        while isRunning && serverSocket >= 0 {
            let clientSocket = Darwin.accept(serverSocket, nil, nil)
            guard clientSocket >= 0 else { break }
            DispatchQueue.global(qos: .utility).async { [weak self] in
                self?.handleClient(clientSocket)
            }
        }
    }

    private func handleClient(_ clientSocket: Int32) {
        var buffer = [UInt8](repeating: 0, count: 4096)
        let bytesRead = Darwin.recv(clientSocket, &buffer, buffer.count, 0)
        guard bytesRead > 0 else {
            Darwin.close(clientSocket)
            return
        }

        let request = String(bytes: buffer.prefix(bytesRead), encoding: .utf8) ?? ""

        // Parse "GET /styles/NAME-style.json HTTP/1.1"
        var responseBody = "{\"version\":8,\"sources\":{},\"layers\":[]}"
        if let range = request.range(of: "GET /styles/"),
           let endRange = request[range.upperBound...].range(of: "-style.json") {
            let name = String(request[range.upperBound..<endRange.lowerBound])
            lock.lock()
            let json = styles[name]
            lock.unlock()
            if let json = json {
                responseBody = json
                NSLog("[StyleServer] Serving style for: \(name) (\(json.count) bytes)")
            } else {
                NSLog("[StyleServer] No style found for: \(name), available: \(styles.keys.joined(separator: ", "))")
            }
        } else {
            NSLog("[StyleServer] Unrecognized request: \(request.prefix(100))")
        }

        let header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: \(responseBody.utf8.count)\r\nConnection: close\r\n\r\n"
        let response = header + responseBody
        let data = Array(response.utf8)
        Darwin.send(clientSocket, data, data.count, 0)
        Darwin.close(clientSocket)
    }
}
