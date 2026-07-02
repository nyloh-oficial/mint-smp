package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** AFK + private messaging + ignore + settings + info commands + links. */
public final class Afk {

    private final MintSMP plugin;
    private final Map<UUID, Boolean> afk = new HashMap<>();
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, Location> afkAnchor = new HashMap<>();
    private final Map<UUID, UUID> replyTarget = new HashMap<>();
    private final Map<UUID, Boolean> msgToggle = new HashMap<>();

    public Afk(MintSMP plugin) { this.plugin = plugin; }

    // ---- AFK --------------------------------------------------------------

    public void touch(Player p) {
        lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
        if (isAfk(p)) setAfk(p, false, true);
    }

    public boolean isAfk(Player p) { return afk.getOrDefault(p.getUniqueId(), false); }

    public void toggleAfk(Player p) { setAfk(p, !isAfk(p), false); }

    public void setAfk(Player p, boolean state, boolean autoReturn) {
        afk.put(p.getUniqueId(), state);
        if (state) {
            afkAnchor.put(p.getUniqueId(), p.getLocation());
            Bukkit.broadcast(Msg.mm("<gray>" + p.getName() + " is now AFK."));
        } else {
            afkAnchor.remove(p.getUniqueId());
            if (!autoReturn) Bukkit.broadcast(Msg.mm("<gray>" + p.getName() + " is no longer AFK."));
        }
    }

    /** Called each tick window to auto-AFK idle players. */
    public void tickAutoAfk() {
        if (!plugin.getConfig().getBoolean("afk-zone.enabled", true)) return;
        long idleMs = plugin.getConfig().getLong("afk-zone.auto-afk-seconds", 300) * 1000L;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAfk(p)) continue;
            long last = lastActivity.getOrDefault(p.getUniqueId(), now);
            if (now - last >= idleMs) setAfk(p, true, false);
        }
    }

    /** Award shards to AFK players (call every minute). */
    public void tickAfkShards() {
        long perMin = plugin.getConfig().getLong("shards.earn.afk-per-minute", 1);
        if (perMin <= 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAfk(p)) {
                int mult = plugin.amethyst().boosterMultiplier(p.getUniqueId());
                plugin.economy().addShards(p.getUniqueId(), perMin * mult);
            }
        }
    }

    // ---- messaging --------------------------------------------------------

    public void msg(Player from, Player to, String text) {
        if (plugin.db().isIgnoring(to.getUniqueId(), from.getUniqueId())) {
            Msg.sendRaw(from, "<red>That player is ignoring you.");
            return;
        }
        if (msgToggle.getOrDefault(to.getUniqueId(), false)) {
            Msg.sendRaw(from, "<red>That player has DMs disabled.");
            return;
        }
        from.sendMessage(Msg.mm("<dark_gray>[<#3DDC84>You<dark_gray> -> <white>" + to.getName() + "<dark_gray>] <gray>" + esc(text)));
        to.sendMessage(Msg.mm("<dark_gray>[<white>" + from.getName() + "<dark_gray> -> <#3DDC84>You<dark_gray>] <gray>" + esc(text)));
        to.playSound(to.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
        replyTarget.put(from.getUniqueId(), to.getUniqueId());
        replyTarget.put(to.getUniqueId(), from.getUniqueId());
    }

    public void reply(Player from, String text) {
        UUID t = replyTarget.get(from.getUniqueId());
        Player to = t == null ? null : Bukkit.getPlayer(t);
        if (to == null) { Msg.sendRaw(from, "<red>No one to reply to."); return; }
        msg(from, to, text);
    }

    public void toggleMsg(Player p) {
        boolean now = !msgToggle.getOrDefault(p.getUniqueId(), false);
        msgToggle.put(p.getUniqueId(), now);
        Msg.sendRaw(p, now ? "<gray>Private messages <red>disabled<gray>." : "<gray>Private messages <green>enabled<gray>.");
    }

    public void ignore(Player p, String name, boolean on) {
        UUID other = plugin.db().findByName(name);
        if (other == null) { Msg.sendRaw(p, "<red>Unknown player."); return; }
        plugin.db().setIgnore(p.getUniqueId(), other, on);
        Msg.sendRaw(p, on ? "<gray>Now ignoring <white>" + name : "<gray>No longer ignoring <white>" + name);
    }

    public void ignoreList(Player p) {
        var list = plugin.db().ignoreList(p.getUniqueId());
        if (list.isEmpty()) { Msg.sendRaw(p, "<gray>You aren't ignoring anyone."); return; }
        String names = list.stream().map(u -> {
            try { return Bukkit.getOfflinePlayer(UUID.fromString(u)).getName(); } catch (Exception e) { return u; }
        }).collect(Collectors.joining(", "));
        Msg.sendRaw(p, "<gray>Ignoring: <white>" + names);
    }

    // ---- info -------------------------------------------------------------

    public void list(Player p) {
        String names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.joining(", "));
        Msg.sendRaw(p, "<green>Online (" + Bukkit.getOnlinePlayers().size() + "): <white>" + names);
    }

    public void ping(Player p, Player target) {
        Msg.sendRaw(p, "<green>" + target.getName() + "<gray>'s ping: <white>" + target.getPing() + "ms");
    }

    public void seen(Player p, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) { Msg.sendRaw(p, "<green>" + name + " <gray>is online now."); return; }
        UUID u = plugin.db().findByName(name);
        if (u == null) { Msg.sendRaw(p, "<red>Never seen that player."); return; }
        long last = plugin.db().lastSeen(u);
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(last));
        Msg.sendRaw(p, "<green>" + name + " <gray>last seen <white>" + when);
    }

    public void findPlayer(Player p, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) { Msg.sendRaw(p, "<red>That player isn't online."); return; }
        Location l = t.getLocation();
        Msg.sendRaw(p, "<green>" + name + "<gray>: " + l.getWorld().getName() + " "
                + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
    }

    public void link(Player p, String key) {
        String url = plugin.getConfig().getString("links." + key, "");
        if (url.isBlank()) { Msg.sendRaw(p, "<red>No link configured."); return; }
        p.sendMessage(Msg.mm("<#3DDC84>" + key.substring(0, 1).toUpperCase() + key.substring(1)
                + ": <click:open_url:'" + url + "'><underlined><white>" + url + "</white></underlined></click>"));
    }

    private String esc(String s) { return s.replace("<", "\u200b<"); }
}
