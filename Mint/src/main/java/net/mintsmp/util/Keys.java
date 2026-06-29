package net.mintsmp.util;

import net.mintsmp.MintSMP;
import org.bukkit.NamespacedKey;

/** Central PDC keys so tags survive shop wipes / drops / storage. */
public final class Keys {
    public static NamespacedKey SPAWNER;        // STRING  -> mob type
    public static NamespacedKey CRATE_KEY;      // STRING  -> crate id
    public static NamespacedKey AMETHYST;       // STRING  -> amethyst item type
    public static NamespacedKey AMETHYST_EXPIRE;// LONG    -> epoch millis self-destruct
    public static NamespacedKey NO_SELL;        // INTEGER -> 1 = never sellable
    public static NamespacedKey MYTHIC;         // STRING  -> mythic item type
    public static NamespacedKey MYTHIC_SERIAL;  // INTEGER -> serial number (1..cap)
    public static NamespacedKey MYTHIC_ARROW;   // INTEGER -> 1 = Stormcaller arrow
    public static NamespacedKey MINERAL;        // INTEGER -> 1 = Mint Mineral
    public static NamespacedKey SPAWNER_STACK;   // INTEGER -> stacked spawner count

    private Keys() {}

    public static void init(MintSMP plugin) {
        SPAWNER        = new NamespacedKey(plugin, "spawner_type");
        CRATE_KEY      = new NamespacedKey(plugin, "crate_key");
        AMETHYST       = new NamespacedKey(plugin, "amethyst");
        AMETHYST_EXPIRE= new NamespacedKey(plugin, "amethyst_expire");
        NO_SELL        = new NamespacedKey(plugin, "no_sell");
        MYTHIC         = new NamespacedKey(plugin, "mythic");
        MYTHIC_SERIAL  = new NamespacedKey(plugin, "mythic_serial");
        MYTHIC_ARROW   = new NamespacedKey(plugin, "mythic_arrow");
        MINERAL        = new NamespacedKey(plugin, "mineral");
        SPAWNER_STACK  = new NamespacedKey(plugin, "spawner_stack");
    }
}
