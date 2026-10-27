package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings changed on the TV itself (GB Home TV > Settings). config.properties only gives the first
 * values; anything the user changes here wins. Read from the :home process (home screen, screensaver, settings).
 */
final class Prefs {
    private Prefs() {}

    /** Remote keys we handle, with the app each opens until the user picks another. Codes read from the TCL's log. */
    static final int KEY_NETFLIX = 4062, KEY_YOUTUBE = 190, KEY_PRIME = 4099, KEY_TCL_CHANNEL = 4020, KEY_GUIDE = KeyEvent.KEYCODE_GUIDE;
    /** The Netflix button on Vestel TVs and Formovie projectors: STEM_1, which the TV hands to its Netflix key receiver (dumpsys window policy). */
    static final int KEY_STEM_NETFLIX = KeyEvent.KEYCODE_STEM_1;
    /** KTC's (NextStar) Tabii button: BUTTON_8, which the TV hands to its hot-key receiver (its log: "keycode = 195"). */
    static final int KEY_KTC_TABII = KeyEvent.KEYCODE_BUTTON_8;
    /** Sony's app buttons, as SONY_KEY + scan code (read from a BRAVIA's log; the key codes are shared). */
    static final int SONY_KEY = 100_000;
    static final int KEY_SONY_CORE = SONY_KEY + 496, KEY_SONY_NETFLIX = SONY_KEY + 583, KEY_SONY_DISNEY = SONY_KEY + 491,
            KEY_SONY_PRIME = SONY_KEY + 490, KEY_SONY_YOUTUBE = SONY_KEY + 485, KEY_SONY_CRUNCHYROLL = SONY_KEY + 507,
            KEY_SONY_PLAY = SONY_KEY + 587;   // the Google Play button of older remotes: BUTTON_8 (BRAVIA 4K UR3, Android 10)
    static final int[] KEYS = RemoteKeys.SONY
            ? new int[] {KEY_SONY_CORE, KEY_SONY_NETFLIX, KEY_SONY_DISNEY, KEY_SONY_PRIME, KEY_SONY_YOUTUBE, KEY_SONY_CRUNCHYROLL,
                    KEY_SONY_PLAY}
            : RemoteKeys.STEM_NETFLIX ? new int[] {KEY_STEM_NETFLIX}
            : RemoteKeys.KTC ? new int[] {KEY_KTC_TABII}
            : new int[] {KEY_NETFLIX, KEY_YOUTUBE, KEY_PRIME, KEY_TCL_CHANNEL};   // the CH button is taught like any other

    /** Bumped by every change: screens compare it to know when to redraw. */
    static volatile int version;

    private static Context app;

    static void init(Context c) {
        app = c.getApplicationContext();
        migrateKeys();   // before anything reads the button choices
        Ui.applyLanguage();   // the language picked on the TV, now that it can be read
    }

    /** The app's context in this process (null before App.onCreate). */
    static Context context() {
        return app;
    }

