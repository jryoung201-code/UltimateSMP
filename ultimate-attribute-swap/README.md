# UltimateAttributeSwap 1.0.0

Restores vanilla attribute-swap timing on **Paper 1.21.11 / Java 21** by setting
Paper's runtime `unsupportedSettings.updateEquipmentOnPlayerActions` to false.
Install the JAR in plugins/ and start the server. No dependencies or manual edit
of paper-global.yml are needed. The change applies to everyone while enabled.

## Commands

- `/attributeswap status` (or `/aswap`): show restoration state and the actual live Paper flag.
- `/attributeswap on`: apply restoration and save `enabled: true` in this plugin's config.
- `/attributeswap off`: release the setting and save `enabled: false`.
- `/attributeswap reload`: reload this plugin's config and reapply. Also useful after `/paper reload`.

Commands require `ultimateattributeswap.admin` (OP by default). Ordinary players
need no permissions for the restored gameplay. The plugin remembers the live
Paper value it found before applying a change and restores it on disable/off.
If the value was already false, disabling the plugin leaves it false.

## Behavior and compatibility

Paper normally calls extra equipment updates during hotbar selection, hand swaps,
inventory actions and other player actions. This plugin disables the Paper
switch that gates those extra updates, letting vanilla equipment timing govern
swaps again. It does not fake damage, copy enchantments, add persistent attribute
modifiers, schedule per-player tasks, or intercept combat packets.

Only the Paper 1.21.11 setting/schema is supported. The plugin refuses unsupported
Minecraft versions or missing/changed fields and reports the error in console.
It reapplies on Bukkit ServerLoadEvent and captures a replacement configuration
instance when reapplying. Some Paper configuration reload commands do not fire
this event; run `/attributeswap on` or `/attributeswap reload` afterward. Another
plugin that forces equipment updates or rewrites combat can still interfere.
This plugin never changes Paper's configuration file on disk.

Restoring Paper's timing does not guarantee every version-specific swap technique,
spear/lunge interaction or Geyser Bedrock input sequence behaves identically to a
particular Java client/version. It does not reverse independent Minecraft changes
or emulate Java combat inputs for Bedrock clients. Test your intended weapon
combinations on the real server. No live PvP test was available here.

## Verification

Build: `mvn -f ultimate-attribute-swap/pom.xml package`

CI builds with Java 21 and tests the real reflection helper with simulated Paper
configuration classes: repeated enable, original true/false values, disable,
replacement objects on reload, invalid schemas and external flag changes. These
are runtime-setting tests, not live combat tests.

The setting and call sites were checked against primary Paper source:
- https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/io/papermc/paper/configuration/GlobalConfiguration.java
- https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/patches/sources/net/minecraft/server/network/ServerGamePacketListenerImpl.java.patch
- https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/patches/sources/net/minecraft/world/entity/player/Player.java.patch
