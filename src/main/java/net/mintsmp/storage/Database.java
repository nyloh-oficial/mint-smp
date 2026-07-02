package net.mintsmp.storage;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite storage for Mint SMP. Single connection, WAL, prepared statements.
 * Access is synchronized on the connection so concurrent async tasks are safe
 * (simple + correct; fine at this scale). The hot economy path is cached in
 * memory (see core.Economy) and only flushed here.
 */
public final class Database {

    private final MintSMP plugin;
    private Connection conn;

    public Database(MintSMP plugin) { this.plugin = plugin; }

    public void open() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) { throw new SQLException("SQLite driver missing from jar", e); }
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        String fileName = plugin.getConfig().getString("storage.file", "mintsmp.db");
        File db = new File(plugin.getDataFolder(), fileName);
        conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            if (plugin.getConfig().getBoolean("storage.wal", true))
                st.execute("PRAGMA journal_mode = WAL;");
        }
        migrate();
    }

    public void close() {
        if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
    }

    private void migrate() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS players (
                  uuid TEXT PRIMARY KEY, name TEXT NOT NULL,
                  money REAL NOT NULL DEFAULT 0, shards INTEGER NOT NULL DEFAULT 0,
                  kills INTEGER NOT NULL DEFAULT 0, deaths INTEGER NOT NULL DEFAULT 0,
                  playtime INTEGER NOT NULL DEFAULT 0, blocks_mined INTEGER NOT NULL DEFAULT 0,
                  settings TEXT NOT NULL DEFAULT '', first_join INTEGER NOT NULL, last_seen INTEGER NOT NULL);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS homes (
                  uuid TEXT NOT NULL, name TEXT NOT NULL, world TEXT NOT NULL,
                  x REAL, y REAL, z REAL, yaw REAL, pitch REAL,
                  PRIMARY KEY (uuid, name));""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS warps (
                  name TEXT PRIMARY KEY, world TEXT NOT NULL, x REAL, y REAL, z REAL, yaw REAL, pitch REAL);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS sell_mult (
                  uuid TEXT NOT NULL, material TEXT NOT NULL, volume INTEGER NOT NULL DEFAULT 0,
                  PRIMARY KEY (uuid, material));""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS spawners (
                  world TEXT NOT NULL, x INTEGER, y INTEGER, z INTEGER, type TEXT NOT NULL,
                  PRIMARY KEY (world, x, y, z));""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS ah_listings (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, seller_uuid TEXT, seller_name TEXT,
                  item TEXT NOT NULL, price REAL NOT NULL, created_at INTEGER NOT NULL);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS ah_collection (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uuid TEXT, item TEXT NOT NULL,
                  reason TEXT, created_at INTEGER NOT NULL);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS crate_keys (
                  uuid TEXT NOT NULL, crate TEXT NOT NULL, amount INTEGER NOT NULL DEFAULT 0,
                  PRIMARY KEY (uuid, crate));""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS teams (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, owner_uuid TEXT,
                  home_world TEXT, home_x REAL, home_y REAL, home_z REAL, home_yaw REAL, home_pitch REAL);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS team_members (
                  team_id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY (team_id, uuid));""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS ignores (
                  uuid TEXT NOT NULL, ignored TEXT NOT NULL, PRIMARY KEY (uuid, ignored));""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS bounties (
                  target TEXT PRIMARY KEY, amount REAL NOT NULL DEFAULT 0);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, buyer_uuid TEXT, buyer_name TEXT,
                  material TEXT NOT NULL, price_each REAL NOT NULL, quantity INTEGER NOT NULL,
                  escrow REAL NOT NULL, created_at INTEGER NOT NULL);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS cooldowns (
                  uuid TEXT NOT NULL, ckey TEXT NOT NULL, next_epoch INTEGER NOT NULL,
                  PRIMARY KEY (uuid, ckey));""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS pwarps (
                  name TEXT PRIMARY KEY, owner_uuid TEXT, owner_name TEXT,
                  world TEXT NOT NULL, x REAL, y REAL, z REAL, yaw REAL, pitch REAL,
                  created_at INTEGER NOT NULL, visits INTEGER NOT NULL DEFAULT 0);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS traps (
                  world TEXT NOT NULL, x INTEGER, y INTEGER, z INTEGER,
                  placed_by TEXT, created_at INTEGER NOT NULL,
                  PRIMARY KEY (world, x, y, z));""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS mutes (
                  uuid TEXT PRIMARY KEY, until_epoch INTEGER NOT NULL, reason TEXT);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS mythic_supply (
                  type TEXT PRIMARY KEY, minted INTEGER NOT NULL DEFAULT 0);""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS jobs (
                  uuid TEXT PRIMARY KEY, job TEXT NOT NULL DEFAULT 'none');""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS enderchests (
                  uuid TEXT PRIMARY KEY, data TEXT);""");
        }
    }

    // ---- jobs + enderchest ------------------------------------------------

    public synchronized String getJob(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT job FROM jobs WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException ex) { warn("getJob", ex); }
        return "none";
    }

    public synchronized void setJob(UUID uuid, String job) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO jobs(uuid, job) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET job=excluded.job")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("setJob", ex); }
    }

    public synchronized String getEnderchest(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT data FROM enderchests WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException ex) { warn("getEnderchest", ex); }
        return null;
    }

    public synchronized void setEnderchest(UUID uuid, String data) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO enderchests(uuid, data) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET data=excluded.data")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, data);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("setEnderchest", ex); }
    }

    // ---- players ----------------------------------------------------------

    public synchronized void loadOrCreate(UUID uuid, String name, PlayerRow into) {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    into.money = rs.getDouble("money");
                    into.shards = rs.getLong("shards");
                    into.kills = rs.getInt("kills");
                    into.deaths = rs.getInt("deaths");
                    into.playtime = rs.getLong("playtime");
                    into.blocksMined = rs.getLong("blocks_mined");
                    into.settings = rs.getString("settings");
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE players SET name=?, last_seen=? WHERE uuid=?")) {
                        up.setString(1, name); up.setLong(2, now); up.setString(3, uuid.toString());
                        up.executeUpdate();
                    }
                    return;
                }
            }
        } catch (SQLException ex) { warn("loadOrCreate read", ex); }

        into.money = plugin.getConfig().getDouble("economy.starting-money", 0);
        into.shards = plugin.getConfig().getLong("economy.starting-shards", 0);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (uuid,name,money,shards,first_join,last_seen) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString()); ps.setString(2, name);
            ps.setDouble(3, into.money); ps.setLong(4, into.shards);
            ps.setLong(5, now); ps.setLong(6, now);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("loadOrCreate insert", ex); }
    }

    public synchronized void savePlayer(UUID uuid, PlayerRow r) {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE players SET money=?, shards=?, kills=?, deaths=?, playtime=?,
                blocks_mined=?, settings=?, last_seen=? WHERE uuid=?""")) {
            ps.setDouble(1, r.money); ps.setLong(2, r.shards); ps.setInt(3, r.kills);
            ps.setInt(4, r.deaths); ps.setLong(5, r.playtime); ps.setLong(6, r.blocksMined);
            ps.setString(7, r.settings == null ? "" : r.settings);
            ps.setLong(8, System.currentTimeMillis()); ps.setString(9, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ex) { warn("savePlayer", ex); }
    }

    /** Adjust an OFFLINE player's money directly in DB. Returns true if a row was changed. */
    public synchronized boolean addOfflineMoney(UUID uuid, double delta) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET money = MAX(0, money + ?) WHERE uuid=?")) {
            ps.setDouble(1, delta); ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { warn("addOfflineMoney", ex); return false; }
    }

    public synchronized UUID findByName(String name) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM players WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return UUID.fromString(rs.getString(1)); }
        } catch (SQLException ex) { warn("findByName", ex); }
        return null;
    }

    public synchronized long lastSeen(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT last_seen FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getLong(1); }
        } catch (SQLException ex) { warn("lastSeen", ex); }
        return 0;
    }

    public record Top(String name, double value) {}

    public synchronized List<Top> top(String column, int limit) {
        List<Top> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, " + column + " AS v FROM players ORDER BY v DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Top(rs.getString("name"), rs.getDouble("v")));
            }
        } catch (SQLException ex) { warn("top", ex); }
        return out;
    }

    // ---- sell multiplier --------------------------------------------------

    public synchronized long getVolume(UUID uuid, String material) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT volume FROM sell_mult WHERE uuid=? AND material=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, material);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getLong(1); }
        } catch (SQLException ex) { warn("getVolume", ex); }
        return 0;
    }

    public synchronized void addVolume(UUID uuid, String material, long amount) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sell_mult (uuid, material, volume) VALUES (?,?,?)
                ON CONFLICT(uuid, material) DO UPDATE SET volume = volume + excluded.volume""")) {
            ps.setString(1, uuid.toString()); ps.setString(2, material); ps.setLong(3, amount);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("addVolume", ex); }
    }

    public synchronized Map<String, Long> topVolumes(UUID uuid, int limit) {
        Map<String, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT material, volume FROM sell_mult WHERE uuid=? ORDER BY volume DESC LIMIT ?")) {
            ps.setString(1, uuid.toString()); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString("material"), rs.getLong("volume"));
            }
        } catch (SQLException ex) { warn("topVolumes", ex); }
        return out;
    }

    // ---- homes / warps ----------------------------------------------------

    public synchronized void setHome(UUID uuid, String name, Location l) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO homes (uuid,name,world,x,y,z,yaw,pitch) VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(uuid,name) DO UPDATE SET world=excluded.world,x=excluded.x,
                y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch""")) {
            ps.setString(1, uuid.toString()); ps.setString(2, name); ps.setString(3, l.getWorld().getName());
            ps.setDouble(4, l.getX()); ps.setDouble(5, l.getY()); ps.setDouble(6, l.getZ());
            ps.setFloat(7, l.getYaw()); ps.setFloat(8, l.getPitch());
            ps.executeUpdate();
        } catch (SQLException ex) { warn("setHome", ex); }
    }

    public synchronized void delHome(UUID uuid, String name) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, name); ps.executeUpdate();
        } catch (SQLException ex) { warn("delHome", ex); }
    }

    public synchronized Location getHome(UUID uuid, String name) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return loc(rs); }
        } catch (SQLException ex) { warn("getHome", ex); }
        return null;
    }

    public synchronized List<String> homeNames(UUID uuid) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM homes WHERE uuid=? ORDER BY name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getString(1)); }
        } catch (SQLException ex) { warn("homeNames", ex); }
        return out;
    }

    public synchronized int homeCount(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM homes WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) { warn("homeCount", ex); }
        return 0;
    }

    public synchronized void setWarp(String name, Location l) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO warps (name,world,x,y,z,yaw,pitch) VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(name) DO UPDATE SET world=excluded.world,x=excluded.x,y=excluded.y,
                z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch""")) {
            ps.setString(1, name); ps.setString(2, l.getWorld().getName());
            ps.setDouble(3, l.getX()); ps.setDouble(4, l.getY()); ps.setDouble(5, l.getZ());
            ps.setFloat(6, l.getYaw()); ps.setFloat(7, l.getPitch());
            ps.executeUpdate();
        } catch (SQLException ex) { warn("setWarp", ex); }
    }

    public synchronized void delWarp(String name) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM warps WHERE name=?")) {
            ps.setString(1, name); ps.executeUpdate();
        } catch (SQLException ex) { warn("delWarp", ex); }
    }

    public synchronized Location getWarp(String name) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM warps WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return loc(rs); }
        } catch (SQLException ex) { warn("getWarp", ex); }
        return null;
    }

    public synchronized List<String> warpNames() {
        List<String> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT name FROM warps ORDER BY name")) {
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException ex) { warn("warpNames", ex); }
        return out;
    }

    private Location loc(ResultSet rs) throws SQLException {
        org.bukkit.World w = plugin.getServer().getWorld(rs.getString("world"));
        if (w == null) return null;
        return new Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"));
    }

    // ---- spawners ---------------------------------------------------------

    public synchronized void saveSpawner(Location l, String type) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO spawners (world,x,y,z,type) VALUES (?,?,?,?,?)
                ON CONFLICT(world,x,y,z) DO UPDATE SET type=excluded.type""")) {
            ps.setString(1, l.getWorld().getName()); ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY()); ps.setInt(4, l.getBlockZ()); ps.setString(5, type);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("saveSpawner", ex); }
    }

    public synchronized String getSpawner(Location l) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT type FROM spawners WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, l.getWorld().getName()); ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY()); ps.setInt(4, l.getBlockZ());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException ex) { warn("getSpawner", ex); }
        return null;
    }

    public synchronized void removeSpawner(Location l) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM spawners WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, l.getWorld().getName()); ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY()); ps.setInt(4, l.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException ex) { warn("removeSpawner", ex); }
    }

    // ---- crate keys -------------------------------------------------------

    public synchronized int keys(UUID uuid, String crate) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT amount FROM crate_keys WHERE uuid=? AND crate=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, crate);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) { warn("keys", ex); }
        return 0;
    }

    public synchronized void addKeys(UUID uuid, String crate, int amount) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO crate_keys (uuid,crate,amount) VALUES (?,?,?)
                ON CONFLICT(uuid,crate) DO UPDATE SET amount = MAX(0, amount + excluded.amount)""")) {
            ps.setString(1, uuid.toString()); ps.setString(2, crate); ps.setInt(3, amount);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("addKeys", ex); }
    }

    // ---- auction house ----------------------------------------------------

    public record Listing(long id, String sellerName, UUID seller, ItemStack item, double price, long created) {}

    public synchronized long ahCreate(UUID seller, String name, ItemStack item, double price) {
        String enc = Items.encode(item);
        if (enc == null) return -1;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ah_listings (seller_uuid,seller_name,item,price,created_at) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, seller.toString()); ps.setString(2, name);
            ps.setString(3, enc); ps.setDouble(4, price); ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        } catch (SQLException ex) { warn("ahCreate", ex); }
        return -1;
    }

    public synchronized int ahCount(UUID seller) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM ah_listings WHERE seller_uuid=?")) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) { warn("ahCount", ex); }
        return 0;
    }

    public synchronized List<Listing> ahList(String search) {
        List<Listing> out = new ArrayList<>();
        String sql = "SELECT * FROM ah_listings ORDER BY created_at DESC LIMIT 500";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemStack item = Items.decode(rs.getString("item"));
                if (item == null) continue;
                if (search != null && !search.isBlank()) {
                    String label = item.getType().name().toLowerCase();
                    if (!label.contains(search.toLowerCase())) continue;
                }
                out.add(new Listing(rs.getLong("id"), rs.getString("seller_name"),
                        uuidSafe(rs.getString("seller_uuid")), item, rs.getDouble("price"), rs.getLong("created_at")));
            }
        } catch (SQLException ex) { warn("ahList", ex); }
        return out;
    }

    public synchronized Listing ahGet(long id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ah_listings WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ItemStack item = Items.decode(rs.getString("item"));
                    if (item == null) return null;
                    return new Listing(rs.getLong("id"), rs.getString("seller_name"),
                            uuidSafe(rs.getString("seller_uuid")), item, rs.getDouble("price"), rs.getLong("created_at"));
                }
            }
        } catch (SQLException ex) { warn("ahGet", ex); }
        return null;
    }

    public synchronized boolean ahDelete(long id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ah_listings WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        } catch (SQLException ex) { warn("ahDelete", ex); return false; }
    }

    public synchronized void collectionAdd(UUID owner, ItemStack item, String reason) {
        String enc = Items.encode(item);
        if (enc == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ah_collection (owner_uuid,item,reason,created_at) VALUES (?,?,?,?)")) {
            ps.setString(1, owner.toString()); ps.setString(2, enc);
            ps.setString(3, reason); ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ex) { warn("collectionAdd", ex); }
    }

    public synchronized List<long[]> collectionIds(UUID owner) { // returns [id]
        List<long[]> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM ah_collection WHERE owner_uuid=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(new long[]{rs.getLong(1)}); }
        } catch (SQLException ex) { warn("collectionIds", ex); }
        return out;
    }

    public synchronized List<Object[]> collection(UUID owner) { // [id(long), ItemStack]
        List<Object[]> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id,item FROM ah_collection WHERE owner_uuid=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack it = Items.decode(rs.getString("item"));
                    if (it != null) out.add(new Object[]{rs.getLong("id"), it});
                }
            }
        } catch (SQLException ex) { warn("collection", ex); }
        return out;
    }

    public synchronized boolean collectionRemove(long id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ah_collection WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        } catch (SQLException ex) { warn("collectionRemove", ex); return false; }
    }

    // ---- ignores ----------------------------------------------------------

    public synchronized boolean isIgnoring(UUID uuid, UUID other) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM ignores WHERE uuid=? AND ignored=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, other.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { warn("isIgnoring", ex); return false; }
    }

    public synchronized void setIgnore(UUID uuid, UUID other, boolean on) {
        try (PreparedStatement ps = on
                ? conn.prepareStatement("INSERT OR IGNORE INTO ignores (uuid,ignored) VALUES (?,?)")
                : conn.prepareStatement("DELETE FROM ignores WHERE uuid=? AND ignored=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, other.toString()); ps.executeUpdate();
        } catch (SQLException ex) { warn("setIgnore", ex); }
    }

    public synchronized List<String> ignoreList(UUID uuid) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT ignored FROM ignores WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getString(1)); }
        } catch (SQLException ex) { warn("ignoreList", ex); }
        return out;
    }

    // ---- bounties ---------------------------------------------------------

    public synchronized double bounty(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT amount FROM bounties WHERE target=?")) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getDouble(1); }
        } catch (SQLException ex) { warn("bounty", ex); }
        return 0;
    }

    public synchronized void addBounty(UUID target, double delta) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO bounties (target,amount) VALUES (?,?)
                ON CONFLICT(target) DO UPDATE SET amount = MAX(0, amount + excluded.amount)""")) {
            ps.setString(1, target.toString()); ps.setDouble(2, delta); ps.executeUpdate();
        } catch (SQLException ex) { warn("addBounty", ex); }
    }

    public synchronized void clearBounty(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bounties WHERE target=?")) {
            ps.setString(1, target.toString()); ps.executeUpdate();
        } catch (SQLException ex) { warn("clearBounty", ex); }
    }

    // ---- teams ------------------------------------------------------------

    public synchronized long teamCreate(String name, UUID owner) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teams (name,owner_uuid) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, owner.toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        } catch (SQLException ex) { return -1; }
        return -1;
    }

    public synchronized void teamAddMember(long teamId, UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO team_members (team_id,uuid) VALUES (?,?)")) {
            ps.setLong(1, teamId); ps.setString(2, uuid.toString()); ps.executeUpdate();
        } catch (SQLException ex) { warn("teamAddMember", ex); }
    }

    public synchronized void teamRemoveMember(long teamId, UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM team_members WHERE team_id=? AND uuid=?")) {
            ps.setLong(1, teamId); ps.setString(2, uuid.toString()); ps.executeUpdate();
        } catch (SQLException ex) { warn("teamRemoveMember", ex); }
    }

    public synchronized Long teamOf(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT team_id FROM team_members WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getLong(1); }
        } catch (SQLException ex) { warn("teamOf", ex); }
        return null;
    }

    public synchronized String teamName(long id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM teams WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException ex) { warn("teamName", ex); }
        return null;
    }

    public synchronized UUID teamOwner(long id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT owner_uuid FROM teams WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return uuidSafe(rs.getString(1)); }
        } catch (SQLException ex) { warn("teamOwner", ex); }
        return null;
    }

    public synchronized Long teamByName(String name) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM teams WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getLong(1); }
        } catch (SQLException ex) { warn("teamByName", ex); }
        return null;
    }

    public synchronized List<UUID> teamMembers(long id) {
        List<UUID> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM team_members WHERE team_id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(uuidSafe(rs.getString(1))); }
        } catch (SQLException ex) { warn("teamMembers", ex); }
        return out;
    }

    public synchronized void teamSetHome(long id, Location l) {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE teams SET home_world=?, home_x=?, home_y=?, home_z=?, home_yaw=?, home_pitch=? WHERE id=?""")) {
            ps.setString(1, l.getWorld().getName()); ps.setDouble(2, l.getX()); ps.setDouble(3, l.getY());
            ps.setDouble(4, l.getZ()); ps.setFloat(5, l.getYaw()); ps.setFloat(6, l.getPitch()); ps.setLong(7, id);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("teamSetHome", ex); }
    }

    public synchronized Location teamHome(long id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM teams WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString("home_world") != null) {
                    org.bukkit.World w = plugin.getServer().getWorld(rs.getString("home_world"));
                    if (w == null) return null;
                    return new Location(w, rs.getDouble("home_x"), rs.getDouble("home_y"),
                            rs.getDouble("home_z"), rs.getFloat("home_yaw"), rs.getFloat("home_pitch"));
                }
            }
        } catch (SQLException ex) { warn("teamHome", ex); }
        return null;
    }

    public synchronized void teamDelete(long id) {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM team_members WHERE team_id=" + id);
            st.execute("DELETE FROM teams WHERE id=" + id);
        } catch (SQLException ex) { warn("teamDelete", ex); }
    }

    // ---- orders (buy orders) ---------------------------------------------

    public record Order(long id, UUID buyer, String buyerName, String material,
                        double priceEach, int quantity, double escrow, long created) {}

    public synchronized long ordersCreate(UUID buyer, String name, String material, double priceEach, int qty, double escrow) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO orders (buyer_uuid,buyer_name,material,price_each,quantity,escrow,created_at) VALUES (?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, buyer.toString()); ps.setString(2, name); ps.setString(3, material);
            ps.setDouble(4, priceEach); ps.setInt(5, qty); ps.setDouble(6, escrow);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        } catch (SQLException ex) { warn("ordersCreate", ex); }
        return -1;
    }

    public synchronized int ordersCount(UUID buyer) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM orders WHERE buyer_uuid=?")) {
            ps.setString(1, buyer.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) { warn("ordersCount", ex); }
        return 0;
    }

    public synchronized List<Order> ordersList(String materialFilter) {
        List<Order> out = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE quantity > 0 ORDER BY price_each DESC LIMIT 500";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String mat = rs.getString("material");
                if (materialFilter != null && !mat.equalsIgnoreCase(materialFilter)) continue;
                out.add(readOrder(rs));
            }
        } catch (SQLException ex) { warn("ordersList", ex); }
        return out;
    }

    public synchronized Order ordersGet(long id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return readOrder(rs); }
        } catch (SQLException ex) { warn("ordersGet", ex); }
        return null;
    }

    private Order readOrder(ResultSet rs) throws SQLException {
        return new Order(rs.getLong("id"), uuidSafe(rs.getString("buyer_uuid")), rs.getString("buyer_name"),
                rs.getString("material"), rs.getDouble("price_each"), rs.getInt("quantity"),
                rs.getDouble("escrow"), rs.getLong("created_at"));
    }

    /** Reduce an order by `amount` units; deletes it when it hits zero. Returns true on success. */
    public synchronized boolean ordersFulfill(long id, int amount, double escrowSpent) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET quantity = quantity - ?, escrow = MAX(0, escrow - ?) WHERE id=? AND quantity >= ?")) {
            ps.setInt(1, amount); ps.setDouble(2, escrowSpent); ps.setLong(3, id); ps.setInt(4, amount);
            if (ps.executeUpdate() == 0) return false;
        } catch (SQLException ex) { warn("ordersFulfill", ex); return false; }
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM orders WHERE id=? AND quantity <= 0")) {
            del.setLong(1, id); del.executeUpdate();
        } catch (SQLException ex) { warn("ordersFulfill cleanup", ex); }
        return true;
    }

    public synchronized boolean ordersDelete(long id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM orders WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        } catch (SQLException ex) { warn("ordersDelete", ex); return false; }
    }

    // ---- cooldowns (kits, daily, etc.) ------------------------------------

    /** Remaining cooldown in milliseconds (0 if ready). */
    public synchronized long cooldownRemaining(UUID uuid, String key) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT next_epoch FROM cooldowns WHERE uuid=? AND ckey=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0, rs.getLong(1) - System.currentTimeMillis());
            }
        } catch (SQLException ex) { warn("cooldownRemaining", ex); }
        return 0;
    }

    public synchronized void setCooldown(UUID uuid, String key, long untilEpoch) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO cooldowns (uuid,ckey,next_epoch) VALUES (?,?,?)
                ON CONFLICT(uuid,ckey) DO UPDATE SET next_epoch=excluded.next_epoch""")) {
            ps.setString(1, uuid.toString()); ps.setString(2, key); ps.setLong(3, untilEpoch);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("setCooldown", ex); }
    }

    // ---- player warps -----------------------------------------------------

    public record PWarp(String name, UUID owner, String ownerName, Location loc, long created, int visits) {}

    public synchronized boolean pwarpCreate(String name, UUID owner, String ownerName, Location l) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO pwarps (name,owner_uuid,owner_name,world,x,y,z,yaw,pitch,created_at,visits) VALUES (?,?,?,?,?,?,?,?,?,?,0)")) {
            ps.setString(1, name); ps.setString(2, owner.toString()); ps.setString(3, ownerName);
            ps.setString(4, l.getWorld().getName()); ps.setDouble(5, l.getX()); ps.setDouble(6, l.getY());
            ps.setDouble(7, l.getZ()); ps.setFloat(8, l.getYaw()); ps.setFloat(9, l.getPitch());
            ps.setLong(10, System.currentTimeMillis());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    public synchronized PWarp pwarpGet(String name) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM pwarps WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return readPwarp(rs); }
        } catch (SQLException ex) { warn("pwarpGet", ex); }
        return null;
    }

    public synchronized List<PWarp> pwarpList() {
        List<PWarp> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM pwarps ORDER BY visits DESC, name LIMIT 500")) {
            while (rs.next()) { PWarp w = readPwarp(rs); if (w != null) out.add(w); }
        } catch (SQLException ex) { warn("pwarpList", ex); }
        return out;
    }

    public synchronized int pwarpCount(UUID owner) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM pwarps WHERE owner_uuid=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) { warn("pwarpCount", ex); }
        return 0;
    }

    public synchronized boolean pwarpDelete(String name) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pwarps WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name); return ps.executeUpdate() > 0;
        } catch (SQLException ex) { warn("pwarpDelete", ex); return false; }
    }

    public synchronized void pwarpVisit(String name) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE pwarps SET visits = visits + 1 WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name); ps.executeUpdate();
        } catch (SQLException ex) { warn("pwarpVisit", ex); }
    }

    private PWarp readPwarp(ResultSet rs) throws SQLException {
        org.bukkit.World w = plugin.getServer().getWorld(rs.getString("world"));
        if (w == null) return null;
        Location l = new Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"));
        return new PWarp(rs.getString("name"), uuidSafe(rs.getString("owner_uuid")),
                rs.getString("owner_name"), l, rs.getLong("created_at"), rs.getInt("visits"));
    }

    // ---- staff traps + mutes ---------------------------------------------

    public synchronized void addTrap(Location l, UUID by) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO traps (world,x,y,z,placed_by,created_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, l.getWorld().getName()); ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY()); ps.setInt(4, l.getBlockZ());
            ps.setString(5, by == null ? null : by.toString()); ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ex) { warn("addTrap", ex); }
    }

    public synchronized boolean isTrap(Location l) {
        if (l == null || l.getWorld() == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM traps WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, l.getWorld().getName()); ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY()); ps.setInt(4, l.getBlockZ());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { warn("isTrap", ex); return false; }
    }

    public synchronized void removeTrap(Location l) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM traps WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, l.getWorld().getName()); ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY()); ps.setInt(4, l.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException ex) { warn("removeTrap", ex); }
    }

    public synchronized int trapCount() {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM traps")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ex) { warn("trapCount", ex); }
        return 0;
    }

    public synchronized void setMute(UUID uuid, long untilEpoch, String reason) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO mutes (uuid,until_epoch,reason) VALUES (?,?,?)")) {
            ps.setString(1, uuid.toString()); ps.setLong(2, untilEpoch); ps.setString(3, reason);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("setMute", ex); }
    }

    /** Remaining mute in ms (0 = not muted). */
    public synchronized long muteRemaining(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT until_epoch FROM mutes WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0, rs.getLong(1) - System.currentTimeMillis());
            }
        } catch (SQLException ex) { warn("muteRemaining", ex); }
        return 0;
    }

    public synchronized void clearMute(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM mutes WHERE uuid=?")) {
            ps.setString(1, uuid.toString()); ps.executeUpdate();
        } catch (SQLException ex) { warn("clearMute", ex); }
    }

    // ---- mythic supply (limited edition) ---------------------------------

    /** Reset all mythic supply so every type is fully available again. */
    public synchronized void resetMythicSupply() {
        try (Statement st = conn.createStatement()) { st.execute("DELETE FROM mythic_supply"); }
        catch (SQLException ex) { warn("resetMythicSupply", ex); }
    }

    public synchronized int mythicMinted(String type) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT minted FROM mythic_supply WHERE type=?")) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) { warn("mythicMinted", ex); }
        return 0;
    }

    /** Atomically mint one if under cap. Returns serial (1..cap) or -1 if sold out. */
    public synchronized int mythicTryMint(String type, int cap) {
        int minted = mythicMinted(type);
        if (minted >= cap) return -1;
        int serial = minted + 1;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO mythic_supply (type, minted) VALUES (?, 1)
                ON CONFLICT(type) DO UPDATE SET minted = minted + 1""")) {
            ps.setString(1, type);
            ps.executeUpdate();
        } catch (SQLException ex) { warn("mythicTryMint", ex); return -1; }
        return serial;
    }

    // ---- wipes ------------------------------------------------------------

    /** Wipe one player's MintSMP data across all tables. */
    public synchronized void wipePlayer(UUID uuid) {
        String u = uuid.toString();
        String[] byUuid = {"players", "homes", "sell_mult", "crate_keys", "cooldowns", "ignores", "team_members", "jobs", "enderchests"};
        try (Statement st = conn.createStatement()) {
            for (String t : byUuid) st.execute("DELETE FROM " + t + " WHERE uuid='" + u + "'");
            st.execute("DELETE FROM bounties WHERE target='" + u + "'");
            st.execute("DELETE FROM mutes WHERE uuid='" + u + "'");
        } catch (SQLException ex) { warn("wipePlayer", ex); }
    }

    /** Wipe ALL player/economy data. Keeps mythic_supply unless full=true. */
    public synchronized void wipeAll(boolean includeMythicSupply) {
        String[] tables = {"players", "homes", "warps", "sell_mult", "spawners", "ah_listings",
                "ah_collection", "crate_keys", "teams", "team_members", "ignores", "bounties",
                "cooldowns", "pwarps", "traps", "mutes", "orders", "jobs", "enderchests"};
        try (Statement st = conn.createStatement()) {
            for (String t : tables) st.execute("DELETE FROM " + t);
            if (includeMythicSupply) st.execute("DELETE FROM mythic_supply");
        } catch (SQLException ex) { warn("wipeAll", ex); }
    }

    public synchronized String settings(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT settings FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException ex) { warn("settings", ex); }
        return "";
    }

    // ---- util -------------------------------------------------------------

    private UUID uuidSafe(String s) { try { return UUID.fromString(s); } catch (Exception e) { return null; } }
    private void warn(String where, Exception ex) { plugin.getLogger().warning("[DB] " + where + ": " + ex.getMessage()); }

    /** Mutable struct used to load/save a player's hot fields. */
    public static final class PlayerRow {
        public double money;
        public long shards, playtime, blocksMined;
        public int kills, deaths;
        public String settings = "";
    }
}