    private static SharedPreferences sp() {
        return app.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    private static void put(String key, String value) {
        sp().edit().putString(key, value).apply();
        version++;
    }

    private static void put(String key, boolean value) {
        sp().edit().putBoolean(key, value).apply();
        version++;
    }

    // MARK: - Language and units

    /** What the greeting calls the person ("Good morning, Ana"); empty for no name. */
    static String userName() { return demo() ? "Gabriel" : sp().getString("userName", "").trim(); }
    static void setUserName(String v) { put("userName", v.trim()); }

    /** "auto" or a language code as picked ("pt", "fr", "zh-CN"…); Ui.applyLanguage finds its catalogue. */
    static String language() {
        String v = app == null ? Config.LANGUAGE : sp().getString("language", Config.LANGUAGE);
        v = v.trim();
        return v.isEmpty() || v.equalsIgnoreCase("auto") ? "auto" : v;
    }

    static void setLanguage(String v) { put("language", v); Ui.applyLanguage(); }

    /** "auto", "C" or "F". */
    static String temperature() {
        String v = sp().getString("temperature", Config.TEMPERATURE).trim().toUpperCase(Locale.ROOT);
        return v.startsWith("F") ? "F" : v.startsWith("C") ? "C" : "auto";
    }

    static void setTemperature(String v) { put("temperature", v); }

    /** "auto" (dark from sunset to sunrise), "light" or "dark". */
    static String theme() {
        String v = sp().getString("theme", "auto");
        return v.equals("light") || v.equals("dark") ? v : "auto";
    }

    static void setTheme(String v) { put("theme", v); }

    /** The same, without the "settings changed" restart: the home screen picks the theme up when it comes back. */
    static void setThemeQuiet(String v) { sp().edit().putString("theme", v).apply(); }

    /** The picks (recommendations) on the home screen; off hides them even with a TMDB key. */
    static boolean recommendations() { return sp().getBoolean("recommendations", true); }
    static void setRecommendations(boolean on) { put("recommendations", on); }

    static int recsTab() { return sp().getInt("recsTab", 0); }
    static void setRecsTab(int tab) { sp().edit().putInt("recsTab", tab).apply(); }

    /** Custom order of shortcuts and inputs in the bottom drawer. */
    static java.util.List<String> drawerOrder() {
        String s = sp().getString("drawerOrder", "");
        if (s.isEmpty()) return java.util.Collections.emptyList();
        return java.util.Arrays.asList(s.split(","));
    }

    static void setDrawerOrder(java.util.List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(keys.get(i));
        }
        sp().edit().putString("drawerOrder", sb.toString()).apply();
    }

    /** Paired Bluetooth devices (by address) the person put on the home screen's shortcuts. */
    static boolean btShown(String address) {
        return sp().getStringSet("btShown", java.util.Collections.<String>emptySet()).contains(address);
    }

    static void setBtShown(String address, boolean shown) {
        java.util.Set<String> now = new java.util.HashSet<>(sp().getStringSet("btShown", java.util.Collections.<String>emptySet()));
        if (shown) now.add(address); else now.remove(address);
        sp().edit().putStringSet("btShown", now).apply();
        version++;
    }

    // MARK: - City

    static String city() { return demo() ? demoPlace()[0] : sp().getString("city", Config.CITY).trim(); }
    static String latitude() { return demo() ? demoPlace()[1] : sp().getString("latitude", Config.LATITUDE); }
    static String longitude() { return demo() ? demoPlace()[2] : sp().getString("longitude", Config.LONGITUDE); }

    /** The demo's city: São Paulo for Portuguese, Madrid for Spanish, New York for the rest. */
    private static String[] demoPlace() {
        String lang = Ui.language;
        if (lang.startsWith("pt")) return new String[] {"São Paulo", "-23.5505", "-46.6333"};
        if (lang.startsWith("es")) return new String[] {"Madrid", "40.4168", "-3.7038"};
        return new String[] {"New York", "40.7128", "-74.0060"};
    }

    /** The person (or config.properties) chose a city; if not, the home screen guesses one from the TV's time zone, once. */
    static boolean placeChosen() { return !Config.CITY.trim().isEmpty() || sp().contains("city") || sp().getBoolean("placeTried", false); }
    static void markPlaceTried() { sp().edit().putBoolean("placeTried", true).apply(); }

    static void setCity(String name, String latitude, String longitude) {
        sp().edit().putString("city", name).putString("latitude", latitude).putString("longitude", longitude).apply();
        version++;
    }

    /** The home screen's soft click when moving and opening. */
    static boolean sounds() { return sp().getBoolean("sounds", true); }
    static void setSounds(boolean on) { put("sounds", on); }

    // MARK: - Weather

    static boolean weather() { return sp().getBoolean("weather", true); }
    static void setWeather(boolean on) { put("weather", on); }

