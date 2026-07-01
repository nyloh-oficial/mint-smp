package net.mintsmp;

import net.mintsmp.command.Router;
import net.mintsmp.core.Economy;
import net.mintsmp.core.Prices;
import net.mintsmp.feature.Afk;
import net.mintsmp.feature.Amethyst;
import net.mintsmp.feature.AntiSpam;
import net.mintsmp.feature.BigEnderChest;
import net.mintsmp.feature.Combat;
import net.mintsmp.feature.Crates;
import net.mintsmp.feature.Display;
import net.mintsmp.feature.Duels;
import net.mintsmp.feature.Extras;
import net.mintsmp.feature.Jobs;
import net.mintsmp.feature.Kits;
import net.mintsmp.feature.Minerals;
import net.mintsmp.feature.Mythic;
import net.mintsmp.feature.Orders;
import net.mintsmp.feature.PlayerWarps;
import net.mintsmp.feature.Ranks;
import net.mintsmp.feature.Settings;
import net.mintsmp.feature.SpawnerStack;
import net.mintsmp.feature.Spawners;
import net.mintsmp.feature.Staff;
import net.mintsmp.feature.Teams;
import net.mintsmp.feature.Teleport;
import net.mintsmp.listener.MintListener;
import net.mintsmp.papi.MintExpansion;
import net.mintsmp.storage.Database;
import net.mintsmp.util.Keys;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MintSMP extends JavaPlugin {

    private Database db;
    private Economy economy;
    private Prices prices;
    private Spawners spawners;
    private Amethyst amethyst;
    private Crates crates;
    private Combat combat;
    private Teleport teleport;
    private Afk afk;
    private Display display;
    private Teams teams;
    private Duels duels;
    private Orders orders;
    private Kits kits;
    private PlayerWarps playerWarps;
    private Extras extras;
    private Staff staff;
    private Mythic mythic;
    private Settings settings;
    private Minerals minerals;
    private AntiSpam antiSpam;
    private SpawnerStack spawnerStack;
    private Ranks ranks;
    private Jobs jobs;
    private BigEnderChest enderChest;
    private YamlConfiguration shopConfig;
    private String shopTitle;

    private static final String[] COMMANDS = {
        "mintsmp","balance","pay","baltop","shards","eco","sell","worth","sellmulti","sellboost",
        "shop","ah","crates","key","keyall","spawner","amethyst","sethome","home","delhome","homes",
        "tpa","tpahere","tpaccept","tpdeny","rtp","spawn","setspawn","warp","setwarp","delwarp",
        "bounty","afk","stats","killtop","playtimetop","blockstop","shardstop","leaderboard","scoreboard",
        "msg","tell","r","ignore","unignore","ignorelist","msgtoggle","near","list","ping","seen",
        "findplayer","rules","report","discord","store","website","settings",
        "team","duel","orders",
        "kit","kits","pwarp","back","daily","ec","craft","anvil","grindstone",
        "cartography","loom","smithing","stonecutter","trash","heal","feed","fly",
        "repair","god","hat","nick",
        "quickbuy","trap","ban","tempban","unban","kick","mute","unmute","mythic",
        "wipeplayer","wipeall","nightvision","mint","rank","jobs","spawnstash","accuse"
    };

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("prices.yml");
        saveResourceIfMissing("shop.yml");
        saveResourceIfMissing("spawners.yml");
        saveResourceIfMissing("crates.yml");
        saveResourceIfMissing("amethyst.yml");
        saveResourceIfMissing("kits.yml");
        saveResourceIfMissing("mythic.yml");

        Keys.init(this);
        Msg.load(this);

        db = new Database(this);
        try {
            db.open();
        } catch (Exception ex) {
            getLogger().severe("Could not open database: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy  = new Economy(this, db);
        prices   = new Prices(this, economy);
        spawners = new Spawners(this);
        amethyst = new Amethyst(this, economy);
        crates   = new Crates(this, spawners, amethyst);
        combat   = new Combat(this, economy);
        teleport = new Teleport(this, combat);
        afk      = new Afk(this);
        display  = new Display(this, economy, afk);
        teams    = new Teams(this, teleport);
        duels    = new Duels(this);
        orders   = new Orders(this, economy);
        kits     = new Kits(this);
        playerWarps = new PlayerWarps(this, economy, teleport);
        extras   = new Extras(this, economy);
        staff    = new Staff(this);
        mythic   = new Mythic(this, economy);
        settings = new Settings(this);
        minerals = new Minerals(this);
        antiSpam = new AntiSpam(this);
        spawnerStack = new SpawnerStack(this);
        ranks    = new Ranks(this);
        jobs     = new Jobs(this);
        enderChest = new BigEnderChest(this);

        reloadAll();

        // Commands
        Router router = new Router(this);
        for (String name : COMMANDS) {
            var cmd = getCommand(name);
            if (cmd != null) { cmd.setExecutor(router); cmd.setTabCompleter(router); }
            else getLogger().warning("Command '" + name + "' missing from plugin.yml");
        }

        // Listeners
        getServer().getPluginManager().registerEvents(new MintListener(this), this);
        getServer().getPluginManager().registerEvents(new net.mintsmp.listener.ServerListener(this), this);
        getServer().getPluginManager().registerEvents(new net.mintsmp.listener.MythicListener(this), this);
        getServer().getPluginManager().registerEvents(new net.mintsmp.listener.ExtraListener(this), this);
        getServer().getPluginManager().registerEvents(new net.mintsmp.listener.SpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new net.mintsmp.listener.ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new net.mintsmp.listener.JobsListener(this), this);

        // Mint Ore generation on the overworld (applies to newly generated chunks).
        try {
            for (World w : Bukkit.getWorlds())
                if (w.getEnvironment() == World.Environment.NORMAL)
                    w.getPopulators().add(new net.mintsmp.feature.MintOrePopulator(this));
        } catch (Throwable t) { getLogger().warning("Mint Ore populator failed to register: " + t.getMessage()); }

        // Optional PlaceholderAPI hook
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new MintExpansion(this).register();
                getLogger().info("Hooked into PlaceholderAPI (%mintsmp_*%).");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI hook failed: " + t.getMessage());
            }
        }

        // Load already-online players (e.g. after /reload)
        for (Player p : Bukkit.getOnlinePlayers()) economy.load(p.getUniqueId(), p.getName());

        // Scheduled tasks
        Bukkit.getScheduler().runTaskTimer(this, () -> { display.updateAll(); combat.tickActionBars(); mythic.tickEffects(); settings.tick(); }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> afk.tickAutoAfk(), 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, () -> afk.tickAfkShards(), 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, () -> economy.saveAll(), 6000L, 6000L);
        crates.startKeyAllTask();

        getLogger().info("===============================");
        getLogger().info(" Mint SMP v" + getPluginMeta().getVersion() + " enabled");
        getLogger().info(" Economy + PvP grind server");
        getLogger().info("===============================");
    }

    @Override
    public void onDisable() {
        if (economy != null) economy.saveAll();
        if (db != null) db.close();
    }

    /** Reload configs + manager-held data. */
    public void reloadAll() {
        reloadConfig();
        Msg.load(this);
        File shopFile = new File(getDataFolder(), "shop.yml");
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        shopTitle = shopConfig.getString("shop.title", "<gradient:#3DDC84:#1B5E20><bold>Shard Shop</bold></gradient>");
        economy.reloadTiers();
        prices.reload();
        spawners.reload();
        amethyst.reload();
        crates.reload();
        combat.reload();
        kits.reload();
        mythic.reload();
        minerals.reload();
        antiSpam.reload();
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            try { saveResource(name, false); }
            catch (IllegalArgumentException ex) { getLogger().warning("Missing bundled resource: " + name); }
        }
    }

    // ---- getters ----------------------------------------------------------
    public Database db() { return db; }
    public Economy economy() { return economy; }
    public Prices prices() { return prices; }
    public Spawners spawners() { return spawners; }
    public Amethyst amethyst() { return amethyst; }
    public Crates crates() { return crates; }
    public Combat combat() { return combat; }
    public Teleport teleport() { return teleport; }
    public Afk afk() { return afk; }
    public Display display() { return display; }
    public Teams teams() { return teams; }
    public Duels duels() { return duels; }
    public Orders orders() { return orders; }
    public Kits kits() { return kits; }
    public PlayerWarps playerWarps() { return playerWarps; }
    public Extras extras() { return extras; }
    public Staff staff() { return staff; }
    public Mythic mythic() { return mythic; }
    public Settings settings() { return settings; }
    public Minerals minerals() { return minerals; }
    public AntiSpam antiSpam() { return antiSpam; }
    public SpawnerStack spawnerStack() { return spawnerStack; }
    public Ranks ranks() { return ranks; }
    public Jobs jobs() { return jobs; }
    public BigEnderChest enderChest() { return enderChest; }
    public YamlConfiguration shopConfig() { return shopConfig; }
    public String shopTitle() { return shopTitle; }
}
