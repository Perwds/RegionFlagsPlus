package dev.regionflags.flag;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A flag holding a set of strings. Input is split on commas/spaces and every token is
 * passed through {@link #expand}, so one token like {@code #hotbar} can become many values.
 */
public abstract class TokenSetFlag extends Flag<Set<String>> {

    protected TokenSetFlag(String name) {
        super(name);
    }

    /** Turn one user token into zero or more stored values. Throw for invalid input. */
    protected abstract Collection<String> expand(String token, Player player) throws InvalidFlagFormat;

    /** Values suggested by tab completion for this flag. */
    public abstract List<String> suggestions(Player player);

    @Override
    public Set<String> parseInput(FlagContext context) throws InvalidFlagFormat {
        return parse(context.getUserInput(), playerOf(context));
    }

    public Set<String> parse(String input, Player player) throws InvalidFlagFormat {
        Set<String> out = new LinkedHashSet<>();
        for (String token : input.trim().split("[,\\s]+")) {
            if (token.isEmpty()) continue;
            out.addAll(expand(token, player));
        }
        if (out.isEmpty()) throw new InvalidFlagFormat("Nothing matched '" + input + "'.");
        return out;
    }

    static Player playerOf(FlagContext context) {
        try {
            return Bukkit.getPlayer(context.getPlayerSender().getUniqueId());
        } catch (InvalidFlagFormat e) {
            return null; // console
        }
    }

    @Override
    public Set<String> unmarshal(Object o) {
        Set<String> out = new LinkedHashSet<>();
        if (o instanceof Collection<?> c) {
            for (Object v : c) if (v != null) out.add(v.toString());
        } else if (o != null) {
            out.add(o.toString());
        }
        return out;
    }

    @Override
    public Object marshal(Set<String> value) {
        return new ArrayList<>(value);
    }
}