    // MARK: - Glass

    /** The home background comes from the photos (the screensaver's) instead of the recommendations. */
    static boolean photoBackground() { return sp().getBoolean("photoBackground", false); }
    static void setPhotoBackground(boolean on) { put("photoBackground", on); version++; }

    /** The old single frost setting (shelf, Control Center): still the default for those two until they get their own. */
    static int glass() { return Math.max(0, Math.min(4, sp().getInt("glass", 2))); }

    /**
     * The home screen's layout: 0 classic (tabs and cards, the shelf at the bottom), 1 photo (your photos, sharp and
     * full screen, with the shelf at the bottom) and 2 photo with the shelf as a sidebar.
     */
    static int layout() { return Math.max(0, Math.min(2, sp().getInt("layout", 0))); }
    static void setLayout(int v) { sp().edit().putInt("layout", Math.max(0, Math.min(2, v))).apply(); version++; }
    static boolean photoLayout() { return layout() != 0; }
    static boolean sidebar() { return layout() == 2; }

    /** How frosted the home screen's background is: 0 (clear, the photo most visible) to 4 (most frosted); 2 is the usual look. */
    static int backdrop() { return Math.max(0, Math.min(4, sp().getInt("backdrop", 2))); }
    static void setBackdrop(int level) { sp().edit().putInt("backdrop", Math.max(0, Math.min(4, level))).apply(); version++; }

    /** Multiplies a glass tint's opacity by the chosen level (the middle one is the default look). */
    private static final float[] FROST = {0.55f, 0.85f, 1.25f, 1.6f, 2f};

    /** The pieces of glass, each with its own frost and refraction (1 to 5 in the settings). */
    static final int G_SHELF = 0, G_DRAWER = 1, G_TOP = 2, G_CONTROL = 3;

    /** How frosted one piece of glass is, 0 (clear) to 4: its own choice; else the old single one (shelf, Control Center); else the middle. */
    static int glassFrost(int element) {
        int usual = element == G_SHELF || element == G_CONTROL ? glass() : 2;
        return Math.max(0, Math.min(4, sp().getInt("glassFrost" + element, usual)));
    }

    static void setGlassFrost(int element, int level) {
        sp().edit().putInt("glassFrost" + element, Math.max(0, Math.min(4, level))).apply();
        version++;
    }

    /** How much one piece of glass bends what is behind it, 0 (not at all) to 4; 2 is the usual. */
    static int glassRefraction(int element) { return Math.max(0, Math.min(4, sp().getInt("glassRefraction" + element, 2))); }

    static void setGlassRefraction(int element, int level) {
        sp().edit().putInt("glassRefraction" + element, Math.max(0, Math.min(4, level))).apply();
        version++;
    }

    private static final float[] REFRACTION = {0f, 0.5f, 1f, 1.6f, 2.4f};

    /** The multiplier of a piece of glass's lens (1 at the usual level). */
    static float lens(int element) { return REFRACTION[glassRefraction(element)]; }

    /** How far each level pulls the tint toward a nearly opaque frost: the last one reads as frosted glass, not as clear. */
    private static final float[] PUSH = {0f, 0f, 0f, 0.3f, 0.92f};

    private static float push(float alpha, int level) {
        alpha = Math.min(0.94f, alpha);
        return alpha + (0.94f - alpha) * PUSH[level];
    }

    static float frost(float alpha, int element) {
        int l = glassFrost(element);
        return push(alpha * FROST[l], l);
    }

    /** A tint tuned for the middle frost, scaled to this glass's own. */
    static float frostRel(float alpha, int element) {
        int l = glassFrost(element);
        return push(alpha * FROST[l] / FROST[2], l);
    }

    // MARK: - Photos from the phone (Photos, UploadServer)

    /** The photo (a name from Photos.list) used as the home screen's background, or "" for the default one. */
    static String wallpaper() { return sp().getString("wallpaper", ""); }
    static void setWallpaper(String name) { put("wallpaper", name); }

