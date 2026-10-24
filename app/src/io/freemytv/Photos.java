package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The photos the person sent from the phone (UploadServer): for the screensaver and the home screen's background. They
 * live in the app's private folder; names are made here (never taken from the sender), so a name can't point elsewhere.
 */
final class Photos {
    private Photos() {}

    static final int MAX_PHOTOS = 60;
    private static final Pattern NAME = Pattern.compile("p\\d{10,}\\.(jpg|png|webp)");

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    /** The names, oldest first. */
    static List<String> list(Context c) {
        List<String> out = new ArrayList<>();
        String[] names = dir(c).list();
        if (names == null) return out;
        Arrays.sort(names);
        for (String n : names) if (NAME.matcher(n).matches()) out.add(n);
        return out;
    }

    /** The file for a name from list(), or null for anything else. */
    static File file(Context c, String name) {
        if (name == null || !NAME.matcher(name).matches()) return null;
        File f = new File(dir(c), name);
        return f.isFile() ? f : null;
    }

    static boolean delete(Context c, String name) {
        File f = file(c, name);
        boolean ok = f != null && f.delete();
        if (ok && name.equals(Prefs.wallpaper())) Prefs.setWallpaper("");
        return ok;
    }

    /** Reads at most `max` bytes from `in` into a new photo; the name, or null when it isn't a JPEG, PNG or WebP image. */
    static String save(Context c, InputStream in, long max) throws IOException {
        if (list(c).size() >= MAX_PHOTOS) return null;
        File tmp = new File(dir(c), "upload.tmp");
        long total = 0;
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    return null;
                }
                out.write(buf, 0, n);
            }
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(tmp.getPath(), bounds);
        String ext = "image/jpeg".equals(bounds.outMimeType) ? "jpg" : "image/png".equals(bounds.outMimeType) ? "png"
                : "image/webp".equals(bounds.outMimeType) ? "webp" : null;
        if (ext == null || bounds.outWidth < 200 || bounds.outHeight < 200) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        if ("jpg".equals(ext)) straighten(tmp);
        String name = "p" + System.currentTimeMillis() + "." + ext;
        if (!tmp.renameTo(new File(dir(c), name))) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        return name;
    }

    /** Phone photos are often saved sideways with an EXIF "rotate" note, which BitmapFactory ignores: turn the pixels. */
    private static void straighten(File f) {
        try {
            int o = new android.media.ExifInterface(f.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int degrees = o == 6 ? 90 : o == 3 ? 180 : o == 8 ? 270 : 0;
            if (degrees == 0) return;
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b == null) return;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(degrees);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            try (FileOutputStream out = new FileOutputStream(f)) {
                r.compress(Bitmap.CompressFormat.JPEG, 92, out);
            }
            r.recycle();
            if (r != b) b.recycle();
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            AppLog.w("photo rotation: " + e.getMessage());
        }
    }

    /** A small JPEG of the photo for the phone's list, or null. */
    static byte[] thumbnail(Context c, String name) {
        File f = file(c, name);
        if (f == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= 480) opts.inSampleSize *= 2;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), opts);
        if (b == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 78, out);
        b.recycle();
        return out.toByteArray();
    }
}
