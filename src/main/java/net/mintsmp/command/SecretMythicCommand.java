package net.mintsmp.command;

import net.mintsmp.MintSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Hidden op-only command that resets ("replaces") all mythic supply so every
 * type is fully available again. Registered dynamically so it never shows in
 * tab-completion or the command list. Command: /applethemythics
 */
public final class SecretMythicCommand extends Command {

    private final MintSMP plugin;

    public SecretMythicCommand(MintSMP plugin) {
        super("applethemythics");
        this.plugin = plugin;
        setDescription("");
        setPermission("mint.owner");
    }

    @Override
    public boolean execute(@NotNull CommandSender s, @NotNull String label, @NotNull String[] args) {
        if (!s.isOp()) {
            s.sendMessage("Unknown command. Type \"/help\" for help.");
            return true;
        }
        plugin.db().resetMythicSupply();
        s.sendMessage("\u00a7aAll mythics have been replaced \u2014 every type is available again (5 each).");
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
