package dev.regionflags.flag;

import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Potion effects as "effect:level", e.g. speed:2, night_vision (level defaults to 1). */
public class EffectSetFlag extends TokenSetFlag {

    public EffectSetFlag(String name) {
        super(name);
    }

    public record Entry(PotionEffectType type, int amplifier) {}

    /** Parse a stored value; returns null when invalid. */
    public static Entry decode(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        if (v.startsWith("minecraft:")) v = v.substring(10);
        String[] parts = v.split(":");
        NamespacedKey key = NamespacedKey.fromString(parts[0]);
        PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
        if (type == null) return null;
        int level = 1;
        if (parts.length > 1) {
            try {
                level = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (level < 1 || level > 256) return null;
        return new Entry(type, level - 1);
    }

    @Override
    protected Collection<String> expand(String token, Player player) throws InvalidFlagFormat {
        Entry e = decode(token);
        if (e == null) throw new InvalidFlagFormat("Bad effect '" + token + "'. Use effect:level, e.g. speed:2");
        return List.of(e.type().getKey().getKey() + ":" + (e.amplifier() + 1));
    }

    @Override
    public List<String> suggestions(Player player) {
        List<String> out = new ArrayList<>();
        for (PotionEffectType t : Registry.EFFECT) {
            String k = t.getKey().getKey();
            out.add(k + ":1");
            out.add(k + ":2");
            out.add(k + ":3");
        }
        return out;
    }
}
