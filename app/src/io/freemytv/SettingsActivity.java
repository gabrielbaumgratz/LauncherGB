package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GB Launcher's settings (the app's icon, "All Apps", or the gear on the home screen), in the style of Apple TV's
 * settings: the big symbol and title on the left, the list on the right with the focused row in white. "Home screen"
 * opens the GB Launcher / Original choice (with a check on the current one); the switch runs in the main process
 * (HomeMode) and this screen only asks for it and follows the state file.
 */
public class SettingsActivity extends Activity {
    private SettingsView view;
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
            showPreview();
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

    void showPreview() {
        preview = new DreamView(this);
        setContentView(preview);
        preview.start();
    }

    private void closePreview() {
        if (preview == null) return;
        preview.stop();
        preview = null;
        setContentView(view);
        view.resume();
    }

    static final class SettingsView extends View implements Choreographer.FrameCallback {
        private static final int MAIN = 0, HOME_CHOICE = 1, HOME_SETTINGS = 2, REMOTE_KEYS = 3, PICKER = 4, SHELF_EDIT = 5;
        private static final float LIST_ROW_H = 68, LIST_ROW_GAP = 6;
        private static final int PICK_VISIBLE = 9;
        private static final float LIST_X = 1000, LIST_R = 1824, LIST_TOP = 186, ROW_GAP = 8, CHOICE_H = 104;
        // The main menu: seven rows, then the comparison, the picks (with the Watchmode credit) and two notices, all on
        // one 1080 px screen.
        private static final float MAIN_TOP = 140, ROW_H = 62, MAIN_GAP = 4;
        private static final int AMBER = 0xFFFFC266, INK = 0xFF1C1C1E;

