# WorldWipe

WorldWipe is a Hytale server mod that schedules per-world wipes, optionally regenerates maps, and ships with a clean in-game UI and command set for server admins.

## Features
- Per-world scheduled wipes with DAILY, WEEKLY, and MONTHLY modes
- Optional regeneration after wipe (global or per-world)
- Protected worlds that never wipe (ex: default, hub)
- In-game UI dashboard for schedules, force wipe, and status
- Offline catch-up using last wipe timestamps
- Permission-based access, compatible with LuckPerms

## Requirements
- Hytale server with mod support
- LuckPerms (recommended for permission management)

## Installation
1. Drop the mod jar into your server `mods/` directory.
2. Start the server once to generate `mods/WorldWipe/config.yml`.
3. Edit the config and reload, or use the in-game UI.

## Commands
All commands require a matching `worldwipe.commands.<command>` permission.

- `/wipe` - Open the dashboard UI
- `/wipe help` - List available commands
- `/wipe info` - Plugin information
- `/wipe status` - Wipe status and schedules
- `/wipe ui` - Open the dashboard UI
- `/wipe now [--dry]` - Run a manual wipe (admin only)
- `/wipe reload` - Reload configuration (admin only)
- `/wipe schedule list`
- `/wipe schedule set <world> [mode] [day/date] [time] [zone]`
- `/wipe schedule remove <world>`
- `/wipe schedule enable`
- `/wipe schedule disable`

## Permissions
Visibility in the default `/help` UI:
- `worldwipe.help`

Admin control:
- `worldwipe.admin`

Schedule visibility:
- `worldwipe.schedule`
- `worldwipe.schedule.<world>`

Command permissions:
- `worldwipe.commands.wipe`
- `worldwipe.commands.help`
- `worldwipe.commands.info`
- `worldwipe.commands.status`
- `worldwipe.commands.ui`
- `worldwipe.commands.now`
- `worldwipe.commands.reload`
- `worldwipe.commands.schedule.list`
- `worldwipe.commands.schedule.set`
- `worldwipe.commands.schedule.remove`
- `worldwipe.commands.schedule.enable`
- `worldwipe.commands.schedule.disable`

## Configuration
Location: `mods/WorldWipe/config.yml`

Example:
```yml
schedulingEnabled: true
regenerateOnWipe: false
protectedWorld: [default, hub]
worlds:
  resource:
    schedule:
      mode: WEEKLY
      day: MONDAY
      time: "06:00"
      zone: system
    regenerateOnWipe: false
  dungeon:
    schedule:
      mode: MONTHLY
      dayOfMonth: 15
      time: "18:30"
      zone: Europe/Oslo
    regenerateOnWipe: true
  events:
    schedule:
      mode: DAILY
      time: "12:00"
      zone: system
```

### Schedule Modes
- DAILY: pick a time
- WEEKLY: pick a weekday + time
- MONTHLY: pick a day of month (1-31). If the month is shorter, the last day is used.

## Development
Build the mod:
```bash
./gradlew build
```

## Support
- Website: https://sdev.no/
- Discord: https://discord.sdev.no/
- Bug Reports: https://github.com/stigping/Hytale-WorldWipe/issues
