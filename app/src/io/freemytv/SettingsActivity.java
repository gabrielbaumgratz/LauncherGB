package io.freemytv;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * GB Home TV's settings (the app's icon, "All Apps", or the gear on the home screen), laid out like the iPad's: the
 * categories in a sidebar on the left and, on the right, the selected one in grouped cards, each under its own small
 * title. The cards, the focus and the colors are the home screen's own (glass, white focus, black or white ink by the
 * light or dark theme). ← goes back to the sidebar, OK or → opens or changes the focused row. "Launcher" opens the GB
 * Launcher / Original choice (with a check on the current one); the switch runs in the main process (HomeMode) and this
 * screen only asks for it and follows the state file.
 */
public class SettingsActivity extends Activity {
    private SettingsView view;
    private boolean previewAlone;   // the preview came from outside (the Control Center): closing it leaves, back to where it started
    private DreamView preview;   // "Screensaver": the screensaver right here, without changing the system setting

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new SettingsView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.resume();
        // From ADB: am start -n io.freemytv/.SettingsActivity --ez screensaver true (straight to the preview).
        if (preview == null && getIntent().getBooleanExtra("screensaver", false)) {
            getIntent().removeExtra("screensaver");
            view.pause();
            previewAlone = true;
            showPreview();
        }
        // From ADB: --ei page 4 opens that category (0 General, 1 Home Screen, 2 Control Center, 3 Weather, 4 Games, 5 Screensaver,
        // 6 Performance, 7 Remote, 8 About); --ez demo true puts a made-up name and city on the screen (for screenshots), false brings the real ones back.
        // From ADB: --ei tennisAtp 5 --ei tennisWta 3 (how many of each ranking the tennis shows; ten by default, no menu).
        if (getIntent().hasExtra("tennisAtp")) Prefs.setTennisTop("atp", getIntent().getIntExtra("tennisAtp", 10));
        if (getIntent().hasExtra("tennisWta")) Prefs.setTennisTop("wta", getIntent().getIntExtra("tennisWta", 10));
        if (getIntent().hasExtra("language")) {   // --es language en|pt|es|auto
            Prefs.setLanguage(getIntent().getStringExtra("language"));
            getIntent().removeExtra("language");
            recreate();
        }
        if (getIntent().hasExtra("demo")) {
            Prefs.setDemo(getIntent().getBooleanExtra("demo", false));
            getIntent().removeExtra("demo");
            HomeData.get(this).settingsChanged();
            view.refresh();
        }
        if (getIntent().hasExtra("page")) {
            view.openPage(getIntent().getIntExtra("page", 0));
            getIntent().removeExtra("page");
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override protected void onPause() {
        closePreview();     // before pausing the view: closing the preview resumes it (view.resume)
        view.pause();
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (preview != null) {
            // Like the real screensaver: arrows, OK or Back close it (other keys go on to the system).
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (event.getAction() == KeyEvent.ACTION_UP) closePreview();
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        view.invalidate();
    }

    void showPreview() {
        preview = new DreamView(this);
        setContentView(preview);
        preview.start();
    }

    private void closePreview() {
        if (preview == null) return;
        preview.stop();
        preview = null;
        if (previewAlone) {
            previewAlone = false;
            finish();
            return;
        }
        setContentView(view);
        view.resume();
    }

    static final class SettingsView extends View implements Choreographer.FrameCallback {
        // What the pane shows: a category's page, or one of the lists that open from it.
        private static final int PAGE = 0, HOME_CHOICE = 1, PICKER = 2, SHELF_EDIT = 3, LEARN = 4;
        private static final int SIDE = 0, PANE = 1;
        private static final int P_GENERAL = 0, P_HOME = 1, P_CONTROL = 2, P_WEATHER = 3, P_GAMES = 4, P_SAVER = 5, P_PERF = 6, P_REMOTE = 7, P_ABOUT = 8, PAGES = 9;
        private static final String[] ICONS = {"settings", "home", "control_center", "sunny", "sports", "bedtime", "speed", "remote", "info"};
        // Rows of a page.
        private static final int HEADER = 0, NOTE = 1, LINK = 2, VALUE = 3, TOGGLE = 4, INFO = 5;

        // Layout, in a 1920 × 1080 screen.
        private static final float SIDE_L = 64, SIDE_R = 600, SIDE_TOP = 236, SIDE_ITEM_H = 78, SIDE_GAP = 6;
        private static final float PANE_L = 680, PANE_R = 1856, PANE_TOP = 214, PANE_BOTTOM = 1032, ROW_H = 74;
        private static final float SUB_TOP = 214, SUB_H = 72, CHOICE_H = 108;
        private static final int PICK_VISIBLE = 9;
        // The home screen's own colors.
        private static final int SMOKE = 0xFF111113, INK = 0xFF1C1C1E, WHITE = 0xFFF5F5F7;
        private static final int L_INK = 0xFF1D1D1F, L_DIM = 0xFF6E6E73;
        private static final float SHADOW_BLUR = 26;
        private static final int PICK_CITY = 0, PICK_LANGUAGE = -1, PICK_TEAM = -2, PICK_CUSTOM = -3, PICK_LEVEL = -4;
        private static final String[] UNITS = {"auto", "C", "F"}, THEMES = {"auto", "light", "dark"};

        private static final class Row {
            final int kind;
            final String label;
            Supplier<String> value;
            BooleanSupplier on, busy;
            Runnable act;

            Row(int kind, String label) {
                this.kind = kind;
                this.label = label;
            }

            boolean focusable() {
                return kind != HEADER && kind != NOTE;
            }
        }

        private final SettingsActivity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Spring[] pop = new Spring[96];   // 0..79: the rows, 80..: the sidebar
        private final Spring scrollSpring = new Spring(0.3f, 0.9f, 0);
        private final Scene scene = new Scene();
        private final Shadows shadows = new Shadows();
        private final int[] neutralGlassPx = new int[Backdrop.GW * Backdrop.GH], neutralFrostPx = new int[Backdrop.FW * Backdrop.FH];
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), dropShadow = new Paint(Paint.ANTI_ALIAS_FLAG), glassShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Row> rows = new ArrayList<>();
        private final HashMap<String, StaticLayout> notes = new HashMap<>();
        private float[] rowY = new float[0];
        private final List<float[]> groups = new ArrayList<>();
        private float contentBottom;
        private float scrollTarget;
        private boolean dark = true;
        private long themeAt;
        // A page that opens from a row (a Bluetooth device, a team): its title and how its rows are made.
        private Runnable detail;
        private String detailTitle = "";
        private int detailFrom;

        private final List<String> appPkgs = new ArrayList<>(), appLabels = new ArrayList<>();
        // The list picker (the app of a remote button, the language, the city, a team): what it shows, which one is in use.
        private final List<String> pickLabels = new ArrayList<>();
        // The shelf editor (also the top buttons' one, with editingTop): what it can hold (selected ones first, in
        // order) and the selection.
        private final List<String[]> shelfRows = new ArrayList<>();
        private final List<String> shelfOrder = new ArrayList<>();
        private boolean editingTop;
        private int pickFor, pickCurrent, pickScroll;   // pickFor: the remote button (index + 1)
        private final List<String[]> cities = new ArrayList<>(), teamResults = new ArrayList<>();
        private String cityStatus = "", teamStatus = "", teamSearchSport = "";
        private int customCode, customScan;   // the taught button being set up
        private final Runnable learnPoll = new Runnable() {
            @Override public void run() {
                if (screen != LEARN) return;
                int[] key = Prefs.learned();
                if (key != null) gotButton(key[0], key[1]);
                else handler.postDelayed(this, 250);
            }
        };
        private final RectF tmp = new RectF(), rowRect = new RectF(), box = new RectF();
        private JSONObject state = new JSONObject(), recs = new JSONObject();
        private boolean hasKey;
        private int screen = PAGE, zone = SIDE, page, paneFocus, focus;
        private boolean running, okDown, backDown, wasBusy;
        private final Spoken spoken;
        private long startedAt, lastNanos, pendingAt;
        private int w, h;
        private float k = 1;
        private Paint rowLabel, rowValue, rowSub, section, bigTitle, sideLabel, sideSmall, hint, spinner, white;
        private TextPaint notePaint;
        private List<HomeApps.Item> inputChoices;
        private List<BluetoothAudio.DeviceInfo> deviceChoices;

        private final Runnable poll = new Runnable() {
            @Override public void run() {
                if (!running) return;
                state = HomeMode.state(getContext());
                hasKey = Recs.hasKey(getContext());
                recs = Recs.status(getContext());
                boolean nowBusy = !busyText().isEmpty();
                if (nowBusy) pendingAt = 0;
                // A switch just ended: TalkBack reads how it went (the newest notice).
                JSONArray notices = state.optJSONArray("notices");
                if (wasBusy && !nowBusy && notices != null && notices.length() > 0) {
                    JSONObject newest = notices.optJSONObject(0);
                    if (newest != null) announceForAccessibility(Ui.render(newest.optString("text")));
                }
                wasBusy = nowBusy;
                updateTheme();
                if (screen == PAGE) rebuild();
                invalidate();
                handler.postDelayed(this, busy() ? 500 : 2000);
            }
        };

        SettingsView(SettingsActivity a) {
            super(a);
            activity = a;
            for (int i = 0; i < pop.length; i++) pop[i] = new Spring(0.28f, 0.85f, 0);
            setFocusable(true);
            setContentDescription("GB Home TV");
            // TalkBack: sidebar items are 1..PAGES, rows of a page 101.., the rows of a list 1...
            spoken = new Spoken(this, new Spoken.Source() {
                @Override public void items(List<Integer> out) {
                    if (screen == PAGE) {
                        for (int i = 0; i < PAGES; i++) out.add(i + 1);
                        for (int i = 0; i < rows.size(); i++) if (rows.get(i).focusable()) out.add(101 + i);
                    } else {
                        for (int i = 1; i <= listRows(); i++) out.add(i);
                    }
                }
                @Override public String text(int id) { return spokenText(id); }
                @Override public boolean bounds(int id, Rect out) {
                    RectF r = spokenBox(id);
                    if (r == null) return false;
                    r.round(out);
                    return true;
                }
                @Override public int focused() {
                    if (screen != PAGE) return focus + 1;
                    return zone == SIDE ? page + 1 : 101 + paneFocus;
                }
                @Override public void focus(int id) {
                    if (screen != PAGE) {
                        focus = id - 1;
                    } else if (id > 100) {
                        zone = PANE;
                        paneFocus = id - 101;
                    } else {
                        zone = SIDE;
                        selectPage(id - 1);
                    }
                    invalidate();
                }
                @Override public void click(int id) {
                    focus(id);
                    activate();
                }
            });
            rebuild();
        }

        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
            return spoken;
        }

        // MARK: - What TalkBack reads, and where

        private String spokenText(int id) {
            if (screen == PAGE) {
                if (id >= 1 && id <= PAGES) return pageTitles()[id - 1];
                int i = id - 101;
                if (i < 0 || i >= rows.size()) return "";
                Row r = rows.get(i);
                String v = r.value == null ? "" : r.value.get();
                if (r.kind == TOGGLE && v.isEmpty()) v = onOff(r.on.getAsBoolean());
                return v.isEmpty() ? r.label : r.label + ", " + v;
            }
            int i = id - 1;
            if (screen == HOME_CHOICE) {
                String name = i == 0 ? "GB Home TV, " + Ui.tr("no ads, with the new screensaver")
                        : "Original, " + Ui.tr("the TV's factory home screen");
                boolean inUse = (i == 0) == freeActive();
                return name + (inUse ? Ui.tr(". In use") : "") + ", " + Ui.trf("{i} of {n}", "i", i + 1, "n", 2);
            }
            if (screen == PICKER) {
                int at = pickScroll + i;
                if (at >= pickLabels.size()) return "";
                return pickLabels.get(at) + (at == pickCurrent ? Ui.tr(", selected") : "")
                        + ", " + Ui.trf("{i} of {n}", "i", at + 1, "n", pickLabels.size());
            }
            int at = pickScroll + i;
            if (at >= shelfRows.size()) return "";
            String[] app = shelfRows.get(at);
            int place = shelfOrder.indexOf(app[0]);
            if (editingTop) {
                return app[1] + ", " + (place >= 0 ? Ui.trf("in the top buttons, place {n}", "n", place + 1)
                        : Ui.tr("not in the top buttons"));
            }
            return app[1] + ", " + (place >= 0 ? Ui.trf("on the shelf, place {n}", "n", place + 1)
                    : Ui.tr("not on the shelf"));
        }

        private RectF spokenBox(int id) {
            if (screen == PAGE) {
                if (id >= 1 && id <= PAGES) {
                    float y = SIDE_TOP + (id - 1) * (SIDE_ITEM_H + SIDE_GAP);
                    box.set(SIDE_L * k, y * k, SIDE_R * k, (y + SIDE_ITEM_H) * k);
                    return box;
                }
                int i = id - 101;
                if (i < 0 || i >= rows.size()) return null;
                layout();
                float y = rowY[i] - scrollSpring.value;
                box.set(PANE_L * k, y * k, PANE_R * k, (y + ROW_H) * k);
                return box;
            }
            int i = id - 1;
            if (i < 0 || i >= listRows()) return null;
            float top = SUB_TOP + i * (screen == HOME_CHOICE ? CHOICE_H : SUB_H);
            box.set(PANE_L * k, top * k, PANE_R * k, (top + (screen == HOME_CHOICE ? CHOICE_H : SUB_H)) * k);
            return box;
        }

        // MARK: - Pages

        private String[] pageTitles() {
            return new String[] {Ui.tr("General"), Ui.tr("Home Screen"), Ui.tr("Control Center"), Ui.tr("Weather"), Ui.tr("Sports"),
                    Ui.tr("Screensaver"), Ui.tr("Performance"), Ui.tr("Remote Buttons"), Ui.tr("About")};
        }

        /** Everything is read again (the demo changed what the name, the city and the devices are). */
        void refresh() {
            inputChoices = null;
            deviceChoices = null;
            rebuild();
            invalidate();
        }

        /** Opens a category from outside (adb), with the focus in its rows. */
        void openPage(int index) {
            screen = PAGE;
            zone = PANE;
            detail = null;
            page = -1;   // always from its first row
            selectPage(Math.max(0, Math.min(PAGES - 1, index)));
            invalidate();
        }

        private void selectPage(int index) {
            if (index == page && !rows.isEmpty()) return;
            page = index;
            detail = null;
            paneFocus = 0;
            scrollSpring.snap(0);
            scrollTarget = 0;
            rebuild();
        }

        private Row add(Row r) {
            rows.add(r);
            return r;
        }

        private Row header(String title) {
            return add(new Row(HEADER, title));
        }

        private Row note(String text) {
            return add(new Row(NOTE, text));
        }

        private Row link(String label, Supplier<String> value, Runnable act) {
            Row r = add(new Row(LINK, label));
            r.value = value;
            r.act = act;
            return r;
        }

        private Row value(String label, Supplier<String> value, Runnable act) {
            Row r = add(new Row(VALUE, label));
            r.value = value;
            r.act = act;
            return r;
        }

        private Row toggle(String label, BooleanSupplier on, Runnable act) {
            Row r = add(new Row(TOGGLE, label));
            r.on = on;
            r.act = act;
            return r;
        }

        private Row info(String label, Supplier<String> value) {
            Row r = add(new Row(INFO, label));
            r.value = value;
            return r;
        }

        /** Opens a page from a row: `builder` makes its rows; Back returns to where the row was. */
        private void openDetail(String title, Runnable builder) {
            detailFrom = paneFocus;
            detail = builder;
            detailTitle = title;
            paneFocus = 0;
            scrollSpring.snap(0);
            scrollTarget = 0;
            rebuild();
            keepVisible();
        }

        private void closeDetail() {
            detail = null;
            paneFocus = detailFrom;
            rebuild();
            keepVisible();
        }

        /** The rows of the selected category (again whenever what they hold may have changed). */
        private void rebuild() {
            rows.clear();
            Context c = getContext();
            if (detail != null) {
                detail.run();
                clampFocus();
                return;
            }
            switch (page) {
                case P_GENERAL: {
                    Row home = link(Ui.tr("Launcher"), () -> busy() ? "" : (freeActive() ? "GB Home TV" : Ui.tr("Original")), () -> {
                        if (busy()) return;
                        screen = HOME_CHOICE;
                        focus = freeActive() ? 0 : 1;
                    });
                    home.busy = this::busy;
                    String busyText = busyText(), error = state.optString("error");
                    boolean allow = busy() && busyText.equals(Ui.tr("Accept “Allow USB debugging?” on the TV (tick “Always allow”)."));
                    note(allow ? Ui.tr("On the TV: tick “Always allow” and press OK.") : !busy() ? error : "");
                    link(Ui.tr("Update launcher"), () -> updateStatus, this::openUpdate);
                    header(Ui.tr("Personal"));
                    value(Ui.tr("Your name"), () -> Prefs.userName().isEmpty() ? Ui.tr("Not set") : Prefs.userName(), this::askName);
                    value(Ui.tr("Language"), () -> {
                        String lang = Prefs.language();
                        return lang.equals("auto") ? Ui.tr("Automatic") : Ui.languageName(lang);
                    }, () -> openPicker(PICK_LANGUAGE));
                    header(Ui.tr("Appearance"));
                    value(Ui.tr("Theme"), () -> {
                        String theme = Prefs.theme();
                        return theme.equals("light") ? Ui.tr("Light") : theme.equals("dark") ? Ui.tr("Dark") : Ui.tr("Automatic");
                    }, () -> {
                        Prefs.setTheme(THEMES[(Arrays.asList(THEMES).indexOf(Prefs.theme()) + 1) % THEMES.length]);
                        HomeData.get(c).settingsChanged();
                        updateTheme();
                    });
                    value(Ui.tr("Time format"), () -> {
                        String f = Prefs.clockFormat();
                        return f.equals("12") ? Ui.tr("12 hours") : f.equals("24") ? Ui.tr("24 hours") : Ui.tr("Automatic");
                    }, () -> {
                        String f = Prefs.clockFormat();
                        Prefs.setClockFormat(f.equals("auto") ? "24" : f.equals("24") ? "12" : "auto");
                    });
                    value(Ui.tr("Glass: clear to frosted"), () -> (Prefs.glass() + 1) + " / 5", () -> {
                        Prefs.setGlass((Prefs.glass() + 1) % 5);
                        HomeData.get(c).settingsChanged();
                    });
                    value(Ui.tr("Background picture"), () -> Prefs.photoBackground() ? Ui.tr("My photos") : Ui.tr("Recommendations"), () -> {
                        Prefs.setPhotoBackground(!Prefs.photoBackground());
                        HomeData.get(c).settingsChanged();
                        HomeActivity.refreshNow();
                    });
                    value(Ui.tr("Background: clear to frosted"), () -> (Prefs.backdrop() + 1) + " / 5", () -> {
                        Prefs.setBackdrop((Prefs.backdrop() + 1) % 5);
                        HomeData.get(c).settingsChanged();
                    });
                    toggle(Ui.tr("Navigation sounds"), Prefs::sounds, () -> Prefs.setSounds(!Prefs.sounds()));
                    header(Ui.tr("System"));
                    link(Ui.tr("TV Settings"), () -> "", () ->
                            activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                    break;
                }
                case P_HOME: {
                    header(Ui.tr("Layout"));
                    link(Ui.tr("Shelf"), () -> "", () -> openShelfEditor(false));
                    link(Ui.tr("Top Buttons"), () -> "", () -> openShelfEditor(true));
                    link(Ui.tr("Live TV shortcut"), this::liveTvSummary, this::openLiveTvPicker);
                    header(Ui.tr("Content"));
                    toggle(Ui.tr("Greeting"), Prefs::greeting, () -> Prefs.setGreeting(!Prefs.greeting()));
                    toggle(Ui.tr("Recommendations"), Prefs::recommendations, () -> {
                        Prefs.setRecommendations(!Prefs.recommendations());
                        HomeData.get(c).settingsChanged();
                        HomeActivity.refreshNow();
                    });
                    toggle(Ui.tr("Sports"), Prefs::sportsOn, () -> {
                        Prefs.setSportsOn(!Prefs.sportsOn());
                        if (Prefs.sportsOn()) Sports.get(c).refresh();
                        HomeData.get(c).settingsChanged();
                        HomeActivity.refreshNow();
                    });
                    note(Ui.tr("The three tabs with picks from your streaming apps, and the screensaver's picks."));
                    value(Ui.tr("Tab order"), () -> Prefs.sportsFirst() ? Ui.tr("Sports, then Recommendations") : Ui.tr("Recommendations, then Sports"), () -> {
                        Prefs.setSportsFirst(!Prefs.sportsFirst());
                        HomeData.get(c).settingsChanged();
                        HomeActivity.refreshNow();
                    });
                    List<HomeApps.Item> inputs = inputChoices();
                    if (!inputs.isEmpty()) {
                        header(Ui.tr("Inputs on the home screen"));
                        for (HomeApps.Item it : inputs) {
                            toggle(it.label, () -> !Prefs.inputHidden(it.key), () -> Prefs.setInputHidden(it.key, !Prefs.inputHidden(it.key)));
                        }
                    }
                    List<BluetoothAudio.DeviceInfo> devices = deviceChoices();
                    if (!devices.isEmpty()) {
                        header(Ui.tr("Bluetooth audio"));
                        for (BluetoothAudio.DeviceInfo d : devices) {
                            link(d.name, () -> Prefs.btShown(d.device.getAddress()) ? Ui.tr("Shown") : Ui.tr("Hidden"), () -> openDevice(d));
                        }
                        note(Ui.tr("OK on a device: show it on the home screen, rename it or forget it."));
                    }
                    break;
                }
                case P_CONTROL: {
                    note(Ui.tr("Choose what the Control Center shows. It opens from the first round button at the top, or from a remote button you pick in Remote Buttons."));
                    header(Ui.tr("Show in the Control Center"));
                    String[][] tiles = {{"bluetooth", "Bluetooth"}, {"theme", Ui.tr("Theme")}, {"recs", Ui.tr("Recommendations")},
                            {"picture", Ui.tr("Picture mode")}, {"pair", Ui.tr("Pair a new device")}, {"tv", Ui.tr("TV Settings")},
                            {"home", Ui.tr("Home Settings")}, {"airplay", "AirPlay"}, {"saver", Ui.tr("Screensaver")},
                            {"mute", Ui.tr("Mute")}, {"network", Ui.tr("Network")}, {"volume", Ui.tr("Volume")}};
                    for (String[] tile : tiles) {
                        toggle(tile[1], () -> Prefs.ccShown(tile[0]), () -> Prefs.setCcShown(tile[0], !Prefs.ccShown(tile[0])));
                    }
                    note(Ui.tr("Picture mode only exists on TVs that keep it in a setting (TCL's); AirPlay needs its receiver app."));
                    break;
                }
                case P_WEATHER: {
                    toggle(Ui.tr("Weather widget"), Prefs::weather, () -> {
                        Prefs.setWeather(!Prefs.weather());
                        HomeData.get(c).settingsChanged();
                    });
                    value(Ui.tr("Temperature"), () -> {
                        String unit = Prefs.temperature();
                        return unit.equals("C") ? "°C" : unit.equals("F") ? "°F" : Ui.tr("unit|Automatic");
                    }, () -> {
                        Prefs.setTemperature(UNITS[(Arrays.asList(UNITS).indexOf(Prefs.temperature()) + 1) % UNITS.length]);
                        HomeData.get(c).settingsChanged();
                    });
                    value(Ui.tr("City"), () -> !cityStatus.isEmpty() ? cityStatus : Prefs.city().isEmpty() ? "—" : Prefs.city(), this::askCity);
                    break;
                }
                case P_GAMES: {
                    toggle(Ui.tr("Sports widget"), Prefs::sportsOn, () -> {
                        Prefs.setSportsOn(!Prefs.sportsOn());
                        if (Prefs.sportsOn()) Sports.get(c).refresh();
                        HomeData.get(c).settingsChanged();
                        HomeActivity.refreshNow();
                    });
                    note(Ui.tr("The next games, beside the weather on the home screen. From ESPN, with no account."));

                    header(Ui.tr("Display priority"));
                    value(Ui.tr("Priority"), () -> {
                        int p = Prefs.sportsPriority();
                        return p == 0 ? Ui.tr("My teams first") : p == 1 ? Ui.tr("Championships first") : Ui.tr("Chronological");
                    }, () -> {
                        Prefs.setSportsPriority((Prefs.sportsPriority() + 1) % 3);
                        Sports.get(c).refresh();
                        HomeData.get(c).settingsChanged();
                        HomeActivity.refreshNow();
                    });

                    header(Ui.tr("My teams and athletes"));
                    List<String[]> allTeams = Prefs.sportsTeams();
                    for (int i = 0; i < allTeams.size(); i++) {
                        String[] team = allTeams.get(i);
                        final int pos = i;
                        link(team[3], () -> (pos == 0 && allTeams.size() > 1 ? Ui.tr("Priority") + " · " : "") + Sports.sportName(team[0]), () -> openTeam(team));
                    }
                    link(Ui.tr("Add a team or athlete"), () -> !teamStatus.isEmpty() ? teamStatus : "", () -> askTeam(null));
                    if (allTeams.isEmpty()) {
                        note(Ui.tr("Follow your favorite teams, tennis players, drivers or fighters."));
                    }

                    header(Ui.tr("Championships"));
                    link(Ui.tr("Championships"), () -> Ui.trf("{n} followed", "n", Prefs.sportsLeagues().size()), this::openLeagues);
                    note(Ui.tr("Choose the leagues, tours and circuits you want to follow."));
                    break;
                }
                case P_SAVER: {
                    link(Ui.tr("Preview"), () -> "", () -> {
                        pause();
                        activity.showPreview();
                    });
                    header(Ui.tr("Photos"));
                    link(Ui.tr("Photos from your phone"), () -> String.valueOf(Photos.list(c).size()), () ->
                            activity.startActivity(new Intent(activity, PhotosActivity.class)));
                    if (!Photos.list(c).isEmpty()) {
                        value(Ui.tr("Screensaver photos"), () -> Prefs.ownPhotos() ? Ui.tr("Mine") : Ui.tr("Recommendations"),
                                () -> Prefs.setOwnPhotos(!Prefs.ownPhotos()));
                    }
                    toggle(Ui.tr("Shuffle photos"), Prefs::shuffle, () -> Prefs.setShuffle(!Prefs.shuffle()));
                    value(Ui.tr("Photo interval"), () -> {
                        int s = Prefs.photoSeconds();
                        return s < 60 ? Ui.trf("{n} s", "n", s) : s < 3600 ? Ui.trf("{n} min", "n", s / 60) : Ui.trf("{n} h", "n", s / 3600);
                    }, () -> {
                        int[] options = Prefs.PHOTO_SECONDS;
                        int at = 0;
                        for (int i = 0; i < options.length; i++) if (options[i] == Prefs.photoSeconds()) at = i;
                        Prefs.setPhotoSeconds(options[(at + 1) % options.length]);
                    });
                    header(Ui.tr("Display"));
                    toggle(Ui.tr("Clock"), Prefs::dreamClock, () -> Prefs.setDreamClock(!Prefs.dreamClock()));
                    toggle(Ui.tr("Date and weather"), Prefs::dreamDate, () -> Prefs.setDreamDate(!Prefs.dreamDate()));
                    value(Ui.tr("Screensaver brightness"), () -> levelNames()[Prefs.dreamLevel()], () -> openPicker(PICK_LEVEL));
                    toggle(Ui.tr("Slow zoom of the photos"), Prefs::dreamMotion, () -> Prefs.setDreamMotion(!Prefs.dreamMotion()));
                    break;
                }
                case P_PERF: {
                    info(Ui.tr("Memory in use now"), () -> Ui.trf("{n} MB", "n", memoryMb()));
                    note(Ui.tr("What the home screen, the settings and the screensaver use together."));
                    header(Ui.tr("Mode"));
                    value(Ui.tr("Mode"), () -> {
                        int mode = perfMode();
                        return mode == 0 ? Ui.tr("Full") : mode == 1 ? Ui.tr("Performance") : Ui.tr("Custom");
                    }, () -> setPerfMode(perfMode() == 0 ? 1 : 0));
                    header(Ui.tr("What each mode does"));
                    info(Ui.tr("Full → Performance"), () -> "");
                    for (String feature : new String[] {Ui.tr("Recommendations"), Ui.tr("Weather widget"), Ui.tr("Sports widget"), Ui.tr("Slow zoom of the photos")}) {
                        info(feature, () -> Ui.tr("On") + "  →  " + Ui.tr("Off"));
                    }
                    note(Ui.tr("Both modes keep your apps, the inputs, the clock, the Control Center and the settings."));
                    header(Ui.tr("Fine tuning"));
                    toggle(Ui.tr("Recommendations"), Prefs::recommendations, () -> {
                        Prefs.setRecommendations(!Prefs.recommendations());
                        HomeData.get(c).settingsChanged();
                        HomeActivity.refreshNow();
                    });
                    toggle(Ui.tr("Weather widget"), Prefs::weather, () -> {
                        Prefs.setWeather(!Prefs.weather());
                        HomeData.get(c).settingsChanged();
                    });
                    toggle(Ui.tr("Sports widget"), Prefs::sportsOn, () -> {
                        Prefs.setSportsOn(!Prefs.sportsOn());
                        if (Prefs.sportsOn()) Sports.get(c).refresh();
                        HomeData.get(c).settingsChanged();
                        HomeActivity.refreshNow();
                    });
                    toggle(Ui.tr("Slow zoom of the photos"), Prefs::dreamMotion, () -> Prefs.setDreamMotion(!Prefs.dreamMotion()));
                    toggle(Ui.tr("Greeting"), Prefs::greeting, () -> Prefs.setGreeting(!Prefs.greeting()));
                    break;
                }
                case P_REMOTE: {
                    if (appPkgs.isEmpty()) loadApps();
                    info(Ui.tr("Your TV"), SettingsView::tvName);
                    header(Ui.tr("Buttons"));
                    if (RemoteKeys.supported()) {
                        String[] labels = keyLabels();
                        for (int i = 0; i < labels.length; i++) {
                            final int key = i;
                            link(labels[i], () -> {
                                int at = appPkgs.indexOf(Prefs.keyApp(Prefs.KEYS[key]));
                                return at >= 0 ? appLabels.get(at) : Ui.tr("Not installed");
                            }, () -> openPicker(key + 1));
                        }
                    }
                    for (String[] k : Prefs.customKeys()) {
                        link(customLabel(k), () -> {
                            int at = appPkgs.indexOf(k[2]);
                            return at >= 0 ? appLabels.get(at) : k[2].isEmpty() ? Ui.tr("Nothing") : Ui.tr("Not installed");
                        }, () -> openCustom(k));
                    }
                    link(Ui.tr("Add a button"), () -> "", this::startLearn);
                    note(RemoteKeys.supported() ? Ui.tr("The first ones are this TV's own buttons; the others you added. Press OK on one to change what it opens.")
                            : Ui.tr("This TV's buttons aren't known yet. Add the ones you want: press the button, then choose what it opens."));
                    header(Ui.tr("Reset"));
                    link(Ui.tr("Restore buttons to default"), () -> "", this::askRestoreButtons);
                    break;
                }
                default: {
                    header("GB Home TV");
                    info(Ui.tr("Version"), () -> versionName(c).replace("GB Home TV ", ""));
                    link(Ui.tr("Update launcher"), () -> updateStatus, this::openUpdate);
                    header(Ui.tr("Comparison"));
                    JSONObject metrics = state.optJSONObject("metrics");
                    JSONObject a = metrics == null ? null : metrics.optJSONObject(HomeMode.ORIGINAL);
                    JSONObject b = metrics == null ? null : metrics.optJSONObject(HomeMode.FREE);
                    String[][] cmp = {{Ui.tr("Home button"), "homeMs"}, {Ui.tr("Free memory"), "memAvailableMB"},
                            {Ui.tr("Home screen memory"), "homePssMB"}};
                    for (String[] m : cmp) info(m[0], () -> metric(a, m[1]) + "  →  " + metric(b, m[1]));
                    note(Ui.tr("Original") + "  →  GB Home TV");
                    header(Ui.tr("Recommendations"));
                    info(Ui.tr("Status"), this::recsLine);
                    if (hasKey) note(Ui.tr("This product uses the TMDB API but is not endorsed or certified by TMDB."));
                    header(Ui.tr("NOTICES"));
                    JSONArray notices = state.optJSONArray("notices");
                    int shown = 0;
                    for (int i = 0; notices != null && i < notices.length() && shown < 3; i++) {
                        JSONObject o = notices.optJSONObject(i);
                        if (o == null || o.optLong("at") < System.currentTimeMillis() - HomeMode.NOTICE_MS) continue;
                        if (!o.optString("text").startsWith("\u0001")) continue;   // written before notices kept their English
                        final String when = ago(o.optLong("at")), text = Ui.render(o.optString("text"));
                        info(text, () -> when);
                        shown++;
                    }
                    if (shown == 0) info(Ui.tr("None yet."), () -> "");
                    header(Ui.tr("Credits"));
                    info("Free My TV", () -> "github.com/simbasjungles/free-my-tv");
                    info("AirPlay", () -> "github.com/jqssun/android-airplay-server");
                    link(Ui.tr("Licenses"), () -> "MIT", this::openLicenses);
                    break;
                }
            }
            clampFocus();
        }

        private void openLicenses() {
            openDetail(Ui.tr("Licenses"), () -> {
                header("GB Home TV");
                info(Ui.tr("License"), () -> "MIT");
                note(licenseText());
                header("Material Symbols");
                info(Ui.tr("License"), () -> "Apache 2.0");
                note("Copyright Google LLC\nLicensed under the Apache License, Version 2.0.");
            });
        }

        private String licenseText() {
            try (InputStream in = getContext().getAssets().open("LICENSE.txt")) {
                return Ui.readUtf8(in).trim();
            } catch (IOException e) {
                return "MIT License\n\nCopyright (c) 2026 Gabriel Baumgratz\nCopyright (c) 2026 Free My TV contributors";
            }
        }

        private String updateStatus = "";
        private String githubUpdateStatus = "";
        private String githubApkUrl = "";
        private boolean githubDownloading;
        private String githubInstallStatus = "";

        private void openUpdate() {
            openDetail(Ui.tr("Update launcher"), () -> {
                Context c = getContext();
                header("GB Home TV");
                info(Ui.tr("Current version"), () -> versionName(c).replace("GB Home TV ", ""));
                info(Ui.tr("Launcher status"), () -> freeActive() ? Ui.tr("Active and protected") : Ui.tr("Inactive (Original)"));

                header(Ui.tr("System update"));
                link(Ui.tr("Check and reapply"), () -> updateStatus.isEmpty() ? Ui.tr("Run now") : updateStatus, this::runCheckAndUpdate);
                note(Ui.tr("If your TV received a system update from the manufacturer, tap to verify and reapply ad blocking and launcher settings."));

                header(Ui.tr("Online updates"));
                link(Ui.tr("Check GitHub"), () -> githubUpdateStatus.isEmpty() ? Ui.tr("Check") : githubUpdateStatus, this::checkGithubUpdates);
                if (!githubApkUrl.isEmpty()) {
                    link(Ui.tr("Install update"), () -> githubDownloading ? Ui.tr("Downloading…") : githubInstallStatus, this::downloadAndInstallGithubRelease);
                }
                info(Ui.tr("Repository"), () -> "github.com/gabrielbaumgratz/LauncherGB");
                note(Ui.tr("Checks for newer releases and updates on GitHub."));
            });
        }

        private void downloadAndInstallGithubRelease() {
            if (githubDownloading || githubApkUrl.isEmpty()) return;
            githubDownloading = true;
            githubInstallStatus = Ui.tr("Downloading…");
            invalidate();
            new Thread(() -> {
                File apkFile = new File(activity.getCacheDir(), "update.apk");
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(githubApkUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "GBHomeTV");
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    if (conn.getResponseCode() == 200) {
                        try (InputStream in = conn.getInputStream();
                             FileOutputStream out = new FileOutputStream(apkFile)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                            }
                        }
                        activity.runOnUiThread(() -> {
                            githubDownloading = false;
                            githubInstallStatus = Ui.tr("Ready to install");
                            invalidate();
                            try {
                                Uri uri = Uri.parse("content://io.freemytv.update/update.apk");
                                Intent install = new Intent(Intent.ACTION_VIEW);
                                install.setDataAndType(uri, "application/vnd.android.package-archive");
                                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(install);
                            } catch (Exception ex) {   // no installer on this TV
                                githubInstallStatus = Ui.tr("Download failed");
                                invalidate();
                            }
                        });
                    } else {
                        activity.runOnUiThread(() -> {
                            githubDownloading = false;
                            githubInstallStatus = Ui.tr("Download failed");
                            invalidate();
                        });
                    }
                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        githubDownloading = false;
                        githubInstallStatus = Ui.tr("Download failed");
                        invalidate();
                    });
                }
            }).start();
        }

        private void runCheckAndUpdate() {
            if (busy()) return;
            updateStatus = Ui.tr("Verifying…");
            invalidate();
            new Thread(() -> {
                try {
                    HomeMode.verify(activity);
                    HomeMode.ensureRemoteServiceNow(activity);
                    Thread.sleep(700);
                    activity.runOnUiThread(() -> {
                        updateStatus = Ui.tr("All settings reapplied");
                        invalidate();
                    });
                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        updateStatus = Ui.tr("Check failed");
                        invalidate();
                    });
                }
            }).start();
        }

        private void checkGithubUpdates() {
            if (Ui.tr("Checking…").equals(githubUpdateStatus)) return;
            githubUpdateStatus = Ui.tr("Checking…");
            invalidate();
            new Thread(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL("https://api.github.com/repos/gabrielbaumgratz/LauncherGB/releases/latest").openConnection();
                    conn.setRequestProperty("User-Agent", "GBHomeTV");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int r;
                        InputStream in = conn.getInputStream();
                        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                        JSONObject rel = new JSONObject(out.toString("UTF-8"));
                        String tag = rel.optString("tag_name", "");
                        String apkUrl = "";
                        JSONArray assets = rel.optJSONArray("assets");
                        for (int i = 0; assets != null && i < assets.length(); i++) {
                            JSONObject a = assets.optJSONObject(i);
                            if (a != null && a.optString("name", "").endsWith(".apk")) {
                                apkUrl = a.optString("browser_download_url", "");
                                break;
                            }
                        }
                        final String finalApkUrl = apkUrl;
                        activity.runOnUiThread(() -> {
                            githubApkUrl = finalApkUrl;
                            String current = versionName(activity).replace("GB Home TV ", "").trim();
                            if (!tag.isEmpty() && !tag.equals(current) && !tag.equals("v" + current)) {
                                githubUpdateStatus = Ui.trf("New: {v}", "v", tag);
                            } else {
                                githubUpdateStatus = Ui.tr("Up to date");
                            }
                            invalidate();
                        });
                    } else {
                        HttpURLConnection c2 = (HttpURLConnection) new URL("https://api.github.com/repos/gabrielbaumgratz/LauncherGB/commits/main").openConnection();
                        c2.setRequestProperty("User-Agent", "GBHomeTV");
                        c2.setConnectTimeout(8000);
                        c2.setReadTimeout(8000);
                        if (c2.getResponseCode() == 200) {
                            activity.runOnUiThread(() -> {
                                githubUpdateStatus = Ui.tr("Up to date");
                                invalidate();
                            });
                        } else {
                            activity.runOnUiThread(() -> {
                                githubUpdateStatus = Ui.tr("No updates found");
                                invalidate();
                            });
                        }
                    }
                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        githubUpdateStatus = Ui.tr("Check failed");
                        invalidate();
                    });
                }
            }).start();
        }

        private void clampFocus() {
            if (paneFocus >= rows.size()) paneFocus = Math.max(0, rows.size() - 1);
            if (!rows.isEmpty() && !rows.get(paneFocus).focusable()) paneFocus = nextFocusable(paneFocus, 1, paneFocus);
        }

        private String recsLine() {
            String error = recs.optString("error");
            int count = recs.optInt("count");
            if (!hasKey) return Ui.tr("No TMDB key: see the README (config.properties).");
            if (error.equals(Recs.NO_KNOWN_APPS)) return Ui.tr("None of the TV's streaming apps can give picks.");
            if (error.equals(Recs.NO_APPS)) return Ui.trf("TMDB has none of the TV's apps for tmdb_region={region}.", "region", Recs.REGION);
            if (!error.isEmpty()) return Ui.trf("Error: {error}.", "error", error);
            if (count > 0) return Ui.trf("On · {n} titles from the TV's apps, 3 new ones every hour.", "n", count);
            return Ui.tr("Fetching titles…");
        }

        private int nextFocusable(int from, int dir, int fallback) {
            for (int i = from + dir; i >= 0 && i < rows.size(); i += dir) if (rows.get(i).focusable()) return i;
            return fallback;
        }

        // MARK: - Bluetooth devices and teams (pages that open from a row)

        private void openDevice(BluetoothAudio.DeviceInfo d) {
            final String address = d.device.getAddress();
            openDetail(d.name, () -> {
                toggle(Ui.tr("Show on the home screen"), () -> Prefs.btShown(address), () -> Prefs.setBtShown(address, !Prefs.btShown(address)));
                value(Ui.tr("Name"), () -> {
                    String alias = Prefs.btAlias(address);
                    return alias.isEmpty() ? d.device.getName() == null ? "Bluetooth" : d.device.getName() : alias;
                }, () -> askDeviceName(d));
                header(Ui.tr("Position"));
                link(Ui.tr("Move up"), () -> "", () -> moveDevice(address, -1));
                link(Ui.tr("Move down"), () -> "", () -> moveDevice(address, 1));
                header(Ui.tr("Pairing"));
                link(Ui.tr("Forget this device"), () -> "", () -> askForget(d));
            });
        }

        /** Moves a device one place in the list the Control Center, the shortcuts and these settings share. */
        private void moveDevice(String address, int dx) {
            List<String> order = new ArrayList<>();
            for (BluetoothAudio.DeviceInfo i : BluetoothAudio.getPairedAudioDevices()) order.add(i.device.getAddress());
            int i = order.indexOf(address), j = i + dx;
            if (i < 0 || j < 0 || j >= order.size()) return;
            java.util.Collections.swap(order, i, j);
            Prefs.setBtOrder(order);
            deviceChoices = null;
            HomeData.get(getContext()).settingsChanged();
        }

        private void askDeviceName(BluetoothAudio.DeviceInfo d) {
            final String address = d.device.getAddress();
            Prompt.ask(activity, Ui.tr("Name"), Ui.tr("The name shown here and on the home screen. Leave it empty for the device's own."),
                    d.device.getName() == null ? "Bluetooth" : d.device.getName(), Prefs.btAlias(address), EditorInfo.IME_ACTION_DONE,
                    Ui.tr("Save"), text -> saveDeviceName(address, text));
        }

        private void saveDeviceName(String address, String name) {
            Prefs.setBtAlias(address, name.length() > 32 ? name.substring(0, 32) : name);
            deviceChoices = null;
            rebuild();
            invalidate();
        }

        private void askForget(BluetoothAudio.DeviceInfo d) {
            Prompt.confirm(activity, Ui.tr("Forget this device"),
                    Ui.trf("{name} will be unpaired. To use it again, pair it again.", "name", d.name), Ui.tr("Forget"), () -> forget(d));
        }

        private void forget(BluetoothAudio.DeviceInfo d) {
            if (Build.VERSION.SDK_INT >= 31 && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return;
            }
            if (!BluetoothAudio.forget(d.device)) {   // the system won't from here: its own Bluetooth screen can
                try {
                    activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    AppLog.w("bt settings: " + e.getMessage());
                }
            }
            Prefs.setBtAlias(d.device.getAddress(), "");
            handler.postDelayed(() -> {
                deviceChoices = null;
                closeDetail();
                invalidate();
            }, 600);
        }

        private void openTeam(String[] team) {
            openDetail(team[3], () -> {
                info(Ui.tr("Sport"), () -> Sports.sportName(team[0]));
                if (team[0].equals("soccer")) {
                    info(Ui.tr("Competitions"), () -> Ui.tr("League, Libertadores, Copa do Brasil"));
                } else if (Sports.individual(team[0])) {
                    info(Ui.tr("Circuit"), () -> Sports.leagueName(team[0] + "/" + team[1]));
                }
                header(Sports.individual(team[0]) ? Ui.tr("Athlete") : Ui.tr("Team"));
                if (Prefs.sportsTeams().size() > 1 && !Prefs.sportsTeams().get(0)[2].equals(team[2])) {
                    link(Ui.tr("Move to top (Priority)"), () -> "", () -> {
                        List<String[]> list = Prefs.sportsTeams();
                        for (int i = list.size() - 1; i >= 0; i--) {
                            if (list.get(i)[2].equals(team[2]) && list.get(i)[0].equals(team[0])) {
                                String[] item = list.remove(i);
                                list.add(0, item);
                                break;
                            }
                        }
                        Prefs.setSportsTeams(list);
                        Sports.get(getContext()).refresh();
                        HomeData.get(getContext()).settingsChanged();
                        HomeActivity.refreshNow();
                        closeDetail();
                    });
                }
                link(Ui.tr("Stop following"), () -> "", () -> {
                    List<String[]> teams = Prefs.sportsTeams();
                    for (int i = teams.size() - 1; i >= 0; i--) if (teams.get(i)[2].equals(team[2]) && teams.get(i)[0].equals(team[0])) teams.remove(i);
                    Prefs.setSportsTeams(teams);
                    closeDetail();
                });
            });
        }

        private void openLeagues() {
            openDetail(Ui.tr("Championships"), () -> {
                note(Ui.tr("Follow championships to see all upcoming games, races and fight cards on the home screen."));
                String lastSport = "";
                for (String[] league : Sports.LEAGUES) {
                    String sport = Sports.sportOf(league[0]);
                    if (!sport.equals(lastSport)) {
                        header(Sports.sportName(sport));
                        lastSport = sport;
                    }
                    Row r = toggle(league[1], () -> Prefs.sportsLeagues().contains(league[0]), () -> {
                        List<String> paths = Prefs.sportsLeagues();
                        if (!paths.remove(league[0])) paths.add(league[0]);
                        Prefs.setSportsLeagues(paths);
                    });
                    r.value = () -> Prefs.sportsLeagues().contains(league[0]) ? Ui.tr("Yes") : Ui.tr("No");
                }
            });
        }

        private void askTeam(String sport) {
            if (Ui.tr("Searching…").equals(teamStatus)) return;
            teamSearchSport = sport;
            String title = Sports.addLabel(sport);
            String subtitle = sport == null
                    ? Ui.tr("A team of any sport, or a player, driver or fighter by name.")
                    : Ui.tr("Search by name on ESPN.");
            String hint = Sports.searchPrompt(sport);
            Prompt.ask(activity, title, subtitle, hint, "", EditorInfo.IME_ACTION_SEARCH,
                    Ui.tr("Search"), name -> searchTeam(name, sport));
        }

        private void searchTeam(String name, final String sport) {
            if (name.trim().isEmpty()) return;
            teamStatus = Ui.tr("Searching…");
            teamSearchSport = sport;
            invalidate();
            new Thread(() -> {
                List<String[]> found = Sports.findTeam(name);
                if (sport != null && !sport.isEmpty() && !found.isEmpty()) {
                    List<String[]> matched = new ArrayList<>();
                    for (String[] t : found) if (sport.equals(t[0])) matched.add(t);
                    if (!matched.isEmpty()) found = matched;
                }
                String message = found.isEmpty() ? Ui.tr("No team or athlete found.") : "";
                final List<String[]> res = found;
                handler.post(() -> {
                    teamStatus = message;
                    if (!res.isEmpty() && screen == PAGE) {
                        teamResults.clear();
                        teamResults.addAll(res);
                        openPicker(PICK_TEAM);
                        spoken.focusMoved();
                    }
                    invalidate();
                    if (!message.isEmpty()) handler.postDelayed(() -> { teamStatus = ""; teamSearchSport = ""; invalidate(); }, 4000);
                });
            }, "searchTeam").start();
        }

        // MARK: - Layout of a page

        private StaticLayout noteLayout(String text) {
            StaticLayout l = notes.get(text);
            if (l == null) {
                int width = Math.round((PANE_R - PANE_L - 64) * k);
                l = StaticLayout.Builder.obtain(text, 0, text.length(), notePaint, width)
                        .setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
                notes.put(text, l);
            }
            return l;
        }

        /** Where each row and each card of the page goes (in design pixels, before scrolling). */
        private void layout() {
            if (notePaint == null) return;
            int n = rows.size();
            if (rowY.length < n) rowY = new float[n + 8];
            groups.clear();
            float y = PANE_TOP;
            int groupStart = -1;
            float groupTop = 0;
            for (int i = 0; i < n; i++) {
                Row r = rows.get(i);
                if (r.kind == HEADER || r.kind == NOTE) {
                    if (groupStart >= 0) {
                        groups.add(new float[] {groupTop, y});
                        groupStart = -1;
                        y += 12;
                    }
                    if (r.kind == HEADER) {
                        y += 14;
                        rowY[i] = y;
                        y += 50;
                    } else if (r.label.isEmpty()) {
                        rowY[i] = y;
                    } else {
                        rowY[i] = y;
                        y += noteLayout(r.label).getHeight() / k + 22;
                    }
                } else {
                    if (groupStart < 0) {
                        groupStart = i;
                        groupTop = y;
                    }
                    rowY[i] = y;
                    y += ROW_H;
                }
            }
            if (groupStart >= 0) {
                groups.add(new float[] {groupTop, y});
                y += 12;
            }
            contentBottom = y + 40;
        }

        private void keepVisible() {
            if (rows.isEmpty()) return;
            layout();
            int i = paneFocus;
            float top = rowY[i], bottom = top + ROW_H;
            if (i > 0 && rows.get(i - 1).kind == HEADER) top = rowY[i - 1] - 10;
            if (bottom - scrollTarget > PANE_BOTTOM - 24) scrollTarget = bottom - (PANE_BOTTOM - 24);
            if (top - scrollTarget < PANE_TOP - 4) scrollTarget = top - (PANE_TOP - 4);
            scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentBottom - PANE_BOTTOM)));
            scrollSpring.target = scrollTarget;
        }

        // MARK: - Lifecycle and frames

        void resume() {
            if (running) return;
            running = true;
            startedAt = SystemClock.uptimeMillis();
            lastNanos = 0;
            inputChoices = null;
            deviceChoices = null;
            appPkgs.clear();   // the apps' names again, in the language now
            appLabels.clear();
            themeAt = 0;
            updateTheme();
            handler.post(poll);
            Choreographer.getInstance().postFrameCallback(this);
            spoken.readAgain();
        }

        void pause() {
            running = false;
            handler.removeCallbacks(poll);
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            boolean moving = busy() || !scrollSpring.settled();
            for (Spring s : pop) moving |= !s.settled();
            if (moving) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            w = width;
            h = height;
            k = w / 1920f;
            notes.clear();
            scene.setSize(w, h);
            bigTitle = Ui.text(Ui.displaySemibold, 54 * k, 0xFFFFFFFF);
            sideLabel = Ui.text(Ui.medium, 31 * k, 0xF2FFFFFF);
            sideSmall = Ui.text(Ui.regular, 22 * k, 0x8CFFFFFF);
            rowLabel = Ui.text(Ui.medium, 30 * k, 0xF2FFFFFF);
            rowValue = Ui.text(Ui.regular, 28 * k, 0x8CFFFFFF);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            rowSub = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            section = Ui.text(Ui.semibold, 21 * k, 0x8CFFFFFF);
            section.setLetterSpacing(0.06f);
            hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            notePaint = new TextPaint(Ui.text(Ui.regular, 23 * k, 0x99FFFFFF));
            spinner = new Paint(Paint.ANTI_ALIAS_FLAG);
            spinner.setStyle(Paint.Style.STROKE);
            spinner.setStrokeCap(Paint.Cap.ROUND);
            spinner.setStrokeWidth(3.5f * k);
            white = new Paint(Paint.ANTI_ALIAS_FLAG);
            white.setColor(WHITE);
            dropShadow.setMaskFilter(new BlurMaskFilter(22 * k, BlurMaskFilter.Blur.NORMAL));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(1, k));
            themeAt = 0;
            updateTheme();
            keepVisible();
        }

        private boolean busy() {
            return !busyText().isEmpty() || (pendingAt != 0 && SystemClock.uptimeMillis() - pendingAt < 10_000);
        }

        private String busyText() {
            long at = state.optLong("busyAt"), now = SystemClock.elapsedRealtime();
            boolean stale = now < at || now - at > 10 * 60_000L;
            return stale ? "" : state.optString("busy");
        }

        private boolean freeActive() {
            return HomeMode.FREE.equals(state.optString("mode")) && !state.optBoolean("homeReverted");
        }

        /** The rows of the open list (not of a page). */
        private int listRows() {
            switch (screen) {
                case HOME_CHOICE: return 2;
                case PICKER: return Math.min(PICK_VISIBLE, pickLabels.size());
                case SHELF_EDIT: return Math.min(PICK_VISIBLE, shelfRows.size());
                default: return 0;
            }
        }

        // MARK: - The home screen's look: glass, white focus, black or white ink

        /** Day or night as the home screen has it (the theme setting, or the sun); the glass reads a flat gray of its own. */
        private void updateTheme() {
            long now = SystemClock.uptimeMillis();
            if (themeAt != 0 && now - themeAt < 1500) return;
            themeAt = now;
            boolean night = HomeData.get(getContext()).isNight(System.currentTimeMillis());
            if (night == dark && neutralGlassPx[0] != 0) return;
            dark = night;
            int gray = dark ? 0xFF141416 : 0xFFEBEBF0;
            Arrays.fill(neutralFrostPx, gray);
            Arrays.fill(neutralGlassPx, gray);
            scene.setFrost(neutralFrostPx);
            scene.setGlassImage(neutralGlassPx);
            invalidate();
        }

        private int ink() {
            return dark ? 0xF2FFFFFF : L_INK;
        }

        private int dimInk() {
            return dark ? 0xADFFFFFF : L_DIM;
        }

        /** Glass for a card or a button: white by day (with a soft shadow), smoked at night. */
        private void glass(Canvas c, RectF r, float radius, float lightTint, int shadowColor, float drop) {
            if (!dark) {
                tmp.set(r);
                tmp.offset(0, drop);
                glassShadow.setColor(shadowColor);
                shadows.draw(c, tmp, radius, SHADOW_BLUR * k, glassShadow);
                scene.glass(c, r, radius, 0xFFFFFFFF, lightTint, 0, 0, 0.02f, 1, 1);
            } else {
                scene.glass(c, r, radius, SMOKE, 0.4f, 0, 0, 0.02f, 1, 1);
            }
        }

        // MARK: - Drawing

        @Override protected void onDraw(Canvas c) {
            if (w == 0) return;
            updateTheme();
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            boolean onPage = screen == PAGE;
            for (int i = 0; i < 80; i++) {
                pop[i].target = (onPage ? zone == PANE && i == paneFocus : i == focus) ? 1 : 0;
                pop[i].step(dt);
            }
            for (int i = 0; i < PAGES; i++) {
                pop[80 + i].target = onPage && zone == SIDE && i == page ? 1 : 0;
                pop[80 + i].step(dt);
            }
            scrollSpring.step(dt);
            float seconds = (SystemClock.uptimeMillis() - startedAt) / 1000f;

            c.drawColor(dark ? 0xFF141416 : 0xFFEBEBF0);
            drawSidebar(c);
            String title = screen == LEARN ? Ui.tr("Add a button") : onPage ? (detail != null ? detailTitle : pageTitles()[page])
                    : screen == HOME_CHOICE ? Ui.tr("Launcher")
                    : screen == PICKER ? pickerTitle()
                    : editingTop ? Ui.tr("Top Buttons") : Ui.tr("Shelf");
            bigTitle.setColor(ink());
            c.drawText(TextUtils.ellipsize(title, new TextPaint(bigTitle), (PANE_R - PANE_L) * k, TextUtils.TruncateAt.END).toString(),
                    PANE_L * k + 4 * k, 150 * k, bigTitle);
            if (onPage) drawPage(c, seconds);
            else if (screen == HOME_CHOICE) drawChoice(c);
            else if (screen == PICKER) drawPicker(c);
            else if (screen == LEARN) drawLearn(c, seconds);
            else drawShelfEditor(c);
        }

        private String pickerTitle() {
            return pickFor == PICK_CITY ? Ui.tr("City") : pickFor == PICK_LANGUAGE ? Ui.tr("Language")
                    : pickFor == PICK_TEAM ? Ui.tr("Add a team") : pickFor == PICK_CUSTOM ? Ui.tr("What should it open?") : pickFor == PICK_LEVEL ? Ui.tr("Screensaver brightness") : keyLabels()[pickFor - 1];
        }

        private void drawSidebar(Canvas c) {
            // The profile card: an initial and the name, like the Apple ID card on the iPad.
            String name = Prefs.userName();
            tmp.set(SIDE_L * k, 64 * k, SIDE_R * k, 184 * k);
            glass(c, new RectF(tmp), 30 * k, 0.72f, 0x1A000000, 10 * k);
            fill.setColor(dark ? WHITE : L_INK);
            c.drawCircle((SIDE_L + 64) * k, 124 * k, 36 * k, fill);
            Paint initial = Ui.text(Ui.displaySemibold, 36 * k, dark ? INK : 0xFFFFFFFF);
            initial.setTextAlign(Paint.Align.CENTER);
            c.drawText(name.isEmpty() ? "GB" : name.substring(0, 1).toUpperCase(Locale.ROOT), (SIDE_L + 64) * k,
                    124 * k + Ui.halfCap(initial), initial);
            Paint who = Ui.text(Ui.semibold, 31 * k, ink());
            c.drawText(TextUtils.ellipsize(name.isEmpty() ? "GB Home TV" : name, new TextPaint(who), 380 * k, TextUtils.TruncateAt.END).toString(),
                    (SIDE_L + 120) * k, 118 * k, who);
            sideSmall.setColor(dimInk());
            c.drawText(name.isEmpty() ? versionName(getContext()).replace("GB Home TV ", "v") : "GB Home TV", (SIDE_L + 120) * k, 152 * k, sideSmall);

            String[] titles = pageTitles();
            for (int i = 0; i < PAGES; i++) {
                float y = (SIDE_TOP + i * (SIDE_ITEM_H + SIDE_GAP)) * k;
                rowRect.set(SIDE_L * k, y, SIDE_R * k, y + SIDE_ITEM_H * k);
                float f = Ui.clamp(pop[80 + i].value);
                if (i == page && f < 0.99f) glass(c, new RectF(rowRect), 22 * k, 0.6f, 0x14000000, 4 * k);
                if (f > 0.01f) pill(c, rowRect, f, 22);
                float cy = rowRect.centerY(), ix = rowRect.left + 24 * k + 18 * k;
                int color = blend(ink(), INK, f);
                Symbols.draw(getContext(), c, ICONS[i], ix, cy, 34 * k, color);
                sideLabel.setColor(color);
                c.drawText(titles[i], ix + 18 * k + 28 * k, cy + Ui.halfCap(sideLabel), sideLabel);
            }
        }

        /** The focus: a white pill, a little larger, with a shadow (the home screen's focus). Leaves the grown rect in `r`. */
        private void pill(Canvas c, RectF r, float f, float radius) {
            float s = 1 + 0.02f * f;
            float cx = r.centerX(), cy = r.centerY();
            tmp.set(cx - r.width() * s / 2, cy - r.height() * s / 2, cx + r.width() * s / 2, cy + r.height() * s / 2);
            float rad = radius * k * s;
            tmp.offset(0, 10 * k * f);
            dropShadow.setColor(Ui.alpha(0xFF000000, 0.4f * f));
            c.drawRoundRect(tmp, rad, rad, dropShadow);
            tmp.offset(0, -10 * k * f);
            white.setAlpha(Math.round(255 * f));
            c.drawRoundRect(tmp, rad, rad, white);
            r.set(tmp);
        }

        private void drawPage(Canvas c, float seconds) {
            layout();
            float scroll = scrollSpring.value;
            c.save();
            c.clipRect(0, (PANE_TOP - 18) * k, w, h);
            // The cards first, then the rows over them.
            for (float[] g : groups) {
                RectF card = new RectF(PANE_L * k, (g[0] - scroll) * k, PANE_R * k, (g[1] - scroll) * k);
                if (card.bottom < 0 || card.top > h) continue;
                glass(c, card, 30 * k, 0.72f, 0x1A000000, 10 * k);
            }
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                float y = (rowY[i] - scroll) * k;
                if (y > h || y < (PANE_TOP - 160) * k) continue;
                if (r.kind == HEADER) {
                    section.setColor(dimInk());
                    c.drawText(r.label.toUpperCase(Locale.ROOT), PANE_L * k + 30 * k, y + 34 * k, section);
                } else if (r.kind == NOTE) {
                    if (r.label.isEmpty()) continue;
                    notePaint.setColor(dimInk());
                    StaticLayout l = noteLayout(r.label);
                    c.save();
                    c.translate(PANE_L * k + 30 * k, y);
                    l.draw(c);
                    c.restore();
                } else {
                    drawRow(c, i, r, y, seconds);
                }
            }
            c.restore();
            // A scroll hint when more is below.
            if (contentBottom - scroll > PANE_BOTTOM + 10) {
                Symbols.draw(getContext(), c, "chevron_down", (PANE_L + PANE_R) / 2 * k, (PANE_BOTTOM + 22) * k, 26 * k, dimInk());
            }
        }

        private void drawRow(Canvas c, int i, Row r, float y, float seconds) {
            rowRect.set((PANE_L + 10) * k, y + 4 * k, (PANE_R - 10) * k, y + (ROW_H - 4) * k);
            float f = Ui.clamp(pop[i].value);
            // The line between rows of a card, unless the focus is on either side of it.
            boolean lastOfCard = i + 1 >= rows.size() || !rows.get(i + 1).focusable();
            if (!lastOfCard && f < 0.5f && Ui.clamp(pop[i + 1].value) < 0.5f) {
                fill.setColor(dark ? 0x24FFFFFF : 0x1A000000);
                c.drawRect((PANE_L + 32) * k, y + ROW_H * k - Math.max(1, k), (PANE_R - 30) * k, y + ROW_H * k, fill);
            }
            if (f > 0.01f) pill(c, rowRect, f, 18);
            rowLabel.setColor(blend(ink(), INK, f));
            rowValue.setColor(blend(dimInk(), 0x8C1C1C1E, f));
            float base = rowRect.centerY() + Ui.halfCap(rowLabel);
            float right = rowRect.right - 24 * k;
            if (r.kind == TOGGLE) {
                if (r.value != null) {
                    float textRight = right - 84 * k - 16 * k;
                    c.drawText(r.value.get(), textRight, base, rowValue);
                }
                drawSwitch(c, right, rowRect.centerY(), r.on.getAsBoolean(), f);
            } else {
                if (r.kind == LINK) {
                    float cw = Symbols.width(getContext(), "chevron_right", 22 * k);
                    Symbols.draw(getContext(), c, "chevron_right", right - cw / 2, rowRect.centerY(), 22 * k, blend(dimInk(), 0x991C1C1E, f));
                    right -= cw + 12 * k;
                }
                if (r.busy != null && r.busy.getAsBoolean()) {
                    String text = busyText().isEmpty() ? Ui.tr("Starting…") : busyText();
                    String shown = TextUtils.ellipsize(text, new TextPaint(rowValue), 470 * k, TextUtils.TruncateAt.END).toString();
                    c.drawText(shown, right, base, rowValue);
                    spinner.setColor(blend(ink(), INK, f));
                    Ui.spinner(c, right - rowValue.measureText(shown) - 26 * k, rowRect.centerY(), 12 * k, seconds * 1.3f, spinner);
                } else if (r.value != null) {
                    float max = Math.max(200 * k, rowRect.width() - 90 * k - rowLabel.measureText(r.label) - (r.kind == LINK ? 34 * k : 0));
                    String v = TextUtils.ellipsize(r.value.get(), new TextPaint(rowValue), max, TextUtils.TruncateAt.END).toString();
                    c.drawText(v, right, base, rowValue);
                }
            }
            float labelMax = rowRect.width() * 0.62f;
            String label = r.kind == INFO || r.kind == LINK ? TextUtils.ellipsize(r.label, new TextPaint(rowLabel), labelMax, TextUtils.TruncateAt.END).toString() : r.label;
            c.drawText(label, rowRect.left + 22 * k, base, rowLabel);
        }

        /** A switch in the home screen's two colors: ink on a track, the opposite on the knob. */
        private void drawSwitch(Canvas c, float right, float cy, boolean on, float f) {
            float tw = 84 * k, th = 50 * k;
            tmp.set(right - tw, cy - th / 2, right, cy + th / 2);
            boolean focused = f > 0.5f;
            int track = on ? (focused || !dark ? INK : WHITE) : (focused || !dark ? 0x33000000 : 0x47FFFFFF);
            fill.setColor(track);
            c.drawRoundRect(tmp, th / 2, th / 2, fill);
            fill.setColor(on && track == WHITE ? INK : 0xFFFFFFFF);
            c.drawCircle(on ? tmp.right - th / 2 : tmp.left + th / 2, cy, th / 2 - 4 * k, fill);
        }

        /** The row of a list that opens in the pane: the focus. Returns how focused it is. */
        private float subRow(Canvas c, int i, float height) {
            float y = (SUB_TOP + i * height) * k;
            rowRect.set((PANE_L + 10) * k, y + 4 * k, (PANE_R - 10) * k, y + (height - 4) * k);
            float f = Ui.clamp(pop[i].value);
            if (f > 0.01f) pill(c, rowRect, f, 18);
            return f;
        }

        private void subCard(Canvas c, int rowCount, float height) {
            RectF card = new RectF(PANE_L * k, SUB_TOP * k, PANE_R * k, (SUB_TOP + rowCount * height) * k);
            glass(c, card, 30 * k, 0.72f, 0x1A000000, 10 * k);
        }

        /** "Press a button on your remote": a card, the remote's symbol and a spinner while the TV listens. */
        private void drawLearn(Canvas c, float seconds) {
            RectF card = new RectF(PANE_L * k, SUB_TOP * k, PANE_R * k, (SUB_TOP + 520) * k);
            glass(c, card, 30 * k, 0.72f, 0x1A000000, 10 * k);
            float cx = (PANE_L + PANE_R) / 2 * k;
            Symbols.draw(getContext(), c, "remote", cx, (SUB_TOP + 150) * k, 110 * k, ink());
            Paint big = Ui.text(Ui.displaySemibold, 40 * k, ink());
            big.setTextAlign(Paint.Align.CENTER);
            c.drawText(Ui.tr("Press a button on your remote"), cx, (SUB_TOP + 290) * k, big);
            hint.setColor(dimInk());
            hint.setTextAlign(Paint.Align.CENTER);
            c.drawText(Ui.tr("Arrows, OK and Back can't be used."), cx, (SUB_TOP + 340) * k, hint);
            hint.setTextAlign(Paint.Align.LEFT);
            spinner.setColor(dimInk());
            Ui.spinner(c, cx, (SUB_TOP + 420) * k, 16 * k, seconds * 1.3f, spinner);
            invalidate();
        }

        /** Home screen choice: GB Home TV or the original, with a check on the one in use. */
        private void drawChoice(Canvas c) {
            subCard(c, 2, CHOICE_H);
            String[][] options = {{"GB Home TV", Ui.tr("No ads, with the new screensaver")},
                    {Ui.tr("Original"), Ui.tr("The TV's factory home screen")}};
            int current = freeActive() ? 0 : 1;
            for (int i = 0; i < 2; i++) {
                float f = subRow(c, i, CHOICE_H);
                rowLabel.setColor(blend(ink(), INK, f));
                rowSub.setColor(blend(dimInk(), 0x991C1C1E, f));
                c.drawText(options[i][0], rowRect.left + 22 * k, rowRect.top + 48 * k, rowLabel);
                c.drawText(options[i][1], rowRect.left + 22 * k, rowRect.top + 84 * k, rowSub);
                if (i == current) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - 24 * k - cw / 2, rowRect.centerY(), 26 * k, blend(ink(), INK, f));
                }
            }
            hint.setColor(dimInk());
            c.drawText(Ui.tr("Switching takes a few seconds; the screen blinks while it measures."), PANE_L * k + 30 * k,
                    (SUB_TOP + 2 * CHOICE_H + 44) * k, hint);
        }

        /** A long list (the apps, the languages): nine rows at a time, the one in use checked. */
        private void drawPicker(Canvas c) {
            int rowsShown = listRows();
            subCard(c, rowsShown, SUB_H);
            for (int i = 0; i < rowsShown; i++) {
                int at = pickScroll + i;
                float f = subRow(c, i, SUB_H);
                rowLabel.setColor(blend(ink(), INK, f));
                c.drawText(TextUtils.ellipsize(pickLabels.get(at), new TextPaint(rowLabel), 960 * k, TextUtils.TruncateAt.END).toString(),
                        rowRect.left + 22 * k, rowRect.centerY() + Ui.halfCap(rowLabel), rowLabel);
                if (at == pickCurrent) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - 24 * k - cw / 2, rowRect.centerY(), 26 * k, blend(ink(), INK, f));
                }
            }
            hint.setColor(dimInk());
            c.drawText((pickScroll + focus + 1) + " / " + pickLabels.size() + "  ·  " + Ui.tr("OK picks, Back cancels."),
                    PANE_L * k + 30 * k, (SUB_TOP + rowsShown * SUB_H + 44) * k, hint);
        }

        private void drawShelfEditor(Canvas c) {
            int rowsShown = listRows();
            subCard(c, rowsShown, SUB_H);
            for (int i = 0; i < rowsShown; i++) {
                String[] app = shelfRows.get(pickScroll + i);
                int place = shelfOrder.indexOf(app[0]);
                float f = subRow(c, i, SUB_H);
                rowLabel.setColor(blend(place >= 0 ? ink() : Ui.alpha(ink(), 0.5f), place >= 0 ? INK : 0x801C1C1E, f));
                rowValue.setColor(blend(dimInk(), 0xB31C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(TextUtils.ellipsize(app[1], new TextPaint(rowLabel), 760 * k, TextUtils.TruncateAt.END).toString(), rowRect.left + 22 * k, base, rowLabel);
                c.drawText(place >= 0 ? "#" + (place + 1) : Ui.tr("shelf|Off"), rowRect.right - 24 * k, base, rowValue);
            }
            hint.setColor(dimInk());
            c.drawText(editingTop ? Ui.tr("OK adds or removes a button. ← → change the order.")
                    : Ui.trf("OK adds or removes (up to {n}). ← → change the order.", "n", HomeApps.MAX_SHELF),
                    PANE_L * k + 30 * k, (SUB_TOP + rowsShown * SUB_H + 44) * k, hint);
        }

        private static int blend(int a, int b, float f) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }

        private static String metric(JSONObject m, String key) {
            if (m == null || !m.has(key)) return "—";
            long v = m.optLong(key);
            if (key.equals("homeMs")) return Ui.trf("{n} ms", "n", v);
            if (key.equals("memAvailableMB") && v >= 1024) return Ui.trf("{n} GB", "n", String.format(Ui.locale(), "%.2f", v / 1024.0));
            return Ui.trf("{n} MB", "n", v);
        }

        private static String ago(long at) {
            long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
            if (minutes < 1) return Ui.tr("now");
            if (minutes < 60) return Ui.trf("{n} min ago", "n", minutes);
            if (minutes < 48 * 60) return Ui.trf("{n} h ago", "n", minutes / 60);
            return Ui.plural("plural|one:{n} day ago|other:{n} days ago", minutes / 1440);
        }

        private static String versionName(Context c) {
            try {
                return "GB Home TV " + c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                return "GB Home TV";
            }
        }

        private static String onOff(boolean on) {
            return on ? Ui.tr("On") : Ui.tr("Off");
        }

        // MARK: - Keys

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down) move(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) move(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) {
                        if (screen == SHELF_EDIT) moveShelfApp(-1);
                        else if (screen == PAGE && zone == PANE) back();
                        invalidate();
                        spoken.focusMoved();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) {
                        if (screen == SHELF_EDIT) moveShelfApp(1);
                        else if (screen == PAGE && zone == SIDE) enterPane();
                        else if (screen == PAGE) activate();
                        invalidate();
                        spoken.focusMoved();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) {
                        if (e.getRepeatCount() == 0) okDown = true;
                    } else {
                        if (okDown && !e.isCanceled() && spoken.keyClick()) activate();
                        okDown = false;
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (screen == PAGE && zone == SIDE) return false;   // leaves the settings
                    if (down) {
                        if (e.getRepeatCount() == 0) backDown = true;
                    } else {
                        if (backDown && !e.isCanceled()) back();
                        backDown = false;
                    }
                    invalidate();
                    spoken.focusMoved();
                    return true;
                default:
                    if (screen == LEARN && down && e.getRepeatCount() == 0) {
                        gotButton(e.getKeyCode(), e.getScanCode());   // a key the TV let through to us
                        return true;
                    }
                    return false;
            }
        }

        private void move(int dir) {
            if (screen == PAGE) {
                if (zone == SIDE) {
                    int next = Math.max(0, Math.min(PAGES - 1, page + dir));
                    if (next != page) selectPage(next);
                } else {
                    int next = nextFocusable(paneFocus, dir, paneFocus);
                    if (next != paneFocus) {
                        paneFocus = next;
                        keepVisible();
                    }
                }
            } else if (dir < 0) {
                if (focus > 0) focus--;
                else if (pickScroll > 0 && screen != HOME_CHOICE) pickScroll--;
            } else {
                if (focus < listRows() - 1) focus++;
                else if (screen != HOME_CHOICE && pickScroll + listRows() < (screen == SHELF_EDIT ? shelfRows.size() : pickLabels.size())) pickScroll++;
            }
            invalidate();
            spoken.focusMoved();
        }

        private void enterPane() {
            if (zone == PANE || rows.isEmpty()) return;
            zone = PANE;
            if (!rows.get(paneFocus).focusable()) paneFocus = nextFocusable(-1, 1, 0);
            if (page == P_REMOTE) HomeMode.ensureRemoteService(getContext(), 0);   // the buttons work from inside other apps through this service
            keepVisible();
        }

        /** Back from a list to its page, from a page that opened from a row to the row, or from the page to the sidebar. */
        private void back() {
            if (screen == LEARN) Prefs.stopLearning();
            if (screen != PAGE) {
                screen = PAGE;
                zone = PANE;
                rebuild();
                keepVisible();
            } else if (detail != null) {
                closeDetail();
            } else {
                zone = SIDE;
            }
        }

        private void activate() {
            if (screen == HOME_CHOICE) {
                boolean wantFree = focus == 0;
                boolean already = wantFree ? freeActive() : HomeMode.ORIGINAL.equals(state.optString("mode", HomeMode.ORIGINAL));
                if (!already && !busy()) {
                    HomeMode.request(getContext(), wantFree ? HomeMode.FREE : HomeMode.ORIGINAL);
                    pendingAt = SystemClock.uptimeMillis();
                }
                back();
            } else if (screen == PICKER && pickFor == PICK_CITY) {
                String[] city = cities.get(pickScroll + focus);
                Prefs.setCity(city[1], city[2], city[3]);
                HomeData.get(getContext()).settingsChanged();
                back();
            } else if (screen == PICKER && pickFor == PICK_LEVEL) {
                Prefs.setDreamLevel(pickScroll + focus);
                back();
            } else if (screen == PICKER && pickFor == PICK_CUSTOM) {
                Prefs.setCustomKey(customCode, customScan, appPkgs.get(pickScroll + focus));
                back();
            } else if (screen == LEARN) {
                return;
            } else if (screen == PICKER && pickFor == PICK_TEAM) {
                String[] team = teamResults.get(pickScroll + focus);
                List<String[]> teams = Prefs.sportsTeams();
                boolean known = false;
                for (String[] t : teams) known |= t[0].equals(team[0]) && t[2].equals(team[2]);
                if (!known) teams.add(team);
                Prefs.setSportsTeams(teams);
                Prefs.setSportsOn(true);
                back();
            } else if (screen == PICKER && pickFor == PICK_LANGUAGE) {
                Prefs.setLanguage(languageChoices()[pickScroll + focus]);
                back();
            } else if (screen == PICKER) {
                Prefs.setKeyApp(Prefs.KEYS[pickFor - 1], appPkgs.get(pickScroll + focus));
                back();
            } else if (screen == SHELF_EDIT) {
                toggleShelfApp();
            } else if (zone == SIDE) {
                enterPane();
            } else if (paneFocus < rows.size() && rows.get(paneFocus).act != null) {
                rows.get(paneFocus).act.run();
                if (screen == PAGE) rebuild();
            }
            for (Spring s : pop) s.snap(0);
            invalidate();
            spoken.focusMoved();
        }

        // MARK: - Name, city, language

        private void askName() {
            Prompt.ask(activity, Ui.tr("Your name"), Ui.tr("The home screen greets you with it. Leave it empty for no name."),
                    Ui.tr("Type your name"), Prefs.userName(), EditorInfo.IME_ACTION_DONE, Ui.tr("Save"), this::saveName);
        }

        private void saveName(String name) {
            Prefs.setUserName(name.length() > 24 ? name.substring(0, 24) : name);
            HomeData.get(getContext()).settingsChanged();
            rebuild();
            invalidate();
        }

        private void askCity() {
            if (Ui.tr("Searching…").equals(cityStatus)) return;
            Prompt.ask(activity, Ui.tr("City"), "", Ui.tr("Type the city's name"), "", EditorInfo.IME_ACTION_SEARCH,
                    Ui.tr("Search"), this::searchCity);
        }

        private void searchCity(String name) {
            if (name.trim().isEmpty()) return;
            cityStatus = Ui.tr("Searching…");
            invalidate();
            new Thread(() -> {
                List<String[]> found;
                String error;
                try {
                    found = HomeData.findCity(name);
                    error = found.isEmpty() ? Ui.tr("No city with that name.") : "";
                } catch (Exception e) {
                    found = new ArrayList<>();
                    error = Ui.trf("Couldn't search now: {error}", "error", e.getMessage());
                    AppLog.w("city search: " + e.getMessage());
                }
                List<String[]> result = found;
                String message = error;
                handler.post(() -> {
                    cityStatus = message;
                    if (!result.isEmpty() && screen == PAGE) {
                        cities.clear();
                        cities.addAll(result);
                        openPicker(PICK_CITY);
                        spoken.focusMoved();
                    }
                    invalidate();
                    if (!message.isEmpty()) handler.postDelayed(() -> { cityStatus = ""; invalidate(); }, 4000);
                });
            }).start();
        }

        /** "auto", then English and every language with a catalogue. */
        private static String[] languageChoices() {
            String[] all = Ui.languages();
            String[] v = new String[all.length + 1];
            v[0] = "auto";
            System.arraycopy(all, 0, v, 1, all.length);
            return v;
        }

        // MARK: - Remote buttons

        /** 0 full (everything on), 1 performance (the four that cost the most off), 2 custom (anything else). */
        private static int perfMode() {
            boolean[] on = {Prefs.recommendations(), Prefs.weather(), Prefs.sportsOn(), Prefs.dreamMotion()};
            int count = 0;
            for (boolean b : on) if (b) count++;
            return count == 4 ? 0 : count == 0 ? 1 : 2;
        }

        private void setPerfMode(int mode) {
            boolean full = mode == 0;
            Prefs.setRecommendations(full);
            Prefs.setWeather(full);
            Prefs.setSportsOn(full);
            Prefs.setDreamMotion(full);
            HomeData.get(getContext()).settingsChanged();
            HomeActivity.refreshNow();
        }

        /** What a taught button is called: the name the person gave it, or its key. */
        private static String customLabel(String[] k) {
            String name = !k[3].isEmpty() ? k[3].trim() : keyName(Integer.parseInt(k[0]), Integer.parseInt(k[1]));
            String prefix = Ui.trf("Button {name}", "name", "").trim();
            if (!prefix.isEmpty() && name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return name;
            }
            return Ui.trf("Button {name}", "name", name);
        }

        private void askButtonName(final int code, final int scan, String now) {
            Prompt.ask(activity, Ui.tr("Name"), Ui.tr("Which button is it? Leave it empty for its number."),
                    keyName(code, scan), now, EditorInfo.IME_ACTION_DONE, Ui.tr("Save"), text -> {
                        Prefs.setCustomKeyName(code, scan, text);
                        for (String[] k : Prefs.customKeys()) if (k[0].equals(String.valueOf(code)) && k[1].equals(String.valueOf(scan))) detailTitle = customLabel(k);
                        rebuild();
                        invalidate();
                    });
        }

        private void askRestoreButtons() {
            Prompt.confirm(activity, Ui.tr("Restore buttons to default"),
                    Ui.tr("Every button opens what it opened at first, and the buttons you added are removed."), Ui.tr("Restore"), () -> {
                        Prefs.restoreButtons();
                        rebuild();
                        invalidate();
                    });
        }

        private static String[] levelNames() {
            return new String[] {Ui.tr("Brightest"), Ui.tr("Brighter"), Ui.tr("Standard"), Ui.tr("Dim"), Ui.tr("Dark"), Ui.tr("Darkest and matte")};
        }

        private static volatile long cachedMemoryMb = 0;
        private static volatile long lastMemoryCheck = 0;
        private static volatile boolean checkingMemory = false;

        /** This process's memory (the home screen, the settings and the screensaver live in it), in MB. */
        private static long memoryMb() {
            long now = SystemClock.uptimeMillis();
            if ((now - lastMemoryCheck > 3000 || cachedMemoryMb == 0) && !checkingMemory) {
                lastMemoryCheck = now;
                checkingMemory = true;
                new Thread(() -> {
                    try {
                        android.os.Debug.MemoryInfo info = new android.os.Debug.MemoryInfo();
                        android.os.Debug.getMemoryInfo(info);
                        cachedMemoryMb = info.getTotalPss() / 1024;
                    } catch (Throwable ignored) {
                    } finally {
                        checkingMemory = false;
                    }
                }, "memoryMb").start();
            }
            if (cachedMemoryMb > 0) return cachedMemoryMb;
            return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        }

        /** "TCL 32S5K": who made the TV and which model it is, as the TV says. */
        private static String tvName() {
            String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
            String model = Build.MODEL == null ? "" : Build.MODEL;
            if (!maker.isEmpty()) maker = maker.substring(0, 1).toUpperCase(Locale.ROOT) + maker.substring(1);
            return model.toLowerCase(Locale.ROOT).startsWith(maker.toLowerCase(Locale.ROOT)) ? model : (maker + " " + model).trim();
        }

        /** A key's name for the list: "Info", or its number when Android has no name for it. */
        private static String keyName(int code, int scan) {
            String name = KeyEvent.keyCodeToString(code);
            name = name.startsWith("KEYCODE_") ? name.substring(8).replace('_', ' ').toLowerCase(Locale.ROOT) : String.valueOf(code);
            if (!name.isEmpty()) name = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
            return scan > 0 ? name + " · " + scan : name;
        }

        /** A taught button: what it opens, or take it out. */
        private void openCustom(String[] key) {
            final int code = Integer.parseInt(key[0]), scan = Integer.parseInt(key[1]);
            openDetail(customLabel(key), () -> {
                value(Ui.tr("Name"), () -> {
                    for (String[] k : Prefs.customKeys()) if (k[0].equals(key[0]) && k[1].equals(key[1])) return customLabel(k);
                    return "";
                }, () -> {
                    String now = "";
                    for (String[] k : Prefs.customKeys()) if (k[0].equals(key[0]) && k[1].equals(key[1])) now = k[3];
                    askButtonName(code, scan, now);
                });
                link(Ui.tr("Opens"), () -> {
                    String pkg = "";
                    for (String[] k : Prefs.customKeys()) if (k[0].equals(key[0]) && k[1].equals(key[1])) pkg = k[2];
                    int at = appPkgs.indexOf(pkg);
                    return at >= 0 ? appLabels.get(at) : pkg.isEmpty() ? Ui.tr("Nothing") : Ui.tr("Not installed");
                }, () -> {
                    customCode = code;
                    customScan = scan;
                    openPicker(PICK_CUSTOM);
                });
                header(Ui.tr("Button"));
                link(Ui.tr("Remove this button"), () -> "", () -> {
                    Prefs.setCustomKey(code, scan, null);
                    closeDetail();
                });
            });
        }

        /** "Add a button": the TV listens for the next key, and the page says so. */
        private void startLearn() {
            Prefs.startLearning();
            HomeMode.ensureRemoteService(getContext(), 0);
            screen = LEARN;
            handler.removeCallbacks(learnPoll);
            handler.postDelayed(learnPoll, 400);
        }

        /** The key was pressed: now, what should it open? */
        private void gotButton(int code, int scan) {
            Prefs.stopLearning();
            customCode = code;
            customScan = scan;
            if (appPkgs.isEmpty()) loadApps();
            openPicker(PICK_CUSTOM);
            spoken.focusMoved();
            invalidate();
        }

        private String[] keyLabels() {
            if (RemoteKeys.STEM_NETFLIX) return new String[] {Ui.tr("Netflix button")};
            if (RemoteKeys.KTC) return new String[] {Ui.tr("Tabii button")};
            if (RemoteKeys.SONY) return new String[] {Ui.tr("BRAVIA CORE button"),
                    Ui.tr("Netflix button"), Ui.tr("Disney+ button"),
                    Ui.tr("Prime Video button"), Ui.tr("YouTube button"),
                    Ui.tr("Crunchyroll button"), Ui.tr("Google Play button")};
            return new String[] {Ui.tr("Netflix button"), Ui.tr("YouTube button"),
                    Ui.tr("Prime Video button"), Ui.tr("TCL Channel button")};
        }

        /** Installed TV apps, by name, after "Nothing": what a remote button can open. */
        private void loadApps() {
            List<String[]> found = new ArrayList<>();
            for (String[] app : HomeApps.launchable(getContext())) {
                if (!app[0].equals(getContext().getPackageName())) found.add(app);
            }
            Collections.sort(found, (a, b) -> a[1].toLowerCase(Locale.ROOT).compareTo(b[1].toLowerCase(Locale.ROOT)));
            appPkgs.clear();
            appLabels.clear();
            appPkgs.add("");
            // On Sony, "nothing" leaves the key to the TV, which opens its usual app.
            appLabels.add(RemoteKeys.SONY ? Ui.tr("Sony default") : Ui.tr("Nothing"));
            appPkgs.add(RemoteKeys.ACTION_SETTINGS);
            appLabels.add(Ui.tr("TV settings"));
            appPkgs.add(RemoteKeys.ACTION_CONTROL);
            appLabels.add(Ui.tr("Control Center"));
            for (String[] f : found) {
                appPkgs.add(f[0]);
                appLabels.add(f[1]);
            }
        }

        /** Inputs the person can switch on or off for the home screen. */
        private List<HomeApps.Item> inputChoices() {
            if (inputChoices == null) inputChoices = HomeApps.inputChoices(getContext());
            return inputChoices;
        }

        private String liveTvSummary() {
            String act = Prefs.liveTvAction();
            if (act == null || act.isEmpty() || act.equals("tuner")) return Ui.tr("TV Tuner");
            if (act.startsWith("input:")) {
                String inputId = act.substring("input:".length());
                for (HomeApps.Item it : HomeApps.inputs(getContext(), true)) {
                    if (it.inputId != null && it.inputId.equals(inputId)) return it.label;
                }
                return inputId;
            }
            if (act.startsWith("app:")) {
                String pkg = act.substring("app:".length());
                if (appPkgs.isEmpty()) loadApps();
                int idx = appPkgs.indexOf(pkg);
                return idx >= 0 ? appLabels.get(idx) : pkg;
            }
            if (appPkgs.isEmpty()) loadApps();
            int idx = appPkgs.indexOf(act);
            return idx >= 0 ? appLabels.get(idx) : act;
        }

        private void openLiveTvPicker() {
            if (appPkgs.isEmpty()) loadApps();
            openDetail(Ui.tr("Live TV shortcut"), () -> {
                note(Ui.tr("Choose whether Live TV opens the TV antenna, an HDMI input, or an installed app."));
                header(Ui.tr("Source"));
                link(Ui.tr("TV Tuner"), () -> Prefs.liveTvAction().equals("tuner") ? Ui.tr("Selected") : "", () -> {
                    Prefs.setLiveTvAction("tuner");
                    HomeData.get(getContext()).settingsChanged();
                    HomeActivity.refreshNow();
                    closeDetail();
                });
                List<HomeApps.Item> inputs = HomeApps.inputs(getContext(), true);
                if (!inputs.isEmpty()) {
                    header(Ui.tr("HDMI Inputs"));
                    for (HomeApps.Item in : inputs) {
                        String key = "input:" + in.inputId;
                        link(in.label, () -> Prefs.liveTvAction().equals(key) ? Ui.tr("Selected") : "", () -> {
                            Prefs.setLiveTvAction(key);
                            HomeData.get(getContext()).settingsChanged();
                            HomeActivity.refreshNow();
                            closeDetail();
                        });
                    }
                }
                if (!appPkgs.isEmpty()) {
                    header(Ui.tr("Installed Apps"));
                    for (int i = 0; i < appPkgs.size(); i++) {
                        String pkg = appPkgs.get(i);
                        if (pkg.isEmpty() || pkg.startsWith("action:")) continue;
                        String label = appLabels.get(i);
                        String key = "app:" + pkg;
                        link(label, () -> (Prefs.liveTvAction().equals(key) || Prefs.liveTvAction().equals(pkg)) ? Ui.tr("Selected") : "", () -> {
                            Prefs.setLiveTvAction(key);
                            HomeData.get(getContext()).settingsChanged();
                            HomeActivity.refreshNow();
                            closeDetail();
                        });
                    }
                }
            });
        }

        /** Paired headphones that can go on the home screen's shortcuts. */
        private List<BluetoothAudio.DeviceInfo> deviceChoices() {
            if (deviceChoices == null) deviceChoices = BluetoothAudio.getPairedAudioDevices();
            return deviceChoices;
        }

        // MARK: - Lists that open in the pane

        private void openPicker(int which) {
            pickFor = which;
            pickLabels.clear();
            if (which == PICK_CITY) {
                for (String[] city : cities) pickLabels.add(city[0]);
                pickCurrent = -1;
            } else if (which == PICK_TEAM) {
                for (String[] team : teamResults) pickLabels.add(Sports.describe(team));
                pickCurrent = -1;
            } else if (which == PICK_LEVEL) {
                pickLabels.addAll(java.util.Arrays.asList(levelNames()));
                pickCurrent = Prefs.dreamLevel();
            } else if (which == PICK_CUSTOM) {
                pickLabels.addAll(appLabels);
                String now = Prefs.customKeyApp(customCode, customScan);
                pickCurrent = now == null ? -1 : appPkgs.indexOf(now);
            } else if (which == PICK_LANGUAGE) {
                String[] choices = languageChoices();
                for (String code : choices) {
                    pickLabels.add(code.equals("auto") ? Ui.tr("Automatic") : Ui.languageName(code));
                }
                pickCurrent = Arrays.asList(choices).indexOf(Prefs.language());
            } else {
                pickLabels.addAll(appLabels);
                pickCurrent = appPkgs.indexOf(Prefs.keyApp(Prefs.KEYS[which - 1]));
            }
            int visible = Math.min(PICK_VISIBLE, pickLabels.size());
            int at = Math.max(0, pickCurrent);
            pickScroll = Math.max(0, Math.min(at - 3, pickLabels.size() - visible));
            focus = at - pickScroll;
            screen = PICKER;
        }

        private void openShelfEditor(boolean top) {
            editingTop = top;
            shelfOrder.clear();
            if (top) {
                shelfOrder.addAll(HomeApps.topButtonKeys());
                rebuildShelfRows(HomeApps.topCandidates());
            } else {
                // Only the apps the shelf shows: one that was uninstalled would hold a place (and count toward the
                // limit) without a row to take it off.
                PackageManager pm = getContext().getPackageManager();
                for (String pkg : HomeApps.shelfPackages(getContext())) if (HomeApps.launchIntent(pm, pkg) != null) shelfOrder.add(pkg);
                rebuildShelfRows(HomeApps.shelfCandidates(getContext()));
            }
            pickScroll = 0;
            focus = 0;
            screen = SHELF_EDIT;
        }

        /** Selected apps first, in shelf order, then the rest in the order they came. */
        private void rebuildShelfRows(List<String[]> candidates) {
            shelfRows.clear();
            for (String pkg : shelfOrder) {
                for (String[] cand : candidates) if (cand[0].equals(pkg)) { shelfRows.add(cand); break; }
            }
            for (String[] cand : candidates) if (!shelfOrder.contains(cand[0])) shelfRows.add(cand);
        }

        /** Same rows after a change: keeps the focus on the app that was focused. */
        private void refreshShelfRows(String focusedPkg) {
            List<String[]> all = new ArrayList<>(shelfRows);
            rebuildShelfRows(all);
            for (int i = 0; i < shelfRows.size(); i++) {
                if (!shelfRows.get(i)[0].equals(focusedPkg)) continue;
                int visible = listRows();
                if (i < pickScroll) pickScroll = i;
                else if (i >= pickScroll + visible) pickScroll = i - visible + 1;
                focus = i - pickScroll;
                break;
            }
            if (editingTop) Prefs.setTopButtons(shelfOrder); else Prefs.setShelf(shelfOrder);
        }

        /** OK: puts the app on the shelf (at the end) or takes it off. */
        private void toggleShelfApp() {
            String pkg = shelfRows.get(pickScroll + focus)[0];
            if (shelfOrder.contains(pkg)) shelfOrder.remove(pkg);
            else if (editingTop || shelfOrder.size() < HomeApps.MAX_SHELF) shelfOrder.add(pkg);   // few buttons: no limit to reach
            else return;
            refreshShelfRows(pkg);
        }

        /** ← → : moves a shelf app one place earlier or later. */
        private void moveShelfApp(int dir) {
            String pkg = shelfRows.get(pickScroll + focus)[0];
            int at = shelfOrder.indexOf(pkg), to = at + dir;
            if (at < 0 || to < 0 || to >= shelfOrder.size()) return;
            shelfOrder.remove(at);
            shelfOrder.add(to, pkg);
            refreshShelfRows(pkg);
        }
    }
}
