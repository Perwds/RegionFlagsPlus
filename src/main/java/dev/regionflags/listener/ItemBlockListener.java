package dev.regionflags.listener;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import dev.regionflags.RegionFlagsPlus;
import dev.regionflags.flag.Flags;
import dev.regionflags.flag.MaterialSetFlag;
import dev.regionflags.flag.Tokens;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** blocked-items, item-cooldowns, deny-block-place / break / use. */
public final class ItemBlockListener implements Listener {

    private final RegionFlagsPlus plugin;

    public ItemBlockListener(RegionFlagsPlus plugin) {
        this.plugin = plugin;
    }

    private boolean listed(Player p, Location at, MaterialSetFlag flag, Material m) {
        if (m == null || m.isAir()) return false;
        if (!RegionFlagsPlus.union(RegionFlagsPlus.regionsAt(at), RegionFlagsPlus.wrap(p), flag).contains(Tokens.key(m))) {
            return false;
        }
        return !plugin.bypasses(p, flag);
    }

    /** True (and messages the player) if the item is blocked where they stand. */
    private boolean blocked(Player p, ItemStack item) {
        if (item == null) return false;
        if (!listed(p, p.getLocation(), Flags.BLOCKED_ITEMS, item.getType())) return false;
        plugin.message(p, "item-blocked", "%item%", Tokens.key(item.getType()));
        return true;
    }

    private static boolean consumable(Material m) {
        return m.isEdible() || m == Material.POTION || m == Material.MILK_BUCKET
                || m == Material.HONEY_BOTTLE || m == Material.OMINOUS_BOTTLE;
    }

    // ---- right / left clicks: item use, spear charge, bows, buckets, block interaction ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        Block clicked = e.getClickedBlock();
        Action action = e.getAction();

        if (clicked != null && (action == Action.RIGHT_CLICK_BLOCK || action == Action.PHYSICAL)
                && listed(p, clicked.getLocation(), Flags.DENY_BLOCK_USE, clicked.getType())) {
            if (action == Action.PHYSICAL) {
                e.setCancelled(true);
                return;
            }
            e.setUseInteractedBlock(Event.Result.DENY);
            plugin.message(p, "block-use-denied", "%block%", Tokens.key(clicked.getType()));
        }

        if (item == null || action == Action.PHYSICAL) return;

        if (blocked(p, item)) {
            e.setUseItemInHand(Event.Result.DENY);
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) e.setCancelled(true);
            return;
        }

        // Cooldowns: only on right-click "use". Eating/drinking is handled on consume and block
        // placing on place, so they don't trip the cooldown before the action happens.
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Material m = item.getType();
        if (consumable(m) || m.isBlock()) return;
        if (e.useItemInHand() == Event.Result.DENY) return;
        if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.getType().isInteractable()
                && !p.isSneaking() && e.useInteractedBlock() != Event.Result.DENY) {
            return; // they're opening a door/chest, not using the item
        }
        if (!plugin.cooldowns().tryUse(p, m)) e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        ItemStack item = e.getHand() == EquipmentSlot.OFF_HAND
                ? e.getPlayer().getInventory().getItemInOffHand()
                : e.getPlayer().getInventory().getItemInMainHand();
        if (blocked(e.getPlayer(), item)) e.setCancelled(true);
    }

    // ---- melee (spear jab, mace smash, swords...) ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return;
        if (blocked(p, hand)) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.cooldowns().tryUse(p, hand.getType())) e.setCancelled(true);
    }

    // ---- eating / drinking ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (blocked(e.getPlayer(), e.getItem())) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.cooldowns().tryUse(e.getPlayer(), e.getItem().getType())) e.setCancelled(true);
    }

    // ---- projectiles ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent e) {
        if (blocked(e.getPlayer(), e.getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (blocked(p, e.getBow()) || blocked(p, e.getConsumable())) e.setCancelled(true);
    }

    // ---- elytra / totem ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!e.isGliding() || !(e.getEntity() instanceof Player p)) return;
        if (blocked(p, p.getInventory().getChestplate())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getHand() == null) return;
        if (blocked(p, p.getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    // ---- blocks ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (blocked(p, e.getItemInHand())) {
            e.setCancelled(true);
            return;
        }
        Block b = e.getBlockPlaced();
        if (listed(p, b.getLocation(), Flags.DENY_BLOCK_PLACE, b.getType())) {
            e.setCancelled(true);
            plugin.message(p, "block-place-denied", "%block%", Tokens.key(b.getType()));
            return;
        }
        if (!plugin.cooldowns().tryUse(p, e.getItemInHand().getType())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (listed(p, b.getLocation(), Flags.DENY_BLOCK_BREAK, b.getType())) {
            e.setCancelled(true);
            plugin.message(p, "block-break-denied", "%block%", Tokens.key(b.getType()));
            return;
        }
        if (blocked(p, p.getInventory().getItemInMainHand())) e.setCancelled(true);
    }
}
