package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.core.Economy;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Misc QoL + admin: /back, /daily, utility GUIs, heal/feed/fly/repair/god/hat/nick. */
public final class Extras {

    private final MintSMP plugin;
    private final Economy economy;
    private final Map<UUID, Location> back = new HashMap<>();
    private final Set<UUID> god = new HashSet<>();

    public Extras(MintSMP plugin, Economy economy) { this.plugin = plugin; this.economy = economy; }

    // ---- /back ------------------------------------------------------------

    public void setBack(Player p, Location l) { if (l != null) back.put(p.getUniqueId(), l); }

    public void back(Player p) {
        Location l = back.get(p.getUniqueId());
        if (l == null) { Msg.sendRaw(p, "<red>Nowhere to go back to yet."); return; }
        plugin.teleport().warp(p, l, "previous location");
    }

    // ---- /god -------------------------------------------------------------

    public boolean isGod(Player p) { return god.contains(p.getUniqueId()); }

    public void god(Player p) {
        if (god.remove(p.getUniqueId())) Msg.sendRaw(p, "<gray>God mode <red>off<gray>.");
        else { god.add(p.getUniqueId()); Msg.sendRaw(p, "<gray>God mode <green>on<gray>."); }
    }

    public void clear(UUID uuid) { god.remove(uuid); back.remove(uuid); }

    // ---- /daily -----------------------------------------------------------

    public void daily(Player p) {
        long remaining = plugin.db().cooldownRemaining(p.getUniqueId(), "daily");
        if (remaining > 0) { Msg.sendRaw(p, "<red>Daily reward in <white>" + Kits.human(remaining)); return; }
        double money = plugin.getConfig().getDouble("daily.money", 1000);
        long shards = plugin.getConfig().getLong("daily.shards", 50);
        economy.addMoney(p.getUniqueId(), money);
        economy.addShards(p.getUniqueId(), shards);
        String crate = plugin.getConfig().getString("daily.crate", "");
        if (crate != null && !crate.isBlank() && plugin.crates().exists(crate)) {
            plugin.crates().giveKey(p.getUniqueId(), crate, plugin.getConfig().getInt("daily.crate-keys", 1));
        }
        plugin.db().setCooldown(p.getUniqueId(), "daily", System.currentTimeMillis() + 86400_000L);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        Msg.sendRaw(p, "<green>Daily reward: <#A8FF60>" + Amounts.money(money) + " <gray>+ <aqua>" + Amounts.shards(shards)
                + (crate.isBlank() ? "" : " <gray>+ a " + crate + " key"));
    }

    // ---- utility containers ----------------------------------------------

    public void enderchest(Player p) { p.openInventory(p.getEnderChest()); }
    public void workbench(Player p) { p.openWorkbench(null, true); }
    public void anvil(Player p) { p.openAnvil(null, true); }
    public void grindstone(Player p) { p.openGrindstone(null, true); }
    public void cartography(Player p) { p.openCartographyTable(null, true); }
    public void loom(Player p) { p.openLoom(null, true); }
    public void smithing(Player p) { p.openSmithingTable(null, true); }
    public void stonecutter(Player p) { p.openStonecutter(null, true); }

