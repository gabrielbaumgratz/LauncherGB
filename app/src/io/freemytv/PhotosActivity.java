package io.freemytv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "Photos from your phone": the address and the PIN to type on the phone. The upload server runs only while this
 * screen is open (see UploadServer); Back closes it.
 */
public class PhotosActivity extends Activity {
    private TextView address, pinView, count, hint;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            count.setText(Ui.trf("Photos on the TV: {n}", "n", Photos.list(PhotosActivity.this).size()));
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xFF0B0B0D);
        TextView title = text(Ui.tr("Photos from your phone"), 30, 0xFFFFFFFF);
        TextView lead = text(Ui.tr("On your phone, on the same Wi-Fi, open:"), 18, 0x99FFFFFF);
        address = text("", 34, 0xFFFFFFFF);
        TextView pinLabel = text(Ui.tr("PIN"), 18, 0x99FFFFFF);
        pinView = text("", 52, 0xFFFFFFFF);
        count = text("", 18, 0x99FFFFFF);
        hint = text(Ui.tr("Back closes this screen and stops the upload."), 15, 0x66FFFFFF);
        for (TextView t : new TextView[] {title, lead, address, pinLabel, pinView, count, hint}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = t == lead || t == pinLabel ? 28 : t == count || t == hint ? 24 : 8;
            box.addView(t, lp);
        }
        setContentView(box);
    }

    @Override protected void onStart() {
        super.onStart();
        String pin = UploadServer.start(this);
        String ip = UploadServer.address();
        if (pin.isEmpty() || ip.isEmpty()) {
            address.setText(Ui.tr("Couldn't start. Is the TV on Wi-Fi?"));
            pinView.setText("");
            UploadServer.stop();
        } else {
            address.setText("http://" + ip + ":" + UploadServer.port());
            pinView.setText(pin.substring(0, 3) + " " + pin.substring(3));
        }
        handler.post(refresh);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(refresh);
        UploadServer.stop();
        super.onStop();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_CENTER) {
            finish();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
