package net.mintsmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Item construction, PDC tagging, GUI filler, and base64 (de)serialization. */
public final class Items {

    private Items() {}

    public static ItemStack of(Material mat, String nameMini, String... loreMini) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            if (nameMini != null) m.displayName(noItalic(Msg.mm(nameMini)));
            if (loreMini.length > 0) {
                List<Component> lore = new ArrayList<>();
                for (String l : loreMini) lore.add(noItalic(Msg.mm(l)));
                m.lore(lore);
            }
            it.setItemMeta(m);
        }
        return it;
    }

    public static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack filler() {
        return of(Material.LIME_STAINED_GLASS_PANE, "<reset>");
    }

    public static ItemStack named(Material mat, String nameMini, List<String> loreMini) {
        return of(mat, nameMini, loreMini.toArray(new String[0]));
    }

    // ---- PDC helpers ------------------------------------------------------

    public static ItemStack tagString(ItemStack it, org.bukkit.NamespacedKey key, String value) {
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.getPersistentDataContainer().set(key, PersistentDataType.STRING, value); it.setItemMeta(m); }
        return it;
    }

    public static ItemStack tagInt(ItemStack it, org.bukkit.NamespacedKey key, int value) {
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value); it.setItemMeta(m); }
        return it;
    }

    public static ItemStack tagLong(ItemStack it, org.bukkit.NamespacedKey key, long value) {
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.getPersistentDataContainer().set(key, PersistentDataType.LONG, value); it.setItemMeta(m); }
        return it;
    }

    public static String getString(ItemStack it, org.bukkit.NamespacedKey key) {
        if (it == null) return null;
        ItemMeta m = it.getItemMeta();
        if (m == null) return null;
        PersistentDataContainer c = m.getPersistentDataContainer();
        return c.has(key, PersistentDataType.STRING) ? c.get(key, PersistentDataType.STRING) : null;
    }

    public static Long getLong(ItemStack it, org.bukkit.NamespacedKey key) {
        if (it == null) return null;
        ItemMeta m = it.getItemMeta();
        if (m == null) return null;
        PersistentDataContainer c = m.getPersistentDataContainer();
        return c.has(key, PersistentDataType.LONG) ? c.get(key, PersistentDataType.LONG) : null;
    }

    public static boolean hasInt(ItemStack it, org.bukkit.NamespacedKey key) {
        if (it == null) return false;
        ItemMeta m = it.getItemMeta();
        return m != null && m.getPersistentDataContainer().has(key, PersistentDataType.INTEGER);
    }

    /** Apply "ENCHANT:level,ENCHANT:level" using Bukkit enchantment keys. */
    public static void applyEnchants(ItemStack it, String spec) {
        if (spec == null || spec.isBlank()) return;
        ItemMeta m = it.getItemMeta();
        if (m == null) return;
        for (String part : spec.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) continue;
            Enchantment ench = enchantByName(kv[0].trim());
            if (ench == null) continue;
            try { m.addEnchant(ench, Integer.parseInt(kv[1].trim()), true); }
            catch (NumberFormatException ignored) {}
        }
        it.setItemMeta(m);
    }

    @SuppressWarnings("deprecation")
    private static Enchantment enchantByName(String name) {
        try {
            org.bukkit.NamespacedKey k = org.bukkit.NamespacedKey.minecraft(name.toLowerCase());
            Enchantment e = org.bukkit.Registry.ENCHANTMENT.get(k);
            if (e != null) return e;
        } catch (Throwable ignored) {}
        // legacy fallback
        return Enchantment.getByName(name.toUpperCase());
    }

    // ---- base64 (for Auction House) ---------------------------------------

    public static String encode(ItemStack item) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeObject(item);
            out.flush();
            return Base64Coder.encodeLines(bos.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    public static ItemStack decode(String data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
            return (ItemStack) in.readObject();
        } catch (Exception ex) {
            return null;
        }
    }

    public static List<Integer> border(int rows) {
        List<Integer> slots = new ArrayList<>();
        int size = rows * 9;
        for (int i = 0; i < 9; i++) slots.add(i);
        for (int i = size - 9; i < size; i++) slots.add(i);
        for (int r = 1; r < rows - 1; r++) { slots.add(r * 9); slots.add(r * 9 + 8); }
        return slots;
    }

    public static List<String> lore(String... lines) {
        return new ArrayList<>(Arrays.asList(lines));
    }
}
