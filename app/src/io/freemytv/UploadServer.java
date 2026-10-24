package io.freemytv;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Receives photos from the phone, on the home network only. It runs just while the "Photos from your phone" screen is
 * open (PhotosActivity), and everything but the page itself needs the PIN that screen shows:
 *  - only private addresses (192.168.x.x, 10.x.x.x, 172.16-31.x.x, link-local, localhost) are served;
 *  - the PIN is 6 random digits, new on every start; 10 wrong tries lock that address out for a minute;
 *  - the PIN travels in a header, never in the address, and no CORS header is sent, so another website open on the
 *    phone can't use the server;
 *  - the sender's file name is never used, the size is capped, and only JPEG, PNG and WebP images are kept.
 */
final class UploadServer {
    private UploadServer() {}

    private static final int MAX_HEADER = 16 * 1024, MAX_PHOTO = 40 * 1024 * 1024, MAX_TRIES = 10;
    private static final long LOCK_MS = 60_000;

    private static ServerSocket server;
    private static String pin = "";
    private static Context app;
    private static final Map<String, long[]> tries = new HashMap<>();   // address -> {wrong tries, locked until}

    /** Starts listening (if it isn't) and returns the PIN, or "" when no port could be opened. */
    static synchronized String start(Context c) {
        app = c.getApplicationContext();
        if (server != null) return pin;
        int digits = new SecureRandom().nextInt(1_000_000);
        pin = String.format("%06d", digits);
        tries.clear();
        for (int port = 8080; port < 8090 && server == null; port++) {
            try {
                server = new ServerSocket(port);
            } catch (IOException ignored) {
            }
        }
        if (server == null) return "";
        final ServerSocket mine = server;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (!mine.isClosed()) {
                    try {
                        final Socket s = mine.accept();
                        new Thread(new Runnable() {
                            @Override public void run() { serve(s); }
                        }, "UploadServer-client").start();
                    } catch (IOException e) {
                        break;
                    }
                }
            }
        }, "UploadServer");
        t.setDaemon(true);
        t.start();
        return pin;
    }

    static synchronized void stop() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
        server = null;
        pin = "";
    }

    static synchronized int port() {
        return server == null ? 0 : server.getLocalPort();
    }

    /** The TV's address on the home network, e.g. "192.168.0.137", or "". */
    static String address() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                Enumeration<InetAddress> all = n.getInetAddresses();
                while (all.hasMoreElements()) {
                    InetAddress a = all.nextElement();
                    if (a.isSiteLocalAddress() && a.getAddress().length == 4) return a.getHostAddress();
                }
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    // MARK: - One connection

    private static void serve(Socket s) {
        try {
            s.setSoTimeout(15_000);
            InetAddress from = s.getInetAddress();
            if (!(from.isSiteLocalAddress() || from.isLoopbackAddress() || from.isLinkLocalAddress())) return;
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String head = readHead(in);
            if (head == null) {
                reply(out, 400, "text/plain", "bad request".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String[] lines = head.split("\r\n");
            String[] first = lines[0].split(" ");
            if (first.length < 2) return;
            String method = first[0], target = first[1], path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c > 0) headers.put(lines[i].substring(0, c).trim().toLowerCase(java.util.Locale.ROOT), lines[i].substring(c + 1).trim());
            }
            if (method.equals("GET") && path.equals("/")) {
                reply(out, 200, "text/html; charset=utf-8", PAGE.getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!path.startsWith("/api/")) {
                reply(out, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String who = from.getHostAddress();
            int auth = authorize(who, headers.get("x-pin"));
            if (auth == 429) {
                reply(out, 429, "text/plain", "wait a minute".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (auth != 200) {
                reply(out, 401, "text/plain", "wrong PIN".getBytes(StandardCharsets.UTF_8));
                return;
            }
            api(method, path, param(query, "name"), headers, in, out);
        } catch (IOException | RuntimeException e) {
            AppLog.w("upload: " + e.getMessage());
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void api(String method, String path, String name, Map<String, String> headers, InputStream in, OutputStream out)
            throws IOException {
        Context c = app;
        if (method.equals("GET") && path.equals("/api/list")) {
            List<String> names = Photos.list(c);
            StringBuilder json = new StringBuilder("{\"photos\":[");
            for (int i = 0; i < names.size(); i++) json.append(i > 0 ? "," : "").append('"').append(names.get(i)).append('"');
            json.append("],\"wallpaper\":\"").append(Prefs.wallpaper()).append("\"}");
            reply(out, 200, "application/json", json.toString().getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("GET") && path.equals("/api/thumb")) {
            byte[] jpg = Photos.thumbnail(c, name);
            if (jpg == null) reply(out, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            else reply(out, 200, "image/jpeg", jpg);
        } else if (method.equals("POST") && path.equals("/api/upload")) {
            long length = -1;
            try {
                length = Long.parseLong(headers.get("content-length"));
            } catch (RuntimeException ignored) {
            }
            if (length <= 0 || length > MAX_PHOTO) {
                reply(out, 413, "text/plain", "too big".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String saved = Photos.save(c, new LimitedStream(in, length), length);
            if (saved == null) reply(out, 415, "text/plain", "not a usable photo (or the TV is full)".getBytes(StandardCharsets.UTF_8));
            else reply(out, 200, "text/plain", saved.getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("POST") && path.equals("/api/delete")) {
            reply(out, Photos.delete(c, name) ? 200 : 404, "text/plain", "".getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("POST") && path.equals("/api/wallpaper")) {
            if (name == null || name.isEmpty()) {
                Prefs.setWallpaper("");
            } else if (name.equals("*")) {   // rotate through all of them
                Prefs.setWallpaper("*");
            } else if (Photos.file(c, name) != null) {
                Prefs.setWallpaper(name);
            } else {
                reply(out, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            reply(out, 200, "text/plain", "".getBytes(StandardCharsets.UTF_8));
        } else {
            reply(out, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 200 when the PIN is right, 401 when it is wrong, 429 when this address is locked out. */
    private static synchronized int authorize(String who, String given) {
        long now = System.currentTimeMillis();
        long[] t = tries.get(who);
        if (t == null) {
            t = new long[2];
            tries.put(who, t);
        }
        if (t[1] > now) return 429;
        boolean ok = given != null && !pin.isEmpty()
                && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), pin.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            t[0] = 0;
            return 200;
        }
        if (++t[0] >= MAX_TRIES) {
            t[0] = 0;
            t[1] = now + LOCK_MS;
        }
        return 401;
    }

    // MARK: - HTTP basics

    /** The request line and headers (up to the blank line), or null if they are too long or cut. */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int a = 0, b = 0, c = 0, d;
        while ((d = in.read()) != -1) {
            buf.write(d);
            if (a == '\r' && b == '\n' && c == '\r' && d == '\n') return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
            if (buf.size() > MAX_HEADER) return null;
            a = b;
            b = c;
            c = d;
        }
        return null;
    }

    private static String param(String query, String key) {
        for (String pair : query.split("&")) {
            int e = pair.indexOf('=');
            if (e > 0 && pair.substring(0, e).equals(key)) return pair.substring(e + 1);
        }
        return null;
    }

    private static void reply(OutputStream out, int status, String type, byte[] body) throws IOException {
        String text = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 404 ? "Not Found" : status == 413 ? "Payload Too Large"
                : status == 415 ? "Unsupported Media Type" : status == 429 ? "Too Many Requests" : "Bad Request";
        String head = "HTTP/1.1 " + status + " " + text + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n"
                + "Content-Security-Policy: default-src 'self'; img-src 'self' blob:; style-src 'unsafe-inline'; script-src 'unsafe-inline'\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    /** Reads exactly the body the request announced, no more. */
    private static final class LimitedStream extends InputStream {
        private final InputStream in;
        private long left;

        LimitedStream(InputStream in, long length) {
            this.in = in;
            left = length;
        }

        @Override public int read() throws IOException {
            if (left <= 0) return -1;
            int b = in.read();
            if (b >= 0) left--;
            return b;
        }

        @Override public int read(byte[] buf, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = in.read(buf, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
    }

    // MARK: - The page on the phone

    private static final String PAGE = "<!doctype html><html><head><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1,viewport-fit=cover'><meta name=color-scheme content='dark light'><title>GB Launcher</title><style>"
            + ":root{--bg:#141416;--fg:#f5f5f7;--dim:#98989d;--line:#ffffff24;--card:#1c1c1e;--on:#f5f5f7;--onfg:#1c1c1e}"
            + "@media(prefers-color-scheme:light){:root{--bg:#ebebf0;--fg:#1d1d1f;--dim:#6e6e73;--line:#00000020;--card:#fff;--on:#1d1d1f;--onfg:#f5f5f7}}"
            + "*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}"
            + "body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.45 -apple-system,BlinkMacSystemFont,'SF Pro Text','Segoe UI',system-ui,sans-serif;padding:max(28px,env(safe-area-inset-top)) 20px 56px}"
            + ".wrap{max-width:640px;margin:0 auto}"
            + "h1{font-size:15px;font-weight:600;letter-spacing:.02em;margin:0 0 4px}"
            + ".sub{color:var(--dim);margin:0 0 28px}"
            + ".card{background:var(--card);border-radius:22px;padding:22px}"
            + "input[type=tel]{width:100%;font:600 30px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;letter-spacing:.5em;text-align:center;padding:14px 0 14px .5em;border:0;border-bottom:1px solid var(--line);background:none;color:var(--fg);outline:0;border-radius:0}"
            + "input[type=tel]:focus{border-bottom-color:var(--fg)}"
            + "button,label.b{display:block;width:100%;text-align:center;font:600 16px -apple-system,system-ui,sans-serif;padding:15px 16px;border-radius:16px;border:0;background:var(--on);color:var(--onfg);margin-top:18px;cursor:pointer}"
            + "button.o{background:none;color:var(--fg);border:1px solid var(--line);margin-top:10px}"
            + "button:active,label.b:active{opacity:.7}"
            + ".two{display:flex;gap:10px}.two button{margin-top:10px}"
            + "#msg{color:var(--dim);min-height:22px;text-align:center;margin-top:14px;font-size:14px}"
            + "#bar{height:3px;background:var(--line);margin-top:18px;display:none;border-radius:2px;overflow:hidden}#bar i{display:block;height:100%;width:0;background:var(--fg);transition:width .25s}"
            + "#state{color:var(--dim);font-size:14px;margin-top:10px;text-align:center;min-height:20px}"
            + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:14px;margin-top:22px}"
            + ".ph img{width:100%;aspect-ratio:4/3;object-fit:cover;display:block;border-radius:16px;background:var(--card)}"
            + ".ph .t{font-size:12px;color:var(--dim);margin:8px 2px 0;min-height:16px}"
            + ".ph .r{display:flex;gap:6px;margin-top:4px}.ph .r button{margin:0;padding:9px 6px;font-size:13px;font-weight:500;border-radius:12px;flex:1}"
            + ".empty{color:var(--dim);text-align:center;padding:34px 8px}"
            + "</style></head><body><div class=wrap>"
            + "<h1>GB Launcher</h1><p class=sub id=sub></p>"
            + "<div id=login class=card><input id=pin type=tel inputmode=numeric maxlength=6 autocomplete=off placeholder='······'><button id=go></button><div id=msg></div></div>"
            + "<div id=main style=display:none><div class=card><label class=b id=pick style=margin-top:0><input id=file type=file accept='image/*' multiple hidden></label>"
            + "<div class=two><button class=o id=clear></button><button class=o id=rot></button></div><div id=bar><i></i></div><div id=state></div></div><div class=grid id=grid></div></div></div><script>"
            + "var lg=(navigator.language||'').toLowerCase().slice(0,2),pt=lg=='pt',es=lg=='es';"
            + "var T=es?{sub:'Fotos para el salvapantallas y el fondo de la TV.',go:'Entrar',pick:'Elegir fotos',clear:'Fondo predeterminado',rot:'Girar todas',wall:'De fondo',del:'Borrar',cur:'Fondo ahora',bad:'PIN incorrecto',lock:'Demasiados intentos. Espera 1 minuto.',up:'Subiendo ',fail:'No se pudo: ',ok:'Listo.',none:'Aún no hay fotos.'}:pt?{sub:'Fotos para o descanso e o fundo da TV.',go:'Entrar',pick:'Escolher fotos',clear:'Fundo padrão',rot:'Girar todas',wall:'De fundo',del:'Apagar',cur:'Fundo agora',"
            + "bad:'PIN errado',lock:'Muitas tentativas. Espere 1 minuto.',up:'Enviando ',fail:'Não deu: ',ok:'Pronto.',none:'Nenhuma foto ainda.'}"
            + ":{sub:'Photos for the TV screensaver and background.',go:'Enter',pick:'Choose photos',clear:'Default background',rot:'Rotate all',wall:'Background',"
            + "del:'Delete',cur:'Background now',bad:'Wrong PIN',lock:'Too many tries. Wait a minute.',up:'Uploading ',fail:'Failed: ',ok:'Done.',none:'No photos yet.'};"
            + "var $=function(i){return document.getElementById(i)},pin='';"
            + "$('sub').textContent=T.sub;$('go').textContent=T.go;$('clear').textContent=T.clear;$('rot').textContent=T.rot;$('pick').insertBefore(document.createTextNode(T.pick),$('file'));"
            + "function api(m,p,b){return fetch(p,{method:m,headers:{'X-Pin':pin},body:b})}"
            + "function load(){api('GET','/api/list').then(function(r){if(r.status==429){$('msg').textContent=T.lock;return}"
            + "if(r.status!=200){$('msg').textContent=T.bad;return}return r.json().then(show)})}"
            + "function show(d){$('login').style.display='none';$('main').style.display='block';var g=$('grid');g.innerHTML='';"
            + "if(!d.photos.length){g.innerHTML='<div class=empty></div>';g.firstChild.textContent=T.none;g.style.display='block'}else g.style.display='grid';"
            + "d.photos.slice().reverse().forEach(function(n){var c=document.createElement('div');c.className='ph';var im=document.createElement('img');"
            + "api('GET','/api/thumb?name='+n).then(function(r){return r.blob()}).then(function(b){im.src=URL.createObjectURL(b)});c.appendChild(im);"
            + "var t=document.createElement('div');t.className='t';t.textContent=(n==d.wallpaper||d.wallpaper=='*')?T.cur:'';c.appendChild(t);"
            + "var bar=document.createElement('div');bar.className='r';var w=document.createElement('button');w.className='o';w.textContent=T.wall;"
            + "w.onclick=function(){api('POST','/api/wallpaper?name='+n).then(load)};var x=document.createElement('button');x.className='o';"
            + "x.textContent=T.del;x.onclick=function(){api('POST','/api/delete?name='+n).then(load)};bar.appendChild(w);bar.appendChild(x);c.appendChild(bar);g.appendChild(c)})}"
            + "$('go').onclick=function(){pin=$('pin').value;load()};$('pin').onkeydown=function(e){if(e.key=='Enter')$('go').onclick()};"
            + "$('clear').onclick=function(){api('POST','/api/wallpaper?name=').then(load)};$('rot').onclick=function(){api('POST','/api/wallpaper?name=*').then(load)};"
            + "$('file').onchange=function(){var fs=Array.prototype.slice.call(this.files),i=0,self=this,bar=$('bar');bar.style.display='block';(function next(){if(i>=fs.length){self.value='';$('state').textContent=T.ok;bar.style.display='none';load();return}"
            + "var f=fs[i++];$('state').textContent=T.up+i+'/'+fs.length;bar.firstChild.style.width=(100*(i-1)/fs.length)+'%';api('POST','/api/upload',f).then(function(r){return r.text().then(function(t){"
            + "if(r.status!=200)$('state').textContent=T.fail+f.name+' ('+t+')';next()})})})()};"
            + "if(/^#\\d{6}$/.test(location.hash)){pin=location.hash.slice(1);history.replaceState(null,'',location.pathname);load()}"
            + "</script></body></html>";
}
