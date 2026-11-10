package de.onder.ocmate

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Kleiner Webserver im Heimnetz: Das Handy öffnet über den QR-Code am Fernseher ein Formular
 * und schickt M3U- oder Xtream-Zugangsdaten direkt an OCMate. Kein fremder Server beteiligt.
 * Läuft nur, solange das QR-Fenster offen ist, und nur mit einem zufälligen Zugangscode im Pfad.
 */
class RemoteServer(private val context: Context, private val onSubmit: (String) -> Unit) {

    @Volatile private var server: ServerSocket? = null
    @Volatile private var token = ""
    @Volatile private var stopAt = 0L

    /** Startet den Server und liefert die Adresse für den QR-Code (leer, wenn kein WLAN/LAN). */
    @Synchronized
    fun start(): String {
        stop()
        val ip = localIp() ?: return ""
        val socket = listOf(8090, 8091, 8092, 0).firstNotNullOfOrNull { port ->
            try { ServerSocket(port) } catch (e: Exception) { null }
        } ?: return ""
        token = randomToken()
        stopAt = System.currentTimeMillis() + 15 * 60 * 1000
        server = socket
        thread(isDaemon = true, name = "ocmate-remote") { acceptLoop(socket) }
        return "http://$ip:${socket.localPort}/$token"
    }

    @Synchronized
    fun stop() {
        try { server?.close() } catch (e: Exception) { }
        server = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            if (System.currentTimeMillis() > stopAt) { stop(); return }
            val client = try { socket.accept() } catch (e: Exception) { return }
            thread(isDaemon = true) { handle(client) }
        }
    }

    private fun handle(client: Socket) {
        client.use { s ->
            try {
                s.soTimeout = 10000
                val input = BufferedInputStream(s.getInputStream())
                val head = readHead(input) ?: return
                val lines = head.split("\r\n")
                val parts = lines.first().split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore('?').trimEnd('/')
                val length = lines.drop(1)
                    .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0

                when {
                    method == "GET" && path == "/$token" -> {
                        val html = context.assets.open("remote.html").bufferedReader().use { it.readText() }
                        send(s, 200, "text/html; charset=utf-8", html)
                    }
                    method == "POST" && path == "/$token/add" && length in 1..65536 -> {
                        val body = readBytes(input, length).toString(Charsets.UTF_8)
                        val form = parseForm(body)
                        val json = JSONObject()
                        for (key in listOf("mode", "name", "url", "epg", "srv", "user", "pass", "ext")) {
                            json.put(key, form[key] ?: "")
                        }
                        onSubmit(json.toString())
                        send(s, 200, "application/json; charset=utf-8", "{\"ok\":true}")
                        stop()
                    }
                    else -> send(s, 404, "text/plain; charset=utf-8", "Nicht gefunden. Bitte den QR-Code am Fernseher neu scannen.")
                }
            } catch (e: Exception) {
                // Verbindung abgebrochen oder ungültige Anfrage: ignorieren
            }
        }
    }

    /** Liest Anfragezeile und Kopfzeilen bis zur Leerzeile (max. 16 KB). */
    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var last4 = 0
        while (buf.size() < 16384) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) return buf.toString("ISO-8859-1").trimEnd()
        }
        return null
    }

    private fun readBytes(input: InputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n < 0) break
            read += n
        }
        return out.copyOf(read)
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split('&').filter { it.contains('=') }.associate {
            val k = URLDecoder.decode(it.substringBefore('='), "UTF-8")
            val v = URLDecoder.decode(it.substringAfter('='), "UTF-8")
            k to v.trim()
        }

    private fun send(s: Socket, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = if (code == 200) "OK" else "Not Found"
        val header = "HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        val out = s.getOutputStream()
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun randomToken(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        return (1..6).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    /** IPv4-Adresse des Fernsehers im Heimnetz (WLAN oder LAN-Kabel). */
    private fun localIp(): String? {
        val candidates = mutableListOf<Pair<String, String>>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        candidates.add(nif.name to (addr.hostAddress ?: continue))
                    }
                }
            }
        } catch (e: Exception) { return null }
        return candidates.sortedBy { (name, _) ->
            when {
                name.startsWith("wlan") -> 0
                name.startsWith("eth") -> 1
                else -> 2
            }
        }.firstOrNull()?.second
    }
}