        private final SettingsActivity activity;
        private final Scene scene = new Scene();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Spring[] pop = new Spring[40];
        private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);   // the glass behind a group of rows
        private final List<String> appPkgs = new ArrayList<>(), appLabels = new ArrayList<>();
        // The list picker (the app of a remote button): what it shows, which one is in use, where we are.
        private final List<String> pickLabels = new ArrayList<>();
        // The shelf editor (also the top buttons' one, with editingTop): what it can hold (selected ones first, in
        // order) and the selection.
        private final List<String[]> shelfRows = new ArrayList<>();
        private final List<String> shelfOrder = new ArrayList<>();
        private boolean editingTop;
        private int pickFor, pickCurrent, pickScroll;   // pickFor: the remote button (index + 1)
        private static final int PICK_CITY = 0, PICK_LANGUAGE = -1;
        private final List<String[]> cities = new ArrayList<>();
        private String cityStatus = "";
        private final RectF tmp = new RectF(), rowRect = new RectF(), focusBox = new RectF();
        private JSONObject state = new JSONObject(), recs = new JSONObject();
        private boolean hasKey;
        private int screen = MAIN, focus;
        private boolean running, okDown, backDown, wasBusy;
        private boolean comparisonExpanded, recsExpanded;
        private StaticLayout errorLayout;   // the last error, wrapped (rebuilt only when it changes)
        private String errorShown;
        private final Spoken spoken;
        private long startedAt, lastNanos, pendingAt;
        private int w, h;
        private float k = 1;
        private Paint rowLabel, rowValue, rowSub, header, cellLabel, cellValue, warn, spinner, white, shadow, bgPaint, versionLeft;
        private TextPaint notice, mainHeading;

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
                    if (newest != null) announceForAccessibility(newest.optString("text"));
                }
                wasBusy = nowBusy;
                invalidate();
                handler.postDelayed(this, busy() ? 500 : 2000);
            }
        };

        SettingsView(SettingsActivity a) {
            super(a);
            activity = a;
            for (int i = 0; i < pop.length; i++) pop[i] = new Spring(0.28f, 0.85f, 0);
            setFocusable(true);
            setContentDescription("GB Launcher");
            // TalkBack: one item per row, id = row + 1.
            spoken = new Spoken(this, new Spoken.Source() {
                @Override public void items(List<Integer> out) { for (int i = 1; i <= rowCount(); i++) out.add(i); }
                @Override public String text(int id) { return id >= 1 && id <= rowCount() ? rowText(id - 1) : ""; }
                @Override public boolean bounds(int id, Rect out) {
                    if (id < 1 || id > rowCount()) return false;
                    rowBox(id - 1).round(out);
                    return true;
                }
                @Override public int focused() { return focus + 1; }
                @Override public void focus(int id) {
                    focus = id - 1;
                    invalidate();
                }
                @Override public void click(int id) { activate(); }
            });
        }

        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
            return spoken;
        }

        /** A row as TalkBack reads it: its name and its value, as on screen. */
        private String rowText(int row) {
            switch (screen) {
                case MAIN: {
                    if (row == 0) {
                        String busyText = busyText();
                        String value = busy() ? (busyText.isEmpty() ? Ui.tr("Starting…") : busyText)
                                : freeActive() ? "GB Launcher" : Ui.tr("Original");
                        return Ui.trf("Home Screen, {mode}", "mode", value);
                    }
                    String[] names = {Ui.tr("Screensaver, preview"), Ui.tr("Home Settings"),
                            Ui.tr("Shelf"), Ui.tr("Top Buttons"), Ui.tr("Remote Buttons"), Ui.tr("TV Settings"),
                            Ui.tr("Comparison"), Ui.tr("Recommendations")};
                    if (row - 1 < names.length) {
                        if (row == 7) return names[row - 1] + (comparisonExpanded ? Ui.tr(", expanded") : Ui.tr(", collapsed"));
                        if (row == 8) return names[row - 1] + (recsExpanded ? Ui.tr(", expanded") : Ui.tr(", collapsed"));
                        return names[row - 1];
                    }
                    return "";
                }
                case HOME_CHOICE: {
                    String name = row == 0 ? "GB Launcher, " + Ui.tr("no ads, with the new screensaver")
                            : "Original, " + Ui.tr("the TV's factory home screen");
                    boolean inUse = (row == 0) == freeActive();
                    return name + (inUse ? Ui.tr(". In use") : "") + ", " + Ui.trf("{i} of {n}", "i", row + 1, "n", 2);
                }
                case HOME_SETTINGS:
                case REMOTE_KEYS: {
                    boolean keys = screen == REMOTE_KEYS;
                    String[] labels = keys ? keyLabels() : settingLabels(), values = keys ? keyValues() : settingValues();
                    return row < labels.length ? labels[row] + ", " + values[row] : "";
                }
                case PICKER: {
                    int at = pickScroll + row;
                    if (at >= pickLabels.size()) return "";
                    return pickLabels.get(at) + (at == pickCurrent ? Ui.tr(", selected") : "")
                            + ", " + Ui.trf("{i} of {n}", "i", at + 1, "n", pickLabels.size());
                }
                case SHELF_EDIT: {
                    int at = pickScroll + row;
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
                default:
                    return "";
            }
        }

        private float comparisonHeight() {
            return 160;
        }

        private float recsHeight() {
            return Config.WATCHMODE_KEY.trim().isEmpty() ? 96 : 126;
        }

        private float errorHeight() {
            boolean working = busy();
            String busyText = busyText();
            String error = state.optString("error");
            if (working && busyText.equals(Ui.tr("Accept “Allow USB debugging?” on the TV (tick “Always allow”)."))) {
                return 46;
            } else if (!working && !error.isEmpty()) {
                int width = Math.round(760 * k);
                if (errorLayout == null || !error.equals(errorShown) || errorLayout.getWidth() != width) {
                    errorShown = error;
                    errorLayout = StaticLayout.Builder.obtain(error, 0, error.length(), new TextPaint(warn), width)
                            .setMaxLines(5).setEllipsize(TextUtils.TruncateAt.END).build();
                }
                return (errorLayout.getLineBaseline(errorLayout.getLineCount() - 1) - errorLayout.getLineBaseline(0)) / k + 46;
            }
            return 0;
        }

        private float mainRowY(int row) {
            float y = MAIN_TOP;
            if (row < 7) {
                return y + row * (ROW_H + MAIN_GAP);
            }
            float err = errorHeight();
            float y7 = y + 7 * (ROW_H + MAIN_GAP) + 12 + err;
            if (row == 7) return y7;
            float y8 = y7 + ROW_H + MAIN_GAP + (comparisonExpanded ? (comparisonHeight() + 10) : 0);
            return y8;
        }

        private RectF rowBox(int row) {
            float top, height;
            if (screen == MAIN) {
                float totalBottom = mainRowY(8) + ROW_H + (recsExpanded ? (recsHeight() + 10) : 0) + 30;
                float scroll = 0;
                if (totalBottom > 1030) {
                    float curY = mainRowY(focus);
                    if (curY + ROW_H + 40 > 1030) {
                        scroll = Math.min(totalBottom - 1030, curY + ROW_H + 40 - 1030);
                    }
                }
                top = mainRowY(row) - scroll;
                height = ROW_H;
            } else if (screen == HOME_CHOICE) {
                top = LIST_TOP + 20 + row * (CHOICE_H + ROW_GAP);
                height = CHOICE_H;
            } else {
                top = LIST_TOP + 20 + row * (LIST_ROW_H + LIST_ROW_GAP);
                height = LIST_ROW_H;
            }
            focusBox.set(LIST_X * k, top * k, LIST_R * k, (top + height) * k);
            return focusBox;
        }

        void resume() {
            if (running) return;
            running = true;
            startedAt = SystemClock.uptimeMillis();
            lastNanos = 0;
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
            boolean moving = busy();
            for (Spring s : pop) moving |= !s.settled();
            if (moving) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            w = width;
            h = height;
            k = w / 1920f;
            RadialGradient gradient = new RadialGradient(
                    w * 0.32f, h * 0.22f, Math.max(w, h) * 0.95f,
                    new int[] { 0xFF181B28, 0xFF0E1018, 0xFF07080D },
                    new float[] { 0f, 0.50f, 1f },
                    Shader.TileMode.CLAMP
            );
            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setShader(gradient);
            mainHeading = new TextPaint(Ui.text(Ui.displaySemibold, 44 * k, 0xFFFFFFFF));
            mainHeading.setTextAlign(Paint.Align.LEFT);
            versionLeft = Ui.text(Ui.regular, 17 * k, 0x38FFFFFF);
            versionLeft.setTextAlign(Paint.Align.LEFT);
            rowLabel = Ui.text(Ui.medium, 30 * k, 0xF2FFFFFF);
            rowValue = Ui.text(Ui.regular, 28 * k, 0x8CFFFFFF);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            rowSub = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            header = Ui.text(Ui.semibold, 21 * k, 0x73FFFFFF);
            header.setLetterSpacing(0.06f);
            cellLabel = Ui.text(Ui.regular, 25 * k, 0xD9FFFFFF);
            cellValue = Ui.numbers(Ui.regular, 25 * k, 0xD9FFFFFF);
            cellValue.setTextAlign(Paint.Align.RIGHT);
            warn = Ui.text(Ui.regular, 23 * k, AMBER);
            notice = new TextPaint(Ui.text(Ui.regular, 23 * k, 0xB3FFFFFF));
            spinner = new Paint(Paint.ANTI_ALIAS_FLAG);
            spinner.setStyle(Paint.Style.STROKE);
            spinner.setStrokeCap(Paint.Cap.ROUND);
            spinner.setStrokeWidth(3.5f * k);
            white = new Paint(Paint.ANTI_ALIAS_FLAG);
            white.setColor(0xFFF5F5F7);
            shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setMaskFilter(new BlurMaskFilter(22 * k, BlurMaskFilter.Blur.NORMAL));
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

        private int rowCount() {
            switch (screen) {
                case MAIN: return 9;
                case HOME_CHOICE: return 2;
                case HOME_SETTINGS: return settingLabels().length;
                case PICKER: return Math.min(PICK_VISIBLE, pickLabels.size());
                case SHELF_EDIT: return Math.min(PICK_VISIBLE, shelfRows.size());
                default: return Prefs.KEYS.length;
            }
        }

        @Override protected void onDraw(Canvas c) {
            if (w == 0) return;
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            for (int i = 0; i < pop.length; i++) {
                pop[i].target = i == focus && i < rowCount() ? 1 : 0;
                pop[i].step(dt);
            }
            long up = SystemClock.uptimeMillis();
            float seconds = (up - startedAt) / 1000f;

            if (bgPaint != null) {
                c.drawRect(0, 0, w, h, bgPaint);
            } else {
                c.drawColor(0xFF0D0F18);
            }

            // On the left: title / greeting and subtle bottom-left version
            String leftTitle = (screen == MAIN) ? Ui.tr("What would you like to configure, Gabriel?")
                    : (screen == HOME_CHOICE) ? Ui.tr("Home Screen")
                    : (screen == HOME_SETTINGS) ? Ui.tr("Home Settings")
                    : (screen == REMOTE_KEYS) ? Ui.tr("Remote Buttons")
                    : (screen == SHELF_EDIT) ? (editingTop ? Ui.tr("Top Buttons") : Ui.tr("Shelf"))
                    : Ui.tr("What would you like to configure, Gabriel?");

            StaticLayout titleLayout = StaticLayout.Builder.obtain(leftTitle, 0, leftTitle.length(), mainHeading, Math.round(760 * k))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();
            c.save();
            c.translate(100 * k, 260 * k);
            titleLayout.draw(c);
            c.restore();

            c.drawText(versionName(getContext()), 100 * k, h - 45 * k, versionLeft);

            if (screen == MAIN) drawMain(c, seconds); else if (screen == HOME_CHOICE) drawChoice(c); else if (screen == PICKER) drawPicker(c); else if (screen == SHELF_EDIT) drawShelfEditor(c); else drawList(c);
        }

        private void drawMain(Canvas c, float seconds) {
            boolean working = busy();
            String busyText = busyText();
            String mode = freeActive() ? "GB Launcher" : Ui.tr("Original");
            String[] labels = {Ui.tr("Home Screen"), Ui.tr("Screensaver"),
                    Ui.tr("Home Settings"), Ui.tr("Shelf"), Ui.tr("Top Buttons"),
                    Ui.tr("Remote Buttons"), Ui.tr("TV Settings")};
            String[] values = {working ? "" : mode, Ui.tr("Preview"), "", "", "", "", ""};

            float totalBottom = mainRowY(8) + ROW_H + (recsExpanded ? (recsHeight() + 10) : 0) + 30;
            float scroll = 0;
            if (totalBottom > 1030) {
                float curY = mainRowY(focus);
                if (curY + ROW_H + 40 > 1030) {
                    scroll = Math.min(totalBottom - 1030, curY + ROW_H + 40 - 1030);
                }
            }

            c.save();
            c.clipRect((LIST_X - 20) * k, (MAIN_TOP - 10) * k, w, h);
            c.translate(0, -scroll * k);

            for (int i = 0; i < labels.length; i++) {
                float y = (MAIN_TOP + i * (ROW_H + MAIN_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + ROW_H * k);
                float f = drawRowBackground(c, i);
                int ink = blend(0xF2FFFFFF, INK, f);
                rowLabel.setColor(ink);
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(labels[i], rowRect.left + 30 * k, base, rowLabel);
                float right = rowRect.right - 30 * k;
                if (i >= 2) {
                    float cw = Symbols.width(getContext(), "chevron_right", 22 * k);
                    Symbols.draw(getContext(), c, "chevron_right", right - cw / 2, rowRect.centerY(), 22 * k, blend(0x66FFFFFF, 0x991C1C1E, f));
                } else if (i == 0 && working) {
                    String text = busyText.isEmpty() ? Ui.tr("Starting…") : busyText;
                    rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                    String shown = TextUtils.ellipsize(text, new TextPaint(rowValue), 470 * k, TextUtils.TruncateAt.END).toString();
                    c.drawText(shown, right, base, rowValue);
                    float sx = right - rowValue.measureText(shown) - 26 * k;
                    spinner.setColor(blend(0xFFFFFFFF, INK, f));
                    Ui.spinner(c, sx, rowRect.centerY(), 12 * k, seconds * 1.3f, spinner);
                } else {
                    rowValue.setColor(blend(0x8CFFFFFF, 0x8C1C1C1E, f));
                    c.drawText(values[i], right, base, rowValue);
                }
            }

            float errY = (MAIN_TOP + labels.length * (ROW_H + MAIN_GAP) + 12) * k;
            String error = state.optString("error");
            if (working && busyText.equals(Ui.tr("Accept “Allow USB debugging?” on the TV (tick “Always allow”)."))) {
                c.drawText(Ui.tr("On the TV: tick “Always allow” and press OK."),
                        LIST_X * k + 30 * k, errY + 24 * k, warn);
            } else if (!working && !error.isEmpty()) {
                int width = Math.round(760 * k);
                if (errorLayout == null || !error.equals(errorShown) || errorLayout.getWidth() != width) {
                    errorShown = error;
                    errorLayout = StaticLayout.Builder.obtain(error, 0, error.length(), new TextPaint(warn), width)
                            .setMaxLines(5).setEllipsize(TextUtils.TruncateAt.END).build();
                }
                c.save();
                c.translate(LIST_X * k + 30 * k, errY - errorLayout.getLineBaseline(0));
                errorLayout.draw(c);
                c.restore();
            }

            // Row 7: Comparison
            float y7 = mainRowY(7) * k;
            rowRect.set(LIST_X * k, y7, LIST_R * k, y7 + ROW_H * k);
            float f7 = drawRowBackground(c, 7);
            rowLabel.setColor(blend(0xF2FFFFFF, INK, f7));
            float base7 = rowRect.centerY() + Ui.halfCap(rowLabel);
            c.drawText(Ui.tr("Comparison"), rowRect.left + 30 * k, base7, rowLabel);
            String ch7 = comparisonExpanded ? "chevron_down" : "chevron_right";
            float cw7 = Symbols.width(getContext(), ch7, 22 * k);
            Symbols.draw(getContext(), c, ch7, rowRect.right - 30 * k - cw7 / 2, rowRect.centerY(), 22 * k, blend(0x66FFFFFF, 0x991C1C1E, f7));

            if (comparisonExpanded) {
                drawComparisonCard(c, (mainRowY(7) + ROW_H + 8) * k);
            }

            // Row 8: Recommendations
            float y8 = mainRowY(8) * k;
            rowRect.set(LIST_X * k, y8, LIST_R * k, y8 + ROW_H * k);
            float f8 = drawRowBackground(c, 8);
            rowLabel.setColor(blend(0xF2FFFFFF, INK, f8));
            float base8 = rowRect.centerY() + Ui.halfCap(rowLabel);
            c.drawText(Ui.tr("Recommendations"), rowRect.left + 30 * k, base8, rowLabel);
            String ch8 = recsExpanded ? "chevron_down" : "chevron_right";
            float cw8 = Symbols.width(getContext(), ch8, 22 * k);
            Symbols.draw(getContext(), c, ch8, rowRect.right - 30 * k - cw8 / 2, rowRect.centerY(), 22 * k, blend(0x66FFFFFF, 0x991C1C1E, f8));

            if (recsExpanded) {
                drawRecsCard(c, (mainRowY(8) + ROW_H + 8) * k);
            }

            float noticesY = (mainRowY(8) + ROW_H + (recsExpanded ? (recsHeight() + 10) : 0) + 24) * k;
            drawNotices(c, noticesY);

            c.restore();
        }

        private void drawComparisonCard(Canvas c, float cardTop) {
            float hPx = comparisonHeight() * k;
            tmp.set(LIST_X * k, cardTop, LIST_R * k, cardTop + hPx);
            card.setStyle(Paint.Style.FILL);
            card.setColor(0x14FFFFFF);
            c.drawRoundRect(tmp, 20 * k, 20 * k, card);
            card.setStyle(Paint.Style.STROKE);
            card.setStrokeWidth(Math.max(1, k));
            card.setColor(0x22FFFFFF);
            c.drawRoundRect(tmp, 20 * k, 20 * k, card);

            float x = LIST_X * k + 30 * k, colA = LIST_R * k - 220 * k, colB = LIST_R * k - 30 * k;
            float headY = cardTop + 34 * k;
            cellValue.setColor(0x73FFFFFF);
            c.drawText(Ui.tr("Original"), colA, headY, cellValue);
            c.drawText("GB Launcher", colB, headY, cellValue);
            cellValue.setColor(0xD9FFFFFF);
            JSONObject metrics = state.optJSONObject("metrics");
            JSONObject a = metrics == null ? null : metrics.optJSONObject(HomeMode.ORIGINAL);
            JSONObject b = metrics == null ? null : metrics.optJSONObject(HomeMode.FREE);
            String[][] rows = {{Ui.tr("Home button"), "homeMs"}, {Ui.tr("Free memory"), "memAvailableMB"},
                    {Ui.tr("Home screen memory"), "homePssMB"}};
            for (int i = 0; i < rows.length; i++) {
                float base = cardTop + (68 + 36 * i) * k;
                c.drawText(rows[i][0], x, base, cellLabel);
                c.drawText(metric(a, rows[i][1]), colA, base, cellValue);
                c.drawText(metric(b, rows[i][1]), colB, base, cellValue);
            }
        }

        private void drawRecsCard(Canvas c, float cardTop) {
            float hPx = recsHeight() * k;
            tmp.set(LIST_X * k, cardTop, LIST_R * k, cardTop + hPx);
            card.setStyle(Paint.Style.FILL);
            card.setColor(0x14FFFFFF);
            c.drawRoundRect(tmp, 20 * k, 20 * k, card);
            card.setStyle(Paint.Style.STROKE);
            card.setStrokeWidth(Math.max(1, k));
            card.setColor(0x22FFFFFF);
            c.drawRoundRect(tmp, 20 * k, 20 * k, card);

            float x = LIST_X * k + 30 * k;
            String line;
            String error = recs.optString("error");
            int count = recs.optInt("count");
            if (!hasKey) line = Ui.tr("No TMDB key: see the README (config.properties).");
            else if (error.equals(Recs.NO_KNOWN_APPS)) line = Ui.tr("None of the TV's streaming apps can give picks.");
            else if (error.equals(Recs.NO_APPS)) line = Ui.trf("TMDB has none of the TV's apps for tmdb_region={region}.", "region", Recs.REGION);
            else if (!error.isEmpty()) line = Ui.trf("Error: {error}.", "error", error);
            else if (count > 0) line = Ui.trf("On · {n} titles from the TV's apps, 3 new ones every hour.", "n", count);
            else line = Ui.tr("Fetching titles…");
            c.drawText(TextUtils.ellipsize(line, notice, 760 * k, TextUtils.TruncateAt.END).toString(), x, cardTop + 36 * k, notice);
            versionLeft.setTextAlign(Paint.Align.LEFT);
            c.drawText(Ui.tr("This product uses the TMDB API but is not endorsed or certified by TMDB."), x, cardTop + 68 * k, versionLeft);
            if (!Config.WATCHMODE_KEY.trim().isEmpty()) {
                c.drawText(Ui.tr("Links to the titles by Watchmode."), x, cardTop + 96 * k, versionLeft);
            }
        }

        /** List row: no background; focused, white with a shadow (the Apple TV focus). Returns how focused it is. */
        private float drawRowBackground(Canvas c, int i) {
            float f = Ui.clamp(pop[i].value);
            if (f > 0.01f) {
                float s = 1 + 0.02f * f;
                float cx = rowRect.centerX(), cy = rowRect.centerY();
                tmp.set(cx - rowRect.width() * s / 2, cy - rowRect.height() * s / 2, cx + rowRect.width() * s / 2, cy + rowRect.height() * s / 2);
                float r = 18 * k * s;
                tmp.offset(0, 10 * k * f);
                shadow.setColor(Ui.alpha(0xFF000000, 0.4f * f));
                c.drawRoundRect(tmp, r, r, shadow);
                tmp.offset(0, -10 * k * f);
                white.setAlpha(Math.round(255 * f));
                c.drawRoundRect(tmp, r, r, white);
                rowRect.set(tmp);
            }
            return f;
        }

        private void drawNotices(Canvas c, float y) {
            float x = LIST_X * k + 30 * k;
            c.drawText(Ui.tr("NOTICES"), x, y, header);
            JSONArray notices = state.optJSONArray("notices");
            int shown = 0;
            for (int i = 0; notices != null && i < notices.length() && shown < 2; i++) {
                JSONObject o = notices.optJSONObject(i);
                if (o == null) continue;
                String line = ago(o.optLong("at")) + " · " + o.optString("text");
                c.drawText(TextUtils.ellipsize(line, notice, 790 * k, TextUtils.TruncateAt.END).toString(), x, y + (42 + 38 * shown) * k, notice);
                shown++;
            }
            if (shown == 0) c.drawText(Ui.tr("None yet."), x, y + 42 * k, notice);
        }

        /** Home screen choice: GB Launcher or the original, with a check on the one in use. */
        private void drawChoice(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(Ui.tr("Home Screen"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            String[][] options = {{"GB Launcher", Ui.tr("No ads, with the new screensaver")},
                    {Ui.tr("Original"), Ui.tr("The TV's factory home screen")}};
            int current = freeActive() ? 0 : 1;
            for (int i = 0; i < 2; i++) {
                float y = (LIST_TOP + 20 + i * (CHOICE_H + ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + CHOICE_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                rowSub.setColor(blend(0x8CFFFFFF, 0x991C1C1E, f));
                c.drawText(options[i][0], rowRect.left + 30 * k, rowRect.top + 44 * k, rowLabel);
                c.drawText(options[i][1], rowRect.left + 30 * k, rowRect.top + 80 * k, rowSub);
                if (i == current) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - 30 * k - cw / 2, rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                }
            }
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            c.drawText(Ui.tr("Switching takes a few seconds; the screen blinks while it measures."), LIST_X * k + 30 * k,
                    (LIST_TOP + 20 + 2 * (CHOICE_H + ROW_GAP) + 40) * k, hint);
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
                return "GB Launcher " + c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return "GB Launcher";
            }
        }

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down && focus > 0) focus--;
                    else if (down && scrolling() && pickScroll > 0) pickScroll--;
                    invalidate();
                    spoken.focusMoved();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && focus < rowCount() - 1) focus++;
                    else if (down && scrolling() && pickScroll + rowCount() < listSize()) pickScroll++;
                    invalidate();
                    spoken.focusMoved();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down && screen == SHELF_EDIT) {
                        moveShelfApp(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                        invalidate();
                    } else if (down && (screen == HOME_SETTINGS || screen == REMOTE_KEYS)) {
                        adjust(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                        invalidate();
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
                    if (screen == MAIN) return false;   // leaves the settings
                    if (down) {
                        if (e.getRepeatCount() == 0) backDown = true;
                    } else {
                        if (backDown && !e.isCanceled()) {
                            if (screen == PICKER && (pickFor == PICK_CITY || pickFor == PICK_LANGUAGE)) {
                                focus = pickFor == PICK_CITY ? CITY_ROW : 0;
                                screen = HOME_SETTINGS;
                            } else if (screen == PICKER) {
                                focus = pickFor - 1;
                                screen = REMOTE_KEYS;
                            } else {
                                focus = screen == HOME_SETTINGS ? 2 : screen == SHELF_EDIT ? (editingTop ? 4 : 3)
                                        : screen == REMOTE_KEYS ? 5 : 0;
                                screen = MAIN;
                            }
                        }
                        backDown = false;
                    }
                    invalidate();
                    spoken.focusMoved();
                    return true;
                default:
                    return false;
            }
        }

        // MARK: - Home Settings and Remote Buttons

        private static final String[] UNITS = {"auto", "C", "F"}, THEMES = {"auto", "light", "dark"};
        /** Home Settings: language, temperature, theme, sounds, weather, city, quotes, then one row per coin. */
        private static final int CITY_ROW = 6, COIN_ROWS = 8;

        private boolean dollar() {
            return "BRL".equalsIgnoreCase(Config.CURRENCY.trim());
        }

        /** Inputs the person can switch on or off for the home screen (listed after the other rows). */
        private List<HomeApps.Item> inputChoices;

        private List<HomeApps.Item> inputChoices() {
            if (inputChoices == null) inputChoices = HomeApps.inputChoices(getContext());
            return inputChoices;
        }

        /** Paired headphones that can go on the home screen's shortcuts (listed after the inputs). */
        private List<BluetoothAudio.DeviceInfo> deviceChoices;

        private List<BluetoothAudio.DeviceInfo> deviceChoices() {
            if (deviceChoices == null) deviceChoices = BluetoothAudio.getPairedAudioDevices();
            return deviceChoices;
        }

        private int deviceStart() {
            return inputStart() + inputChoices().size();
        }

        /** First row of the inputs: after the city, and after the quotes and coins when they are in this build. */
        private int inputStart() {
            return Config.SHOW_CRYPTO ? COIN_ROWS + 3 + (dollar() ? 1 : 0) : CITY_ROW + 1;
        }

        /** Where each group of rows starts (and the end): General, Weather, quotes, inputs and shortcuts, photos. */
        private int[] groupBounds(int rows) {
            List<Integer> b = new ArrayList<>();
            b.add(0);
            b.add(4);
            b.add(CITY_ROW + 1);
            if (Config.SHOW_CRYPTO) b.add(inputStart());
            b.add(deviceStart() + deviceChoices().size());
            b.add(rows);
            int[] out = new int[b.size()];
            for (int i = 0; i < out.length; i++) out[i] = b.get(i);
            return out;
        }

        private String[] groupTitles() {
            List<String> t = new ArrayList<>();
            t.add(Ui.tr("General"));
            t.add(Ui.tr("Weather"));
            if (Config.SHOW_CRYPTO) t.add(Ui.tr("Quotes widget"));
            t.add(Ui.tr("Inputs and shortcuts"));
            t.add(Ui.tr("Photos"));
            return t.toArray(new String[0]);
        }

        private String[] settingLabels() {
            List<String> l = new ArrayList<>();
            l.add(Ui.tr("Language"));
            l.add(Ui.tr("Theme"));
            l.add(Ui.tr("Navigation sounds"));
            l.add(Ui.tr("Glass: clear to frosted"));
            l.add(Ui.tr("Temperature"));
            l.add(Ui.tr("Weather widget"));
            l.add(Ui.tr("City"));
            if (Config.SHOW_CRYPTO) {
                l.add(Ui.tr("Quotes widget"));
                l.add("Bitcoin");
                l.add("Ethereum");
                l.add("Solana");
                if (dollar()) l.add(Ui.tr("Dollar"));
            }
            for (HomeApps.Item it : inputChoices()) l.add(Ui.trf("Shown on home: {name}", "name", it.label));
            for (BluetoothAudio.DeviceInfo d : deviceChoices()) l.add(Ui.trf("Shown on home: {name}", "name", d.name));
            l.add(Ui.tr("Photos from your phone"));
            if (!Photos.list(getContext()).isEmpty()) l.add(Ui.tr("Screensaver photos"));
            l.add(Ui.tr("Photo interval"));
            l.add(Ui.tr("Screensaver brightness"));
            return l.toArray(new String[0]);
        }

        private String[] settingValues() {
            String[] labels = settingLabels();
            String[] v = new String[labels.length];
            String lang = Prefs.language();
            v[0] = lang.equals("auto") ? Ui.tr("Automatic") : Ui.languageName(lang);
            String unit = Prefs.temperature();
            v[3] = (Prefs.glass() + 1) + " / 5";
            v[4] = unit.equals("C") ? "°C" : unit.equals("F") ? "°F" : Ui.tr("unit|Automatic");
            String theme = Prefs.theme();
            v[1] = theme.equals("light") ? Ui.tr("Light") : theme.equals("dark") ? Ui.tr("Dark") : Ui.tr("Automatic");
            v[2] = onOff(Prefs.sounds());
            v[5] = onOff(Prefs.weather());
            v[CITY_ROW] = !cityStatus.isEmpty() ? cityStatus : Prefs.city().isEmpty() ? "—" : Prefs.city();
            if (Config.SHOW_CRYPTO) {
                v[COIN_ROWS - 1] = onOff(Prefs.quotes());
                // No quotes widget, nowhere to show prices: the coins read as off (and stay locked) until it is back on.
                for (int i = COIN_ROWS; i < inputStart(); i++) v[i] = onOff(Prefs.quotes() && Prefs.coin(i - COIN_ROWS));
            }
            List<HomeApps.Item> inputs = inputChoices();
            for (int i = 0; i < inputs.size(); i++) v[inputStart() + i] = onOff(!Prefs.inputHidden(inputs.get(i).key));
            List<BluetoothAudio.DeviceInfo> devices = deviceChoices();
            for (int i = 0; i < devices.size(); i++) v[deviceStart() + i] = onOff(Prefs.btShown(devices.get(i).device.getAddress()));
            int photos = Photos.list(getContext()).size(), photosRow = deviceStart() + devices.size();
            v[photosRow] = String.valueOf(photos);
            if (photos > 0) v[photosRow + 1] = Prefs.ownPhotos() ? Ui.tr("Mine") : Ui.tr("Recommendations");
            int s = Prefs.photoSeconds();
            v[photosRow + (photos > 0 ? 2 : 1)] = s < 60 ? Ui.trf("{n} s", "n", s) : s < 3600 ? Ui.trf("{n} min", "n", s / 60) : Ui.trf("{n} h", "n", s / 3600);
            v[photosRow + (photos > 0 ? 3 : 2)] = new String[] {Ui.tr("Standard"), Ui.tr("Dimmer"), Ui.tr("Dimmest")}[Prefs.dreamDim()];
            return v;
        }

        /** "auto", then English and every language with a catalogue. */
        private static String[] languageChoices() {
            String[] all = Ui.languages();
            String[] v = new String[all.length + 1];
            v[0] = "auto";
            System.arraycopy(all, 0, v, 1, all.length);
            return v;
        }

        private static String onOff(boolean on) {
            return on ? Ui.tr("On") : Ui.tr("Off");
        }

        private String[] keyLabels() {
            if (RemoteKeys.STEM_NETFLIX) return new String[] {Ui.tr("Netflix button")};
            if (RemoteKeys.KTC) return new String[] {Ui.tr("Tabii button")};
            if (RemoteKeys.SONY) return new String[] {Ui.tr("BRAVIA CORE button"),
                    Ui.tr("Netflix button"), Ui.tr("Disney+ button"),
                    Ui.tr("Prime Video button"), Ui.tr("YouTube button"),
                    Ui.tr("Crunchyroll button"), Ui.tr("Google Play button")};
            return new String[] {Ui.tr("Netflix button"), Ui.tr("YouTube button"),
                    Ui.tr("Prime Video button"), Ui.tr("TCL Channel button"), Ui.tr("CH button")};
        }

        private String[] keyValues() {
            String[] v = new String[Prefs.KEYS.length];
            for (int i = 0; i < v.length; i++) {
                int at = appPkgs.indexOf(Prefs.keyApp(Prefs.KEYS[i]));
                v[i] = at >= 0 ? appLabels.get(at) : Ui.tr("Not installed");
            }
            return v;
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
            for (String[] f : found) {
                appPkgs.add(f[0]);
                appLabels.add(f[1]);
            }
        }

        /** Changes the focused row's value one step (OK and → go forward, ← goes back). */
        private void adjust(int dir) {
            if (screen == REMOTE_KEYS) {
                int n = appPkgs.size();
                int at = appPkgs.indexOf(Prefs.keyApp(Prefs.KEYS[focus]));
                int next = at < 0 ? (dir > 0 ? 0 : n - 1) : (at + dir + n) % n;
                Prefs.setKeyApp(Prefs.KEYS[focus], appPkgs.get(next));
                return;
            }
            int photosRow = deviceStart() + deviceChoices().size();
            if (focus == photosRow) {
                activity.startActivity(new Intent(activity, PhotosActivity.class));
                return;
            }
            if (focus > photosRow) {
                if (focus == photosRow + 1 && !Photos.list(getContext()).isEmpty()) {
                    Prefs.setOwnPhotos(!Prefs.ownPhotos());
                } else if (focus == photosRow + (Photos.list(getContext()).isEmpty() ? 2 : 3)) {
                    Prefs.setDreamDim((Prefs.dreamDim() + dir + 3) % 3);
                } else {
                    int[] options = Prefs.PHOTO_SECONDS;
                    int at = 0;
                    for (int i = 0; i < options.length; i++) if (options[i] == Prefs.photoSeconds()) at = i;
                    Prefs.setPhotoSeconds(options[(at + dir + options.length) % options.length]);
                }
                return;
            }
            if (focus >= deviceStart()) {
                String address = deviceChoices().get(focus - deviceStart()).device.getAddress();
                Prefs.setBtShown(address, !Prefs.btShown(address));
                return;
            }
            if (focus >= inputStart()) {
                String key = inputChoices().get(focus - inputStart()).key;
                Prefs.setInputHidden(key, !Prefs.inputHidden(key));
                return;
            }
            switch (focus) {
                case 0: break;   // OK opens the language picker.
                case 3: Prefs.setGlass((Prefs.glass() + dir + 5) % 5); break;
                case 4: {
                    int at = java.util.Arrays.asList(UNITS).indexOf(Prefs.temperature());
                    Prefs.setTemperature(UNITS[(at + dir + UNITS.length) % UNITS.length]);
                    break;
                }
                case 1: {
                    int at = java.util.Arrays.asList(THEMES).indexOf(Prefs.theme());
                    Prefs.setTheme(THEMES[(at + dir + THEMES.length) % THEMES.length]);
                    break;
                }
                case 2: Prefs.setSounds(!Prefs.sounds()); break;
                case 5: Prefs.setWeather(!Prefs.weather()); break;
                case CITY_ROW: return;
                case COIN_ROWS - 1: Prefs.setQuotes(!Prefs.quotes()); break;
                default:
                    if (!Prefs.quotes()) return;
                    Prefs.setCoin(focus - COIN_ROWS, !Prefs.coin(focus - COIN_ROWS));
                    break;
            }
            HomeData.get(getContext()).settingsChanged();
        }

        private void askCity() {
            if (Ui.tr("Searching…").equals(cityStatus)) return;
            EditText field = new EditText(activity);
            field.setSingleLine(true);
            field.setHint(Ui.tr("Type the city's name"));
            field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(Ui.tr("City"))
                    .setView(field)
                    .setPositiveButton(Ui.tr("Search"), (d, which) -> searchCity(field.getText().toString()))
                    .setNegativeButton(Ui.tr("Cancel"), null)
                    .create();
            field.setOnEditorActionListener((v, action, event) -> {
                dialog.dismiss();
                searchCity(field.getText().toString());
                return true;
            });
            dialog.show();
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
                    if (!result.isEmpty() && screen == HOME_SETTINGS) {
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

        private boolean scrolling() {
            return screen == PICKER || screen == SHELF_EDIT;
        }

        private int listSize() {
            return screen == SHELF_EDIT ? shelfRows.size() : pickLabels.size();
        }

        // MARK: - Shelf editor

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
                int visible = rowCount();
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
            else if (editingTop || shelfOrder.size() < HomeApps.MAX_SHELF) shelfOrder.add(pkg);   // three buttons: no limit to reach
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

        private void drawShelfEditor(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(editingTop ? Ui.tr("Top Buttons") : Ui.tr("Shelf"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                String[] app = shelfRows.get(pickScroll + i);
                int place = shelfOrder.indexOf(app[0]);
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(place >= 0 ? 0xF2FFFFFF : 0x80FFFFFF, place >= 0 ? INK : 0x801C1C1E, f));
                rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(TextUtils.ellipsize(app[1], new TextPaint(rowLabel), 520 * k, TextUtils.TruncateAt.END).toString(), rowRect.left + 30 * k, base, rowLabel);
                c.drawText(place >= 0 ? "#" + (place + 1) : Ui.tr("shelf|Off"), rowRect.right - 30 * k, base, rowValue);
            }
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
            c.drawText(editingTop ? Ui.tr("OK adds or removes a button. ← → change the order.")
                    : Ui.trf("OK adds or removes (up to {n}). ← → change the order.", "n", HomeApps.MAX_SHELF),
                    LIST_X * k + 30 * k, hy, hint);
        }

        private void openPicker(int which) {
            pickFor = which;
            pickLabels.clear();
            if (which == PICK_CITY) {
                for (String[] city : cities) pickLabels.add(city[0]);
                pickCurrent = -1;
            } else if (which == PICK_LANGUAGE) {
                String[] choices = languageChoices();
                for (String code : choices) {
                    pickLabels.add(code.equals("auto") ? Ui.tr("Automatic") : Ui.languageName(code));
                }
                pickCurrent = java.util.Arrays.asList(choices).indexOf(Prefs.language());
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

        /** A long list (the apps): nine rows at a time, the one in use checked. */
        private void drawPicker(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            String title = pickFor == PICK_CITY ? Ui.tr("City")
                    : pickFor == PICK_LANGUAGE ? Ui.tr("Language") : keyLabels()[pickFor - 1];
            c.drawText(title, LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                int at = pickScroll + i;
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                c.drawText(pickLabels.get(at), rowRect.left + 30 * k, rowRect.centerY() + Ui.halfCap(rowLabel), rowLabel);
                if (at == pickCurrent) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - 30 * k - cw / 2, rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                }
            }
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
            c.drawText((pickScroll + focus + 1) + " / " + pickLabels.size() + "  ·  " + Ui.tr("OK picks, Back cancels."),
                    LIST_X * k + 30 * k, hy, hint);
        }

        private void drawList(Canvas c) {
            boolean keys = screen == REMOTE_KEYS;
            String[] labels = keys ? keyLabels() : settingLabels();
            String[] values = keys ? keyValues() : settingValues();
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(keys ? Ui.tr("Remote Buttons") : Ui.tr("Home Settings"),
                    LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            // Home Settings come in groups, each a glass card under its own title; the page scrolls with the focus.
            int n = labels.length;
            int[] bounds = keys ? new int[] {0, n} : groupBounds(n);
            String[] titles = keys ? null : groupTitles();
            float[] ry = new float[n];
            float y0 = LIST_TOP + 20, head = 62, between = 30;
            float[] cardTop = new float[bounds.length - 1], cardBottom = new float[bounds.length - 1], headY = new float[bounds.length - 1];
            float cursor = y0;
            for (int g = 0; g < bounds.length - 1; g++) {
                if (!keys) {
                    headY[g] = cursor + 32;
                    cursor += head;
                }
                cardTop[g] = cursor - (keys ? 0 : 8);
                for (int i = bounds[g]; i < bounds[g + 1]; i++) {
                    ry[i] = cursor;
                    cursor += LIST_ROW_H + LIST_ROW_GAP;
                }
                cardBottom[g] = cursor - LIST_ROW_GAP + (keys ? 0 : 8);
                cursor += between;
            }
            float scroll = Math.max(0, ry[Math.min(focus, n - 1)] + LIST_ROW_H + 36 - 1040);
            if (ry[Math.min(focus, n - 1)] - scroll < y0) scroll = Math.max(0, ry[Math.min(focus, n - 1)] - y0 - (focus > 0 ? head : 0));
            c.save();
            c.clipRect(0, (LIST_TOP - 6) * k, w, h);
            if (!keys) {
                Paint section = Ui.text(Ui.semibold, 22 * k, 0x99FFFFFF);
                section.setLetterSpacing(0.06f);
                for (int g = 0; g < bounds.length - 1; g++) {
                    tmp.set((LIST_X - 12) * k, (cardTop[g] - scroll) * k, (LIST_R + 12) * k, (cardBottom[g] - scroll) * k);
                    card.setStyle(Paint.Style.FILL);
                    card.setColor(0x16FFFFFF);
                    c.drawRoundRect(tmp, 28 * k, 28 * k, card);
                    card.setStyle(Paint.Style.STROKE);
                    card.setStrokeWidth(Math.max(1, k));
                    card.setColor(0x26FFFFFF);
                    c.drawRoundRect(tmp, 28 * k, 28 * k, card);
                    c.drawText(titles[g].toUpperCase(Locale.ROOT), LIST_X * k + 30 * k, (headY[g] - scroll) * k, section);
                }
            }
            for (int i = 0; i < n; i++) {
                float y = (ry[i] - scroll) * k;
                if (y < (LIST_TOP - 80) * k || y > h) continue;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                boolean locked = !keys && Config.SHOW_CRYPTO && i >= COIN_ROWS && i < inputStart() && !Prefs.quotes();
                rowValue.setColor(blend(locked ? 0x59FFFFFF : 0xB3FFFFFF, locked ? 0x591C1C1E : 0xB31C1C1E, f));
                if (locked) rowLabel.setColor(blend(0x73FFFFFF, 0x731C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(labels[i], rowRect.left + 30 * k, base, rowLabel);
                c.drawText(values[i], rowRect.right - 30 * k, base, rowValue);
            }
            c.restore();
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            float hy = keys ? (cardBottom[0] + 36) * k : (LIST_TOP - 6) * k - 4 * k;
            if (keys) {
                c.drawText(Ui.tr("OK or the arrows change the value."), LIST_X * k + 30 * k, hy, hint);
            } else {
                hint.setTextAlign(Paint.Align.RIGHT);
                c.drawText(Ui.tr("OK or the arrows change the value."), LIST_R * k + 12 * k, (LIST_TOP - 24) * k, hint);
            }
            if (keys) {
                c.drawText(RemoteKeys.VESTEL ? Ui.tr("YouTube, Prime Video and Google Play: the TV itself controls those buttons.")
                        : RemoteKeys.KTC ? Ui.tr("Netflix, YouTube and Prime Video: the TV itself controls those buttons.")
                        : RemoteKeys.SONY ? Ui.tr("Sony default: the button opens what it always did.")
                        : RemoteKeys.FORMOVIE ? Ui.tr("The other buttons: the TV itself controls them.")
                        : RemoteKeys.supported() ? Ui.tr("Disney and Globoplay: the TV itself controls those buttons.")
                        : Ui.tr("TCL remote buttons: on this TV they work as always."), LIST_X * k + 30 * k, hy + 38 * k, hint);
            }
        }

        private void activate() {
            Context c = getContext();
            if (screen == HOME_CHOICE) {
                boolean wantFree = focus == 0;
                boolean already = wantFree ? freeActive() : HomeMode.ORIGINAL.equals(state.optString("mode", HomeMode.ORIGINAL));
                if (!already && !busy()) {
                    HomeMode.request(c, wantFree ? HomeMode.FREE : HomeMode.ORIGINAL);
                    pendingAt = SystemClock.uptimeMillis();
                }
                screen = MAIN;
                focus = 0;
            } else if (screen == PICKER && pickFor == PICK_CITY) {
                String[] city = cities.get(pickScroll + focus);
                Prefs.setCity(city[1], city[2], city[3]);
                HomeData.get(c).settingsChanged();
                focus = CITY_ROW;
                screen = HOME_SETTINGS;
            } else if (screen == PICKER && pickFor == PICK_LANGUAGE) {
                Prefs.setLanguage(languageChoices()[pickScroll + focus]);
                focus = 0;
                screen = HOME_SETTINGS;
            } else if (screen == PICKER) {
                int at = pickScroll + focus;
                Prefs.setKeyApp(Prefs.KEYS[pickFor - 1], appPkgs.get(at));
                focus = pickFor - 1;
                screen = REMOTE_KEYS;
            } else if (screen == SHELF_EDIT) {
                toggleShelfApp();
                invalidate();
                return;
            } else if (screen == REMOTE_KEYS) {
                openPicker(focus + 1);
            } else if (screen == HOME_SETTINGS && focus == 0) {
                openPicker(PICK_LANGUAGE);
            } else if (screen == HOME_SETTINGS && focus == CITY_ROW) {
                askCity();
                return;
            } else if (screen == HOME_SETTINGS) {
                adjust(1);
                invalidate();
                return;
            } else if (focus == 0) {
                if (busy()) return;
                screen = HOME_CHOICE;
                focus = freeActive() ? 0 : 1;
            } else if (focus == 1) {
                pause();
                activity.showPreview();
            } else if (focus == 2) {
                screen = HOME_SETTINGS;
                focus = 0;
            } else if (focus == 3) {
                openShelfEditor(false);
            } else if (focus == 4) {
                openShelfEditor(true);
            } else if (focus == 5) {
                HomeMode.ensureRemoteService(c, 0);   // the buttons work from inside other apps through this service
                loadApps();
                screen = REMOTE_KEYS;
                focus = 0;
            } else if (focus == 6) {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (focus == 7) {
                comparisonExpanded = !comparisonExpanded;
                invalidate();
                spoken.focusMoved();
                return;
            } else if (focus == 8) {
                recsExpanded = !recsExpanded;
                invalidate();
                spoken.focusMoved();
                return;
            }
            for (Spring s : pop) s.snap(0);
            invalidate();
            spoken.focusMoved();
        }
    }
}
