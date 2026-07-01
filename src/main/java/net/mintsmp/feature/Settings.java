package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.storage.Database;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player toggles, stored as "k=1,k2=0" in the player's settings column
 * (persisted via Economy.savePlayer). Keys: pvp, nv (night vision),
 * fall (fall-damage immune), mobs (hostile mobs target you), tpa (accept requests).
 */
public final class Settings {

    private final MintSMP plugin;
    public Settings(MintSMP plugin) { this.plugin = plugin; }

    public static final String PVP = "pvp";
    public static final String NIGHT_VISION = "nv";
    public static final String FALL_IMMUNE = "fall";
    public static final String MOBS = "mobs";
    public static final String TPA = "tpa";
    public static final String TEAM_CHAT = "teamchat";

    private Map<String, Boolean> parse(String raw) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return m;
        for (String part : raw.split(",")) {
            String[] kv = part.split("=");
            if (kv.length == 2) m.put(kv[0].trim(), kv[1].trim().equals("1"));
        }
        return m;
    }

    private String serialize(Map<String, Boolean> m) {
        StringBuilder b = new StringBuilder();
        for (var e : m.entrySet()) {
            if (b.length() > 0) b.append(',');
            b.append(e.getKey()).append('=').append(e.getValue() ? '1' : '0');
        }
        return b.toString();
    }

    public boolean get(Player p, String key, boolean def) {
        Database.PlayerRow row = plugin.economy().row(p.getUniqueId());
        String raw = row != null ? row.settings : plugin.db().settings(p.getUniqueId());
        Map<String, Boolean> m = parse(raw);
        return m.getOrDefault(key, def);
    }

    public boolean getOffline(UUID uuid, String key, boolean def) {
        Database.PlayerRow row = plugin.economy().row(uuid);
        String raw = row != null ? row.settings : plugin.db().settings(uuid);
        return parse(raw).getOrDefault(key, def);
    }

    public void set(Player p, String key, boolean value) {
        Database.PlayerRow row = plugin.economy().row(p.getUniqueId());
        if (row == null) return;
        Map<String, Boolean> m = parse(row.settings);
        m.put(key, value);
        row.settings = serialize(m);
    }

    public boolean toggle(Player p, String key, boolean def) {
        boolean now = !get(p, key, def);
        set(p, key, now);
        return now;
    }

    /** Apply per-tick personal effects (night vision). Called ~1/sec. */
    public void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (get(p, NIGHT_VISION, false)) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.NIGHT_VISION, 260, 0, true, false, false));
            }
        }
    }
}
