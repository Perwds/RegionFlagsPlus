package dev.regionflags.listener;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.regionflags.RegionFlagsPlus;
import dev.regionflags.flag.Flags;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/** keep-inventory/xp, no-hunger, pearl/chorus escape blocking, cleanup on quit. */
public final class MiscListener implements Listener {

    private final RegionFlagsPlus plugin;

    public MiscListener(RegionFlagsPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        LocalPlayer lp = RegionFlagsPlus.wrap(p);
        ApplicableRegionSet set = RegionFlagsPlus.regionsAt(p.getLocation());
        if (set.queryState(lp, Flags.REGION_KEEP_INVENTORY) == StateFlag.State.ALLOW) {
            e.setKeepInventory(true);
            e.getDrops().clear();
        }
        if (set.queryState(lp, Flags.REGION_KEEP_XP) == StateFlag.State.ALLOW) {
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getFoodLevel() >= p.getFoodLevel()) return; // eating is fine
        ApplicableRegionSet set = RegionFlagsPlus.regionsAt(p.getLocation());
        if (set.queryState(RegionFlagsPlus.wrap(p), Flags.NO_HUNGER) == StateFlag.State.ALLOW) {
            e.setCancelled(true);
            p.setSaturation(Math.max(p.getSaturation(), 5f));
        }
    }

    /** Ender pearls and chorus fruit count as teleports to WorldGuard - treat them as walking instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        TeleportCause cause = e.getCause();
        boolean pearl = cause == TeleportCause.ENDER_PEARL;
        boolean chorus = cause == TeleportCause.CONSUMABLE_EFFECT; // chorus fruit since 1.21.2
        if (pearl && !plugin.getConfig().getBoolean("teleport-only.block-ender-pearls", true)) return;
        if (chorus && !plugin.getConfig().getBoolean("teleport-only.block-chorus-fruit", true)) return;
        if (!pearl && !chorus) return;

        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();

        if (!plugin.bypasses(p, Flags.EXIT_TELEPORT_ONLY)) {
            for (ProtectedRegion r : RegionFlagsPlus.regionsAt(from)) {
                if (r.getFlag(Flags.EXIT_TELEPORT_ONLY) == StateFlag.State.ALLOW && !inside(r, from, to)) {
                    e.setCancelled(true);
                    plugin.message(p, "exit-teleport-only");
                    return;
                }
            }
        }
        if (!plugin.bypasses(p, Flags.ENTRY_TELEPORT_ONLY)) {
            for (ProtectedRegion r : RegionFlagsPlus.regionsAt(to)) {
                if (r.getFlag(Flags.ENTRY_TELEPORT_ONLY) == StateFlag.State.ALLOW && !inside(r, to, from)) {
                    e.setCancelled(true);
                    plugin.message(p, "entry-teleport-only");
                    return;
                }
            }
        }
    }

    /** Is {@code other} inside region r (which contains {@code ref})? */
    private static boolean inside(ProtectedRegion r, Location ref, Location other) {
        return other.getWorld() != null && other.getWorld().equals(ref.getWorld())
                && r.contains(other.getBlockX(), other.getBlockY(), other.getBlockZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.states().clear(e.getPlayer());
        plugin.cooldowns().forget(e.getPlayer());
        plugin.forget(e.getPlayer());
    }
}
