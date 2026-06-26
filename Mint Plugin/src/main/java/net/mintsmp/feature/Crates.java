package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import net.mintsmp.util.Keys;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Crates: pick 1 of 7 presented rewards (NO RNG). Hourly keyall. */
public final class Crates {

    private final MintSMP plugin;
    private final Spawners spawners;
    private final Amethyst amethyst;
    private YamlConfiguration cfg;
    private int pickCount = 7;

    public Crates(MintSMP plugin, Spawners spawners, Amethyst amethyst) {
        this.plugin = plugin; this.spawners = spawners; this.amethyst = amethyst;
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "crates.yml");
        if (!f.exists()) plugin.saveResource("crates.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        pickCount = cfg.getInt("crates.pick-count", 7);
    }

    public int pickCount() { return pickCount; }

    public List<String> crateIds() {
        ConfigurationSection sec = cfg.getConfigurationSection("crates.list");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    public boolean exists(String id) { return cfg.getConfigurationSection("crates.list." + id) != null; }

    public String display(String id) { return cfg.getString("crates.list." + id + ".display", "<green>" + id); }

    public int keys(Player p, String id) { return plugin.db().keys(p.getUniqueId(), id); }

    public void giveKey(java.util.UUID uuid, String id, int amt) { plugin.db().addKeys(uuid, id, amt); }

    public List<Map> rewards(String id) {
        List<Map> out = new ArrayList<>();
        List<java.util.Map<?, ?>> list = cfg.getMapList("crates.list." + id + ".rewards");
        for (var m : list) out.add(new Map(m));
        return out;
    }

    /** Reward as shown / granted. */
    public static final class Map {
        public final String type, material, enchants, spawner, amethyst;
        public Map(java.util.Map<?, ?> m) {
            type = str(m, "type");
            material = str(m, "material");
            enchants = str(m, "enchants");
            spawner = str(m, "spawner");
            amethyst = str(m, "amethyst");
        }
        private static String str(java.util.Map<?, ?> m, String k) {
            Object o = m.get(k); return o == null ? null : String.valueOf(o);
        }
    }

    public ItemStack preview(Map r) {
        ItemStack it;
        switch (r.type == null ? "" : r.type) {
            case "spawner" -> it = spawners.isType(r.spawner) ? spawners.createItem(r.spawner) : Items.of(Material.SPAWNER, "<green>Spawner");
            case "amethyst" -> it = amethyst.isType(r.amethyst) ? amethyst.create(r.amethyst) : Items.of(Material.AMETHYST_SHARD, "<aqua>Amethyst Item");
            default -> {
                Material mat = Material.matchMaterial(r.material == null ? "STONE" : r.material);
                if (mat == null) mat = Material.STONE;
                it = new ItemStack(mat);
                if (r.enchants != null) Items.applyEnchants(it, r.enchants);
            }
        }
        return it;
    }

    /** Consume one key and grant reward index. Returns false if no key. */
    public boolean redeem(Player p, String id, int index) {
        if (keys(p, id) <= 0) { Msg.sendRaw(p, "<red>You have no <white>" + id + "</white> keys."); return false; }
        List<Map> rewards = rewards(id);
        if (index < 0 || index >= rewards.size()) return false;
        plugin.db().addKeys(p.getUniqueId(), id, -1);
        ItemStack reward = preview(rewards.get(index));
        for (ItemStack rem : p.getInventory().addItem(reward).values())
            p.getWorld().dropItemNaturally(p.getLocation(), rem);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        Msg.sendRaw(p, "<#A8FF60>You claimed a reward from the " + display(id) + "<#A8FF60>!");
        return true;
    }

    public void keyAll(String id, int amt) {
        for (Player p : Bukkit.getOnlinePlayers()) plugin.db().addKeys(p.getUniqueId(), id, amt);
        Bukkit.broadcast(Msg.mm("<#3DDC84>[Mint] <green>Everyone received <white>" + amt + "x " + id + "</white> key(s)!"));
    }

    public void startKeyAllTask() {
        if (!cfg.getBoolean("crates.keyall.enabled", true)) return;
        int minutes = cfg.getInt("crates.keyall.interval-minutes", 60);
        String crate = cfg.getString("crates.keyall.crate", "COMMON");
        long ticks = minutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> keyAll(crate, 1), ticks, ticks);
    }
}
