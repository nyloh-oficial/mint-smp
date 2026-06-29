package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.feature.Combat;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Homes, /tpa family, /rtp (safe spot), /spawn, /warp — all with warmups. */
public final class Teleport {

    private final MintSMP plugin;
    private final Combat combat;
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();     // target -> requester
    private final Map<UUID, UUID> tpaHereRequests = new HashMap<>(); // requester wants target to come

    public Teleport(MintSMP plugin, Combat combat) { this.plugin = plugin; this.combat = combat; }

    // ---- generic warmup ---------------------------------------------------

    public void warp(Player p, Location dest, String label) {
        if (dest == null) { Msg.sendRaw(p, "<red>Destination not found."); return; }
        if (combat.isTagged(p)) { Msg.sendRaw(p, "<red>You can't teleport while in combat."); return; }
        int warmup = plugin.getConfig().getInt("teleport.tpa-warmup-seconds", 3);
        Location start = p.getLocation().getBlock().getLocation();
        Msg.sendRaw(p, "<gray>Teleporting to <white>" + label + "</white> in <white>" + warmup + "s</white> — don't move.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (combat.isTagged(p)) { Msg.sendRaw(p, "<red>Teleport cancelled (combat)."); return; }
            Location now = p.getLocation().getBlock().getLocation();
            if (!now.getWorld().equals(start.getWorld()) || now.distanceSquared(start) > 1.5) {
                Msg.sendRaw(p, "<red>Teleport cancelled (you moved)."); return;
            }
            p.teleport(dest);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        }, warmup * 20L);
    }

    // ---- homes ------------------------------------------------------------

    public void setHome(Player p, String name) {
        int max = plugin.getConfig().getInt("homes.max-homes", 3);
        if (plugin.db().getHome(p.getUniqueId(), name) == null && plugin.db().homeCount(p.getUniqueId()) >= max) {
            Msg.sendRaw(p, "<red>You've reached your home limit (" + max + ")."); return;
        }
        plugin.db().setHome(p.getUniqueId(), name, p.getLocation());
        Msg.sendRaw(p, "<green>Home <white>" + name + "</white> set.");
    }

    public void home(Player p, String name) {
        Location l = plugin.db().getHome(p.getUniqueId(), name);
        if (l == null) { Msg.sendRaw(p, "<red>No home named <white>" + name + "</white>."); return; }
        warp(p, l, "home " + name);
    }

    public void delHome(Player p, String name) {
        if (plugin.db().getHome(p.getUniqueId(), name) == null) { Msg.sendRaw(p, "<red>No such home."); return; }
        plugin.db().delHome(p.getUniqueId(), name);
        Msg.sendRaw(p, "<green>Home <white>" + name + "</white> deleted.");
    }

    public void homesList(Player p) {
        List<String> homes = plugin.db().homeNames(p.getUniqueId());
        if (homes.isEmpty()) { Msg.sendRaw(p, "<gray>You have no homes. Use <white>/sethome</white>."); return; }
        Msg.sendRaw(p, "<green>Homes: <white>" + String.join("<gray>, <white>", homes));
    }

    // ---- tpa --------------------------------------------------------------

    public void tpa(Player from, Player to) {
        if (!plugin.settings().get(to, net.mintsmp.feature.Settings.TPA, true)) {
            Msg.sendRaw(from, "<red>" + to.getName() + " has teleport requests disabled.");
            return;
        }
        tpaRequests.put(to.getUniqueId(), from.getUniqueId());
        scheduleExpire(tpaRequests, to.getUniqueId());
        Msg.sendRaw(to, "<green>" + from.getName() + " <gray>wants to teleport to you. <white>/tpaccept</white> | <white>/tpdeny</white>");
        Msg.sendRaw(from, "<gray>Request sent to <white>" + to.getName() + "</white>.");
    }

    public void tpaHere(Player from, Player to) {
        tpaHereRequests.put(to.getUniqueId(), from.getUniqueId());
        scheduleExpire(tpaHereRequests, to.getUniqueId());
        Msg.sendRaw(to, "<green>" + from.getName() + " <gray>wants you to teleport to them. <white>/tpaccept</white> | <white>/tpdeny</white>");
        Msg.sendRaw(from, "<gray>Request sent to <white>" + to.getName() + "</white>.");
    }

    public void accept(Player target) {
        UUID reqUuid = tpaRequests.remove(target.getUniqueId());
        if (reqUuid != null) {
            Player req = Bukkit.getPlayer(reqUuid);
            if (req != null) { warp(req, target.getLocation(), target.getName()); Msg.sendRaw(target, "<green>Accepted."); }
            return;
        }
        reqUuid = tpaHereRequests.remove(target.getUniqueId());
        if (reqUuid != null) {
            Player req = Bukkit.getPlayer(reqUuid);
            if (req != null) { warp(target, req.getLocation(), req.getName()); Msg.sendRaw(target, "<green>Accepted."); }
            return;
        }
        Msg.sendRaw(target, "<red>No pending requests.");
    }

    public void deny(Player target) {
        boolean had = tpaRequests.remove(target.getUniqueId()) != null
                | tpaHereRequests.remove(target.getUniqueId()) != null;
        Msg.sendRaw(target, had ? "<gray>Request denied." : "<red>No pending requests.");
    }

    private void scheduleExpire(Map<UUID, UUID> map, UUID key) {
        int secs = plugin.getConfig().getInt("teleport.tpa-request-expire-seconds", 60);
        Bukkit.getScheduler().runTaskLater(plugin, () -> map.remove(key), secs * 20L);
    }

    // ---- spawn / warps ----------------------------------------------------

    public void setSpawn(Player p) { plugin.db().setWarp("spawn", p.getLocation()); Msg.sendRaw(p, "<green>Spawn set."); }

    /** Server spawn: the /setspawn warp if set, else the configured fixed coords. */
    public Location spawnLocation() {
        Location l = plugin.db().getWarp("spawn");
        if (l != null) return l;
        var c = plugin.getConfig();
        String worldName = c.getString("spawn.world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (w == null) return null;
        return new Location(w,
                c.getDouble("spawn.x", 113.5), c.getDouble("spawn.y", 94), c.getDouble("spawn.z", -158.5),
                (float) c.getDouble("spawn.yaw", 0), (float) c.getDouble("spawn.pitch", 0));
    }

    /** True if the location is inside the protected spawn radius. */
    public boolean inSpawnRegion(Location loc) {
        if (loc == null) return false;
        if (!plugin.getConfig().getBoolean("spawn.protection.enabled", true)) return false;
        Location s = spawnLocation();
        if (s == null || s.getWorld() == null || loc.getWorld() == null) return false;
        if (!s.getWorld().equals(loc.getWorld())) return false;
        double r = plugin.getConfig().getDouble("spawn.protection.radius", 40);
        return loc.distanceSquared(s) <= r * r;
    }

    public boolean isServerSpawnSet() { return plugin.db().getWarp("spawn") != null; }

    public void spawn(Player p) {
        Location l = spawnLocation();
        if (l == null) l = p.getWorld().getSpawnLocation();
        warp(p, l, "spawn");
    }

    public void setWarp(Player p, String name) { plugin.db().setWarp(name, p.getLocation()); Msg.sendRaw(p, "<green>Warp <white>" + name + "</white> set."); }
    public void delWarp(Player p, String name) { plugin.db().delWarp(name); Msg.sendRaw(p, "<green>Warp <white>" + name + "</white> deleted."); }

    public void warpTo(Player p, String name) {
        Location l = plugin.db().getWarp(name);
        if (l == null) { Msg.sendRaw(p, "<red>No warp named <white>" + name + "</white>."); return; }
        warp(p, l, name);
    }

    public List<String> warpNames() { return plugin.db().warpNames(); }

    // ---- rtp --------------------------------------------------------------

    public void rtp(Player p, String region) {
        if (combat.isTagged(p)) { Msg.sendRaw(p, "<red>You can't RTP while in combat."); return; }
        String path = "teleport.rtp.regions." + region;
        if (plugin.getConfig().getConfigurationSection(path) == null) {
            Msg.sendRaw(p, "<red>Unknown region. Try <white>overworld | nether | end</white>."); return;
        }
        String worldName = plugin.getConfig().getString(path + ".world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) { Msg.sendRaw(p, "<red>That world isn't loaded."); return; }
        int radius = plugin.getConfig().getInt(path + ".radius", 5000);
        int attempts = plugin.getConfig().getInt(path + ".max-attempts", 50);
        Msg.sendRaw(p, "<gray>Searching for a safe spot... <dark_gray>(generating distant terrain, hang tight)");
        rtpAttempt(p, w, radius, attempts, region);
    }

    /** Async: load a random chunk off-thread, then check for a safe spot on the main thread. */
    private void rtpAttempt(Player p, World w, int radius, int attemptsLeft, String region) {
        if (attemptsLeft <= 0) { Msg.sendRaw(p, "<red>Couldn't find a safe spot, try again."); return; }
        int x = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int z = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        w.getChunkAtAsync(x >> 4, z >> 4, true).thenAccept(chunk ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                Location safe = safeIn(w, x, z);
                if (safe != null) warp(p, safe, region);
                else rtpAttempt(p, w, radius, attemptsLeft - 1, region);
            }));
    }

    private Location safeIn(World w, int x, int z) {
        boolean nether = w.getEnvironment() == World.Environment.NETHER;
        int y = nether ? scanNether(w, x, z) : w.getHighestBlockYAt(x, z);
        if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 2) return null;
        Block ground = w.getBlockAt(x, y, z);
        Block feet = w.getBlockAt(x, y + 1, z);
        Block head = w.getBlockAt(x, y + 2, z);
        if (!ground.getType().isSolid()) return null;
        if (ground.getType() == Material.LAVA || ground.getType() == Material.WATER) return null;
        if (!feet.getType().isAir() || !head.getType().isAir()) return null;
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    private int scanNether(World w, int x, int z) {
        for (int y = 100; y > 10; y--) {
            Block b = w.getBlockAt(x, y, z);
            Block above = w.getBlockAt(x, y + 1, z);
            Block above2 = w.getBlockAt(x, y + 2, z);
            if (b.getType().isSolid() && above.getType().isAir() && above2.getType().isAir()) return y;
        }
        return -1;
    }
}
