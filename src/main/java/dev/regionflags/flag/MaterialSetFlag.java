package dev.regionflags.flag;

import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** Set of item/block names. Supports #hand, #hotbar, #tag:, wildcards... */
public class MaterialSetFlag extends TokenSetFlag {

    private final boolean blocksOnly;

    public MaterialSetFlag(String name, boolean blocksOnly) {
        super(name);
        this.blocksOnly = blocksOnly;
    }

    @Override
    protected Collection<String> expand(String token, Player player) throws InvalidFlagFormat {
        return Tokens.expandMaterials(token, player, blocksOnly);
    }

    @Override
    public List<String> suggestions(Player player) {
        return Tokens.materialSuggestions(blocksOnly);
    }
}
