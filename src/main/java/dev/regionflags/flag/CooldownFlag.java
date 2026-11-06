package dev.regionflags.flag;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** item:seconds pairs, e.g. "ender_pearl:10, golden_apple:30, #hand:5, *_spear:3". */
public class CooldownFlag extends Flag<Map<String, Double>> {

    public CooldownFlag(String name) {
        super(name);
    }

    @Override
    public Map<String, Double> parseInput(FlagContext context) throws InvalidFlagFormat {
        return parse(context.getUserInput(), TokenSetFlag.playerOf(context));
    }

    public static Map<String, Double> parse(String input, Player player) throws InvalidFlagFormat {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String token : input.trim().split("[,\\s]+")) {
            if (token.isEmpty()) continue;
            int idx = token.lastIndexOf(':');
            if (idx <= 0 || idx == token.length() - 1) {
                throw new InvalidFlagFormat("Use item:seconds, e.g. ender_pearl:10 (got '" + token + "')");
            }
            double secs;
            try {
                secs = Double.parseDouble(token.substring(idx + 1));
            } catch (NumberFormatException e) {
                throw new InvalidFlagFormat("Bad seconds in '" + token + "'. Use item:seconds, e.g. ender_pearl:10");
            }
            if (secs <= 0) throw new InvalidFlagFormat("Seconds must be > 0 in '" + token + "'");
            for (String m : Tokens.expandMaterials(token.substring(0, idx), player, false)) out.put(m, secs);
        }
        if (out.isEmpty()) throw new InvalidFlagFormat("Nothing given.");
        return out;
    }

    /** For removal: items only, seconds optional. */
    public static Set<String> parseKeys(String input, Player player) throws InvalidFlagFormat {
        Set<String> out = new LinkedHashSet<>();
        for (String token : input.trim().split("[,\\s]+")) {
            if (token.isEmpty()) continue;
            int idx = token.lastIndexOf(':');
            if (idx > 0 && token.substring(idx + 1).matches("[0-9.]+")) token = token.substring(0, idx);
            out.addAll(Tokens.expandMaterials(token, player, false));
        }
        return out;
    }

    public static List<String> suggestions() {
        return Tokens.materialSuggestions(false).stream()
                .map(s -> s.endsWith(":") ? s : s + ":")
                .toList();
    }

    @Override
    public Map<String, Double> unmarshal(Object o) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                try {
                    out.put(e.getKey().toString(), Double.parseDouble(e.getValue().toString()));
                } catch (NumberFormatException ignored) {
                    // skip corrupt entry
                }
            }
        }
        return out;
    }

    @Override
    public Object marshal(Map<String, Double> value) {
        return new LinkedHashMap<>(value);
    }
}
