package dev.regionflags.hook;

import org.bukkit.entity.Player;

/** Applies a temporary prefix/suffix. Null removes it. */
public interface PrefixHook {

    void apply(Player player, String prefix, String suffix);

    void clear(Player player);
}
