package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import java.util.Arrays;

/**
 * "Photos from your phone": a QR code to scan with the phone, and the address and PIN to type on a computer (which has
 * no camera), in the home screen's look (glass cards, black or white ink by the theme). The upload server runs only
 * while this screen is open (see UploadServer); Back closes it.
 */
public class PhotosActivity extends Activity {
    private PhotosView view;
    private String address = "", pin = "";
    private boolean[][] qr;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            view.invalidate();
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        view = new PhotosView(this);
        setContentView(view);
    }

    @Override protected void onStart() {
        super.onStart();
        String code = UploadServer.start(this);
        String ip = UploadServer.address();
        if (code.isEmpty() || ip.isEmpty()) {
            address = "";
            pin = "";
            qr = null;
            UploadServer.stop();
        } else {
            address = "http://" + ip + ":" + UploadServer.port();
            pin = code;
            qr = Qr.modules(address + "/#" + pin);   // the PIN rides in the fragment: the phone signs in by itself
        }
        handler.post(refresh);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(refresh);
        UploadServer.stop();
        super.onStop();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            finish();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private final class PhotosView extends View {
        private static final int SMOKE = 0xFF111113, INK = 0xFF1C1C1E, L_INK = 0xFF1D1D1F, L_DIM = 0xFF6E6E73;
        private final Scene scene = new Scene();
        private final Shadows shadows = new Shadows();
        private final int[] glassPx = new int[Backdrop.GW * Backdrop.GH], frostPx = new int[Backdrop.FW * Backdrop.FH];
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF(), tmp = new RectF();
        private float k = 1;
        private boolean dark = true, themed;
        private Paint title, lead, step, stepNumber, label, big, digit, small, warn;

        PhotosView(Context c) {
            super(c);
            setContentDescription(Ui.tr("Photos from your phone"));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            k = w / 1920f;
            scene.setSize(w, h);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(1, k));
            title = Ui.text(Ui.displaySemibold, 62 * k, 0xFFFFFFFF);
            lead = Ui.text(Ui.regular, 30 * k, 0x99FFFFFF);
            step = Ui.text(Ui.medium, 31 * k, 0xF2FFFFFF);
            stepNumber = Ui.text(Ui.semibold, 28 * k, 0xFFFFFFFF);
            stepNumber.setTextAlign(Paint.Align.CENTER);
            label = Ui.text(Ui.semibold, 21 * k, 0x8CFFFFFF);
            label.setLetterSpacing(0.06f);
            big = Ui.text(Ui.displaySemibold, 40 * k, 0xFFFFFFFF);
            digit = Ui.numbers(Ui.displaySemibold, 56 * k, 0xFFFFFFFF);
            digit.setTextAlign(Paint.Align.CENTER);
            small = Ui.text(Ui.regular, 24 * k, 0x8CFFFFFF);
            warn = Ui.text(Ui.regular, 28 * k, 0xFFFFC266);
            themed = false;
        }

        private void theme() {
            boolean night = HomeData.get(getContext()).isNight(System.currentTimeMillis());
            if (themed && night == dark) return;
            themed = true;
            dark = night;
            Arrays.fill(frostPx, dark ? 0xFF141416 : 0xFFEBEBF0);
            Arrays.fill(glassPx, dark ? 0xFF141416 : 0xFFEBEBF0);
            scene.setFrost(frostPx);
            scene.setGlassImage(glassPx);
        }

        private int ink() { return dark ? 0xF2FFFFFF : L_INK; }

        private int dim() { return dark ? 0xADFFFFFF : L_DIM; }

        private void card(Canvas c, RectF rect, float radius) {
            if (!dark) {
                tmp.set(rect);
                tmp.offset(0, 10 * k);
                shade.setColor(0x1A000000);
                shadows.draw(c, tmp, radius, 26 * k, shade);
                scene.glass(c, rect, radius, 0xFFFFFFFF, 0.72f, 0, 0, 0.02f, 1, 1);
            } else {
                scene.glass(c, rect, radius, SMOKE, 0.4f, 0, 0, 0.02f, 1, 1);
            }
        }

        @Override protected void onDraw(Canvas c) {
            theme();
            c.drawColor(dark ? 0xFF141416 : 0xFFEBEBF0);
            title.setColor(ink());
            lead.setColor(dim());
            step.setColor(ink());
            label.setColor(dim());
            big.setColor(ink());
            digit.setColor(ink());
            small.setColor(dim());
            c.drawText(Ui.tr("Photos from your phone"), 120 * k, 190 * k, title);
            c.drawText(Ui.tr("Send photos to the screensaver and the background."), 120 * k, 246 * k, lead);

            String[] steps = {Ui.tr("Scan the code with your phone's camera."),
                    Ui.tr("On a computer, type the address and the PIN."),
                    Ui.tr("Phone and TV need the same Wi-Fi.")};
            for (int i = 0; i < steps.length; i++) {
                float cy = (420 + i * 130) * k;
                fill.setColor(ink());
                c.drawCircle(158 * k, cy, 36 * k, fill);
                stepNumber.setColor(dark ? INK : 0xFFFFFFFF);
                c.drawText(String.valueOf(i + 1), 158 * k, cy + Ui.halfCap(stepNumber), stepNumber);
                c.drawText(steps[i], 222 * k, cy + Ui.halfCap(step), step);
            }

            // The glass card: the QR code, then the address and the PIN.
            r.set(1000 * k, 250 * k, 1800 * k, 950 * k);
            card(c, new RectF(r), 34 * k);
            if (qr != null) {
                float quiet = 3, modules = qr.length + 2 * quiet, size = 300 * k, unit = size / modules;
                float qx = 1400 * k - size / 2, qy = 292 * k;
                r.set(qx, qy, qx + size, qy + size);
                fill.setColor(0xFFFFFFFF);
                c.drawRoundRect(r, 18 * k, 18 * k, fill);
                fill.setColor(0xFF000000);
                for (int y = 0; y < qr.length; y++) {
                    for (int x = 0; x < qr.length; x++) {
                        if (qr[y][x]) c.drawRect(qx + (x + quiet) * unit, qy + (y + quiet) * unit,
                                qx + (x + quiet + 1) * unit + 0.5f, qy + (y + quiet + 1) * unit + 0.5f, fill);
                    }
                }
            }
            c.drawText(Ui.tr("Address").toUpperCase(), 1050 * k, 720 * k, label);
            if (address.isEmpty()) {
                c.drawText(Ui.tr("Couldn't start. Is the TV on Wi-Fi?"), 1050 * k, 780 * k, warn);
            } else {
                c.drawText(address.replace("http://", ""), 1050 * k, 776 * k, big);
            }
            c.drawText(Ui.tr("PIN"), 1050 * k, 836 * k, label);
            for (int i = 0; i < 6; i++) {
                float x = 1050 * k + i * 114 * k;
                r.set(x, 856 * k, x + 98 * k, 912 * k + 2 * k);
                r.bottom = 912 * k;
                if (i < pin.length()) c.drawText(String.valueOf(pin.charAt(i)), r.centerX(), r.bottom - 4 * k, digit);
                fill.setColor(dim());
                c.drawRect(x, r.bottom + 4 * k, x + 98 * k, r.bottom + 4 * k + Math.max(2, 2 * k), fill);
            }
            c.drawText(Ui.trf("Photos on the TV: {n}", "n", Photos.list(PhotosActivity.this).size()), 120 * k, 990 * k, small);
            small.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Ui.tr("Back closes this screen and stops the upload."), 1800 * k, 990 * k, small);
            small.setTextAlign(Paint.Align.LEFT);
        }
    }
}
