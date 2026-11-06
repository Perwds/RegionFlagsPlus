package dev.regionflags.hook;

import dev.regionflags.RegionFlagsPlus;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Uses LuckPerms *transient* nodes: they live only in memory, are never saved, and vanish on
 * logout - so a crash can never leave a region prefix stuck on someone.
 * Only loaded when LuckPerms is installed.
 */
public final class LuckPermsHook implements PrefixHook {

    private final RegionFlagsPlus plugin;
    private final LuckPerms lp;
    private final Map<UUID, Node> prefixes = new HashMap<>();
    private final Map<UUID, Node> suffixes = new HashMap<>();

    public LuckPermsHook(RegionFlagsPlus plugin) {
        this.plugin = plugin;
        this.lp = LuckPermsProvider.get();
    }

    @Override
    public void apply(Player player, String prefix, String suffix) {
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;
        int priority = plugin.getConfig().getInt("luckperms.priority", 10000);
        swap(user, prefixes, prefix == null ? null : PrefixNode.builder(prefix, priority).build());
        swap(user, suffixes, suffix == null ? null : SuffixNode.builder(suffix, priority).build());
    }

    private void swap(User user, Map<UUID, Node> store, Node next) {
        Node old = store.remove(user.getUniqueId());
        if (old != null) user.transientData().remove(old);
        if (next != null) {
            user.transientData().add(next);
            store.put(user.getUniqueId(), next);
        }
    }

    @Override
    public void clear(Player player) {
        User user = lp.getUserManager().getUser(player.getUniqueId());
        Node p = prefixes.remove(player.getUniqueId());
        Node s = suffixes.remove(player.getUniqueId());
        if (user == null) return;
        if (p != null) user.transientData().remove(p);
        if (s != null) user.transientData().remove(s);
    }
}
