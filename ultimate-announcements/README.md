# UltimateAnnouncements 1.0.0

Paper 1.21.11, Java 21. Standalone plugin; no extra dependencies. Chat, titles,
action bars and boss bars use standard server APIs for Java and Geyser Bedrock players.

## Install

Stop the server, place UltimateAnnouncements-1.0.0.jar in plugins/, and start.
Edit plugins/UltimateAnnouncements/config.yml and use `/announcements reload`.
Automatic announcements start after five minutes by default and rotate in order.
No players online means no broadcast and no rotation advance. Scheduled announcements
pause during restart countdowns. `/announcements pause` lasts until resume/restart;
set `enabled: false` in config to persistently disable automatic announcements.

## Commands

- `/announce <message>`: broadcast a chat message, supports & colors.
- `/announcements list`: list configured enabled message IDs.
- `/announcements send <id>`: broadcast an entry using its configured display.
- `/announcements preview <id>`: show an entry only to yourself, in-game.
- `/announcements pause` or `resume`: pause/resume automatic announcements.
- `/announcements reload`: validate and reload config. If invalid, keep the working
  settings. Cancel any running restart countdown before reloading.
- `/announcements status`: show interval, state and countdown.
- `/restartcountdown`: begin the default 10-second countdown.
- `/restartcountdown <seconds>`: choose 1 to 3600 seconds.
- `/restartcountdown cancel`: cancel before zero.
- `/restartcountdown status`: view countdown state.

OPs have `ultimateannouncements.manage` and `ultimateannouncements.restart` by
default. Grant either permission to trusted staff through your permissions plugin.
UltimateRanks rank names alone do not automatically grant these permissions; with
that plugin's current version, use OP for these commands unless another permissions
provider grants them. Regular players receive announcements without permissions.
Command completion only lists staff options for authorized users.

## Restart behavior

Shows "Server is restarting in 10...", then 9 through 1, then "Restarting......".
Chat and title text/colors can be changed in config. At zero the plugin saves every
world and requests a normal Paper shutdown, which also saves player data.
**Your hosting panel must automatically start the server again.** The plugin cannot
launch a stopped server process. This is an operator-started countdown, not a
recurring automatic restart schedule. Installation never initiates a restart.
Cancellation and plugin disable clear the countdown; overlapping countdowns are
rejected. Countdown steps occur no faster than once a second; server lag can delay
them. The countdown works even with zero players online.

## Configuration

Each enabled entry has `display` (`chat`, `title`, `actionbar`, `bossbar`) and
`messages` (1-20 lines). Titles use the first line as the title and remaining lines
as the subtitle. Action bars and boss bars join lines with spaces. Titles/boss bars
accept `duration-seconds` (1-60). Boss bars clean up on expiry, quit and disable.
Action bar duration is controlled by the client. Bedrock may render these displays
differently from Java, but no custom textures or client mods are required.

`interval-seconds` accepts 10-86400. `random-order: true` avoids repeating the same
entry consecutively when there are multiple entries. Placeholders: `{player}`,
`{server}`, `{online}`, `{max_players}`; restart text also accepts `{seconds}`.
Formatting uses legacy & color codes, not MiniMessage or PlaceholderAPI.
A single synchronous heartbeat manages timers and cleanup. Configuration is read
only at startup/reload. There are no external downloads or recurring file reads.

## Build and checks

`mvn -f ultimate-announcements/pom.xml package`

Run `shop.blinksmp.announcements.AnnouncementRulesTest` using compiled main/test
classes. Checks cover full 10-to-0 ordering, cancellation, duplicate stops,
overlapping countdowns, numeric bounds and ordered/random rotation.
GitHub Actions builds with Java 21 and runs these checks. Live Java/Bedrock UI
and hosting-panel restart behavior still need verification on the real server.
