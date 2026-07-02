package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** 54-slot (6-row) virtual ender chest, persisted per player in the database. */
public final class BigEnderChest {

    private final MintSMP plugin;
    public BigEnderChest(MintSMP plugin) { this.plugin = plugin; }

    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        @NotNull @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Msg.mm("<gradient:#3DDC84:#A8FF60><bold>Ender Chest</bold></gradient>"));
        holder.inv = inv;
        String data = plugin.db().getEnderchest(p.getUniqueId());
        if (data != null && !data.isBlank()) {
            ItemStack[] items = deserialize(data);
            if (items != null) for (int i = 0; i < Math.min(items.length, 54); i++) inv.setItem(i, items[i]);
        }
        p.openInventory(inv);
    }

    public void save(Player p, Inventory inv) {
        plugin.db().setEnderchest(p.getUniqueId(), serialize(inv.getContents()));
    }

    private String serialize(ItemStack[] items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeInt(items.length);
            for (ItemStack it : items) out.writeObject(it);
            out.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Ender chest save failed: " + e.getMessage());
            return "";
        }
    }

    private ItemStack[] deserialize(String data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
            int len = in.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) items[i] = (ItemStack) in.readObject();
            return items;
        } catch (Exception e) {
            plugin.getLogger().warning("Ender chest load failed: " + e.getMessage());
            return null;
        }
    }
}