    public void trash(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, Msg.mm("<red>Trash <gray>(closes = deleted)"));
        p.openInventory(inv);
    }

    // ---- self actions -----------------------------------------------------

    public void heal(Player p) {
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20); p.setSaturation(20); p.setFireTicks(0);
        Msg.sendRaw(p, "<green>Healed.");
    }

    public void feed(Player p) { p.setFoodLevel(20); p.setSaturation(20); Msg.sendRaw(p, "<green>Fed."); }

    public void fly(Player p) {
        boolean now = !p.getAllowFlight();
        p.setAllowFlight(now);
        p.setFlying(now && p.isFlying());
        Msg.sendRaw(p, now ? "<gray>Flight <green>enabled<gray>." : "<gray>Flight <red>disabled<gray>.");
    }

    public void repair(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { Msg.sendRaw(p, "<red>Hold an item to repair."); return; }
        if (hand.getItemMeta() instanceof Damageable dmg) {
            dmg.setDamage(0);
            hand.setItemMeta(dmg);
            Msg.sendRaw(p, "<green>Item repaired.");
        } else Msg.sendRaw(p, "<red>That item can't be repaired.");
    }

    public void hat(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { Msg.sendRaw(p, "<red>Hold an item to wear."); return; }
        ItemStack helmet = p.getInventory().getHelmet();
        p.getInventory().setHelmet(hand.clone());
        p.getInventory().setItemInMainHand(helmet);
        Msg.sendRaw(p, "<green>Hat on!");
    }

    public void nick(Player p, String[] a) {
        if (a.length == 0) { Msg.sendRaw(p, "<red>/nick <name|off>"); return; }
        if (a[0].equalsIgnoreCase("off")) {
            p.displayName(net.kyori.adventure.text.Component.text(p.getName()));
            p.playerListName(net.kyori.adventure.text.Component.text(p.getName()));
            Msg.sendRaw(p, "<gray>Nickname cleared.");
            return;
        }
        var name = Msg.mm(a[0]);
        p.displayName(name);
        p.playerListName(name);
        Msg.sendRaw(p, "<green>Nickname updated.");
    }

    // ---- /spawnstash (op) -------------------------------------------------

    /** Spawns a pre-filled chest, a stackable skeleton spawner, and 2 pre-filled
     *  barrels in a 4x4 area around the player. */
    public void spawnStash(Player p) {
        org.bukkit.block.Block base = p.getLocation().getBlock();
        org.bukkit.World w = p.getWorld();
        int bx = base.getX(), by = base.getY(), bz = base.getZ();

        // Chest (pre-filled with maxed netherite gear)
        org.bukkit.block.Block chestB = w.getBlockAt(bx + 1, by, bz + 1);
        chestB.setType(Material.CHEST);
        if (chestB.getState() instanceof org.bukkit.block.Chest chest) {
            for (org.bukkit.inventory.ItemStack it : netheriteKit()) chest.getInventory().addItem(it);
            chest.update();
        }

        // Stackable skeleton spawner (registered as a custom spawner so it harvests/stacks)
        org.bukkit.block.Block spB = w.getBlockAt(bx + 2, by, bz + 1);
        spB.setType(Material.SPAWNER);
        if (spB.getState() instanceof org.bukkit.block.CreatureSpawner cs) {
            cs.setSpawnedType(org.bukkit.entity.EntityType.SKELETON);
            cs.update();
        }
        plugin.db().saveSpawner(spB.getLocation(), "SKELETON");

        // Two pre-filled barrels
        fillBarrel(w.getBlockAt(bx + 1, by, bz + 2));
        fillBarrel(w.getBlockAt(bx + 2, by, bz + 2));

        Msg.sendRaw(p, "<green>Spawn stash created: <white>chest + skeleton spawner + 2 barrels</white>.");
    }

    private void fillBarrel(org.bukkit.block.Block b) {
        b.setType(Material.BARREL);
        if (b.getState() instanceof org.bukkit.block.Barrel barrel) {
            barrel.getInventory().addItem(
                    new org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE, 16),
                    new org.bukkit.inventory.ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4),
                    new org.bukkit.inventory.ItemStack(Material.ENDER_PEARL, 16),
                    new org.bukkit.inventory.ItemStack(Material.TOTEM_OF_UNDYING, 4),
                    new org.bukkit.inventory.ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                    new org.bukkit.inventory.ItemStack(Material.OBSIDIAN, 64));
            barrel.update();
        }
    }

    private java.util.List<org.bukkit.inventory.ItemStack> netheriteKit() {
        java.util.List<org.bukkit.inventory.ItemStack> list = new java.util.ArrayList<>();
        list.add(gear(Material.NETHERITE_HELMET, "protection", 4, "unbreaking", 3, "mending", 1, "respiration", 3, "aqua_affinity", 1));
        list.add(gear(Material.NETHERITE_CHESTPLATE, "protection", 4, "unbreaking", 3, "mending", 1));
        list.add(gear(Material.NETHERITE_LEGGINGS, "protection", 4, "unbreaking", 3, "mending", 1));
        list.add(gear(Material.NETHERITE_BOOTS, "protection", 4, "unbreaking", 3, "mending", 1, "feather_falling", 4, "depth_strider", 3));
        list.add(gear(Material.NETHERITE_SWORD, "sharpness", 5, "unbreaking", 3, "mending", 1, "looting", 3, "fire_aspect", 2, "sweeping_edge", 3));
        list.add(gear(Material.NETHERITE_PICKAXE, "efficiency", 5, "unbreaking", 3, "mending", 1, "fortune", 3));
        list.add(gear(Material.NETHERITE_AXE, "efficiency", 5, "unbreaking", 3, "mending", 1, "sharpness", 5));
        list.add(gear(Material.NETHERITE_SHOVEL, "efficiency", 5, "unbreaking", 3, "mending", 1, "fortune", 3));
        return list;
    }

    /** Build an item and apply enchants by key (allows max levels). Args: key, level, key, level... */
    private org.bukkit.inventory.ItemStack gear(Material mat, Object... kv) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(mat);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = (String) kv[i];
            int lvl = (int) kv[i + 1];
            org.bukkit.enchantments.Enchantment ench =
                    org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(key));
            if (ench != null) it.addUnsafeEnchantment(ench, lvl);
        }
        return it;
    }

    // ---- /spawnore (op) ---------------------------------------------------

    /** Spawns a random 4-8 block vein of the given ore type at the block you're looking at. */
    public void spawnOre(Player p, String type) {
        Material ore = switch (type.toLowerCase()) {
            case "diamond"  -> Material.DIAMOND_ORE;
            case "emerald"  -> Material.EMERALD_ORE;
            case "coal"     -> Material.COAL_ORE;
            case "gold"     -> Material.GOLD_ORE;
            case "lapis"    -> Material.LAPIS_ORE;
            case "redstone" -> Material.REDSTONE_ORE;
            default -> null;
        };
        if (ore == null) { Msg.sendRaw(p, "<red>Ore must be: diamond, emerald, coal, gold, lapis, redstone."); return; }
        org.bukkit.block.Block center = p.getTargetBlockExact(6);
        if (center == null) center = p.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
        int count = 4 + (int) (Math.random() * 5); // 4..8
        java.util.List<org.bukkit.block.Block> vein = new java.util.ArrayList<>();
        vein.add(center);
        center.setType(ore);
        int guard = 0;
        while (vein.size() < count && guard++ < 40) {
            org.bukkit.block.Block from = vein.get((int) (Math.random() * vein.size()));
            org.bukkit.block.BlockFace[] faces = org.bukkit.block.BlockFace.values();
            org.bukkit.block.Block next = from.getRelative(faces[(int) (Math.random() * 6)]);
            if (!vein.contains(next)) { next.setType(ore); vein.add(next); }
        }
        Msg.sendRaw(p, "<green>Spawned a <white>" + vein.size() + "</white>-block " + type.toLowerCase() + " vein.");
    }
}
