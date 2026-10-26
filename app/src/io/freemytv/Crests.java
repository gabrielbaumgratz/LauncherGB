package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The teams' crests for the sports tab: ESPN's own logos at 96 px (about 5 KB each), kept as files and, for the few on
 * screen, as bitmaps (at most 12, around 0.4 MB). Until one is ready the card draws the team's colored initial.
 */
final class Crests {
    private static final int SIZE = 96, KEEP = 12, MAX_FILES = 60;
    private static final Map<String, Bitmap> MEM = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) { return size() > KEEP; }
    };
    private static final Set<String> LOADING = new HashSet<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static volatile Runnable listener;

    private Crests() {}

    /** Called (from any thread) when a crest has arrived: the screen draws again. */
    static void setListener(Runnable r) { listener = r; }

    /** The crest for this ESPN logo address, or null while it is coming (or has none). */
    static synchronized Bitmap get(String url) {
        if (url == null || url.isEmpty()) return null;
        Bitmap b = MEM.get(url);
        if (b != null && !b.isRecycled()) return b;
        if (LOADING.add(url)) {
            final String key = url;
            WORKER.execute(() -> {
                Bitmap made = load(key);
                synchronized (Crests.class) {
                    LOADING.remove(key);
                    if (made != null) MEM.put(key, made);
                }
                Runnable l = listener;
                if (made != null && l != null) l.run();
            });
        }
        return null;
    }

    private static Bitmap load(String url) {
        Context c = Prefs.context();
        if (c == null) return null;
        File dir = new File(c.getFilesDir(), "crests");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, Integer.toHexString(url.hashCode()) + ".png");
        if (!f.exists()) {
            String[] old = dir.list();
            if (old != null && old.length > MAX_FILES) for (File x : dir.listFiles()) x.delete();   // a few leagues' worth; start over
            if (!download(url, f)) return null;
        }
        return BitmapFactory.decodeFile(f.getPath());
    }

    private static boolean download(String url, File to) {
        String path = url.contains("/i/teamlogos/") ? url.substring(url.indexOf("/i/teamlogos/")) : null;
        String address = path == null ? url : "https://a.espncdn.com/combiner/i?img=" + path + "&h=" + SIZE + "&w=" + SIZE;
        if (!address.startsWith("https://")) return false;
        HttpURLConnection conn = null;
        File part = new File(to.getPath() + ".part");
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            if (conn.getResponseCode() != 200) return false;
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[8192];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 300_000) return false;
                    out.write(buf, 0, n);
                }
            }
            return part.renameTo(to);
        } catch (java.io.IOException e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
            if (part.exists()) part.delete();
        }
    }
}
