package net.mintsmp.feature;

import net.kyori.adventure.text.Component;
import net.mintsmp.MintSMP;
import net.mintsmp.util.Msg;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.UUID;

/** Staff anti-cheat traps (honeypot chests) + moderation (ban/kick/mute). */
public final class Staff {

    private final MintSMP plugin;
    public Staff(MintSMP plugin) { this.plugin = plugin; }

    public boolean isStaff(CommandSender s) { return s.isOp() || s.hasPermission("mint.admin"); }

    // ---- traps ------------------------------------------------------------

    /** Register the container the admin is looking at as a honeypot. */
    public void setTrap(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof Container)) {
            Msg.sendRaw(p, "<red>Look at a chest/barrel/container within 6 blocks.");
            return;
        }
        plugin.db().addTrap(target.getLocation(), p.getUniqueId());
        Msg.sendRaw(p, "<green>Honeypot trap set. Anyone non-staff who opens or breaks it is flagged.");
    }

    public void removeTrap(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null) { Msg.sendRaw(p, "<red>Look at the trap block."); return; }
        if (!plugin.db().isTrap(target.getLocation())) { Msg.sendRaw(p, "<red>That isn't a trap."); return; }
        plugin.db().removeTrap(target.getLocation());
        Msg.sendRaw(p, "<gray>Trap removed.");
    }

    public void trapInfo(Player p) {
        Msg.sendRaw(p, "<gray>Active traps: <white>" + plugin.db().trapCount()
                + "<gray>. Look at a container + <white>/trap set</white> | <white>/trap remove</white>.");
    }

    /** Called when a non-staff player opens/breaks a trapped block. Returns true if it was a trap. */
    public boolean triggered(Player p, Location loc, String how) {
        if (!plugin.db().isTrap(loc)) return false;
        if (isStaff(p)) return true; // staff can inspect freely; don't flag
        String coords = loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        String alert = "<#3DDC84>[Staff] <red>" + p.getName() + " <gray>" + how + " a honeypot trap at <white>" + coords;
        for (Player staff : Bukkit.getOnlinePlayers()) if (isStaff(staff)) Msg.sendRaw(staff, alert);
        plugin.getLogger().warning("[TRAP] " + p.getName() + " " + how + " a trap at " + coords);

        String action = plugin.getConfig().getString("trap.action", "alert").toLowerCase();
        switch (action) {
            case "kick" -> p.kick(Msg.mm("<red>Caught accessing a staff honeypot."));
            case "ban" -> {
                String reason = plugin.getConfig().getString("trap.ban-reason", "X-ray / honeypot trap");
                banName(p.getName(), reason, null, "MintSMP-Trap");
                p.kick(Msg.mm("<red>Banned: " + reason));
            }
            default -> { /* alert only */ }
        }
        return true;
    }

    // ---- moderation -------------------------------------------------------

    public void ban(CommandSender s, String name, String reason) {
        if (reason == null || reason.isBlank()) reason = "Banned by an operator";
        banName(name, reason, null, s.getName());
        kickIfOnline(name, "<red>Banned: " + reason);
        notifyStaff("<red>" + name + " <gray>was banned: <white>" + reason);
        Msg.sendRaw(s, "<green>Banned <white>" + name + "</white>.");
    }

    public void tempban(CommandSender s, String name, String durationStr, String reason) {
        long ms = parseDuration(durationStr);
        if (ms <= 0) { Msg.sendRaw(s, "<red>Bad duration. Examples: 30m, 2h, 7d, 4w."); return; }
        if (reason == null || reason.isBlank()) reason = "Temporarily banned";
        Date expires = new Date(System.currentTimeMillis() + ms);
        banName(name, reason, expires, s.getName());
        kickIfOnline(name, "<red>Banned for " + Kits.human(ms) + ": " + reason);
        notifyStaff("<red>" + name + " <gray>was temp-banned for <white>" + Kits.human(ms) + "</white>: <white>" + reason);
        Msg.sendRaw(s, "<green>Temp-banned <white>" + name + "</white> for <white>" + Kits.human(ms) + "</white>.");
    }

    @SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
    private void banName(String name, String reason, Date expires, String source) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        banList.addBan(name, reason, expires, source);
    }

    @SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
    public void unban(CommandSender s, String name) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        banList.pardon(name);
        Msg.sendRaw(s, "<green>Unbanned <white>" + name + "</white>.");
    }

    public void kick(CommandSender s, String name, String reason) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) { Msg.sendRaw(s, "<red>That player isn't online."); return; }
        if (reason == null || reason.isBlank()) reason = "Kicked by an operator";
        target.kick(Msg.mm("<red>" + reason));
        notifyStaff("<red>" + name + " <gray>was kicked: <white>" + reason);
        Msg.sendRaw(s, "<green>Kicked <white>" + name + "</white>.");
    }

    public void mute(CommandSender s, String name, String durationStr, String reason) {
        UUID target = resolve(name);
        if (target == null) { Msg.sendRaw(s, "<red>Unknown player."); return; }
        long until;
        if (durationStr == null || durationStr.equalsIgnoreCase("perm") || durationStr.equalsIgnoreCase("permanent")) {
            until = Long.MAX_VALUE;
        } else {
            long ms = parseDuration(durationStr);
            if (ms <= 0) { Msg.sendRaw(s, "<red>Bad duration. Use 30m, 2h, 7d, or 'perm'."); return; }
            until = System.currentTimeMillis() + ms;
        }
        plugin.db().setMute(target, until, reason);
        Player online = Bukkit.getPlayer(target);
        if (online != null) Msg.sendRaw(online, "<red>You have been muted" + (reason == null || reason.isBlank() ? "." : ": " + reason));
        notifyStaff("<red>" + name + " <gray>was muted.");
        Msg.sendRaw(s, "<green>Muted <white>" + name + "</white>.");
    }

    public void unmute(CommandSender s, String name) {
        UUID target = resolve(name);
        if (target == null) { Msg.sendRaw(s, "<red>Unknown player."); return; }
        plugin.db().clearMute(target);
        Msg.sendRaw(s, "<green>Unmuted <white>" + name + "</white>.");
    }

    public boolean isMuted(UUID uuid) { return plugin.db().muteRemaining(uuid) > 0; }

    // ---- helpers ----------------------------------------------------------

    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        return plugin.db().findByName(name);
    }

    private void kickIfOnline(String name, String mini) {
        Player target = Bukkit.getPlayerExact(name);
        if (target != null) target.kick(Msg.mm(mini));
    }

    private void notifyStaff(String mini) {
        for (Player staff : Bukkit.getOnlinePlayers()) if (isStaff(staff)) Msg.sendRaw(staff, mini);
    }

    /** Parse 30s/15m/2h/7d/4w into milliseconds; -1 if invalid. */
    public static long parseDuration(String s) {
        if (s == null || s.isBlank()) return -1;
        s = s.trim().toLowerCase();
        char unit = s.charAt(s.length() - 1);
        long mult;
        switch (unit) {
            case 's' -> mult = 1000L;
            case 'm' -> mult = 60_000L;
            case 'h' -> mult = 3_600_000L;
            case 'd' -> mult = 86_400_000L;
            case 'w' -> mult = 604_800_000L;
            default -> { return -1; }
        }
        try { return Long.parseLong(s.substring(0, s.length() - 1)) * mult; }
        catch (NumberFormatException ex) { return -1; }
    }
}