    /** Seconds between photos: the screensaver, and the background when it rotates through all the photos. */
    static final int[] PHOTO_SECONDS = {30, 60, 300, 900, 1800, 3600};

    /** Seconds between the photos behind the home screen when it shows all of them (its own, apart from the screensaver's). */
    static int backgroundSeconds() { return sp().getInt("backgroundSeconds", 900); }
    static void setBackgroundSeconds(int s) { sp().edit().putInt("backgroundSeconds", s).apply(); version++; }
    static int photoSeconds() { return sp().getInt("photoSeconds", 30); }
    static void setPhotoSeconds(int s) { sp().edit().putInt("photoSeconds", s).apply(); version++; }

    /**
     * How bright the screensaver is: 0 brightest, 1 bright, 2 standard, 3 dark, 4 darkest and most matte. The levels
     * around the standard one are a veil over everything (DreamView.veil).
     */
    static int dreamLevel() {
        if (demo()) return 2;   // screenshots and videos at the standard brightness
        if (sp().contains("dreamLevel5")) return Math.max(0, Math.min(4, sp().getInt("dreamLevel5", 2)));
        // From the six levels (or the first version: none, a little, more): the dim and the dark ones became one.
        int old = sp().contains("dreamLevel") ? sp().getInt("dreamLevel", 2)
                : sp().getInt("dreamDim", 0) == 0 ? 2 : sp().getInt("dreamDim", 0) == 1 ? 3 : 4;
        return old <= 2 ? Math.max(0, old) : old <= 4 ? 3 : 4;
    }
    static void setDreamLevel(int level) { sp().edit().putInt("dreamLevel5", Math.max(0, Math.min(4, level))).apply(); version++; }

    /** The slow zoom and drift of the photos (off: still photos, less for the GPU to do). */
    static boolean dreamMotion() { return sp().getBoolean("dreamMotion", true); }
    static void setDreamMotion(boolean on) { put("dreamMotion", on); }

    /** The greeting at the top of the home screen (off: "For You" there instead). */
    static boolean greeting() { return sp().getBoolean("greeting", true); }
    static void setGreeting(boolean on) { put("greeting", on); }

    /** The screensaver shows the photos from the phone (when there are any) instead of the recommendations. */
    static boolean ownPhotos() { return sp().getBoolean("ownPhotos", true); }
    static void setOwnPhotos(boolean on) { put("ownPhotos", on); }

    // MARK: - Inputs on the home screen

    /** Inputs (HomeApps keys: "live", "hdmi2"...) the person took off the home screen. */
    static boolean inputHidden(String key) {
        return sp().getStringSet("hiddenInputs", java.util.Collections.<String>emptySet()).contains(key);
    }

    static void setInputHidden(String key, boolean hidden) {
        java.util.Set<String> now = new java.util.HashSet<>(sp().getStringSet("hiddenInputs", java.util.Collections.<String>emptySet()));
        if (hidden) now.add(key); else now.remove(key);
        sp().edit().putStringSet("hiddenInputs", now).apply();
        version++;
    }

    static String liveTvAction() { return sp().getString("liveTvAction", "tuner"); }
    static void setLiveTvAction(String action) { put("liveTvAction", action); }

    // MARK: - Shelf

