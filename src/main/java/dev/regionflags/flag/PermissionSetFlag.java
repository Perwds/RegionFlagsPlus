package dev.regionflags.flag;

import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Permissions granted while inside. Prefix with '-' to force a permission to false. */
public class PermissionSetFlag extends TokenSetFlag {

    private static List<String> cache = List.of();
    private static long cacheTime;

    public PermissionSetFlag(String name) {
        super(name);
    }

    @Override
    protected Collection<String> expand(String token, Player player) throws InvalidFlagFormat {
        String perm = token.toLowerCase(Locale.ROOT);
        String bare = perm.startsWith("-") ? perm.substring(1) : perm;
        if (bare.isEmpty() || !bare.matches("[a-z0-9_.*\\-]+")) {
            throw new InvalidFlagFormat("Bad permission: " + token);
        }
        return List.of(perm);
    }

    @Override
    public List<String> suggestions(Player player) {
        return knownPermissions();
    }

    /** Every permission registered by plugins plus every command's permission. Cached 30s. */
    public static synchronized List<String> knownPermissions() {
        long now = System.currentTimeMillis();
        if (now - cacheTime < 30_000 && !cache.isEmpty()) return cache;
        TreeSet<String> perms = new TreeSet<>();
        for (Permission p : Bukkit.getPluginManager().getPermissions()) {
            perms.add(p.getName().toLowerCase(Locale.ROOT));
        }
        for (Command c : Bukkit.getCommandMap().getKnownCommands().values()) {
            String p = c.getPermission();
            if (p == null) continue;
            for (String s : p.split(";")) {
                if (!s.isBlank()) perms.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        cache = new ArrayList<>(perms);
        cacheTime = now;
        return cache;
    }
}
