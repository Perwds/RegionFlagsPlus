package dev.regionflags.flag;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.DoubleFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/** All custom flags. Names avoid WorldGuardExtraFlags' names so both plugins can run together. */
public final class Flags {

    // Region state (applied while inside, undone on leave)
    public static EffectSetFlag REGION_EFFECTS = new EffectSetFlag("region-effects");
    public static EffectSetFlag REGION_BLOCKED_EFFECTS = new EffectSetFlag("region-blocked-effects");
    public static StringFlag REGION_PREFIX = new StringFlag("region-prefix");
    public static StringFlag REGION_SUFFIX = new StringFlag("region-suffix");
    public static PermissionSetFlag REGION_PERMISSIONS = new PermissionSetFlag("region-permissions");
    public static StateFlag REGION_FLY = new StateFlag("region-fly", false);
    public static DoubleFlag REGION_WALK_SPEED = new DoubleFlag("region-walk-speed");

    // Movement
    public static StateFlag EXIT_TELEPORT_ONLY = new StateFlag("exit-teleport-only", false);
    public static StateFlag ENTRY_TELEPORT_ONLY = new StateFlag("entry-teleport-only", false);
    public static CommandListFlag ENTER_COMMANDS = new CommandListFlag("enter-commands");
    public static CommandListFlag EXIT_COMMANDS = new CommandListFlag("exit-commands");
    public static StringFlag ENTRY_PERMISSION = new StringFlag("entry-permission");
    public static IntegerFlag REGION_MAX_PLAYERS = new IntegerFlag("region-max-players");
    public static SoundFlag ENTER_SOUND = new SoundFlag("enter-sound");
    public static SoundFlag EXIT_SOUND = new SoundFlag("exit-sound");

    // Items / blocks
    public static MaterialSetFlag BLOCKED_ITEMS = new MaterialSetFlag("blocked-items", false);
    public static CooldownFlag ITEM_COOLDOWNS = new CooldownFlag("item-cooldowns");
    public static MaterialSetFlag DENY_BLOCK_PLACE = new MaterialSetFlag("deny-block-place", true);
    public static MaterialSetFlag DENY_BLOCK_BREAK = new MaterialSetFlag("deny-block-break", true);
    public static MaterialSetFlag DENY_BLOCK_USE = new MaterialSetFlag("deny-block-use", true);

    // Misc
    public static StateFlag REGION_KEEP_INVENTORY = new StateFlag("region-keep-inventory", false);
    public static StateFlag REGION_KEEP_XP = new StateFlag("region-keep-xp", false);
    public static StateFlag NO_HUNGER = new StateFlag("no-hunger", false);

    /** name -> flag, with a one-line description used by /wgf flags. */
    public static final Map<Flag<?>, String> ALL = new LinkedHashMap<>();

    private Flags() {}

    public static void register(Logger log) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        REGION_EFFECTS = reg(registry, log, REGION_EFFECTS, "Infinite potion effects while inside, e.g. speed:2,night_vision");
        REGION_BLOCKED_EFFECTS = reg(registry, log, REGION_BLOCKED_EFFECTS, "Potion effects stripped while inside, e.g. invisibility");
        REGION_PREFIX = reg(registry, log, REGION_PREFIX, "LuckPerms prefix while inside (restored on leave)");
        REGION_SUFFIX = reg(registry, log, REGION_SUFFIX, "LuckPerms suffix while inside (restored on leave)");
        REGION_PERMISSIONS = reg(registry, log, REGION_PERMISSIONS, "Permissions given while inside (-perm to deny one)");
        REGION_FLY = reg(registry, log, REGION_FLY, "allow = can fly inside, deny = flight disabled inside");
        REGION_WALK_SPEED = reg(registry, log, REGION_WALK_SPEED, "Walk speed inside (vanilla default 0.2, max 1.0)");
        EXIT_TELEPORT_ONLY = reg(registry, log, EXIT_TELEPORT_ONLY, "allow = can only leave by teleporting");
        ENTRY_TELEPORT_ONLY = reg(registry, log, ENTRY_TELEPORT_ONLY, "allow = can only enter by teleporting");
        ENTER_COMMANDS = reg(registry, log, ENTER_COMMANDS, "Commands run on enter, split by ; (player: prefix = run as player)");
        EXIT_COMMANDS = reg(registry, log, EXIT_COMMANDS, "Commands run on leave, split by ; (player: prefix = run as player)");
        ENTRY_PERMISSION = reg(registry, log, ENTRY_PERMISSION, "Only players with this permission can get in, e.g. rp.police");
        REGION_MAX_PLAYERS = reg(registry, log, REGION_MAX_PLAYERS, "Most players allowed inside at once");
        ENTER_SOUND = reg(registry, log, ENTER_SOUND, "Sound played on enter, sound:volume:pitch, e.g. block.bell.use:1:0.8");
        EXIT_SOUND = reg(registry, log, EXIT_SOUND, "Sound played on leave, sound:volume:pitch");
        BLOCKED_ITEMS = reg(registry, log, BLOCKED_ITEMS, "Items that don't work inside (use, attack, place, eat, shoot)");
        ITEM_COOLDOWNS = reg(registry, log, ITEM_COOLDOWNS, "Per-item cooldown in seconds, e.g. ender_pearl:10");
        DENY_BLOCK_PLACE = reg(registry, log, DENY_BLOCK_PLACE, "Blocks that can't be placed inside");
        DENY_BLOCK_BREAK = reg(registry, log, DENY_BLOCK_BREAK, "Blocks that can't be broken inside");
        DENY_BLOCK_USE = reg(registry, log, DENY_BLOCK_USE, "Blocks that can't be right-clicked/stepped on inside");
        REGION_KEEP_INVENTORY = reg(registry, log, REGION_KEEP_INVENTORY, "allow = keep inventory on death inside");
        REGION_KEEP_XP = reg(registry, log, REGION_KEEP_XP, "allow = keep XP on death inside");
        NO_HUNGER = reg(registry, log, NO_HUNGER, "allow = hunger doesn't drop inside");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Flag<?>> T reg(FlagRegistry registry, Logger log, T flag, String description) {
        try {
            registry.register(flag);
            ALL.put(flag, description);
            return flag;
        } catch (FlagConflictException e) {
            log.severe("Flag '" + flag.getName() + "' is already registered (another plugin, or /reload was used"
                    + " - restart the server instead). It will not work.");
            return flag;
        } catch (IllegalStateException e) {
            log.severe("Could not register flag '" + flag.getName() + "': " + e.getMessage()
                    + " (plugins must not be loaded after WorldGuard has started)");
            return flag;
        }
    }

    public static Flag<?> byName(String name) {
        for (Flag<?> f : ALL.keySet()) if (f.getName().equalsIgnoreCase(name)) return f;
        return null;
    }
}
