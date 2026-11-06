package dev.regionflags.flag;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Ordered list of commands separated by ';'. Each runs from console unless prefixed with "player:".
 * Placeholders: %player% %uuid% %region% %world%
 */
public class CommandListFlag extends Flag<List<String>> {

    public CommandListFlag(String name) {
        super(name);
    }

    @Override
    public List<String> parseInput(FlagContext context) throws InvalidFlagFormat {
        return parse(context.getUserInput());
    }

    public static List<String> parse(String input) throws InvalidFlagFormat {
        List<String> out = new ArrayList<>();
        for (String part : input.split(";")) {
            String cmd = part.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (!cmd.isEmpty()) out.add(cmd);
        }
        if (out.isEmpty()) throw new InvalidFlagFormat("No commands given. Separate multiple with ;");
        return out;
    }

    @Override
    public List<String> unmarshal(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof Collection<?> c) {
            for (Object v : c) if (v != null) out.add(v.toString());
        } else if (o != null) {
            out.add(o.toString());
        }
        return out;
    }

    @Override
    public Object marshal(List<String> value) {
        return new ArrayList<>(value);
    }
}
