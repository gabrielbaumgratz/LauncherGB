package io.freemytv;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RuntimeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center, laid out like Tigris's (github.com/lesnolie/Tigris, MIT): a small glass panel at the top right,
 * over the home screen or any app. A Bluetooth tile (OK opens the list of headphones, one click connects), capsules for
 * the theme and the recommendations, round buttons, and the volume as a slider (← → slide, OK mutes). Back or the
 * channel button closes it. Sizes are Tigris's, in dp.
 */
public class ControlCenterActivity extends Activity {
    private static final int BLUETOOTH = 0, THEME = 1, RECS = 2, VOLUME = 3, PAIR = 4, TV_SETTINGS = 5, HOME_SETTINGS = 6, DEVICE = 7, AIRPLAY = 8, PICTURE = 9, MODE = 10, SAVER = 11, MUTE = 12, NETWORK = 13;
    /** The TV's own picture modes (its picture_mode setting): Standard, Movie, Sports, Game, Vivid. */
    private static final int[] MODE_IDS = {7, 9, 2, 10, 3};
    private static final String AIRPLAY_PACKAGE = "io.github.jqssun.airplay";
    private static final float CELL = 47, GAP = 12, WIDTH = CELL * 4 + GAP * 3, SLIDER_H = 38, ROOM = 16;

    /** One thing on the panel. */
    private static final class Control {
        final int kind;
        final RectF rect = new RectF();
        String title = "", sub = "", symbol = "";
        BluetoothAudio.DeviceInfo device;
        float scale = 1;
        int modeId;
        boolean on;   // the Bluetooth tile: the sound goes out through a headphone; a device row: it is connected
        Control(int kind) { this.kind = kind; }
    }

    /** The home screen as it was when this opened (small: the bilinear scaling blurs it); the glass refracts it. */
    static Bitmap backdrop;

    private PanelView view;
    private AudioManager audio;
    private boolean btOpen, pictureOpen, dark;
    private int appliedMode = -1;   // the mode just chosen, until the setting reads back
    private String btStatus = "";

