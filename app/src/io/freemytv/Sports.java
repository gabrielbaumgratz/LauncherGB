package io.freemytv;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The games widget next to the weather: the next games of the teams and championships the person follows (Settings →
 * Games), from ESPN's public JSON (no key, no account; unofficial, so it can change: when it does, only this file needs a
 * look). The followed teams' games lead the list; the rest follows by date. Refreshed every 3 hours in the background
 * (and at once when the choice changes); the last list is kept on disk, so the widget is never empty on a bad day.
 */
final class Sports {
    static final class Game {
        long when;
        String match = "", tag = "", sport = "soccer";
        boolean live, mine;

        /** "TODAY", "TOMORROW" or "SAT 25/10", in the language now. */
        String day() { return dayText(when); }

        /** The time, in the TV's clock (12 or 24 hours, as set). */
        String time() { return timeText(when); }
    }

    /** A championship the person can follow: ESPN's path (sport/league) and its name. */
    static final String[][] LEAGUES = {
            {"soccer/bra.1", "Brasileirão"}, {"soccer/bra.2", "Série B"}, {"soccer/conmebol.libertadores", "Libertadores"},
            {"soccer/conmebol.sudamericana", "Sudamericana"}, {"soccer/bra.copa_do_brazil", "Copa do Brasil"},
            {"soccer/eng.1", "Premier League"}, {"soccer/esp.1", "LaLiga"}, {"soccer/ita.1", "Serie A"},
            {"soccer/ger.1", "Bundesliga"}, {"soccer/fra.1", "Ligue 1"}, {"soccer/por.1", "Liga Portugal"},
            {"soccer/uefa.champions", "Champions League"}, {"soccer/uefa.europa", "Europa League"}, {"soccer/usa.1", "MLS"},
            {"basketball/nba", "NBA"}, {"basketball/wnba", "WNBA"}, {"football/nfl", "NFL"}, {"baseball/mlb", "MLB"},
            {"hockey/nhl", "NHL"}, {"tennis/atp", "ATP"}, {"tennis/wta", "WTA"},
            {"racing/f1", "Formula 1"}, {"golf/pga", "PGA Tour"}, {"mma/ufc", "UFC"}};

    /** Sports of competitors, not teams: followed by name (a player, a driver, a fighter). */
    static boolean individual(String sport) {
        return sport.equals("tennis") || sport.equals("golf") || sport.equals("racing") || sport.equals("mma");
    }