    /** Packages on the home shelf, in order; null until the user edits it (then the built-in list applies). */
    static List<String> shelf() {
        String v = sp().getString("shelf", null);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setShelf(List<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("shelf", sb.toString());
    }

    // MARK: - Demo, options of the screensaver, the clock and the Control Center

    /**
     * For screenshots and videos (adb: `am start -n io.freemytv/.SettingsActivity --ez demo true`): a made-up name, city
     * and no Bluetooth devices, so nothing personal is on the screen. `--ez demo false` brings the real ones back.
     */
    static boolean demo() { return sp().getBoolean("demo", false); }
    static void setDemo(boolean on) { put("demo", on); }

    /** The screensaver shows its photos in a random order instead of the order they came in. */
    static boolean shuffle() { return sp().getBoolean("shuffle", false); }
    static void setShuffle(boolean on) { put("shuffle", on); }

    static boolean dreamClock() { return sp().getBoolean("dreamClock", true); }
    static void setDreamClock(boolean on) { put("dreamClock", on); }

    static boolean dreamDate() { return sp().getBoolean("dreamDate", true); }
    static void setDreamDate(boolean on) { put("dreamDate", on); }

    /** "auto" (the language's own), "12" or "24". */
    static String clockFormat() { return sp().getString("clockFormat", "auto"); }
    static void setClockFormat(String v) { put("clockFormat", v); }

    /** The Control Center's tiles the person turned off (their ids: bluetooth, theme, recs, picture, pair, tv, home, airplay, volume). */
    static boolean ccShown(String id) {
        if (CC_OFF.contains(id)) return sp().getStringSet("ccExtra", new java.util.HashSet<String>()).contains(id);
        return !sp().getStringSet("ccHidden", new java.util.HashSet<String>()).contains(id);
    }

    /** Tiles that are off until the person turns them on (the Control Center keeps its usual look). */
    private static final java.util.Set<String> CC_OFF = new java.util.HashSet<>(java.util.Arrays.asList("saver", "mute", "network"));

    static void setCcShown(String id, boolean shown) {
        String key = CC_OFF.contains(id) ? "ccExtra" : "ccHidden";
        java.util.Set<String> set = new java.util.HashSet<>(sp().getStringSet(key, new java.util.HashSet<String>()));
        if (shown == CC_OFF.contains(id)) set.add(id); else set.remove(id);
        sp().edit().putStringSet(key, set).apply();
        version++;
    }

    // MARK: - Games widget (Sports) and Bluetooth names

    static boolean sportsOn() { return demo() || sp().getBoolean("sportsOn", false); }

    /** The Sports tab before Explore, Movies and Shows (it is the one the home screen opens on), instead of after them. */
    static boolean sportsFirst() { return sp().getBoolean("sportsFirst", false); }
    static void setSportsFirst(boolean on) { sp().edit().putBoolean("sportsFirst", on).putInt("recsTab", 0).apply(); version++; }

    static int sportsPriority() { return sp().getInt("sportsPriority", 0); }
    static void setSportsPriority(int priority) { sp().edit().putInt("sportsPriority", priority).apply(); version++; }

    /** The Sports tab on the home screen (needs the sports widget on). */
    static boolean sportsTab() { return sp().getBoolean("sportsTab", true); }
    static void setSportsTab(boolean on) { put("sportsTab", on); }

    /** The sports card in the drawer; where the tab shows the games already, it starts off. */
    static boolean sportsDrawer() { return sp().contains("sportsDrawer") ? sp().getBoolean("sportsDrawer", true) : !sportsTab(); }
    static void setSportsDrawer(boolean on) { put("sportsDrawer", on); }

    /** The weather card in the drawer (the clock's weather line follows the weather widget). */
    static boolean weatherDrawer() { return sp().getBoolean("weatherDrawer", true); }
    static void setWeatherDrawer(boolean on) { put("weatherDrawer", on); }

    /** The tennis tournaments followed, by level ("slam", "1000", "500"): every match of them shows. The Slams and the 1000s unless set. */
    static boolean tennisLevel(String level) { return sp().getBoolean("tennis_" + level, !level.equals("500")); }
    static void setTennisLevel(String level, boolean on) { put("tennis_" + level, on); version++; }
    static void setSportsOn(boolean on) { put("sportsOn", on); }

    /** The followed teams, as {sport, league, id, name}. */
    static List<String[]> sportsTeams() {
        List<String[]> out = new ArrayList<>();
        if (demo()) {   // the country's own: Brazil, Spain, the United States
            String lang = Ui.language;
            if (lang.startsWith("pt")) out.add(new String[] {"soccer", "bra.1", "819", "Flamengo"});
            else if (lang.startsWith("es")) {
                out.add(new String[] {"soccer", "esp.1", "86", "Real Madrid"});
                out.add(new String[] {"soccer", "esp.1", "83", "FC Barcelona"});
            } else out.add(new String[] {"basketball", "nba", "13", "Los Angeles Lakers"});
            return out;
        }
        for (String line : sp().getString("sportsTeams", "").split("\n")) {
            String[] p = line.split("[|]", 4);
            if (p.length == 4) out.add(p);
        }
        return out;
    }

    static void setSportsTeams(List<String[]> teams) {
        StringBuilder sb = new StringBuilder();
        for (String[] t : teams) sb.append(sb.length() == 0 ? "" : "\n").append(t[0]).append('|').append(t[1]).append('|').append(t[2]).append('|').append(t[3].replace('|', ' '));
        put("sportsTeams", sb.toString());
    }

    /** The followed championships, as ESPN's paths ("soccer/bra.1"). */
    static List<String> sportsLeagues() {
        List<String> out = new ArrayList<>();
        if (demo()) {
            String lang = Ui.language;
            out.add(lang.startsWith("pt") ? "soccer/bra.1" : lang.startsWith("es") ? "soccer/esp.1" : "basketball/nba");
            out.add("tennis/atp");
            return out;
        }
        if (!sp().contains("sportsLeagues")) {
            String lang = Ui.language;
            out.add(lang.startsWith("pt") ? "soccer/bra.1" : lang.startsWith("es") ? "soccer/esp.1" : "basketball/nba");
            return out;
        }
        for (String p : sp().getString("sportsLeagues", "").split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setSportsLeagues(List<String> paths) {
        StringBuilder sb = new StringBuilder();
        for (String p : paths) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("sportsLeagues", sb.toString());
    }

    /** Changes when what the widget follows does (the list is fetched again). */
    static String sportsKey() {
        StringBuilder sb = new StringBuilder();
        for (String[] t : sportsTeams()) sb.append(t[0]).append(t[2]).append(',');
        return sb.append('#').append(sportsLeagues()).append('#').append(tennisLevel("slam")).append(tennisLevel("1000")).append(tennisLevel("500")).toString();
    }

    /** The name the person gave a Bluetooth device (empty: the device's own). */
    static String btAlias(String address) { return sp().getString("btAlias_" + address, ""); }
    static void setBtAlias(String address, String name) { put("btAlias_" + address, name.trim()); }

    /** The order the person gave the Bluetooth devices (their addresses); empty: connected first, then by name. */
    static List<String> btOrder() {
        String s = sp().getString("btOrder", "");
        return s.isEmpty() ? new ArrayList<String>() : new ArrayList<>(java.util.Arrays.asList(s.split(",")));
    }

    static void setBtOrder(List<String> addresses) {
        put("btOrder", String.join(",", addresses));
        version++;
    }

    // MARK: - Top buttons

    /**
     * The round buttons at the top right, in order: "live", "inputs", "control" and/or "settings". null until the user edits
     * them (then HomeApps.TOP_DEFAULT applies); empty when they took all three off.
     */
    static List<String> topButtons() {
        String v = sp().getString("topButtons", null);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setTopButtons(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String p : keys) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("topButtons", sb.toString());
    }

    // MARK: - Remote buttons

    // Buttons the person taught the TV ("Add a button"): any remote, any brand. One line each in custom-keys.txt:
    // keyCode:scanCode=package, or keyCode:scanCode=package|name when the person named it. Both processes read it; the
    // service asks on every key, so it is read again only when the file changes.
    private static File customFile() { return new File(app.getFilesDir(), "custom-keys.txt"); }

    private static List<String[]> customCache = new ArrayList<>();
    private static long customStamp = Long.MIN_VALUE;

    /** {keyCode, scanCode, package, name} for each taught button (the name is empty until the person gives one). */
    static synchronized List<String[]> customKeys() {
        File f = customFile();
        long stamp = f.exists() ? f.lastModified() : 0;
        if (stamp != customStamp) {
            customStamp = stamp;
            List<String[]> out = new ArrayList<>();
            try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    int eq = line.indexOf('='), colon = line.indexOf(':');
                    if (eq > colon && colon > 0) {
                        String rest = line.substring(eq + 1);
                        int bar = rest.indexOf('|');
                        out.add(new String[] {line.substring(0, colon), line.substring(colon + 1, eq), bar < 0 ? rest : rest.substring(0, bar), bar < 0 ? "" : rest.substring(bar + 1)});
                    }
                }
            } catch (IOException ignored) {
            }
            customCache = out;
        }
        return customCache;
    }

    /** The app of a taught button (empty: the key goes on as if we weren't here), or null if the key isn't one of them. */
    static String customKeyApp(int keyCode, int scanCode) {
        for (String[] k : customKeys()) {
            if (!k[0].equals(String.valueOf(keyCode))) continue;
            if (k[1].equals("0") || k[1].equals(String.valueOf(scanCode))) return k[2];
        }
        return null;
    }

    /** Sets what a taught button opens (null: takes the button out); its name, if it has one, stays. */
    static void setCustomKey(int keyCode, int scanCode, String pkg) {
        String name = "";
        List<String[]> all = new ArrayList<>();
        for (String[] k : customKeys()) {
            if (k[0].equals(String.valueOf(keyCode)) && k[1].equals(String.valueOf(scanCode))) name = k[3];
            else all.add(k);
        }
        if (pkg != null) all.add(new String[] {String.valueOf(keyCode), String.valueOf(scanCode), pkg, name});
        saveCustomKeys(all);
    }

    static void setCustomKeyName(int keyCode, int scanCode, String name) {
        List<String[]> all = new ArrayList<>(customKeys());
        for (int i = 0; i < all.size(); i++) {
            String[] k = all.get(i);
            if (k[0].equals(String.valueOf(keyCode)) && k[1].equals(String.valueOf(scanCode))) {
                all.set(i, new String[] {k[0], k[1], k[2], name.replace('|', ' ').replace('\n', ' ').trim()});
            }
        }
        saveCustomKeys(all);
    }

    private static void saveCustomKeys(List<String[]> all) {
        StringBuilder sb = new StringBuilder();
        for (String[] k : all) {
            sb.append(k[0]).append(':').append(k[1]).append('=').append(k[2]);
            if (!k[3].isEmpty()) sb.append('|').append(k[3]);
            sb.append('\n');
        }
        writeFile(customFile(), sb.toString());
        version++;
    }

    /** Every remote button as it was: the brand's buttons open their own apps again, and the taught ones are gone. */
    static void restoreButtons() {
        for (int key : KEYS) setKeyApp(key, defaultApp(key));
        saveCustomKeys(new ArrayList<String[]>());
    }

    private static boolean writeFile(File target, String text) {
        File tmp = new File(app.getFilesDir(), target.getName() + "." + android.os.Process.myPid() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return tmp.renameTo(target);
    }

    private static File learnFile() { return new File(app.getFilesDir(), "learn-on.txt"); }
    private static File learnedFile() { return new File(app.getFilesDir(), "learned.txt"); }

    /** "Add a button": for a minute the key service keeps the next key (any but the arrows, OK and Back) for the settings. */
    static void startLearning() {
        //noinspection ResultOfMethodCallIgnored
        learnedFile().delete();
        writeFile(learnFile(), String.valueOf(System.currentTimeMillis()));
    }

    static void stopLearning() {
        //noinspection ResultOfMethodCallIgnored
        learnFile().delete();
        //noinspection ResultOfMethodCallIgnored
        learnedFile().delete();
    }

    static boolean learning() {
        File f = learnFile();
        return f.exists() && System.currentTimeMillis() - f.lastModified() < 90_000;
    }

    static void setLearned(int keyCode, int scanCode) {
        writeFile(learnedFile(), keyCode + ":" + scanCode);
    }

    /** {keyCode, scanCode} of the key that was pressed, or null yet. */
    static int[] learned() {
        File f = learnedFile();
        if (!f.exists()) return null;
        try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
            String[] p = in.readLine().split(":");
            return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String defaultApp(int keyCode) {
        switch (keyCode) {
            case KEY_NETFLIX: case KEY_STEM_NETFLIX: return "com.netflix.ninja";
            case KEY_YOUTUBE: return "com.google.android.youtube.tv";
            case KEY_PRIME: return "com.amazon.amazonvideo.livingroom";
            case KEY_TCL_CHANNEL: return "";   // TCL's own button: the TV keeps doing what it does
            case KEY_GUIDE: return "";   // the CH button: the TV keeps doing what it does until a live TV app is picked
            default: return "";
        }
    }

    // The key service runs in its own small process (:keys), so that a busy home screen can never delay the TV's keys.
    // SharedPreferences are not shared between processes, so these few values live in a tiny file both processes read.
    // The service asks on every key: the values stay in memory and the file is read again only when it changes.
    private static File keysFile() {
        return new File(app.getFilesDir(), "remote-keys.txt");
    }

    private static final String[] keyApps = new String[KEYS.length];   // null = the default
    private static long keysStamp = Long.MIN_VALUE;

    /** A temporary file per process (two can start together after an update), synced before it replaces the old one. */
    private static boolean writeKeys(String text) {
        File tmp = new File(app.getFilesDir(), "remote-keys." + android.os.Process.myPid() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return tmp.renameTo(keysFile());
    }

    /** Choices saved before they moved to the file (in SharedPreferences) are copied there once. */
    private static void migrateKeys() {
        // Version 2: TCL's own button keeps its original job; the CH button (Guide) is the one for the live TV app.
        if (!sp().getBoolean("keysV2", false)) {
            sp().edit().putBoolean("keysV2", true).apply();
            if (keysFile().exists() && !keyApp(KEY_TCL_CHANNEL).isEmpty()) setKeyApp(KEY_TCL_CHANNEL, "");
        }
        if (keysFile().exists()) return;
        SharedPreferences old = sp();
        boolean any = false;
        for (int key : KEYS) any |= old.contains("key" + key);
        if (!any) return;
        StringBuilder sb = new StringBuilder();
        for (int key : KEYS) sb.append(key).append('=').append(old.getString("key" + key, defaultApp(key))).append('\n');
        writeKeys(sb.toString());
    }

    /** Package the key opens ("" = nothing). */
    static String keyApp(int keyCode) {
        synchronized (keyApps) {
            File f = keysFile();
            long stamp = f.lastModified() * 31 + f.length();   // 0 without the file: every key opens its default
            if (stamp != keysStamp) {
                keysStamp = stamp;
                java.util.Arrays.fill(keyApps, null);
                if (stamp != 0) {
                    try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            for (int i = 0; i < KEYS.length; i++) {
                                String prefix = KEYS[i] + "=";
                                if (line.startsWith(prefix)) keyApps[i] = line.substring(prefix.length());
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
            for (int i = 0; i < KEYS.length; i++) if (KEYS[i] == keyCode && keyApps[i] != null) return keyApps[i];
            return defaultApp(keyCode);
        }
    }

    static void setKeyApp(int keyCode, String pkg) {
        StringBuilder sb = new StringBuilder();
        for (int key : KEYS) sb.append(key).append('=').append(key == keyCode ? pkg : keyApp(key)).append('\n');
        if (writeKeys(sb.toString())) version++;
    }
}
