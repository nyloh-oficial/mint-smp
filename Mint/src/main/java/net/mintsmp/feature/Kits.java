package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Kits with persistent (DB-backed) cooldowns. Kits are equal for all players. */
public final class Kits {

    private final MintSMP plugin;
    private YamlConfiguration cfg;
    private long defaultCooldown = 86400;

    public Kits(MintSMP plugin) { this.plugin = plugin; }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "kits.yml");
        if (!f.exists()) plugin.saveResource("kits.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        defaultCooldown = cfg.getLong("kits.default-cooldown-seconds", 86400);
    }

    public List<String> kitIds() {
        ConfigurationSection sec = cfg.getConfigurationSection("kits.list");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    public boolean exists(String id) { return cfg.getConfigurationSection("kits.list." + id) != null; }

    public String display(String id) { return cfg.getString("kits.list." + id + ".display", "<green>" + id); }

    private String permission(String id) { return cfg.getString("kits.list." + id + ".permission", null); }

    public boolean canUse(Player p, String id) {
        String perm = permission(id);
        return perm == null || p.hasPermission(perm);
    }

    public void give(Player p, String id) {
        if (!exists(id)) { Msg.sendRaw(p, "<red>Unknown kit. <white>/kit"); return; }
        if (!canUse(p, id)) { Msg.sendRaw(p, "<red>You don't have access to that kit."); return; }
        String key = "kit:" + id;
        long remaining = plugin.db().cooldownRemaining(p.getUniqueId(), key);
        if (remaining > 0) { Msg.sendRaw(p, "<red>Kit on cooldown: <white>" + human(remaining)); return; }

        List<Map<?, ?>> items = cfg.getMapList("kits.list." + id + ".items");
        int given = 0;
        for (Map<?, ?> m : items) {
            try {
                Material mat = Material.valueOf(String.valueOf(m.get("material")));
                int amt = m.get("amount") == null ? 1 : ((Number) m.get("amount")).intValue();
                ItemStack it = new ItemStack(mat, Math.max(1, amt));
                Object ench = m.get("enchants");
                if (ench != null) Items.applyEnchants(it, String.valueOf(ench));
                for (ItemStack rem : p.getInventory().addItem(it).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                given++;
            } catch (Exception ex) {
                plugin.getLogger().warning("kits.yml: bad item in kit '" + id + "'");
            }
        }
        if (given == 0) { Msg.sendRaw(p, "<red>That kit is empty."); return; }
        long cd = cfg.getLong("kits.list." + id + ".cooldown-seconds", defaultCooldown);
        plugin.db().setCooldown(p.getUniqueId(), key, System.currentTimeMillis() + cd * 1000L);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        Msg.sendRaw(p, "<green>You claimed the " + display(id) + "<green>!");
    }

    public void list(Player p) {
        Msg.sendRaw(p, "<gradient:#3DDC84:#1B5E20><bold>Kits</bold></gradient>");
        for (String id : kitIds()) {
            if (!canUse(p, id)) continue;
            long remaining = plugin.db().cooldownRemaining(p.getUniqueId(), "kit:" + id);
            String state = remaining > 0 ? "<red>" + human(remaining) : "<green>ready";
            Msg.sendRaw(p, "<gray>- " + display(id) + " <dark_gray>(" + state + "<dark_gray>) <white>/kit " + id);
        }
    }

    public static String human(long ms) {
        long s = ms / 1000;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder b = new StringBuilder();
        if (h > 0) b.append(h).append("h ");
        if (m > 0) b.append(m).append("m ");
        b.append(s).append("s");
        return b.toString().trim();
    }
}