    /** Name of the Bluetooth device the sound goes out through now, or null when it comes out of the TV. */
    private String soundOnBluetooth() {
        try {
            AudioDeviceInfo[] out;
            if (Build.VERSION.SDK_INT >= 33) {
                List<AudioDeviceInfo> used = audio.getAudioDevicesForAttributes(
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
                out = used.toArray(new AudioDeviceInfo[0]);
            } else {
                out = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            }
            for (AudioDeviceInfo d : out) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    CharSequence name = d.getProductName();
                    return name == null || name.length() == 0 ? "Bluetooth" : name.toString();
                }
            }
        } catch (RuntimeException e) {
            AppLog.w("sound route: " + e.getMessage());
        }
        return null;
    }
    private float volume, shownVolume;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 31) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            getWindow().getAttributes().dimAmount = 0.1f;
        }
        if (getIntent().getBooleanExtra("fromKey", false)) backdrop = null;   // over an app: no picture of the home screen
        if (backdrop == null && Build.VERSION.SDK_INT >= 31) getWindow().setBackgroundBlurRadius(60);
        dark = HomeData.get(this).isNight(System.currentTimeMillis());
        volume = shownVolume = readVolume();
        view = new PanelView(this);
        setContentView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.requestFocus();
        float dp = getResources().getDisplayMetrics().density;
        view.setAlpha(0);
        view.setTranslationX(24 * dp);
        view.animate().alpha(1).translationX(0).setDuration(240).start();
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 1);
        }
    }

    /** The INFO button pressed again while this is open: closes it. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("fromKey", false)) finish();
    }

    private int pictureMode() {
        return appliedMode >= 0 ? appliedMode : Settings.Global.getInt(getContentResolver(), "picture_mode", -1);
    }

    private String modeName(int id) {
        if (id == 7) return Ui.tr("Standard");
        if (id == 9) return Ui.tr("Movie");
        if (id == 2) return Ui.tr("Sports");
        if (id == 10) return Ui.tr("Game");
        if (id == 3) return Ui.tr("Vivid");
        return Ui.trf("Mode {n}", "n", id);
    }

    /**
     * Only a TV that keeps its picture mode in the picture_mode setting (TCL's) shows the row: elsewhere the setting is
     * missing, and a row that does nothing would only get in the way. Its own modes, plus the current one if it isn't
     * among the known five.
     */
    private boolean pictureSupported(int current) {
        String maker = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(java.util.Locale.ROOT);
        return current >= 0 && maker.contains("tcl");
    }

    private int[] modeIds(int current) {
        for (int id : MODE_IDS) if (id == current) return MODE_IDS;
        int[] all = java.util.Arrays.copyOf(MODE_IDS, MODE_IDS.length + 1);
        all[MODE_IDS.length] = current;
        return all;
    }

    /** Changes the TV's picture mode through the TV's own ADB (the setting needs the shell's permission). */
    private void applyPicture(final int id) {
        appliedMode = id;
        pictureOpen = false;
        view.build();
        new Thread(new Runnable() {
            @Override public void run() {
                try (Adb adb = Adb.connect(ControlCenterActivity.this, 10_000, null)) {
                    adb.shell("settings put global picture_mode " + id);
                } catch (java.io.IOException e) {
                    AppLog.w("picture mode: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            appliedMode = -1;
                            view.build();
                        }
                    });
                }
            }
        }, "picture-mode").start();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        view.build();
    }

    /** A system screen (the Bluetooth one is where a headphone that won't connect from here gets paired). */
    private void openSystem(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            AppLog.w("control center: " + e.getMessage());
        }
        finish();
    }

    private void activate(Control c) {
        switch (c.kind) {
            case BLUETOOTH:
                btOpen = !btOpen;
                pictureOpen = false;   // one list at a time: the panel has to fit the screen
                view.build();
                break;
            case DEVICE: {
                btStatus = Ui.tr("Connecting…");
                view.build();
                BluetoothAudio.ConnectCallback done = new BluetoothAudio.ConnectCallback() {
                    @Override public void onResult(final boolean ok) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                btStatus = "";
                                if (ok) view.build(); else openSystem(Settings.ACTION_BLUETOOTH_SETTINGS);
                            }
                        });
                    }
                };
                if (c.device.isConnected) BluetoothAudio.disconnect(this, c.device.device, done);
                else BluetoothAudio.connect(this, c.device.device, done);
                break;
            }
            case PAIR: openSystem(Settings.ACTION_BLUETOOTH_SETTINGS); break;
            case PICTURE:
                pictureOpen = !pictureOpen;
                btOpen = false;
                view.build();
                break;
            case MODE: applyPicture(c.modeId); break;
            case THEME: {
                // Automatic (dark at night) -> light -> dark. No restart, and the home screen behind changes with the panel.
                String now = Prefs.theme();
                Prefs.setThemeQuiet(now.equals("auto") ? "light" : now.equals("light") ? "dark" : "auto");
                dark = HomeData.get(this).isNight(System.currentTimeMillis());
                HomeActivity.refreshNow();
                view.build();
                break;
            }
            case RECS:
                Prefs.setRecommendations(!Prefs.recommendations());
                HomeData.get(this).settingsChanged();
                HomeActivity.refreshNow();
                view.build();
                break;
            case VOLUME:
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                volume = readVolume();
                view.invalidate();
                break;
            case AIRPLAY: {
                Intent intent = getPackageManager().getLaunchIntentForPackage(AIRPLAY_PACKAGE);
                if (intent != null) startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                break;
            }
            case TV_SETTINGS: openSystem(Settings.ACTION_SETTINGS); break;
            case NETWORK: openSystem(Settings.ACTION_WIFI_SETTINGS); break;
            case MUTE:
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                volume = readVolume();
                view.invalidate();
                break;
            case SAVER:
                try {
                    startActivity(new Intent(this, SettingsActivity.class).putExtra("screensaver", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    AppLog.w("control center: " + e.getMessage());
                }
                finish();
                break;
            case HOME_SETTINGS:
                try {
                    startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    AppLog.w("control center: " + e.getMessage());
                }
                finish();
                break;
            default:
        }
    }

    private float readVolume() {
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return 0;
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    /** One key press = 2% of the range (at least one step), like Tigris's volume slider. */
    private void slide(int dir) {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int step = Math.max(1, max / 50);
        int next = Math.max(0, Math.min(max, Math.round(volume * max) + dir * step));
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
        volume = next / (float) max;
        view.invalidate();
    }

    private final class PanelView extends View {
        private final float dp = getResources().getDisplayMetrics().density;
        private final List<Control> controls = new ArrayList<>();
        private final RectF panel = new RectF(), body = new RectF(), pill = new RectF();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int focus;
        private RuntimeShader liquid;
        private final Paint liquidPaint = new Paint();

        PanelView(Context c) {
            super(c);
            setFocusable(true);
            stroke.setStyle(Paint.Style.STROKE);
            if (Build.VERSION.SDK_INT >= 33 && backdrop != null) {
                try {
                    liquid = new RuntimeShader(Shaders.LIQUID);
                    BitmapShader s = new BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    s.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                    liquid.setInputShader("imageA", s);
                    liquid.setInputShader("imageB", s);
                    liquid.setFloatUniform("mixB", 0);
                    liquid.setFloatUniform("offset", 0, 0);
                    liquid.setFloatUniform("dispersion", 0.3f);
                    liquidPaint.setShader(liquid);
                } catch (RuntimeException | LinkageError e) {
                    AppLog.w("control center glass: " + e.getMessage());
                    liquid = null;
                }
            }
            build();
        }

        /**
         * Liquid glass over the home screen's snapshot (the same shader as the shelf): the picture is frosted and bent
         * near the edge, and the tint keeps the text readable. False when there is no shader: the caller fills flat.
         */
        private boolean glass(Canvas c, RectF r, float radius, int tint, float tintAlpha, float band, float refraction, float specular) {
            if (liquid == null || getWidth() == 0) return false;
            liquid.setFloatUniform("imageScale", (float) backdrop.getWidth() / getWidth(), (float) backdrop.getHeight() / getHeight());
            liquid.setFloatUniform("center", r.centerX(), r.centerY());
            liquid.setFloatUniform("halfSize", r.width() / 2, r.height() / 2);
            liquid.setFloatUniform("radius", Math.min(radius, Math.min(r.width(), r.height()) / 2));
            liquid.setFloatUniform("tint", Color.red(tint) / 255f, Color.green(tint) / 255f, Color.blue(tint) / 255f, tintAlpha);
            liquid.setFloatUniform("lift", dark ? 0.02f : 0.03f);
            liquid.setFloatUniform("band", Math.max(1, band));
            liquid.setFloatUniform("refraction", refraction);
            liquid.setFloatUniform("specular", specular);
            c.drawRect(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1, liquidPaint);
            return true;
        }

        private Control add(int kind, String title, String sub, String symbol) {
            Control c = new Control(kind);
            c.title = title;
            c.sub = sub;
            c.symbol = symbol;
            controls.add(c);
            return c;
        }

        void build() {
            Control keep = focus < controls.size() ? controls.get(focus) : null;
            controls.clear();
            List<BluetoothAudio.DeviceInfo> devices = BluetoothAudio.getPairedAudioDevices();
            String linked = "";
            for (BluetoothAudio.DeviceInfo d : devices) {
                if (d.isConnected) {
                    linked = d.name;
                    break;
                }
            }
            String soundTo = soundOnBluetooth();
            if (Prefs.ccShown("bluetooth")) {
                Control bluetooth = add(BLUETOOTH, "Bluetooth", !btStatus.isEmpty() ? btStatus
                        : soundTo != null ? Ui.trf("Sound: {name}", "name", soundTo) : Ui.tr("Sound: TV"), "speaker");
                bluetooth.on = soundTo != null;
            }
            String theme = Prefs.theme();
            if (Prefs.ccShown("theme")) {
                add(THEME, Ui.tr("Theme"), theme.equals("dark") ? Ui.tr("Dark") : theme.equals("light") ? Ui.tr("Light") : Ui.tr("Automatic"),
                        theme.equals("auto") ? "partly_cloudy_day" : dark ? "clear_night" : "sunny");
            }
            if (Prefs.ccShown("recs")) add(RECS, Ui.tr("Recommendations"), Prefs.recommendations() ? Ui.tr("On") : Ui.tr("Off"), "play");
            if (btOpen && Prefs.ccShown("bluetooth")) {
                for (BluetoothAudio.DeviceInfo d : devices) {
                    Control row = add(DEVICE, d.name, d.isConnected ? Ui.tr("Connected") : "", "speaker");
                    row.device = d;
                    row.on = d.isConnected;
                }
            }
            int mode = pictureMode();
            if (pictureSupported(mode) && Prefs.ccShown("picture")) {
                add(PICTURE, Ui.tr("Picture mode"), modeName(mode), "tv");
                if (pictureOpen) {
                    for (int id : modeIds(mode)) {
                        Control m = add(MODE, modeName(id), "", "");
                        m.modeId = id;
                        m.on = id == mode;
                    }
                }
            }
            if (Prefs.ccShown("pair")) add(PAIR, Ui.tr("Pair a new device"), "", "sync");
            if (Prefs.ccShown("tv")) add(TV_SETTINGS, Ui.tr("TV Settings"), "", "tv_settings");
            if (Prefs.ccShown("home") || controls.isEmpty() && !Prefs.ccShown("volume")) add(HOME_SETTINGS, Ui.tr("Home Settings"), "", "settings");
            if (Prefs.ccShown("airplay") && getPackageManager().getLaunchIntentForPackage(AIRPLAY_PACKAGE) != null) add(AIRPLAY, "AirPlay", "", "");
            if (Prefs.ccShown("saver")) add(SAVER, Ui.tr("Screensaver"), "", "bedtime");
            if (Prefs.ccShown("mute")) add(MUTE, Ui.tr("Mute"), "", "volume_off");
            if (Prefs.ccShown("network")) add(NETWORK, Ui.tr("Network"), "", "wifi");
            if (Prefs.ccShown("volume")) add(VOLUME, "", "", "speaker");
            focus = 0;
            if (keep != null) {
                for (int i = 0; i < controls.size(); i++) {
                    Control c = controls.get(i);
                    if (c.kind == keep.kind && (c.kind != DEVICE || c.device.name.equals(keep.device.name))) focus = i;
                }
            }
            layout();
            invalidate();
        }

        private boolean isRound(Control c) {
            return c.kind == PAIR || c.kind == TV_SETTINGS || c.kind == HOME_SETTINGS || c.kind == AIRPLAY
                    || c.kind == SAVER || c.kind == MUTE || c.kind == NETWORK;
        }

        /** Tigris's grid: a square tile beside two capsules, then the device list, three round buttons and the slider. */
        private void layout() {
            float cell = CELL * dp, gap = GAP * dp, w = WIDTH * dp, room = ROOM * dp;
            float x = getWidth() - 32 * dp - w, top = 66 * dp, y = top, tile = cell * 2 + gap;
            int rounds = 0, round = 0, mode = 0, modes = 0;
            for (Control r : controls) {
                if (isRound(r)) rounds++;
                if (r.kind == MODE) modes++;
            }
            int perRow = Math.min(rounds, 4);   // up to four round buttons to a row, then another row
            float roundStep = perRow > 1 ? (w - cell) / (perRow - 1) : 0;
            // The square Bluetooth tile has the Theme and Recommendations capsules beside it; without the tile (or with
            // one capsule) the rest still fits: full-width capsules, or one capsule centered beside the tile.
            boolean hasBt = false;
            int capsules = 0, capSlot = 0;
            for (Control r : controls) {
                if (r.kind == BLUETOOTH) hasBt = true;
                if (r.kind == THEME || r.kind == RECS) capsules++;
            }
            float blockTop = y;
            for (Control c : controls) {
                switch (c.kind) {
                    case BLUETOOTH:
                        blockTop = y;
                        c.rect.set(x, y, x + tile, y + tile);
                        if (capsules == 0) y += tile + gap;
                        break;
                    case THEME: case RECS:
                        if (hasBt) {
                            float cy = capsules == 2 ? blockTop + capSlot * (cell + gap) : blockTop + (tile - cell) / 2;
                            c.rect.set(x + tile + gap, cy, x + w, cy + cell);
                            if (++capSlot == capsules) y = blockTop + tile + gap;
                        } else {
                            c.rect.set(x, y, x + w, y + cell);
                            y += cell + gap;
                        }
                        break;
                    case DEVICE: case PICTURE:
                        c.rect.set(x, y, x + w, y + cell);
                        y += cell + gap;
                        break;
                    case MODE: {   // the five modes in a grid of three, two lines
                        float chipW = (w - 2 * 8 * dp) / 3, chipH = 38 * dp;
                        float left = x + (mode % 3) * (chipW + 8 * dp), top2 = y + (mode / 3) * (chipH + 8 * dp);
                        c.rect.set(left, top2, left + chipW, top2 + chipH);
                        if (++mode == modes) y += ((modes + 2) / 3) * (chipH + 8 * dp) - 8 * dp + gap;
                        break;
                    }
                    case VOLUME:
                        c.rect.set(x, y, x + w, y + SLIDER_H * dp);
                        y += SLIDER_H * dp + gap;
                        break;
                    default: {   // the round buttons share rows of four
                        float left = x + (round % 4) * roundStep, rowTop = y + (round / 4) * (cell + gap);
                        c.rect.set(left, rowTop, left + cell, rowTop + cell);
                        if (++round == rounds) y += ((rounds + 3) / 4) * (cell + gap);
                    }
                }
            }
            panel.set(x - room, top - room, x + w + room, y - gap + room);
            // A long list would run off the screen: lift the whole panel (never above the top margin).
            float bottomMargin = rounds > 0 ? 44 * dp : 10 * dp;
            float over = panel.bottom - (getHeight() - bottomMargin), lift = Math.max(0, Math.min(over, panel.top - 10 * dp));
            if (lift > 0) {
                panel.offset(0, -lift);
                for (Control c : controls) c.rect.offset(0, -lift);
            }
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            layout();
        }

        @Override protected void onDraw(Canvas c) {
            if (panel.isEmpty()) layout();
            float r = 28 * dp;
            fill.setShader(null);
            if (!glass(c, panel, r, dark ? 0xFF1C1C20 : 0xFFF6F6FA, Prefs.frost(dark ? 0.62f : 0.68f, Prefs.G_CONTROL), 26 * dp, 18 * dp * Prefs.lens(Prefs.G_CONTROL), dark ? 0.9f : 0.7f)) {
                int base = dark ? 0x1E1E22 : 0xF4F4F8;
                int alpha = (int) (Prefs.frost(dark ? 0.78f : 0.85f, Prefs.G_CONTROL) * 255);
                fill.setColor((alpha << 24) | base);
                c.drawRoundRect(panel, r, r, fill);
            }
            rim(c, panel, r, dark ? 0x99FFFFFF : 0xCCFFFFFF, dark ? 0x28FFFFFF : 0x33FFFFFF, 1.2f * dp);
            boolean moving = false;
            shownVolume += (volume - shownVolume) * 0.35f;
            if (Math.abs(volume - shownVolume) > 0.002f) moving = true; else shownVolume = volume;
            for (int i = 0; i < controls.size(); i++) {
                Control ctl = controls.get(i);
                float target = i != focus ? 1 : ctl.kind == BLUETOOTH ? 1.06f : isRound(ctl) ? 1.1f : ctl.kind == VOLUME ? 1.03f : 1.05f;
                ctl.scale += (target - ctl.scale) * 0.3f;
                if (Math.abs(target - ctl.scale) > 0.002f) moving = true; else ctl.scale = target;
                drawControl(c, ctl, i == focus);
            }
            if (moving) postInvalidateOnAnimation();
        }

        private void rim(Canvas c, RectF rect, float r, int lit, int faint, float width) {
            stroke.setStrokeWidth(width);
            stroke.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, lit, faint, Shader.TileMode.CLAMP));
            body.set(rect);
            body.inset(width / 2, width / 2);
            c.drawRoundRect(body, r, r, stroke);
            stroke.setShader(null);
        }

        private void drawControl(Canvas c, Control ctl, boolean focused) {
            RectF r = ctl.rect;
            boolean slider = ctl.kind == VOLUME, round = isRound(ctl), tile = ctl.kind == BLUETOOTH;
            float rad = slider ? 19 * dp : tile ? 24 * dp : Math.min(r.height() / 2, 100 * dp);
            c.save();
            c.scale(ctl.scale, ctl.scale, r.centerX(), r.centerY());
            fill.setShader(null);
            if (focused && !slider) {
                fill.setColor(dark ? 0xFFFFFFFF : 0xFF1C1C1E);
                fill.setShadowLayer(14 * dp, 0, 4 * dp, 0x55000000);
            } else {
                fill.setColor((slider && focused ? 70 : 34) << 24 | (dark ? 0xFFFFFF : 0x000000));
            }
            boolean flat = focused && !slider;
            if (flat) {
                c.drawRoundRect(r, rad, rad, fill);
            } else if (!glass(c, r, rad, dark ? 0xFF34343A : 0xFFFFFFFF, Prefs.frost(dark ? 0.52f : 0.6f, Prefs.G_CONTROL), 9 * dp, 7 * dp * Prefs.lens(Prefs.G_CONTROL), dark ? 0.8f : 0.6f)) {
                int base = dark ? 0x2C2C32 : 0xEAEAEF;
                int alpha = (int) (Prefs.frost(dark ? 0.60f : 0.72f, Prefs.G_CONTROL) * 255);
                fill.setColor((alpha << 24) | base);
                c.drawRoundRect(r, rad, rad, fill);
            }
            fill.clearShadowLayer();
            if (ctl.kind == BLUETOOTH && ctl.on && !focused) {   // the sound is in the headphones: the tile is lit green
                fill.setColor(0x4D30D158);
                c.drawRoundRect(r, rad, rad, fill);
            }
            if (!focused || slider) rim(c, r, rad, dark ? 0x66FFFFFF : 0x77FFFFFF, dark ? 0x18FFFFFF : 0x22FFFFFF, dp);
            int ink = focused ? (dark ? 0xE5000000 : 0xFFFFFFFF) : (dark ? 0xFFFFFFFF : 0xE51C1C1E);
            int soft = focused ? (dark ? 0xAA000000 : 0xB3FFFFFF) : (dark ? 0xCDFFFFFF : 0x991C1C1E);
            if (slider) {
                drawSlider(c, r, rad, focused);
            } else if (round && ctl.kind == AIRPLAY) {
                airplay(c, r.centerX(), r.centerY(), 10 * dp, ink);
            } else if (round) {
                Symbols.draw(getContext(), c, ctl.symbol, r.centerX(), r.centerY(), 15 * dp, ink);
            } else if (tile) {
                Symbols.draw(getContext(), c, ctl.symbol, r.centerX(), r.top + 36 * dp, 26 * dp, ink);
                Paint title = Ui.text(Ui.displaySemibold, 12.5f * dp, ink);
                Paint sub = Ui.text(Ui.regular, 10.5f * dp, soft);
                title.setTextAlign(Paint.Align.CENTER);
                sub.setTextAlign(Paint.Align.CENTER);
                c.drawText(ctl.title, r.centerX(), r.bottom - 30 * dp, title);
                c.drawText(trim(ctl.sub, sub, r.width() - 12 * dp), r.centerX(), r.bottom - 14 * dp, sub);
            } else if (ctl.kind == MODE) {
                Paint title = Ui.text(Ui.displaySemibold, 10.5f * dp, ink);
                title.setTextAlign(Paint.Align.CENTER);
                c.drawText(trim(ctl.title, title, r.width() - 22 * dp), r.centerX() + (ctl.on ? 5 * dp : 0), r.centerY() + Ui.halfCap(title), title);
            } else {
                // A capsule: the icon at the left, the title and a line under it.
                Symbols.draw(getContext(), c, ctl.symbol, r.left + 21 * dp, r.centerY(), 14 * dp, ink);
                Paint title = Ui.text(Ui.displaySemibold, 10.5f * dp, ink);
                Paint sub = Ui.text(Ui.regular, 9.5f * dp, soft);
                float tx = r.left + 38 * dp, max = r.right - tx - 8 * dp;
                if (ctl.sub.isEmpty()) {
                    c.drawText(trim(ctl.title, title, max), tx, r.centerY() + Ui.halfCap(title), title);
                } else {
                    c.drawText(trim(ctl.title, title, max), tx, r.centerY() - 2 * dp, title);
                    c.drawText(trim(ctl.sub, sub, max), tx, r.centerY() + 11 * dp, sub);
                }
            }
            if (ctl.kind == BLUETOOTH || ctl.kind == DEVICE || ctl.kind == MODE) {   // a dot: green = on, grey = off
                fill.setShader(null);
                fill.setColor(ctl.on ? 0xFF30D158 : (ink & 0x00FFFFFF) | 0x77000000);
                float dot = (ctl.kind == BLUETOOTH ? 5.5f : 4.5f) * dp;
                if (ctl.kind == MODE) {   // only the current mode gets a dot, in front of its name
                    if (ctl.on) c.drawCircle(r.left + 14 * dp, r.centerY(), 3.5f * dp, fill);
                } else {
                    c.drawCircle(r.right - 15 * dp, ctl.kind == BLUETOOTH ? r.top + 15 * dp : r.centerY(), dot, fill);
                }
            }
            c.restore();
            if (round && focused && !ctl.title.isEmpty()) {
                Paint cap = Ui.text(Ui.displaySemibold, 11.5f * dp, dark ? 0xFFFFFFFF : 0xFF1C1C1E);
                cap.setTextAlign(Paint.Align.CENTER);
                float tw = cap.measureText(ctl.title);
                float padH = 14 * dp;
                float pillH = 26 * dp;
                float pillW = tw + padH * 2;
                float cx = panel.centerX();
                float cy = panel.bottom + 20 * dp;
                pill.set(cx - pillW / 2, cy - pillH / 2, cx + pillW / 2, cy + pillH / 2);

                fill.setShader(null);
                fill.setColor(dark ? 0xF21C1C20 : 0xF6F2F2F6);
                fill.setShadowLayer(10 * dp, 0, 3 * dp, 0x88000000);
                c.drawRoundRect(pill, pillH / 2, pillH / 2, fill);
                fill.clearShadowLayer();

                rim(c, pill, pillH / 2, dark ? 0x66FFFFFF : 0x88FFFFFF, dark ? 0x22FFFFFF : 0x22FFFFFF, 1f * dp);
                c.drawText(ctl.title, cx, cy + Ui.halfCap(cap), cap);
            }
        }

        private final android.graphics.Path airplayPath = new android.graphics.Path();

        /** The AirPlay mark: a screen open at the bottom, with a triangle rising into it. */
        private void airplay(Canvas c, float cx, float cy, float s, int color) {
            airplayPath.rewind();
            airplayPath.moveTo(cx - s * 0.5f, cy + s * 0.55f);
            airplayPath.lineTo(cx - s, cy + s * 0.55f);
            airplayPath.lineTo(cx - s, cy - s * 0.8f);
            airplayPath.lineTo(cx + s, cy - s * 0.8f);
            airplayPath.lineTo(cx + s, cy + s * 0.55f);
            airplayPath.lineTo(cx + s * 0.5f, cy + s * 0.55f);
            stroke.setColor(color);
            stroke.setStrokeWidth(1.8f * dp);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            c.drawPath(airplayPath, stroke);
            airplayPath.rewind();
            airplayPath.moveTo(cx, cy - s * 0.05f);
            airplayPath.lineTo(cx + s * 0.62f, cy + s * 0.9f);
            airplayPath.lineTo(cx - s * 0.62f, cy + s * 0.9f);
            airplayPath.close();
            fill.setShader(null);
            fill.setColor(color);
            c.drawPath(airplayPath, fill);
        }

        /** White fill from the left; the icon and the number turn dark where the fill covers them (Tigris's). */
        private void drawSlider(Canvas c, RectF r, float rad, boolean focused) {
            float edge = r.left + r.width() * shownVolume;
            int save = c.save();
            c.clipRect(r.left, r.top, edge, r.bottom);
            fill.setColor((focused ? 255 : 190) << 24 | (dark ? 0xFFFFFF : 0x1C1C1E));
            c.drawRoundRect(r, rad, rad, fill);
            c.restoreToCount(save);
            int over = dark ? 0xE5000000 : 0xFFFFFFFF, off = dark ? 0xFFFFFFFF : 0xE51C1C1E;
            float ix = r.left + 23 * dp;
            Symbols.draw(getContext(), c, "speaker", ix, r.centerY(), 12 * dp, ix < edge ? over : off);
            Paint num = Ui.text(Ui.displaySemibold, 11.5f * dp, r.right - 15 * dp < edge ? over : off);
            num.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Math.round(volume * 100) + "%", r.right - 15 * dp, r.centerY() + Ui.halfCap(num), num);
        }

        private String trim(String s, Paint p, float max) {
            if (p.measureText(s) <= max) return s;
            int n = s.length();
            while (n > 1 && p.measureText(s, 0, n) + p.measureText("…") > max) n--;
            return s.substring(0, n) + "…";
        }

        @Override public boolean onKeyDown(int code, KeyEvent e) {
            Control cur = controls.get(focus);
            switch (code) {
                case KeyEvent.KEYCODE_BACK: case Prefs.KEY_TCL_CHANNEL:
                    finish();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                    if (e.getRepeatCount() == 0) activate(cur);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (cur.kind == VOLUME) {
                        slide(-1);
                        return true;
                    }
                    return move(cur, -1, 0);
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (cur.kind == VOLUME) {
                        slide(1);
                        return true;
                    }
                    return move(cur, 1, 0);
                case KeyEvent.KEYCODE_DPAD_UP: return move(cur, 0, -1);
                case KeyEvent.KEYCODE_DPAD_DOWN: return move(cur, 0, 1);
                default: return super.onKeyDown(code, e);
            }
        }

        /**
         * The nearest control in that direction, by the gap between the edges (not the centers), favoring the ones that
         * overlap the current one across: a full-width capsule is straight below a tile and a capsule beside it.
         */
        private boolean move(Control cur, int dx, int dy) {
            int best = -1;
            float bestScore = Float.MAX_VALUE;
            RectF a = cur.rect;
            for (int i = 0; i < controls.size(); i++) {
                if (i == focus) continue;
                RectF o = controls.get(i).rect;
                float centers = dx != 0 ? (o.centerX() - a.centerX()) * dx : (o.centerY() - a.centerY()) * dy;
                if (centers <= 1) continue;
                float along, across;
                if (dx != 0) {
                    along = dx > 0 ? o.left - a.right : a.left - o.right;
                    across = Math.max(0, Math.max(o.top - a.bottom, a.top - o.bottom));
                } else {
                    along = dy > 0 ? o.top - a.bottom : a.top - o.bottom;
                    across = Math.max(0, Math.max(o.left - a.right, a.left - o.right));
                }
                float score = Math.max(0, along) + 3 * across + 0.01f * Math.abs(dx != 0 ? o.centerY() - a.centerY() : o.centerX() - a.centerX());
                if (score < bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            if (best >= 0) {
                focus = best;
                invalidate();
            }
            return true;
        }
    }

    @Override protected void onDestroy() {
        if (backdrop != null && !backdrop.isRecycled()) {
            backdrop.recycle();
            backdrop = null;
        }
        super.onDestroy();
    }
}
