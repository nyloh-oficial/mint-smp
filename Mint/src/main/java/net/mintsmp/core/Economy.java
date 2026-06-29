package net.mintsmp.core;

import net.mintsmp.MintSMP;
import net.mintsmp.storage.Database;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Economy + stats. The hot path (money/shards/kills/...) is held in memory per
 * online player and flushed to SQLite on autosave / quit / disable, so we never
 * block the main thread on the DB for balance reads.
 *
 * Shards are NOT tradeable between players (no shard /pay). Money is primary,
 * tradeable, and Vault-style but self-contained (no external Vault provider).
 */
public final class Economy {

    private final MintSMP plugin;
    private final Database db;
    private final Map<UUID, Database.PlayerRow> cache = new HashMap<>();

    public Economy(MintSMP plugin, Database db) { this.plugin = plugin; this.db = db; }

    // ---- lifecycle --------------------------------------------------------

    public void load(UUID uuid, String name) {
        Database.PlayerRow row = new Database.PlayerRow();
        db.loadOrCreate(uuid, name, row);
        cache.put(uuid, row);
    }

    public void unload(UUID uuid) {
        Database.PlayerRow row = cache.remove(uuid);
        if (row != null) db.savePlayer(uuid, row);
    }

    public void saveAll() {
        cache.forEach(db::savePlayer);
    }

    public Database.PlayerRow row(UUID uuid) { return cache.get(uuid); }

    // ---- money ------------------------------------------------------------

    public double money(UUID uuid) {
        Database.PlayerRow r = cache.get(uuid);
        return r != null ? r.money : 0;
    }

    public void setMoney(UUID uuid, double v) {
        Database.PlayerRow r = cache.get(uuid);
        if (r != null) r.money = Math.max(0, v);
    }

    public void addMoney(UUID uuid, double v) {
        Database.PlayerRow r = cache.get(uuid);
        if (r != null) r.money = Math.max(0, r.money + v);
    }

    public boolean takeMoney(UUID uuid, double v) {
        Database.PlayerRow r = cache.get(uuid);
        if (r == null || r.money < v) return false;
        r.money -= v;
        return true;
    }

    /** Add money to a player whether online (cache) or offline (DB). */
    public void addMoneyAnywhere(UUID uuid, double v) {
        if (cache.containsKey(uuid)) addMoney(uuid, v);
        else db.addOfflineMoney(uuid, v);
    }

    // ---- shards -----------------------------------------------------------

    public long shards(UUID uuid) {
        Database.PlayerRow r = cache.get(uuid);
        return r != null ? r.shards : 0;
    }

    public void addShards(UUID uuid, long v) {
        Database.PlayerRow r = cache.get(uuid);
        if (r != null) r.shards = Math.max(0, r.shards + v);
    }

    public boolean takeShards(UUID uuid, long v) {
        Database.PlayerRow r = cache.get(uuid);
        if (r == null || r.shards < v) return false;
        r.shards -= v;
        return true;
    }

    // ---- stats ------------------------------------------------------------

    public void addKill(UUID uuid)  { Database.PlayerRow r = cache.get(uuid); if (r != null) r.kills++; }
    public void addDeath(UUID uuid) { Database.PlayerRow r = cache.get(uuid); if (r != null) r.deaths++; }
    public void addPlaytime(UUID uuid, long sec) { Database.PlayerRow r = cache.get(uuid); if (r != null) r.playtime += sec; }
    public void addBlock(UUID uuid) { Database.PlayerRow r = cache.get(uuid); if (r != null) r.blocksMined++; }

    public int kills(UUID uuid)  { Database.PlayerRow r = cache.get(uuid); return r != null ? r.kills : 0; }
    public int deaths(UUID uuid) { Database.PlayerRow r = cache.get(uuid); return r != null ? r.deaths : 0; }
    public long playtime(UUID uuid) { Database.PlayerRow r = cache.get(uuid); return r != null ? r.playtime : 0; }
    public long blocks(UUID uuid)   { Database.PlayerRow r = cache.get(uuid); return r != null ? r.blocksMined : 0; }

    public double kd(UUID uuid) {
        Database.PlayerRow r = cache.get(uuid);
        if (r == null) return 0;
        return r.deaths == 0 ? r.kills : (double) r.kills / r.deaths;
    }

    // ---- baltop (reads DB; flush first for accuracy) ----------------------

    public List<Database.Top> top(String column, int limit) {
        saveAll();
        return db.top(column, limit);
    }

    // ---- sell multiplier --------------------------------------------------

    private final TreeMap<Long, Double> tiers = new TreeMap<>();
    private double minMult = 1.0, maxMult = 3.0;
    private boolean multEnabled = true;

    public void reloadTiers() {
        tiers.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sell-multiplier");
        if (sec == null) return;
        multEnabled = sec.getBoolean("enabled", true);
        minMult = sec.getDouble("min", 1.0);
        maxMult = sec.getDouble("max", 3.0);
        ConfigurationSection t = sec.getConfigurationSection("tiers");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                try { tiers.put(t.getLong(k), Double.parseDouble(k)); }
                catch (NumberFormatException ignored) {}
            }
        }
        if (tiers.isEmpty()) tiers.put(0L, minMult);
    }

    /** Per-item multiplier from cumulative volume sold. */
    public double multiplier(UUID uuid, Material mat) {
        if (!multEnabled) return 1.0;
        long volume = db.getVolume(uuid, mat.name());
        Map.Entry<Long, Double> floor = tiers.floorEntry(volume);
        Map.Entry<Long, Double> ceil = tiers.higherEntry(volume);
        if (floor == null) return minMult;
        if (ceil == null) return Math.min(maxMult, floor.getValue());
        // linear interpolation between tier breakpoints
        double span = ceil.getKey() - floor.getKey();
        double frac = span <= 0 ? 0 : (double) (volume - floor.getKey()) / span;
        double m = floor.getValue() + frac * (ceil.getValue() - floor.getValue());
        return Math.max(minMult, Math.min(maxMult, m));
    }

    public void recordVolume(UUID uuid, Material mat, long qty) {
        db.addVolume(uuid, mat.name(), qty);
    }

    public OfflinePlayer offline(UUID uuid) { return plugin.getServer().getOfflinePlayer(uuid); }
    public Database db() { return db; }
}
