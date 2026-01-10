package no.synth.where.data

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Simple HTTP server for serving MapLibre style JSON files locally.
 * Runs on localhost and generates style JSON dynamically based on the requested layer.
 * This is a singleton to prevent multiple servers from binding to the same port.
 */
class StyleServer private constructor(private val port: Int) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning = false

    fun start() {
        if (isRunning) {
            Log.d(TAG, "Server already running on port $port")
            return
        }

        isRunning = true
        thread(start = true, isDaemon = true, name = "StyleServer") {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Server started on port $port")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting connection: $e")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: $e", e)
            } finally {
                serverSocket?.close()
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }

    private fun handleClient(socket: Socket) {
        thread(start = true, isDaemon = true) {
            try {
                socket.use { client ->
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val writer = PrintWriter(client.getOutputStream(), true)

                    // Read the HTTP request line
                    val requestLine = reader.readLine() ?: return@use

                    // Skip remaining headers
                    while (true) {
                        val line = reader.readLine()
                        if (line.isNullOrBlank()) break
                    }

                    // Parse request: "GET /styles/kartverket-style.json HTTP/1.1"
                    val parts = requestLine.split(" ")
                    if (parts.size >= 2) {
                        val uri = parts[1]
                        val response = serveStyleJson(uri)
                        writer.write(response)
                        writer.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client: $e")
            }
        }
    }

    private fun serveStyleJson(uri: String): String {
        // Extract layer name from URI like /styles/kartverket-style.json
        val layerName = uri.substringAfter("/styles/").substringBefore("-style.json")

        val tileUrl = when (layerName) {
            "kartverket" -> "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png"
            "toporaster" -> "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png"
            "sjokartraster" -> "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png"
            "osm" -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            "opentopomap" -> "https://tile.opentopomap.org/{z}/{x}/{y}.png"
            "waymarkedtrails" -> "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png"
            else -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        }

        val styleJson = """
{
  "version": 8,
  "sources": {
    "$layerName": {
      "type": "raster",
      "tiles": ["$tileUrl"],
      "tileSize": 256
    }
  },
  "layers": [
    {
      "id": "$layerName-layer",
      "type": "raster",
      "source": "$layerName"
    }
  ]
}
        """.trimIndent()

        val contentLength = styleJson.toByteArray(Charsets.UTF_8).size

        return """HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: $contentLength
Connection: close

$styleJson"""
    }

    companion object {
        private const val TAG = "StyleServer"
        private const val DEFAULT_PORT = 8765
        
        @Volatile
        private var instance: StyleServer? = null
        
        fun getInstance(): StyleServer {
            return instance ?: synchronized(this) {
                instance ?: StyleServer(DEFAULT_PORT).also { instance = it }
            }
        }
    }
}
