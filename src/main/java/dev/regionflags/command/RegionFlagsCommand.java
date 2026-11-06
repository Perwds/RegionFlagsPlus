package dev.regionflags.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.regionflags.RegionFlagsPlus;
import dev.regionflags.flag.CommandListFlag;
import dev.regionflags.flag.CooldownFlag;
import dev.regionflags.flag.Flags;
import dev.regionflags.flag.MaterialSetFlag;
import dev.regionflags.flag.PermissionSetFlag;
import dev.regionflags.flag.SoundFlag;
import dev.regionflags.flag.TokenSetFlag;
import dev.regionflags.flag.Tokens;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * /wgf  set|add|remove|clear|info|flags|hand|reload
 * add/remove edit list flags without retyping the whole list, and support #hand, #hotbar, ...
 */
public final class RegionFlagsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("add", "remove", "set", "clear", "info", "flags", "hand", "reload", "help");
    private static final String GLOBAL = "__global__";

    private final RegionFlagsPlus plugin;

    public RegionFlagsCommand(RegionFlagsPlus plugin) {
        this.plugin = plugin;
    }

    private static void send(CommandSender s, String msg) {
        s.sendMessage(RegionFlagsPlus.color(msg));
    }

    private void help(CommandSender s, String label) {
        send(s, "&6&lRegionFlagsPlus &7- extra WorldGuard flags");
        send(s, "&e/" + label + " add <region> <flag> <values...> &7- add to a list flag");
        send(s, "&e/" + label + " remove <region> <flag> <values...> &7- remove from a list flag");
        send(s, "&e/" + label + " set <region> <flag> <value> &7- replace the value");
        send(s, "&e/" + label + " clear <region> <flag> &7- unset the flag");
        send(s, "&e/" + label + " info <region> &7- show this plugin's flags on a region");
        send(s, "&e/" + label + " flags &7- list every flag");
        send(s, "&e/" + label + " hand &7- show the names of the items you're holding");
        send(s, "&e/" + label + " reload &7- reload config.yml");
        send(s, "&7Shortcuts for item flags: &f#hand #offhand #hotbar #inventory #armor #target #tag:<tag> *_spear");
        send(s, "&7Add &f-w <world> &7to use another world. Normal &f/rg flag &7works too.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] rawArgs) {
        List<String> args = new ArrayList<>(Arrays.asList(rawArgs));
        World world = extractWorld(sender, args);
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args.get(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "flags" -> {
                send(sender, "&6Flags &7(use with /rg flag or /" + label + "):");
                Flags.ALL.forEach((f, d) -> send(sender, "&e" + f.getName() + " &7- " + d));
                return true;
            }
            case "reload" -> {
                plugin.reloadConfig();
                send(sender, "&aRegionFlagsPlus config reloaded.");
                return true;
            }
            case "hand" -> {
                if (!(sender instanceof Player p)) {
                    send(sender, "&cPlayers only.");
                    return true;
                }
                send(sender, "&7Main hand: &f" + name(p.getInventory().getItemInMainHand()));
                send(sender, "&7Off hand: &f" + name(p.getInventory().getItemInOffHand()));
                return true;
            }
            default -> { /* region commands below */ }
        }

        if (!List.of("add", "remove", "set", "clear", "info").contains(sub)) {
            help(sender, label);
            return true;
        }
        if (world == null) {
            send(sender, "&cFrom console add &f-w <world>&c.");
            return true;
        }
        if (args.size() < 2) {
            send(sender, "&cUsage: /" + label + " " + sub + " <region>" + (sub.equals("info") ? "" : " <flag> ..."));
            return true;
        }
        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (rm == null) {
            send(sender, "&cRegions are disabled in that world.");
            return true;
        }
        String id = args.get(1);
        ProtectedRegion region = rm.getRegion(id);
        if (region == null && id.equalsIgnoreCase(GLOBAL)) {
            region = new GlobalProtectedRegion(GLOBAL);
            rm.addRegion(region);
        }
        if (region == null) {
            send(sender, "&cNo region '" + id + "' in " + world.getName() + ".");
            return true;
        }

        if (sub.equals("info")) {
            send(sender, "&6RegionFlagsPlus flags on &e" + region.getId() + "&6:");
            boolean any = false;
            for (Flag<?> f : Flags.ALL.keySet()) {
                Object v = region.getFlag(f);
                if (v == null) continue;
                any = true;
                send(sender, "&e" + f.getName() + "&7: &f" + v);
            }
            if (!any) send(sender, "&7(none)");
            return true;
        }

        if (args.size() < 3) {
            send(sender, "&cMissing flag name. See /" + label + " flags");
            return true;
        }
        Flag<?> flag = Flags.byName(args.get(2));
        if (flag == null) {
            send(sender, "&cUnknown flag '" + args.get(2) + "'. See /" + label + " flags");
            return true;
        }
        String input = String.join(" ", args.subList(3, args.size())).trim();
        Player player = sender instanceof Player p ? p : null;

        try {
            switch (sub) {
                case "clear" -> {
                    region.setFlag(flag, null);
                    send(sender, "&aCleared &e" + flag.getName() + " &aon &e" + region.getId());
                }
                case "set" -> {
                    requireInput(input);
                    setParsed(sender, region, flag, input);
                    send(sender, "&aSet &e" + flag.getName() + " &aon &e" + region.getId() + "&a to &f" + region.getFlag(flag));
                }
                case "add" -> {
                    requireInput(input);
                    add(region, flag, input, player);
                    send(sender, "&aAdded. &e" + flag.getName() + "&7 = &f" + region.getFlag(flag));
                }
                case "remove" -> {
                    requireInput(input);
                    remove(region, flag, input, player);
                    Object now = region.getFlag(flag);
                    send(sender, "&aRemoved. &e" + flag.getName() + "&7 = &f" + (now == null ? "(unset)" : now));
                }
                default -> { }
            }
        } catch (InvalidFlagFormat e) {
            send(sender, "&c" + e.getMessage());
        }
        return true;
    }

    private static void requireInput(String input) throws InvalidFlagFormat {
        if (input.isEmpty()) throw new InvalidFlagFormat("Give a value.");
    }

    private static String name(ItemStack it) {
        return it == null || it.getType().isAir() ? "(empty)" : Tokens.key(it.getType());
    }

    private static <T> void setParsed(CommandSender sender, ProtectedRegion region, Flag<T> flag, String input) throws InvalidFlagFormat {
        FlagContext ctx = FlagContext.create()
                .setSender(WorldGuardPlugin.inst().wrapCommandSender(sender))
                .setInput(input)
                .setObject("region", region)
                .build();
        T value = flag.parseInput(ctx);
        region.setFlag(flag, value);
    }

    @SuppressWarnings("unchecked")
    private static void add(ProtectedRegion region, Flag<?> flag, String input, Player player) throws InvalidFlagFormat {
        if (flag instanceof TokenSetFlag f) {
            Set<String> cur = region.getFlag(f);
            Set<String> next = cur == null ? new LinkedHashSet<>() : new LinkedHashSet<>(cur);
            next.addAll(f.parse(input, player));
            region.setFlag(f, next);
        } else if (flag instanceof CooldownFlag f) {
            Map<String, Double> cur = region.getFlag(f);
            Map<String, Double> next = cur == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cur);
            next.putAll(CooldownFlag.parse(input, player));
            region.setFlag(f, next);
        } else if (flag instanceof CommandListFlag f) {
            List<String> cur = region.getFlag(f);
            List<String> next = cur == null ? new ArrayList<>() : new ArrayList<>(cur);
            next.addAll(CommandListFlag.parse(input));
            region.setFlag(f, next);
        } else {
            throw new InvalidFlagFormat(flag.getName() + " isn't a list - use set instead.");
        }
    }

    private static void remove(ProtectedRegion region, Flag<?> flag, String input, Player player) throws InvalidFlagFormat {
        if (flag instanceof TokenSetFlag f) {
            Set<String> cur = region.getFlag(f);
            if (cur == null) return;
            Set<String> next = new LinkedHashSet<>(cur);
            // Remove exact stored text first (so old/invalid entries can always be removed), then parsed values.
            Set<String> raw = new LinkedHashSet<>(Arrays.asList(input.toLowerCase(Locale.ROOT).split("[,\\s]+")));
            next.removeAll(raw);
            try {
                next.removeAll(f.parse(input, player));
            } catch (InvalidFlagFormat ignored) {
                // nothing parsable beyond the raw text
            }
            region.setFlag(f, next.isEmpty() ? null : next);
        } else if (flag instanceof CooldownFlag f) {
            Map<String, Double> cur = region.getFlag(f);
            if (cur == null) return;
            Map<String, Double> next = new LinkedHashMap<>(cur);
            next.keySet().removeAll(CooldownFlag.parseKeys(input, player));
            region.setFlag(f, next.isEmpty() ? null : next);
        } else if (flag instanceof CommandListFlag f) {
            List<String> cur = region.getFlag(f);
            if (cur == null) return;
            List<String> next = new ArrayList<>(cur);
            String target = input.startsWith("/") ? input.substring(1) : input;
            if (target.matches("#\\d+")) {
                int idx = Integer.parseInt(target.substring(1)) - 1;
                if (idx < 0 || idx >= next.size()) throw new InvalidFlagFormat("No command #" + (idx + 1));
                next.remove(idx);
            } else if (!next.remove(target)) {
                throw new InvalidFlagFormat("That command isn't in the list. Use #1, #2... to remove by number.");
            }
            region.setFlag(f, next.isEmpty() ? null : next);
        } else {
            throw new InvalidFlagFormat(flag.getName() + " isn't a list - use clear instead.");
        }
    }

    /** Pulls "-w <world>" out of the args. Defaults to the player's world. */
    private static World extractWorld(CommandSender sender, List<String> args) {
        int i = args.indexOf("-w");
        if (i >= 0 && i + 1 < args.size()) {
            World w = Bukkit.getWorld(args.get(i + 1));
            args.remove(i + 1);
            args.remove(i);
            return w;
        }
        return sender instanceof Player p ? p.getWorld() : null;
    }

    // ---------------- tab completion ----------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] rawArgs) {
        List<String> args = new ArrayList<>(Arrays.asList(rawArgs));
        String last = args.isEmpty() ? "" : args.get(args.size() - 1);
        if (args.size() >= 2 && args.get(args.size() - 2).equals("-w")) {
            return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), last);
        }
        World world = extractWorld(sender, args);
        if (args.size() <= 1) return filter(SUBS, last);

        String sub = args.get(0).toLowerCase(Locale.ROOT);
        if (!List.of("add", "remove", "set", "clear", "info").contains(sub)) return List.of();
        RegionManager rm = world == null ? null
                : WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));

        if (args.size() == 2) {
            if (rm == null) return List.of();
            List<String> ids = new ArrayList<>(rm.getRegions().keySet());
            if (!ids.contains(GLOBAL)) ids.add(GLOBAL);
            return filter(ids, last);
        }
        if (sub.equals("info")) return List.of();
        if (args.size() == 3) {
            List<String> names = new ArrayList<>();
            for (Flag<?> f : Flags.ALL.keySet()) {
                boolean list = f instanceof TokenSetFlag || f instanceof CooldownFlag || f instanceof CommandListFlag;
                if ((sub.equals("add") || sub.equals("remove")) && !list) continue;
                names.add(f.getName());
            }
            return filter(names, last);
        }
        if (sub.equals("clear")) return List.of();

        Flag<?> flag = Flags.byName(args.get(2));
        if (flag == null) return List.of();
        Player player = sender instanceof Player p ? p : null;
        ProtectedRegion region = rm == null ? null : rm.getRegion(args.get(1));

        if (sub.equals("remove") && region != null) {
            Object cur = region.getFlag(flag);
            if (cur instanceof Map<?, ?> m) return filter(m.keySet().stream().map(Object::toString).toList(), last);
            if (cur instanceof Collection<?> c) {
                if (flag instanceof CommandListFlag) {
                    List<String> nums = new ArrayList<>();
                    for (int i = 1; i <= c.size(); i++) nums.add("#" + i);
                    return filter(nums, last);
                }
                return filter(c.stream().map(Object::toString).toList(), last);
            }
            return List.of();
        }

        if (flag instanceof TokenSetFlag f) {
            if (f instanceof MaterialSetFlag && last.startsWith("#tag:")) return filter(tagSuggestions(), last);
            List<String> base = f.suggestions(player);
            if (last.startsWith("-") && f == Flags.REGION_PERMISSIONS) {
                return filter(base.stream().map(s -> "-" + s).toList(), last);
            }
            return filter(base, last);
        }
        if (flag instanceof CooldownFlag) {
            if (last.contains(":") && !last.startsWith("#tag:")) {
                String item = last.substring(0, last.lastIndexOf(':') + 1);
                return List.of(item + "5", item + "10", item + "30", item + "60");
            }
            if (last.startsWith("#tag:")) return filter(tagSuggestions(), last);
            return filter(CooldownFlag.suggestions(), last);
        }
        if (flag instanceof StateFlag) return filter(List.of("allow", "deny"), last);
        if (flag == Flags.REGION_WALK_SPEED) return filter(List.of("0.1", "0.2", "0.3", "0.4", "0.5"), last);
        if (flag == Flags.REGION_PREFIX || flag == Flags.REGION_SUFFIX) return filter(List.of("&7[&aSpawn&7]&r "), last);
        if (flag instanceof CommandListFlag) return filter(List.of("say %player% entered %region%", "player:"), last);
        if (flag == Flags.ENTRY_PERMISSION) return filter(PermissionSetFlag.knownPermissions(), last);
        if (flag == Flags.REGION_MAX_PLAYERS) return filter(List.of("1", "2", "4", "8", "16"), last);
        if (flag instanceof SoundFlag) {
            if (last.matches(".*[a-z]:[0-9.]*$") && !last.startsWith("minecraft:")) {
                String base = last.substring(0, last.indexOf(':') + 1);
                return List.of(base + "1", base + "0.5", base + "1:0.8", base + "1:1.5");
            }
            return filter(SoundFlag.suggestions(), last);
        }
        return List.of();
    }

    private static List<String> tagSuggestions() {
        List<String> out = new ArrayList<>();
        for (var t : Bukkit.getTags(org.bukkit.Tag.REGISTRY_ITEMS, org.bukkit.Material.class)) {
            out.add("#tag:" + t.getKey().getKey());
        }
        for (var t : Bukkit.getTags(org.bukkit.Tag.REGISTRY_BLOCKS, org.bukkit.Material.class)) {
            String s = "#tag:" + t.getKey().getKey();
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        // Comma-separated input: complete only the part after the last comma.
        int comma = p.lastIndexOf(',');
        String head = comma >= 0 ? prefix.substring(0, comma + 1) : "";
        String tail = comma >= 0 ? p.substring(comma + 1) : p;
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(tail)) {
                out.add(head + o);
                if (out.size() >= 200) break;
            }
        }
        return out;
    }
}
