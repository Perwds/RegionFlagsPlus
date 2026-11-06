package dev.regionflags.listener;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import dev.regionflags.RegionFlagsPlus;
import dev.regionflags.flag.Flags;
import dev.regionflags.flag.Tokens;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player, per-item cooldowns driven by the item-cooldowns flag. */
public final class CooldownManager {

    private final RegionFlagsPlus plugin;
    private final Map<UUID, Map<Material, Long>> readyAt = new HashMap<>();

    public CooldownManager(RegionFlagsPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns false (and tells the player) if the item is still cooling down here.
     * Otherwise starts the cooldown and returns true.
     */
    public boolean tryUse(Player p, Material m) {
        if (m == null || m.isAir()) return true;
        LocalPlayer lp = RegionFlagsPlus.wrap(p);
        ApplicableRegionSet set = RegionFlagsPlus.regionsAt(p.getLocation());
        Double secs = null;
        for (Map<String, Double> map : set.queryAllValues(lp, Flags.ITEM_COOLDOWNS)) {
            if (map == null) continue;
            Double v = map.get(Tokens.key(m));
            if (v != null && (secs == null || v > secs)) secs = v;
        }
        if (secs == null || plugin.bypasses(p, Flags.ITEM_COOLDOWNS)) return true;

        long now = System.currentTimeMillis();
        Map<Material, Long> mine = readyAt.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Long ready = mine.get(m);
        if (ready != null && ready > now) {
            String left = String.format("%.1f", (ready - now) / 1000.0);
            plugin.message(p, "item-cooldown", "%item%", Tokens.key(m), "%time%", left);
            return false;
        }
        mine.put(m, now + (long) (secs * 1000));
        int ticks = (int) Math.ceil(secs * 20);
        // Next tick: setting it now would make vanilla refuse this very use.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.setCooldown(m, ticks);
        });
        return true;
    }

    public void forget(Player p) {
        readyAt.remove(p.getUniqueId());
    }
}
