package dev.regionflags.session;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import dev.regionflags.RegionFlagsPlus;
import dev.regionflags.flag.EffectSetFlag;
import dev.regionflags.flag.Flags;
import dev.regionflags.hook.PrefixHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Tracks everything a region gave a player (effects, prefix, permissions, flight, speed) so it
 * can be undone exactly when they leave. What we applied is also written to the player's
 * persistent data so it can be cleaned up after a crash.
 */
public final class PlayerStateManager {

    private static final class State {
        Map<PotionEffectType, Integer> effects = new HashMap<>();
        String prefix;
        String suffix;
        Set<String> perms = Set.of();
        PermissionAttachment attachment;
        boolean flyGranted;
        boolean flyRevoked;
        Float originalWalkSpeed;
    }

    private final RegionFlagsPlus plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final NamespacedKey effectsKey;
    private final NamespacedKey flyKey;
    private final NamespacedKey walkKey;

    public PlayerStateManager(RegionFlagsPlus plugin) {
        this.plugin = plugin;
        this.effectsKey = new NamespacedKey(plugin, "effects");
        this.flyKey = new NamespacedKey(plugin, "fly");
        this.walkKey = new NamespacedKey(plugin, "walk");
    }

    /** Bring the player's state in line with the regions they're standing in. */
    public void update(Player p, LocalPlayer lp, ApplicableRegionSet set) {
        State st = states.get(p.getUniqueId());
        if (st == null) {
            cleanupStale(p);
            st = new State();
            states.put(p.getUniqueId(), st);
        }
        updateEffects(p, st, set, lp);
        stripBlockedEffects(p, st, set, lp);
        updatePrefix(p, st, set, lp);
        updatePermissions(p, st, set, lp);
        updateFly(p, st, set, lp);
        updateWalkSpeed(p, st, set, lp);
        save(p, st);
    }

    // ---- potion effects ----

    private void updateEffects(Player p, State st, ApplicableRegionSet set, LocalPlayer lp) {
        Map<PotionEffectType, Integer> want = new HashMap<>();
        for (String v : RegionFlagsPlus.union(set, lp, Flags.REGION_EFFECTS)) {
            EffectSetFlag.Entry e = EffectSetFlag.decode(v);
            if (e != null) want.merge(e.type(), e.amplifier(), Math::max);
        }
        for (Map.Entry<PotionEffectType, Integer> old : st.effects.entrySet()) {
            if (!Objects.equals(want.get(old.getKey()), old.getValue())) removeIfOurs(p, old.getKey(), old.getValue());
        }
        boolean particles = plugin.getConfig().getBoolean("region-effects.particles", false);
        boolean icon = plugin.getConfig().getBoolean("region-effects.icon", true);
        for (Map.Entry<PotionEffectType, Integer> w : want.entrySet()) {
            PotionEffect cur = p.getPotionEffect(w.getKey());
            if (cur != null && cur.isInfinite() && cur.getAmplifier() == w.getValue()) continue;
            // Let a stronger drunk potion run out first; we re-apply on a later tick.
            if (cur != null && !cur.isInfinite() && cur.getAmplifier() > w.getValue()) continue;
            p.addPotionEffect(new PotionEffect(w.getKey(), PotionEffect.INFINITE_DURATION, w.getValue(), true, particles, icon));
        }
        st.effects = want;
    }

