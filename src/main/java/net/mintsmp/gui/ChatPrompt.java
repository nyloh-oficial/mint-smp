package net.mintsmp.gui;

import net.mintsmp.util.Msg;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Lets a GUI ask the player to type something in chat (DonutSMP-style search). */
public final class ChatPrompt {

    private static final Map<UUID, Consumer<String>> WAITING = new HashMap<>();
    private ChatPrompt() {}

    /** Close the menu and wait for the player's next chat message. */
    public static void await(Player p, String prompt, Consumer<String> callback) {
        WAITING.put(p.getUniqueId(), callback);
        p.closeInventory();
        Msg.sendRaw(p, prompt);
        Msg.sendRaw(p, "<dark_gray>(type <white>cancel</white> to stop)");
    }

    public static boolean isWaiting(UUID uuid) { return WAITING.containsKey(uuid); }

    /** Feed the typed message to the waiting callback. Returns true if consumed. */
    public static boolean feed(Player p, String input) {
        Consumer<String> cb = WAITING.remove(p.getUniqueId());
        if (cb == null) return false;
        if (!input.equalsIgnoreCase("cancel")) cb.accept(input);
        else Msg.sendRaw(p, "<gray>Cancelled.");
        return true;
    }

    public static void cancel(UUID uuid) { WAITING.remove(uuid); }
}
