package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * The remote's app buttons on TCL TVs. The stock home screen opens the apps for Netflix, YouTube and Prime (and TCL
 * Channel); with it off, the keys still arrive, so we open the app the person picked (Settings > Remote Buttons).
 * Disney and Globoplay are opened by the system itself, so they are not listed. The YouTube button (BUTTON_3) never
 * reaches an app's window; only the accessibility service's key filter (RemoteKeyService) sees it.
 * On Vestel TVs and Formovie (Xiaomi) projectors only the Netflix button is ours: the system opens Netflix itself (a
 * global key, after the filter).
 * On KTC TVs (NextStar) only the Tabii button is ours, a global key too: the TV's hot-key receiver opens Tabii with it.
 * On Sony TVs, the app buttons (and the Google Play button of older remotes) are global keys for Sony's own receiver;
 * the filter sees them first, the same way.
 */
final class RemoteKeys {
    private RemoteKeys() {}

    /** Some TCL remotes (BT_RC833) send TCL's own YouTube key instead of BUTTON_3: the same button. */
    private static final int KEY_YOUTUBE_TCL = 4065;

    /**
     * The key codes are the brand's own: on other brands they can be other buttons, so nothing here runs there.
     * Vestel builds TVs sold under other brands (F&U...), so BRAND varies: check the maker too.
     */
    static final boolean TCL = is("TCL"), VESTEL = is("Vestel"), KTC = is("KTC"), SONY = is("Sony"), FORMOVIE = is("Formovie");
    /** Brands whose Netflix button is STEM_1, a global key (MediaTek): only that button is ours. */
    static final boolean STEM_NETFLIX = VESTEL || FORMOVIE;

    static boolean supported() {
        return TCL || STEM_NETFLIX || KTC || SONY;
    }

    private static boolean is(String maker) {
        return maker.equalsIgnoreCase(Build.MANUFACTURER) || maker.equalsIgnoreCase(Build.BRAND);
    }

    /** Opens the app for this key on its first press. True if the key is ours and in use (it is then consumed). */
    static boolean onKey(Context c, KeyEvent e) {
        if (!supported()) return false;
        // BUTTON_3 is also a game controller's button: those keys from a gamepad or a joystick are left alone. (Only
        // those: TCL's IR remote reports itself as a gamepad too, source 0x701, and its keys must still get through.)
        int source = e.getSource();
        int pressed = e.getKeyCode();
        boolean buttonKey = pressed >= KeyEvent.KEYCODE_BUTTON_A && pressed <= KeyEvent.KEYCODE_BUTTON_MODE
                || pressed >= KeyEvent.KEYCODE_BUTTON_1 && pressed <= KeyEvent.KEYCODE_BUTTON_16;
        if (buttonKey && ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) return false;
        // The Settings button of TCL's remote sends NOTIFICATION; with the stock home screen off nothing answers it:
        // it opens the TV's settings.
        int raw = e.getKeyCode();
        if (TCL && (raw == KeyEvent.KEYCODE_NOTIFICATION || raw == KeyEvent.KEYCODE_SETTINGS)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                c.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        // The INFO ("i") button opens the Control Center, over whatever is on screen; in the live TV app it keeps showing
        // the program's info.
        if (raw == KeyEvent.KEYCODE_INFO) {
            if (isLiveTv(foreground)) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                c.startActivity(new Intent().setComponent(new android.content.ComponentName(c, ControlCenterActivity.class))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("fromKey", true));
            }
            return true;
        }
        int keyCode = e.getKeyCode() == KEY_YOUTUBE_TCL ? Prefs.KEY_YOUTUBE : e.getKeyCode();
        // Sony sends BUTTON_9 for five app buttons (BUTTON_4 for Netflix, BUTTON_8 for Google Play): only the scan code
        // tells them apart.
        if (SONY && (keyCode == KeyEvent.KEYCODE_BUTTON_4 || keyCode == KeyEvent.KEYCODE_BUTTON_8
                || keyCode == KeyEvent.KEYCODE_BUTTON_9)) {
            keyCode = Prefs.SONY_KEY + e.getScanCode();
        }
        boolean ours = false;
        for (int key : Prefs.KEYS) ours |= key == keyCode;
        if (!ours) return false;
        String pkg = Prefs.keyApp(keyCode);
        if (pkg.isEmpty()) return false;   // "Nothing": the key goes on as if we weren't here
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            Intent intent = launchIntent(c.getPackageManager(), pkg);
            if (intent == null && (keyCode == Prefs.KEY_TCL_CHANNEL || keyCode == Prefs.KEY_GUIDE)) intent = liveTv(c.getPackageManager());   // the default app isn't here
            AppLog.w("remote key " + e.getKeyCode() + " -> " + pkg + (intent == null ? " (not installed)" : ""));
            if (intent != null) c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    /** The app on screen, as the key service last heard it (that process only). */
    static volatile String foreground = "";

    private static boolean isLiveTv(String pkg) {
        if (pkg == null) return false;
        for (String live : LIVE_TV) if (live.equals(pkg)) return true;
        return false;
    }

    /** The same live TV apps as HomeApps.LIVE_TV_APPS (copied: this process stays small). */
    private static final String[] LIVE_TV = {"com.tcl.tv", "com.google.android.tv", "com.mediatek.wwtv.tvcenter", "com.sony.dtv.tvx",
            "com.smartdevice.livetv", "com.mediatek.tv.oneworld.tvcenter"};

    private static Intent liveTv(PackageManager pm) {
        for (String pkg : LIVE_TV) {
            Intent intent = launchIntent(pm, pkg);
            if (intent != null) return intent;
        }
        return null;
    }

    /** The app's TV launcher, or its plain one (kept here, not in HomeApps, so the key process stays small). */
    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }
}