    private static void removeIfOurs(Player p, PotionEffectType type, int amplifier) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.isInfinite() && cur.getAmplifier() == amplifier) p.removePotionEffect(type);
    }

    private void stripBlockedEffects(Player p, State st, ApplicableRegionSet set, LocalPlayer lp) {
        for (String v : RegionFlagsPlus.union(set, lp, Flags.REGION_BLOCKED_EFFECTS)) {
            EffectSetFlag.Entry e = EffectSetFlag.decode(v);
            if (e == null || st.effects.containsKey(e.type())) continue;
            if (p.hasPotionEffect(e.type())) p.removePotionEffect(e.type());
        }
    }

    // ---- LuckPerms prefix / suffix ----

    private void updatePrefix(Player p, State st, ApplicableRegionSet set, LocalPlayer lp) {
        PrefixHook hook = plugin.prefixHook();
        if (hook == null) return;
        String prefix = set.queryValue(lp, Flags.REGION_PREFIX);
        String suffix = set.queryValue(lp, Flags.REGION_SUFFIX);
        if (Objects.equals(prefix, st.prefix) && Objects.equals(suffix, st.suffix)) return;
        hook.apply(p, prefix, suffix);
        st.prefix = prefix;
        st.suffix = suffix;
    }

    // ---- permissions ----

    private void updatePermissions(Player p, State st, ApplicableRegionSet set, LocalPlayer lp) {
        Set<String> want = RegionFlagsPlus.union(set, lp, Flags.REGION_PERMISSIONS);
        if (want.equals(st.perms)) return;
        if (st.attachment != null) {
            try {
                p.removeAttachment(st.attachment);
            } catch (IllegalArgumentException ignored) {
                // already removed (e.g. player quit)
            }
            st.attachment = null;
        }
        if (!want.isEmpty()) {
            st.attachment = p.addAttachment(plugin);
            for (String perm : want) {
                if (perm.startsWith("-")) st.attachment.setPermission(perm.substring(1), false);
                else st.attachment.setPermission(perm, true);
            }
        }
        st.perms = want;
        p.updateCommands();
    }

    // ---- flight ----

    private static boolean survivalLike(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    private void updateFly(Player p, State st, ApplicableRegionSet set, LocalPlayer lp) {
        StateFlag.State fly = set.queryState(lp, Flags.REGION_FLY);
        if (fly == StateFlag.State.ALLOW) {
            if (st.flyRevoked) { // came straight from a deny region: they could fly before that
                st.flyRevoked = false;
                p.setAllowFlight(true);
            } else if (!p.getAllowFlight()) {
                p.setAllowFlight(true);
                st.flyGranted = true;
            }
        } else if (fly == StateFlag.State.DENY && survivalLike(p) && !plugin.bypasses(p, Flags.REGION_FLY)) {
            if (p.getAllowFlight() || p.isFlying()) {
                if (p.isFlying()) plugin.message(p, "fly-denied");
                p.setFlying(false);
                p.setAllowFlight(false);
                // flight we granted ourselves is simply dropped; otherwise remember to give it back
                if (st.flyGranted) st.flyGranted = false;
                else st.flyRevoked = true;
            }
        } else {
            if (st.flyGranted) {
                st.flyGranted = false;
                if (survivalLike(p)) {
                    if (p.isFlying()) {
                        // soften the landing
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, true, false, true));
                    }
                    p.setFlying(false);
                    p.setAllowFlight(false);
                }
            }
            if (st.flyRevoked) {
                st.flyRevoked = false;
                p.setAllowFlight(true);
            }
        }
    }

    // ---- walk speed ----

    private void updateWalkSpeed(Player p, State st, ApplicableRegionSet set, LocalPlayer lp) {
        Double speed = set.queryValue(lp, Flags.REGION_WALK_SPEED);
        if (speed != null) {
            float target = (float) Math.max(-1.0, Math.min(1.0, speed));
            if (st.originalWalkSpeed == null) st.originalWalkSpeed = p.getWalkSpeed();
            if (p.getWalkSpeed() != target) p.setWalkSpeed(target);
        } else if (st.originalWalkSpeed != null) {
            p.setWalkSpeed(st.originalWalkSpeed);
            st.originalWalkSpeed = null;
        }
    }

    // ---- lifecycle ----

    /** Undo everything (player left the server, or plugin disabling). */
    public void clear(Player p) {
        State st = states.remove(p.getUniqueId());
        if (st == null) return;
        for (Map.Entry<PotionEffectType, Integer> e : st.effects.entrySet()) removeIfOurs(p, e.getKey(), e.getValue());
        if (plugin.prefixHook() != null) plugin.prefixHook().clear(p);
        if (st.attachment != null) {
            try {
                p.removeAttachment(st.attachment);
            } catch (IllegalArgumentException ignored) {
                // already gone
            }
        }
        if (st.flyGranted && survivalLike(p)) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        if (st.flyRevoked) p.setAllowFlight(true);
        if (st.originalWalkSpeed != null) p.setWalkSpeed(st.originalWalkSpeed);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(effectsKey);
        pdc.remove(flyKey);
        pdc.remove(walkKey);
    }

    public void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        states.clear();
    }

    private void save(Player p, State st) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (st.effects.isEmpty()) {
            pdc.remove(effectsKey);
        } else {
            StringJoiner j = new StringJoiner(",");
            st.effects.forEach((t, a) -> j.add(t.getKey().getKey() + ":" + (a + 1)));
            pdc.set(effectsKey, PersistentDataType.STRING, j.toString());
        }
        byte fly = (byte) (st.flyGranted ? 1 : st.flyRevoked ? 2 : 0);
        if (fly == 0) pdc.remove(flyKey);
        else pdc.set(flyKey, PersistentDataType.BYTE, fly);
        if (st.originalWalkSpeed == null) pdc.remove(walkKey);
        else pdc.set(walkKey, PersistentDataType.FLOAT, st.originalWalkSpeed);
    }

    /** Remove leftovers from a previous session that didn't shut down cleanly. */
    private void cleanupStale(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String effects = pdc.get(effectsKey, PersistentDataType.STRING);
        if (effects != null) {
            for (String v : effects.split(",")) {
                EffectSetFlag.Entry e = EffectSetFlag.decode(v);
                if (e != null) removeIfOurs(p, e.type(), e.amplifier());
            }
        }
        Byte fly = pdc.get(flyKey, PersistentDataType.BYTE);
        if (fly != null && fly == 1 && survivalLike(p)) {
            p.setFlying(false);
            p.setAllowFlight(false);
        } else if (fly != null && fly == 2) {
            p.setAllowFlight(true);
        }
        Float walk = pdc.get(walkKey, PersistentDataType.FLOAT);
        if (walk != null) p.setWalkSpeed(walk);
        pdc.remove(effectsKey);
        pdc.remove(flyKey);
        pdc.remove(walkKey);
    }
}
