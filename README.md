# RegionFlagsPlus

More WorldGuard flags for Paper 1.21.11+ (Java 21). Needs **WorldGuard 7**. **LuckPerms** is optional and only needed for the prefix/suffix flags.

Install: download the jar from the [latest release](https://github.com/Perwds/RegionFlagsPlus/releases/latest), put it in `plugins/` and **restart** the server. WorldGuard flags can't be added with `/reload` or PlugMan.

## Flags

| Flag | Value | What it does |
|---|---|---|
| `region-effects` | `speed:2,night_vision` | Gives you infinite potion effects while you're inside and removes them when you leave. It re-applies them about once a second, so milk and `/effect clear` don't get rid of them. |
| `region-blocked-effects` | `invisibility,speed` | Removes these effects from anyone inside. |
| `region-prefix` | `&7[&cJail&7]&r ` | Gives you a LuckPerms prefix while inside. Your normal prefix comes back when you leave. |
| `region-suffix` | ` &e★` | Same as above, for the suffix. |
| `region-permissions` | `essentials.fly,-essentials.home` | Gives you these permissions while inside. Put `-` in front of one to take it away instead. |
| `exit-teleport-only` | `allow` | You can only leave by teleporting (`/spawn`, `/warp`...). Walking, flying, riding and ender pearls are all blocked. |
| `entry-teleport-only` | `allow` | You can only get in by teleporting. |
| `blocked-items` | `#hotbar` or `*_spear,mace` | These items don't work inside: no using, charging, attacking, placing, eating, shooting, gliding or totem pops. |
| `item-cooldowns` | `ender_pearl:10,golden_apple:30` | Puts a cooldown on any item, in seconds. It shows the vanilla cooldown bar too. |
| `deny-block-place` | `tnt,#tag:beds` | These blocks can't be placed. |
| `deny-block-break` | `#target` | These blocks can't be broken. |
| `deny-block-use` | `chest,lever,#tag:doors` | These blocks can't be right-clicked or stepped on (pressure plates). |
| `region-fly` | `allow` / `deny` | `allow` lets you fly inside. You get slow falling when you leave so you don't die. `deny` turns flight off. |
| `region-walk-speed` | `0.4` | Walk speed while inside. Vanilla is 0.2. |
| `enter-commands` | `say hi %player%; player:spawn` | Runs commands when you enter. Separate them with `;`. Commands run from console unless they start with `player:`. |
| `exit-commands` | same | Runs commands when you leave. |
| `entry-permission` | `rp.police` | Only players with this permission can get in, by walking **or** teleporting. Good for a police station, staff room or VIP club. Use it with LuckPerms groups. |
| `region-max-players` | `4` | The most players allowed inside at once. Anyone else is kept out until someone leaves. Good for interrogation rooms, small shops and event stages. |
| `enter-sound` | `block.bell.use:1:0.8` | Plays a sound when you enter. The format is `sound:volume:pitch`, and volume and pitch are optional. Custom resource-pack sounds work too. |
| `exit-sound` | `block.wooden_door.close` | Plays a sound when you leave. |
| `region-keep-inventory` | `allow` | You keep your inventory when you die here. |
| `region-keep-xp` | `allow` | You keep your XP when you die here. |
| `no-hunger` | `allow` | Hunger doesn't go down. |

Placeholders you can use in commands: `%player% %uuid% %region% %world%`

## Quick-add shortcuts (all item and block flags, and cooldowns)

| Shortcut | Adds |
|---|---|
| `#hand` | The item in your main hand |
| `#offhand` | The item in your off hand |
| `#hotbar` | Everything in your hotbar |
| `#inventory` | Your whole inventory |
| `#armor` | The armor you're wearing |
| `#target` | The block you're looking at |
| `#tag:logs` | Every item or block in a vanilla tag |
| `*_spear` | A wildcard, which here means every spear |

These work in plain WorldGuard too:
```
/rg flag spawn blocked-items #hotbar
/rg flag pvp item-cooldowns #hand:15,ender_pearl:10
```

## /wgf command (aliases /regionflags, /rfp)

`/rg flag` *replaces* the whole value. `/wgf` lets you **add** or **remove** single entries, and it tab-completes everything: regions, flags, items, effects and **every permission** on the server.

```
/wgf add spawn blocked-items #hand
/wgf add spawn blocked-items *_spear
/wgf remove spawn blocked-items diamond_spear
/wgf add arena item-cooldowns golden_apple:30
/wgf add vip region-permissions essentials.fly     <- tab-complete shows every permission
/wgf add jail region-effects slowness:2
/wgf set jail exit-teleport-only allow
/wgf set jail region-prefix &7[&cJailed&7]&r
/wgf add jail enter-commands say %player% got jailed
/wgf remove jail enter-commands #1
/wgf clear jail region-prefix
/wgf info jail
/wgf flags          list every flag
/wgf hand           show the names of the items you're holding
/wgf reload
```
To change another world, add `-w <world>`. This also works from the console.

## Permissions
- `regionflags.admin`: lets you use `/wgf` (ops have it by default)
- `regionflags.bypass.<flag>`: ignore one restriction flag
- `regionflags.bypass.*`: ignore all of them

**Testing as an op:** WorldGuard's region bypass lets ops ignore these restriction flags, the same as WorldGuard's own flags. Run `/rg bypass` to turn it off, or set `respect-worldguard-bypass: false` in `config.yml`.

## Roleplay examples
```
/wgf set policehq entry-permission rp.police
/wgf set policehq enter-sound block.iron_door.open
/wgf set interrogation region-max-players 2
/wgf set tavern enter-sound block.bell.use:1:0.8
/wgf set tavern exit-sound block.wooden_door.close
/lp group police permission set rp.police true
```

## Notes
- **entry-permission / region-max-players** only stop someone *entering*. A player who logs in, respawns or changes worlds into the region isn't kicked out.
- **exit-teleport-only:** respawning after death and going through nether/end portals count as teleports, so they still let a player out. For a jail, also block the commands you don't want them to use with WorldGuard's `blocked-cmds` flag, e.g. `/rg flag jail blocked-cmds /home,/tpa`.
- **Spears:** `blocked-items` stops the jab, because it cancels melee hits with that item. It also stops the hold-to-charge attack, because right-click use is cancelled. If something gets past it in a new Minecraft version, put the item in `item-cooldowns` as well.
- The prefix and suffix are **transient** LuckPerms nodes. They're never saved, so a crash can't leave someone stuck with a region prefix.
- If the server crashes while someone has region effects, flight or a changed walk speed, it's all cleaned up the next time they join.
