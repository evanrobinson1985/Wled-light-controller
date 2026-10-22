package com.wledcontroller.app.show

import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * A tiny web server on the phone for the viewer jukebox: the page visitors open from the QR
 * code, the requests and votes they make, and the song (so they can listen on their own phones
 * in time with the lights). Plain HTTP on the local network or the phone's hotspot.
 */
class JukeboxServer(private val jukebox: Jukebox, private val title: () -> String = { "Light show" }) {

    @Volatile private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(12)
    /** Phones that fetched the page or state recently (for "N watching"). */
    private val recent = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val requestsServed = AtomicInteger()

    val running get() = server?.isClosed == false
    val port get() = server?.localPort ?: 0
    val listeners get() = recent.count { System.currentTimeMillis() - it.value < 15_000 }
    val requests get() = requestsServed.get()

    /** Starts on the first free port from [preferred]. Returns the port. */
    @Synchronized
    fun start(preferred: Int = 8080): Int {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        var lastError: Exception? = null
        for (p in preferred until preferred + 20) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(p))
                server = s
                Thread({ acceptLoop(s) }, "jukebox-http").apply { isDaemon = true }.start()
                return p
            } catch (e: Exception) { lastError = e }
        }
        throw lastError ?: IllegalStateException("No free port")
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = runCatching { s.accept() }.getOrNull() ?: break
            pool.execute { runCatching { handle(c) }; runCatching { c.close() } }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { '=' in it }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val client = socket.inetAddress?.hostAddress ?: "?"
        val out = socket.getOutputStream()
        requestsServed.incrementAndGet()
        when {
            path == "/" || path == "/index.html" -> { recent[client] = System.currentTimeMillis(); send(out, 200, "text/html; charset=utf-8", page().toByteArray()) }
            path == "/api/state" -> { recent[client] = System.currentTimeMillis(); send(out, 200, "application/json", jukebox.stateJson(client).toByteArray()) }
            path == "/api/request" && (method == "POST" || method == "GET") -> {
                val outcome = jukebox.request(client, query["id"].orEmpty())
                val ok = outcome == Jukebox.Outcome.Ok
                send(out, if (ok) 200 else 409, "application/json",
                    """{"ok":$ok,"message":${jsonString(outcome.message)}}""".toByteArray())
            }
            path == "/audio" -> sendAudio(out, headers["range"])
            else -> send(out, 404, "text/plain", "Not found".toByteArray())
        }
    }

    private fun jsonString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun send(out: OutputStream, status: Int, type: String, body: ByteArray, extra: String = "") {
        val reason = when (status) { 200 -> "OK"; 206 -> "Partial Content"; 404 -> "Not Found"; 409 -> "Conflict"; 416 -> "Range Not Satisfiable"; else -> "OK" }
        out.write(("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n$extra\r\n").toByteArray())
        out.write(body)
        out.flush()
    }

    /** The current song, with byte ranges so the visitor's phone can jump to the right moment. */
    private fun sendAudio(out: OutputStream, range: String?) {
        val f = jukebox.audioPath?.let(::File)?.takeIf { it.isFile } ?: return send(out, 404, "text/plain", "No song playing".toByteArray())
        val len = f.length()
        val type = when (f.extension.lowercase()) { "mp3" -> "audio/mpeg"; "m4a", "aac", "mp4" -> "audio/mp4"; "ogg", "oga" -> "audio/ogg"; "wav" -> "audio/wav"; "flac" -> "audio/flac"; else -> "application/octet-stream" }
        var start = 0L; var end = len - 1
        val partial = range?.startsWith("bytes=") == true
        if (partial) {
            val spec = range!!.removePrefix("bytes=").substringBefore(',')
            val a = spec.substringBefore('-'); val b = spec.substringAfter('-')
            if (a.isEmpty()) { start = (len - (b.toLongOrNull() ?: 0)).coerceAtLeast(0) } else {
                start = a.toLongOrNull() ?: 0
                end = b.toLongOrNull()?.coerceAtMost(len - 1) ?: (len - 1)
            }
            if (start >= len || start > end) return send(out, 416, "text/plain", ByteArray(0), "Content-Range: bytes */$len\r\n")
        }
        val count = end - start + 1
        out.write(("HTTP/1.1 ${if (partial) "206 Partial Content" else "200 OK"}\r\nContent-Type: $type\r\nContent-Length: $count\r\n" +
            "Accept-Ranges: bytes\r\n" + (if (partial) "Content-Range: bytes $start-$end/$len\r\n" else "") + "Connection: close\r\n\r\n").toByteArray())
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n <= 0) break
                out.write(buf, 0, n); left -= n
            }
        }
        out.flush()
    }

    private fun page(): String = PAGE.replace("{{TITLE}}", title().replace("&", "&amp;").replace("<", "&lt;"))

    companion object {
        /** The phone's addresses on its networks (Wi-Fi, hotspot), for the QR code. */
        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filter { it is java.net.Inet4Address && it.isSiteLocalAddress }
                .map(InetAddress::getHostAddress)
        }.getOrDefault(emptyList())

        private val PAGE = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{{TITLE}}</title>
