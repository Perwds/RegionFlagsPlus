package dev.regionflags.session;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import dev.regionflags.RegionFlagsPlus;
import dev.regionflags.flag.Flags;
import dev.regionflags.flag.SoundFlag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/** WorldGuard session handler: one per online player, notified whenever they cross a region border. */
public final class RegionFlagsHandler extends Handler {

    public static final Factory FACTORY = new Factory();

    public static final class Factory extends Handler.Factory<RegionFlagsHandler> {
        @Override
        public RegionFlagsHandler create(Session session) {
            return new RegionFlagsHandler(session);
        }
    }

    private RegionFlagsHandler(Session session) {
        super(session);
    }

    @Override
    public void initialize(LocalPlayer lp, Location current, ApplicableRegionSet set) {
        Player p = Bukkit.getPlayer(lp.getUniqueId());
        if (p != null) RegionFlagsPlus.get().states().update(p, lp, set);
    }

    @Override
    public boolean onCrossBoundary(LocalPlayer lp, Location from, Location to, ApplicableRegionSet toSet,
                                   Set<ProtectedRegion> entered, Set<ProtectedRegion> exited, MoveType moveType) {
        RegionFlagsPlus plugin = RegionFlagsPlus.get();
        Player p = Bukkit.getPlayer(lp.getUniqueId());
        if (p == null) return true;

        if (moveType.isCancellable() && !moveType.isTeleport()) {
            for (ProtectedRegion r : exited) {
                if (r.getFlag(Flags.EXIT_TELEPORT_ONLY) == StateFlag.State.ALLOW
                        && !plugin.bypasses(p, Flags.EXIT_TELEPORT_ONLY)) {
                    plugin.message(p, "exit-teleport-only");
                    return false;
                }
            }
            for (ProtectedRegion r : entered) {
                if (r.getFlag(Flags.ENTRY_TELEPORT_ONLY) == StateFlag.State.ALLOW
                        && !plugin.bypasses(p, Flags.ENTRY_TELEPORT_ONLY)) {
                    plugin.message(p, "entry-teleport-only");
                    return false;
                }
            }
        }

        // Permission and capacity apply to teleports too, so /tpa can't be used to get into the police station.
        if (moveType.isCancellable()) {
            for (ProtectedRegion r : entered) {
                String perm = r.getFlag(Flags.ENTRY_PERMISSION);
                if (perm != null && !perm.isBlank() && !p.hasPermission(perm.trim())
                        && !plugin.bypasses(p, Flags.ENTRY_PERMISSION)) {
                    plugin.message(p, "entry-permission", "%region%", r.getId());
                    return false;
                }
                Integer max = r.getFlag(Flags.REGION_MAX_PLAYERS);
                if (max != null && countInside(r, p) >= max && !plugin.bypasses(p, Flags.REGION_MAX_PLAYERS)) {
                    plugin.message(p, "region-full", "%region%", r.getId(), "%max%", String.valueOf(max));
                    return false;
                }
            }
        }

        plugin.states().update(p, lp, toSet);

        for (ProtectedRegion r : exited) {
            runCommands(p, r, r.getFlag(Flags.EXIT_COMMANDS));
            SoundFlag.play(p, r.getFlag(Flags.EXIT_SOUND));
        }
        for (ProtectedRegion r : entered) {
            runCommands(p, r, r.getFlag(Flags.ENTER_COMMANDS));
            SoundFlag.play(p, r.getFlag(Flags.ENTER_SOUND));
        }
        return true;
    }

    /** Players other than {@code self} currently standing in the region. */
    private static int countInside(ProtectedRegion r, Player self) {
        int n = 0;
        for (Player o : self.getWorld().getPlayers()) {
            if (o.equals(self)) continue;
            org.bukkit.Location l = o.getLocation();
            if (r.contains(l.getBlockX(), l.getBlockY(), l.getBlockZ())) n++;
        }
        return n;
    }

    @Override
    public void tick(LocalPlayer lp, ApplicableRegionSet set) {
        // Re-apply every tick of the session (about once a second) so milk, deaths,
        // /effect clear or other plugins can't remove region effects.
        Player p = Bukkit.getPlayer(lp.getUniqueId());
        if (p != null && !p.isDead()) RegionFlagsPlus.get().states().update(p, lp, set);
    }

    private static void runCommands(Player p, ProtectedRegion region, List<String> commands) {
        if (commands == null || commands.isEmpty()) return;
        RegionFlagsPlus plugin = RegionFlagsPlus.get();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            for (String raw : commands) {
                String cmd = raw.replace("%player%", p.getName())
                        .replace("%uuid%", p.getUniqueId().toString())
                        .replace("%region%", region.getId())
                        .replace("%world%", p.getWorld().getName());
                if (cmd.regionMatches(true, 0, "player:", 0, 7)) {
                    p.performCommand(cmd.substring(7).trim());
                } else {
                    if (cmd.regionMatches(true, 0, "console:", 0, 8)) cmd = cmd.substring(8).trim();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
        });
    }
}
