package dev.regionflags.flag;

import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Expands material shortcuts:
 * <pre>
 *   #hand / #mainhand   item in main hand
 *   #offhand            item in off hand
 *   #hotbar             all 9 hotbar items
 *   #inventory / #inv   whole inventory (incl. armor + offhand)
 *   #armor              worn armor
 *   #target / #looking  block you're looking at (10 blocks)
 *   #tag:logs           every material in a vanilla block/item tag
 *   *_spear             wildcard on material names
 *   diamond_sword       plain material name
 * </pre>
 */
public final class Tokens {

    public static final List<String> SHORTCUTS = List.of(
            "#hand", "#offhand", "#hotbar", "#inventory", "#armor", "#target", "#tag:");

    private Tokens() {}

    public static String key(Material m) {
        return m.getKey().getKey();
    }

    public static Collection<String> expandMaterials(String raw, Player player, boolean blocksOnly) throws InvalidFlagFormat {
        String token = raw.toLowerCase(Locale.ROOT);
        Set<Material> mats = new LinkedHashSet<>();

        if (token.startsWith("#tag:")) {
            String tagName = token.substring(5);
            NamespacedKey nk = NamespacedKey.fromString(tagName);
            if (nk == null) throw new InvalidFlagFormat("Bad tag name: " + tagName);
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, nk, Material.class);
            if (tag == null) tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, nk, Material.class);
            if (tag == null) throw new InvalidFlagFormat("Unknown tag: " + tagName);
            mats.addAll(tag.getValues());
        } else if (token.startsWith("#")) {
            if (player == null) throw new InvalidFlagFormat(token + " can only be used by a player.");
            PlayerInventory inv = player.getInventory();
            switch (token) {
                case "#hand", "#mainhand" -> add(mats, inv.getItemInMainHand());
                case "#offhand" -> add(mats, inv.getItemInOffHand());
                case "#hotbar" -> { for (int i = 0; i < 9; i++) add(mats, inv.getItem(i)); }
                case "#inventory", "#inv" -> {
                    for (ItemStack it : inv.getContents()) add(mats, it);
                }
                case "#armor" -> { for (ItemStack it : inv.getArmorContents()) add(mats, it); }
                case "#target", "#looking" -> {
                    Block b = player.getTargetBlockExact(10);
                    if (b == null) throw new InvalidFlagFormat("You're not looking at a block.");
                    mats.add(b.getType());
                }
                default -> throw new InvalidFlagFormat("Unknown shortcut " + token + ". Try: " + String.join(", ", SHORTCUTS));
            }
            if (mats.isEmpty()) throw new InvalidFlagFormat(token + " is empty.");
        } else if (token.contains("*")) {
            Pattern p = Pattern.compile(Pattern.quote(stripNs(token)).replace("*", "\\E.*\\Q"));
            for (Material m : Material.values()) {
                if (!m.isLegacy() && p.matcher(key(m)).matches()) mats.add(m);
            }
            if (mats.isEmpty()) throw new InvalidFlagFormat("No materials match " + token);
        } else {
            Material m = Material.matchMaterial(token);
            if (m == null || m.isLegacy()) throw new InvalidFlagFormat("Unknown item/block: " + token);
            mats.add(m);
        }

        List<String> out = new ArrayList<>();
        for (Material m : mats) {
            if (m.isAir()) continue;
            if (blocksOnly && !m.isBlock()) continue;
            out.add(key(m));
        }
        if (out.isEmpty()) throw new InvalidFlagFormat("Nothing valid in " + token + (blocksOnly ? " (blocks only)" : ""));
        return out;
    }

    private static String stripNs(String s) {
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }

    private static void add(Set<Material> mats, ItemStack it) {
        if (it != null && !it.getType().isAir()) mats.add(it.getType());
    }

    public static List<String> materialSuggestions(boolean blocksOnly) {
        List<String> out = new ArrayList<>(SHORTCUTS);
        for (Material m : Material.values()) {
            if (m.isLegacy() || m.isAir()) continue;
            if (blocksOnly && !m.isBlock()) continue;
            out.add(key(m));
        }
        return out;
    }
}