<style>
:root{color-scheme:dark}body{margin:0;font-family:system-ui,sans-serif;background:#0b0d12;color:#eef;}
header{padding:20px 16px 8px;text-align:center}h1{margin:0;font-size:1.6em}
.now{margin:12px 16px;padding:16px;border-radius:16px;background:linear-gradient(135deg,#7a1f1f,#1f5a2a);}
.now small{opacity:.8}.bar{height:6px;background:#ffffff33;border-radius:3px;margin-top:10px}.bar div{height:6px;background:#fff;border-radius:3px;width:0}
.song{display:flex;align-items:center;gap:10px;margin:8px 16px;padding:12px 14px;border-radius:14px;background:#1a1d26}
.song .t{flex:1}.song button{border:0;border-radius:20px;padding:9px 16px;font-weight:600;background:#ffd34d;color:#222}
.song button:disabled{opacity:.4}.badge{font-size:.8em;opacity:.8}
#msg{position:fixed;left:16px;right:16px;bottom:16px;padding:12px;border-radius:12px;background:#333;display:none;text-align:center}
.listen{display:block;margin:8px auto;border:0;border-radius:22px;padding:10px 20px;font-weight:600;background:#4dd0ff;color:#002}
</style></head><body>
<header><h1>{{TITLE}}</h1><div id="how" class="badge"></div></header>
<div class="now" id="now"><small>Now playing</small><div id="nowTitle" style="font-size:1.3em;font-weight:700">—</div><div class="bar"><div id="prog"></div></div></div>
<button class="listen" id="listen" style="display:none">🔊 Listen on this phone</button>
<audio id="audio" preload="none"></audio>
<div id="songs"></div><div id="msg"></div>
<script>
let state=null, listening=false, nowId=null;
const audio=document.getElementById('audio');
function fmt(ms){const s=Math.floor(ms/1000);return Math.floor(s/60)+':'+String(s%60).padStart(2,'0')}
function toast(t){const m=document.getElementById('msg');m.textContent=t;m.style.display='block';setTimeout(()=>m.style.display='none',3000)}
async function ask(id){try{const r=await fetch('/api/request?id='+encodeURIComponent(id),{method:'POST'});const j=await r.json();toast(j.message);load()}catch(e){toast('Couldn\'t reach the show')}}
function sync(){
  if(!listening||!state||!state.hasAudio){if(!audio.paused)audio.pause();return}
  if(nowId!==state.nowId){nowId=state.nowId;audio.src='/audio?song='+encodeURIComponent(nowId)}
  const want=(state.positionMs+150)/1000;
  if(Math.abs(audio.currentTime-want)>0.6){audio.currentTime=want}
  if(audio.paused)audio.play().catch(()=>{})
}
async function load(){
  try{state=await (await fetch('/api/state')).json()}catch(e){return}
  document.getElementById('how').textContent=state.mode==='votes'?'Vote for the next show — most votes plays next':'Pick a show — they play in the order asked';
  document.getElementById('nowTitle').textContent=state.nowPlaying||'Starting soon…';
  document.getElementById('prog').style.width=state.nowPlaying?Math.min(100,100*state.positionMs/state.durationMs)+'%':'0';
  document.getElementById('listen').style.display=state.hasAudio?'block':'none';
  const list=document.getElementById('songs');list.innerHTML='';
  for(const s of state.songs){
    const d=document.createElement('div');d.className='song';
    const t=document.createElement('div');t.className='t';t.innerHTML='<b></b><div class="badge"></div>';
    t.querySelector('b').textContent=s.title;
    t.querySelector('.badge').textContent=fmt(s.durationMs)+(state.mode==='votes'?' · '+s.votes+' vote'+(s.votes==1?'':'s'):(s.queued?' · coming up':''));
    const b=document.createElement('button');b.textContent=state.mode==='votes'?'Vote':'Request';
    b.disabled=!!state.waitSeconds||(state.mode!=='votes'&&s.queued);b.onclick=()=>ask(s.id);
    d.appendChild(t);d.appendChild(b);list.appendChild(d);
  }
  sync();
}
document.getElementById('listen').onclick=()=>{listening=!listening;document.getElementById('listen').textContent=listening?'🔇 Stop listening':'🔊 Listen on this phone';sync()};
load();setInterval(load,2000);
</script></body></html>"""
    }
}
