package dev.regionflags.flag;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A sound played to the player, as "sound[:volume[:pitch]]", e.g. block.bell.use:1:0.8.
 * Any sound key works, including custom resource-pack sounds.
 */
public class SoundFlag extends Flag<String> {

    public record Entry(String sound, float volume, float pitch) {}

    public SoundFlag(String name) {
        super(name);
    }

    @Override
    public String parseInput(FlagContext context) throws InvalidFlagFormat {
        String input = context.getUserInput().trim().toLowerCase(Locale.ROOT);
        if (decode(input) == null) {
            throw new InvalidFlagFormat("Use sound:volume:pitch, e.g. block.bell.use:1:0.8 (got '" + input + "')");
        }
        return input;
    }

    /** Parse a stored value; returns null when invalid. */
    public static Entry decode(String value) {
        if (value == null) return null;
        String[] parts = value.trim().toLowerCase(Locale.ROOT).split(":");
        // Trailing numbers are volume and pitch; everything before is the (possibly namespaced) sound key.
        int end = parts.length;
        float pitch = 1f;
        float volume = 1f;
        Float last = number(parts[end - 1]);
        if (end > 1 && last != null) {
            end--;
            Float prev = number(parts[end - 1]);
            if (end > 1 && prev != null) {
                end--;
                volume = prev;
                pitch = last;
            } else {
                volume = last;
            }
        }
        String sound = String.join(":", Arrays.copyOf(parts, end));
        if (sound.isEmpty() || !sound.matches("([a-z0-9_.\\-]+:)?[a-z0-9_./\\-]+")) return null;
        if (volume <= 0 || pitch < 0.5f || pitch > 2f) return null;
        return new Entry(sound, volume, pitch);
    }

    private static Float number(String s) {
        if (!s.matches("[0-9]*\\.?[0-9]+")) return null;
        return Float.parseFloat(s);
    }

    public static void play(Player p, String value) {
        Entry e = decode(value);
        if (e != null) p.playSound(p.getLocation(), e.sound(), e.volume(), e.pitch());
    }

    public static List<String> suggestions() {
        List<String> out = new ArrayList<>();
        for (Sound s : Registry.SOUND_EVENT) out.add(Registry.SOUND_EVENT.getKeyOrThrow(s).getKey());
        return out;
    }

    @Override
    public String unmarshal(Object o) {
        return o == null ? null : o.toString();
    }

    @Override
    public Object marshal(String value) {
        return value;
    }
}
