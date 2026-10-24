package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;

/**
 * "Photos from your phone": the address and the PIN to type on the phone, in the settings' look (the dark gradient,
 * glass cards, big numbers). The upload server runs only while this screen is open (see UploadServer); Back closes it.
 */
public class PhotosActivity extends Activity {
    private PhotosView view;
    private String address = "", pin = "";
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
            UploadServer.stop();
        } else {
            address = "http://" + ip + ":" + UploadServer.port();
            pin = code;
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
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float k = 1;
        private Paint title, lead, step, stepNumber, label, big, digit, small, warn;

        PhotosView(Context c) {
            super(c);
            setContentDescription(Ui.tr("Photos from your phone"));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            k = w / 1920f;
            bg.setShader(new RadialGradient(w * 0.32f, h * 0.22f, Math.max(w, h) * 0.95f,
                    new int[] {0xFF181B28, 0xFF0E1018, 0xFF07080D}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(1, k));
            title = Ui.text(Ui.displaySemibold, 62 * k, 0xFFFFFFFF);
            lead = Ui.text(Ui.regular, 30 * k, 0x99FFFFFF);
            step = Ui.text(Ui.medium, 31 * k, 0xF2FFFFFF);
            stepNumber = Ui.text(Ui.semibold, 28 * k, 0xFFFFFFFF);
            stepNumber.setTextAlign(Paint.Align.CENTER);
            label = Ui.text(Ui.semibold, 21 * k, 0x8CFFFFFF);
            label.setLetterSpacing(0.06f);
            big = Ui.text(Ui.displaySemibold, 46 * k, 0xFFFFFFFF);
            digit = Ui.numbers(Ui.displaySemibold, 64 * k, 0xFFFFFFFF);
            digit.setTextAlign(Paint.Align.CENTER);
            small = Ui.text(Ui.regular, 24 * k, 0x8CFFFFFF);
            warn = Ui.text(Ui.regular, 28 * k, 0xFFFFC266);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), bg);
            c.drawText(Ui.tr("Photos from your phone"), 120 * k, 190 * k, title);
            c.drawText(Ui.tr("Send photos to the screensaver and the background."), 120 * k, 246 * k, lead);

            String[] steps = {Ui.tr("Connect your phone to the same Wi-Fi as the TV."),
                    Ui.tr("Open the address on the right in your phone's browser."),
                    Ui.tr("Type the PIN to enter.")};
            for (int i = 0; i < steps.length; i++) {
                float cy = (400 + i * 120) * k;
                fill.setColor(0xFF0A84FF);
                c.drawCircle(158 * k, cy, 38 * k, fill);
                c.drawText(String.valueOf(i + 1), 158 * k, cy + Ui.halfCap(stepNumber), stepNumber);
                c.drawText(steps[i], 224 * k, cy + Ui.halfCap(step), step);
            }

            // The glass card with the address and the PIN.
            r.set(1080 * k, 330 * k, 1800 * k, 800 * k);
            fill.setColor(0x1CFFFFFF);
            c.drawRoundRect(r, 34 * k, 34 * k, fill);
            stroke.setColor(0x2EFFFFFF);
            c.drawRoundRect(r, 34 * k, 34 * k, stroke);
            c.drawText(Ui.tr("Address").toUpperCase(), 1130 * k, 400 * k, label);
            if (address.isEmpty()) {
                c.drawText(Ui.tr("Couldn't start. Is the TV on Wi-Fi?"), 1130 * k, 470 * k, warn);
            } else {
                c.drawText(address.replace("http://", ""), 1130 * k, 470 * k, big);
            }
            fill.setColor(0x26FFFFFF);
            c.drawRect(1130 * k, 520 * k, 1750 * k, 520 * k + Math.max(1, k), fill);
            c.drawText(Ui.tr("PIN"), 1130 * k, 580 * k, label);
            for (int i = 0; i < 6; i++) {
                float x = 1130 * k + i * 104 * k;
                r.set(x, 610 * k, x + 90 * k, 730 * k);
                fill.setColor(0x33000000);
                c.drawRoundRect(r, 22 * k, 22 * k, fill);
                stroke.setColor(0x33FFFFFF);
                c.drawRoundRect(r, 22 * k, 22 * k, stroke);
                if (i < pin.length()) c.drawText(String.valueOf(pin.charAt(i)), r.centerX(), r.centerY() + Ui.halfCap(digit), digit);
            }
            c.drawText(Ui.trf("Photos on the TV: {n}", "n", Photos.list(PhotosActivity.this).size()), 120 * k, 960 * k, small);
            small.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Ui.tr("Back closes this screen and stops the upload."), 1800 * k, 960 * k, small);
            small.setTextAlign(Paint.Align.LEFT);
        }
    }
}