    /** "soccer" from "soccer/bra.1". */
    static String sportOf(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    /** The sport's name, for the headings of the championships. */
    static String sportName(String sport) {
        switch (sport) {
            case "soccer": return Ui.tr("Soccer");
            case "basketball": return Ui.tr("Basketball");
            case "football": return Ui.tr("American football");
            case "baseball": return Ui.tr("Baseball");
            case "hockey": return Ui.tr("Hockey");
            case "racing": return Ui.tr("Motorsport");
            case "golf": return Ui.tr("Golf");
            case "mma": return Ui.tr("MMA");
            default: return Ui.tr("Tennis");
        }
    }

    /** The sport's symbol (a monochrome PNG in assets/symbols). */
    static String icon(String sport) {
        switch (sport) {
            case "basketball": return "basketball";
            case "football": return "football";
            case "baseball": return "baseball";
            case "hockey": return "hockey";
            case "tennis": return "tennis";
            case "racing": return "motorsports";
            case "golf": return "golf";
            case "mma": return "mma";
            default: return "sports";
        }
    }

    interface Listener { void onSports(); }

    private static final String API = "https://site.api.espn.com/apis/site/v2/sports/";
    private static final long EVERY = 3 * 3600_000L, DAY = 24 * 3600_000L;
    private static final int MAX_BODY = 4_000_000, KEEP = 12;
    /** Where a soccer team can also play besides its own league: the continental and the national cups. */
    private static final String[] SOCCER_CUPS = {"conmebol.libertadores", "conmebol.sudamericana", "uefa.champions", "uefa.europa",
            "bra.copa_do_brazil", "esp.copa_del_rey", "eng.fa", "eng.league_cup", "ger.dfb_pokal", "ita.coppa_italia", "fra.coupe_de_france"};

    private static Sports instance;

    static synchronized Sports get(Context c) {
        if (instance == null) instance = new Sports(c.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile List<Game> games = new ArrayList<>();
    private volatile Listener listener;
    private volatile int revision;
    private long fetchedAt;
    private String fetchedFor = "";
    private boolean fetching;

    private Sports(Context c) {
        context = c;
        games = load();
    }

    List<Game> games() { return games; }

    /** Changes with every new list: the home screen redraws the card when it does. */
    int revision() { return revision; }

    void setListener(Listener l) { listener = l; }

    synchronized void refresh() {
        fetchedAt = 0;
        fetchedFor = "";
        refreshIfStale();
    }

    /** Fetches again when the list is older than 3 hours or the choice changed (in the background; the listener hears). */
    synchronized void refreshIfStale() {
        if (!Prefs.sportsOn()) return;
        final String config = Prefs.sportsKey();
        long now = System.currentTimeMillis();
        if (fetching || (config.equals(fetchedFor) && now - fetchedAt < EVERY)) return;
        fetching = true;
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    List<Game> fresh = fetch();
                    games = fresh;
                    save(fresh);
                    fetchedAt = System.currentTimeMillis();
                    fetchedFor = config;
                    revision++;
                } catch (RuntimeException e) {
                    AppLog.w("sports: " + e.getMessage());
                } finally {
                    synchronized (Sports.this) { fetching = false; }
                    Listener l = listener;
                    if (l != null) l.onSports();
                }
            }
        });
    }

    // MARK: - Searching a team

    /** Teams whose name matches (ESPN's search): {sport, league, id, name}. Slow: call it off the main thread. */
    static List<String[]> findTeam(String name) {
        List<String[]> out = new ArrayList<>();
        try {
            String q = URLEncoder.encode(name.trim(), "UTF-8");
            for (String type : new String[] {"team", "player"}) {   // teams of any sport, and tennis players
                JSONObject json = get("https://site.web.api.espn.com/apis/common/v3/search?limit=12&type=" + type + "&query=" + q);
                JSONArray items = json == null ? null : json.optJSONArray("items");
                for (int i = 0; items != null && i < items.length(); i++) {
                    JSONObject t = items.optJSONObject(i);
                    if (t == null || t.optString("id").isEmpty() || t.optString("sport").isEmpty()) continue;
                    String sport = t.optString("sport");
                    if (type.equals("player") && !individual(sport)) continue;   // a player of a team sport is found through his team
                    String league = t.optString("league");
                    if (sport.equals("mma") && league.isEmpty()) league = "ufc";
                    if (league.isEmpty()) continue;
                    if (t.optString("displayName").matches(".* U[0-9][0-9]")) continue;   // the youth teams
                    out.add(new String[] {sport, league, t.optString("id"), t.optString("displayName")});
                }
            }
        } catch (java.io.UnsupportedEncodingException ignored) {
        }
        return out;
    }

    /** "Flamengo · Futebol (Brasileirão)": a search result, as the list shows it. */
    static String describe(String[] team) {
        String sport = sportName(team[0]);
        String league = leagueName(team[0] + "/" + team[1]);
        if (league.isEmpty() || league.equalsIgnoreCase(sport)) return team[3] + " · " + sport;
        return team[3] + " · " + sport + " (" + league + ")";
    }

    static String addLabel(String sport) {
        if (sport == null) return Ui.tr("Add a team or athlete");
        switch (sport) {
            case "tennis": return Ui.tr("Add a tennis player");
            case "racing": return Ui.tr("Add a driver");
            case "golf": return Ui.tr("Add a golfer");
            case "mma": return Ui.tr("Add a fighter");
            default: return Ui.tr("Add a team");
        }
    }

    static String searchPrompt(String sport) {
        if (sport == null) return Ui.tr("Name of the team or athlete");
        switch (sport) {
            case "tennis": return Ui.tr("Name of the tennis player");
            case "racing": return Ui.tr("Name of the driver");
            case "golf": return Ui.tr("Name of the golfer");
            case "mma": return Ui.tr("Name of the fighter");
            default: return Ui.tr("Name of the team");
        }
    }

    static String leagueName(String path) {
        for (String[] l : LEAGUES) if (l[0].equals(path)) return l[1];
        return path.substring(path.indexOf('/') + 1);
    }

    // MARK: - Fetching

    private List<Game> fetch() {
        List<Game> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long now = System.currentTimeMillis();
        String range = stamp(now) + "-" + stamp(now + 21 * DAY);
        Set<String> players = new HashSet<>();   // the tennis players followed, by name
        java.util.Map<String, Set<String>> athletes = new java.util.HashMap<>();   // the golfers, drivers and fighters followed, by sport
        Set<String> tours = new HashSet<>();
        Set<String> circuits = new java.util.LinkedHashSet<>();   // "golf/pga", "racing/f1", "mma/ufc"
        Set<String> wholeCircuits = new HashSet<>();
        for (String[] team : Prefs.sportsTeams()) {   // {sport, league, id, name}
            if (team[0].equals("tennis")) {
                players.add(team[3]);
                tours.add(team[1]);
                continue;
            }
            if (individual(team[0])) {
                if (!athletes.containsKey(team[0])) athletes.put(team[0], new HashSet<String>());
                athletes.get(team[0]).add(team[3]);
                circuits.add(team[0] + "/" + team[1]);
                continue;
            }
            team(out, seen, team[0], team[1], team[2], team[3]);
            if (team[0].equals("soccer")) for (String cup : SOCCER_CUPS) if (!cup.equals(team[1])) team(out, seen, team[0], cup, team[2], team[3]);
        }
        for (String path : Prefs.sportsLeagues()) {
            if (path.equals("tennis/atp") || path.equals("tennis/wta")) tours.add(path.substring(7));
            else if (individual(sportOf(path))) {
                circuits.add(path);
                wholeCircuits.add(path);
            } else scoreboard(out, seen, path, now);
        }
        String longRange = stamp(now) + "-" + stamp(now + 60 * DAY);
        for (String path : circuits) {
            Set<String> names = athletes.containsKey(sportOf(path)) ? athletes.get(sportOf(path)) : new HashSet<String>();
            events(out, seen, path, longRange, names, wholeCircuits.contains(path));
        }
        for (String tour : tours) {   // the ranking's top, if the championship is followed; the players followed, always
            boolean followed = Prefs.sportsLeagues().contains("tennis/" + tour);
            tennis(out, seen, tour, now, followed ? Prefs.tennisTop(tour) : 0, players);
        }
        List<Game> upcoming = new ArrayList<>();
        for (Game g : out) if (g.when >= now - 3 * 3600_000L) upcoming.add(g);
        final int priority = Prefs.sportsPriority();
        final List<String[]> teamsList = Prefs.sportsTeams();
        final List<String> leaguesList = Prefs.sportsLeagues();
        Collections.sort(upcoming, new Comparator<Game>() {
            @Override public int compare(Game a, Game b) {
                if (a.live != b.live) return a.live ? -1 : 1;
                if (priority == 0) {
                    if (a.mine != b.mine) return a.mine ? -1 : 1;
                    if (a.mine) {
                        int ia = teamIndex(a, teamsList);
                        int ib = teamIndex(b, teamsList);
                        if (ia != ib) return Integer.compare(ia, ib);
                    }
                } else if (priority == 1) {
                    boolean aLeague = isFollowedLeague(a, leaguesList);
                    boolean bLeague = isFollowedLeague(b, leaguesList);
                    if (aLeague != bLeague) return aLeague ? -1 : 1;
                }
                return Long.compare(a.when, b.when);
            }
        });
        List<Game> keep = new ArrayList<>();
        for (Game g : upcoming) {
            if (keep.size() < KEEP) keep.add(g);
        }
        return keep;
    }

    private static int teamIndex(Game g, List<String[]> teams) {
        if (g.match == null) return 999;
        for (int i = 0; i < teams.size(); i++) {
            String name = teams.get(i)[3];
            if (!name.isEmpty() && g.match.contains(name)) return i;
        }
        return 999;
    }

    private static boolean isFollowedLeague(Game g, List<String> leagues) {
        if (g.tag == null) return false;
        for (String l : leagues) {
            String lname = leagueName(l);
            if (!lname.isEmpty() && g.tag.equalsIgnoreCase(lname)) return true;
        }
        return false;
    }

    private void team(List<Game> out, Set<String> seen, String sport, String league, String id, String name) {
        JSONObject json = get(API + sport + "/" + league + "/teams/" + id + "/schedule?fixture=true");
        JSONArray events = json == null ? null : json.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) add(out, seen, events.optJSONObject(i), sport + "/" + league, name);
    }

    /**
     * Golf, Formula 1 and UFC: the events are tournaments, race weekends and fight cards. `all` shows each event; a name
     * followed shows the events it is in (a golfer in the field, a driver in the race) or, in UFC, its bout.
     */
    private void events(List<Game> out, Set<String> seen, String path, String range, Set<String> names, boolean all) {
        JSONObject json = get(API + path + "/scoreboard?dates=" + range + "&limit=100");
        JSONArray events = json == null ? null : json.optJSONArray("events");
        String sport = sportOf(path);
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || stateOf(e.optJSONObject("status")).equals("post")) continue;
            long when = parse(e.optString("date"));
            if (when == 0) continue;
            String title = e.optString("name", e.optString("shortName"));
            boolean live = stateOf(e.optJSONObject("status")).equals("in");
            JSONArray comps = e.optJSONArray("competitions");
            if (sport.equals("mma")) {
                for (int c = 0; comps != null && c < comps.length(); c++) {
                    JSONObject comp = comps.optJSONObject(c);
                    if (comp == null || stateOf(comp.optJSONObject("status")).equals("post")) continue;
                    List<String> fighters = new ArrayList<>();
                    boolean mine = false;
                    JSONArray cs = comp.optJSONArray("competitors");
                    for (int k = 0; cs != null && k < cs.length(); k++) {
                        JSONObject a = cs.optJSONObject(k) == null ? null : cs.optJSONObject(k).optJSONObject("athlete");
                        if (a == null) continue;
                        fighters.add(a.optString("displayName"));
                        mine |= names.contains(a.optString("displayName"));
                    }
                    if (!mine || fighters.size() != 2 || !seen.add("u" + comp.optString("id") + fighters.get(0))) continue;
                    Game g = new Game();
                    g.when = parse(comp.optString("date"));
                    if (g.when == 0) g.when = when;
                    g.match = fighters.get(0) + " × " + fighters.get(1);
                    g.tag = title.contains(":") ? title.substring(0, title.indexOf(':')) : title;
                    g.sport = sport;
                    g.mine = true;
                    g.live = stateOf(comp.optJSONObject("status")).equals("in");
                    out.add(g);
                }
                if (all && seen.add("e" + e.optString("id"))) out.add(eventGame(title, when, path, live, false));
                continue;
            }
            // golf and racing: whoever is followed and in the field
            String who = "";
            JSONObject first = comps == null ? null : comps.optJSONObject(0);
            JSONArray field = first == null ? null : first.optJSONArray("competitors");
            for (int k = 0; field != null && k < field.length() && who.isEmpty(); k++) {
                JSONObject a = field.optJSONObject(k) == null ? null : field.optJSONObject(k).optJSONObject("athlete");
                if (a != null && names.contains(a.optString("displayName"))) who = a.optString("displayName");
            }
            // The grid of a race weekend isn't listed until it's near: a driver followed races them all.
            if (who.isEmpty() && sport.equals("racing") && !names.isEmpty() && (field == null || field.length() == 0)) who = names.iterator().next();
            if (!all && who.isEmpty()) continue;
            if (!seen.add("e" + e.optString("id") + who)) continue;
            out.add(eventGame(who.isEmpty() ? title : who + " · " + title, when, path, live, !who.isEmpty()));
        }
    }

    private static Game eventGame(String match, long when, String path, boolean live, boolean mine) {
        Game g = new Game();
        g.when = when;
        g.match = match;
        g.tag = leagueName(path);
        g.sport = sportOf(path);
        g.live = live;
        g.mine = mine;
        return g;
    }

    private void scoreboard(List<Game> out, Set<String> seen, String path, long now) {
        JSONObject json = get(API + path + "/scoreboard");
        JSONArray events = json == null ? null : json.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) add(out, seen, events.optJSONObject(i), path, null);
        for (int d = 1; d <= 2; d++) {
            JSONObject next = get(API + path + "/scoreboard?dates=" + stamp(now + d * DAY));
            JSONArray nextEvents = next == null ? null : next.optJSONArray("events");
            for (int i = 0; nextEvents != null && i < nextEvents.length(); i++) add(out, seen, nextEvents.optJSONObject(i), path, null);
        }
    }

    /** One event with a pair of teams; `mine` is the followed team's name (the game leads the list), or null. */
    private void add(List<Game> out, Set<String> seen, JSONObject e, String path, String mine) {
        if (e == null) return;
        JSONObject comp = first(e.optJSONArray("competitions"));
        if (comp == null) return;
        String state = stateOf(comp.optJSONObject("status"));
        if (state.equals("post")) return;
        String home = "", away = "";
        JSONArray teams = comp.optJSONArray("competitors");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            JSONObject team = t == null ? null : t.optJSONObject("team");
            if (team == null) continue;
            String name = team.optString("displayName", team.optString("name", ""));
            if ("away".equals(t.optString("homeAway"))) away = name; else home = name;
        }
        if (home.isEmpty() || away.isEmpty()) return;
        String id = e.optString("id");
        if (!seen.add(id.isEmpty() ? home + away : id)) return;
        Game g = new Game();
        g.when = parse(e.optString("date"));
        if (g.when == 0) return;
        g.match = home + " × " + away;
        g.tag = leagueName(path);
        g.sport = sportOf(path);
        g.live = state.equals("in");
        g.mine = mine != null && (home.equals(mine) || away.equals(mine));
        out.add(g);
    }

    private void tennis(List<Game> out, Set<String> seen, String tour, long now, int topN, Set<String> players) {
        JSONObject ranking = get(API + "tennis/" + tour + "/rankings");
        JSONArray blocks = ranking == null ? null : ranking.optJSONArray("rankings");
        JSONObject block = blocks == null ? null : blocks.optJSONObject(0);
        JSONArray ranks = block == null ? null : block.optJSONArray("ranks");
        Set<String> top = new HashSet<>();
        for (int i = 0; ranks != null && i < ranks.length() && i < topN; i++) {
            JSONObject a = ranks.optJSONObject(i) == null ? null : ranks.optJSONObject(i).optJSONObject("athlete");
            if (a != null) top.add(a.optString("displayName"));
        }
        top.addAll(players);
        if (top.isEmpty()) return;
        for (int d = 0; d < 3; d++) {
            JSONObject day = get(API + "tennis/" + tour + "/scoreboard?dates=" + stamp(now + d * DAY));
            JSONArray events = day == null ? null : day.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                JSONArray groups = event == null ? null : event.optJSONArray("groupings");
                for (int j = 0; groups != null && j < groups.length(); j++) {
                    JSONArray matches = groups.optJSONObject(j).optJSONArray("competitions");
                    for (int m = 0; matches != null && m < matches.length(); m++) {
                        match(out, seen, matches.optJSONObject(m), top, players, tour.toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
    }

    private void match(List<Game> out, Set<String> seen, JSONObject comp, Set<String> top, Set<String> players, String tour) {
        if (comp == null) return;
        String state = stateOf(comp.optJSONObject("status"));
        if (state.equals("post")) return;
        JSONArray competitors = comp.optJSONArray("competitors");
        List<String> names = new ArrayList<>();
        boolean star = false, mine = false;
        for (int i = 0; competitors != null && i < competitors.length(); i++) {
            JSONObject p = competitors.optJSONObject(i);
            JSONObject athlete = p == null ? null : p.optJSONObject("athlete");
            if (athlete == null) continue;
            String name = athlete.optString("displayName");
            names.add(name);
            star |= top.contains(name);
            mine |= players.contains(name);
        }
        if (!star || names.size() != 2) return;
        if (!seen.add("t" + comp.optString("id") + names.get(0))) return;
        Game g = new Game();
        g.when = parse(comp.optString("date"));
        if (g.when == 0) return;
        g.match = names.get(0) + " × " + names.get(1);
        g.tag = tour;
        g.sport = "tennis";
        g.mine = mine;
        g.live = state.equals("in");
        out.add(g);
    }

    private static String stateOf(JSONObject status) {
        return status == null || status.optJSONObject("type") == null ? "pre" : status.optJSONObject("type").optString("state", "pre");
    }

    // MARK: - Text

    private static String dayText(long when) {
        long now = System.currentTimeMillis();
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
        String today = dayFormat.format(new Date(now)), that = dayFormat.format(new Date(when));
        if (that.equals(today)) return Ui.tr("TODAY");
        if (that.equals(dayFormat.format(new Date(now + DAY)))) return Ui.tr("TOMORROW");
        return new SimpleDateFormat("EEE dd/MM", Ui.locale()).format(new Date(when)).toUpperCase(Ui.locale()).replace(".", "");
    }

    private static String timeText(long when) {
        String f = Prefs.clockFormat();
        if (f.equals("24")) return new SimpleDateFormat("HH:mm", Ui.locale()).format(new Date(when));
        if (f.equals("12")) return new SimpleDateFormat("h:mm a", Ui.locale()).format(new Date(when));
        return DateFormat.getTimeInstance(DateFormat.SHORT, Ui.locale()).format(new Date(when));
    }

    private static String stamp(long when) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("America/New_York"));   // ESPN's own day
        return f.format(new Date(when));
    }

    private static long parse(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso).getTime();
        } catch (java.text.ParseException | NullPointerException e) {
            return 0;
        }
    }

    private static JSONObject first(JSONArray a) {
        return a == null ? null : a.optJSONObject(0);
    }

    // MARK: - Network and disk

    /** One GET, or null (an answer that isn't JSON, a slow network, a league ESPN doesn't know). */
    private static JSONObject get(String address) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(address).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > MAX_BODY) return null;
                }
                return new JSONObject(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (IOException | JSONException e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private File file() { return new File(context.getFilesDir(), "sports.json"); }

    private void save(List<Game> list) {
        try (FileOutputStream out = new FileOutputStream(file())) {
            JSONArray a = new JSONArray();
            for (Game g : list) a.put(new JSONObject().put("w", g.when).put("m", g.match).put("t", g.tag).put("l", g.live).put("p", g.mine).put("s", g.sport));
            out.write(a.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            AppLog.w("sports not saved: " + e.getMessage());
        }
    }

    private List<Game> load() {
        List<Game> list = new ArrayList<>();
        try (InputStream in = new java.io.FileInputStream(file())) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            JSONArray a = new JSONArray(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            long now = System.currentTimeMillis();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Game g = new Game();
                g.when = o.getLong("w");
                if (g.when < now - 3 * 3600_000L) continue;
                g.match = o.getString("m");
                g.tag = o.getString("t");
                g.live = o.optBoolean("l");
                g.mine = o.optBoolean("p");
                g.sport = o.optString("s", "soccer");
                list.add(g);
            }
        } catch (IOException | JSONException ignored) {
        }
        return list;
    }
}
