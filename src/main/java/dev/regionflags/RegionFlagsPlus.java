package dev.regionflags;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.session.SessionManager;
import dev.regionflags.command.RegionFlagsCommand;
import dev.regionflags.flag.Flags;
import dev.regionflags.hook.LuckPermsHook;
import dev.regionflags.hook.PrefixHook;
import dev.regionflags.listener.CooldownManager;
import dev.regionflags.listener.ItemBlockListener;
import dev.regionflags.listener.MiscListener;
import dev.regionflags.session.PlayerStateManager;
import dev.regionflags.session.RegionFlagsHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RegionFlagsPlus extends JavaPlugin {

    private static RegionFlagsPlus instance;

    private PrefixHook prefixHook;
    private PlayerStateManager states;
    private CooldownManager cooldowns;
    private final Map<UUID, Map<String, Long>> lastMessage = new HashMap<>();

    public static RegionFlagsPlus get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        Flags.register(getLogger());
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                prefixHook = new LuckPermsHook(this);
                getLogger().info("Hooked into LuckPerms - region-prefix/region-suffix enabled.");
            } catch (Throwable t) {
                getLogger().warning("LuckPerms found but hook failed: " + t);
            }
        } else {
            getLogger().info("LuckPerms not found - region-prefix/region-suffix will do nothing.");
        }

        states = new PlayerStateManager(this);
        cooldowns = new CooldownManager(this);

        SessionManager sessions = WorldGuard.getInstance().getPlatform().getSessionManager();
        sessions.registerHandler(RegionFlagsHandler.FACTORY, null);

        Bukkit.getPluginManager().registerEvents(new ItemBlockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MiscListener(this), this);

        RegionFlagsCommand cmd = new RegionFlagsCommand(this);
        PluginCommand pc = getCommand("regionflags");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        getLogger().info("Loaded " + Flags.ALL.size() + " custom flags.");
    }

    @Override
    public void onDisable() {
        if (states != null) states.clearAll();
        try {
            WorldGuard.getInstance().getPlatform().getSessionManager().unregisterHandler(RegionFlagsHandler.FACTORY);
        } catch (Throwable ignored) {
            // WorldGuard already gone
        }
    }

    public PrefixHook prefixHook() {
        return prefixHook;
    }

    public PlayerStateManager states() {
        return states;
    }

    public CooldownManager cooldowns() {
        return cooldowns;
    }

    // ---- WorldGuard helpers ----

    public static LocalPlayer wrap(Player p) {
        return WorldGuardPlugin.inst().wrapPlayer(p);
    }

    public static ApplicableRegionSet regionsAt(Location loc) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(loc));
    }

    /** Union of every region's value for a set flag at this location. */
    public static Set<String> union(ApplicableRegionSet set, LocalPlayer lp, Flag<Set<String>> flag) {
        Set<String> out = new LinkedHashSet<>();
        for (Collection<String> c : set.queryAllValues(lp, flag)) {
            if (c != null) out.addAll(c);
        }
        return out;
    }

    /** True when the player should ignore a restriction flag. */
    public boolean bypasses(Player p, Flag<?> flag) {
        if (p.hasPermission("regionflags.bypass." + flag.getName())) return true;
        if (!getConfig().getBoolean("respect-worldguard-bypass", true)) return false;
        return WorldGuard.getInstance().getPlatform().getSessionManager()
                .hasBypass(wrap(p), BukkitAdapter.adapt(p.getWorld()));
    }

    // ---- messages ----

    public static Component color(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    /** Action-bar message from config, throttled to once a second per message type. */
    public void message(Player p, String key, String... replacements) {
        // No explicit default, so keys missing from an older config.yml fall back to the jar's copy.
        String msg = getConfig().getString("messages." + key);
        if (msg == null || msg.isEmpty()) return;
        long now = System.currentTimeMillis();
        Map<String, Long> times = lastMessage.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Long last = times.get(key);
        if (last != null && now - last < 1000) return;
        times.put(key, now);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        p.sendActionBar(color(msg));
    }

    public void forget(Player p) {
        lastMessage.remove(p.getUniqueId());
    }
}
