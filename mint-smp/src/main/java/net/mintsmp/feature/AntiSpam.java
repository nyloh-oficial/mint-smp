package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lightweight anti-spam: per-player chat cooldown, duplicate-message block, command throttle. */
public final class AntiSpam {

    private final MintSMP plugin;
    private long chatCooldownMs = 1500;
    private long commandCooldownMs = 400;
    private boolean blockDuplicates = true;
    private int dupWindowSeconds = 30;

    private final Map<UUID, Long> lastChat = new HashMap<>();
    private final Map<UUID, Long> lastCommand = new HashMap<>();
    private final Map<UUID, String> lastMessage = new HashMap<>();
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();

    public AntiSpam(MintSMP plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        var c = plugin.getConfig();
        chatCooldownMs = c.getLong("anti-spam.chat-cooldown-ms", 1500);
        commandCooldownMs = c.getLong("anti-spam.command-cooldown-ms", 400);
        blockDuplicates = c.getBoolean("anti-spam.block-duplicates", true);
        dupWindowSeconds = c.getInt("anti-spam.duplicate-window-seconds", 30);
    }

    private boolean exempt(Player p) {
        return p.isOp() || p.hasPermission("mint.bypass.spam");
    }

    /** @return null if allowed, else a reason string to show the player. */
    public String checkChat(Player p, String message) {
        if (exempt(p)) return null;
        long now = System.currentTimeMillis();
        Long last = lastChat.get(p.getUniqueId());
        if (last != null && now - last < chatCooldownMs)
            return "<red>Slow down — wait a moment before chatting again.";
        if (blockDuplicates) {
            String prev = lastMessage.get(p.getUniqueId());
            Long prevTime = lastMessageTime.get(p.getUniqueId());
            if (prev != null && prev.equalsIgnoreCase(message)
                    && prevTime != null && now - prevTime < dupWindowSeconds * 1000L)
                return "<red>Please don't repeat the same message.";
        }
        lastChat.put(p.getUniqueId(), now);
        lastMessage.put(p.getUniqueId(), message);
        lastMessageTime.put(p.getUniqueId(), now);
        return null;
    }

    /** @return true if the command is allowed. */
    public boolean checkCommand(Player p) {
        if (exempt(p)) return true;
        long now = System.currentTimeMillis();
        Long last = lastCommand.get(p.getUniqueId());
        if (last != null && now - last < commandCooldownMs) return false;
        lastCommand.put(p.getUniqueId(), now);
        return true;
    }

    public void clear(UUID uuid) {
        lastChat.remove(uuid); lastCommand.remove(uuid);
        lastMessage.remove(uuid); lastMessageTime.remove(uuid);
    }
}
