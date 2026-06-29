# LinuxSync Android

Android companion app for LinuxSync — runs as a foreground service on your phone and communicates with the Linux desktop app over WebSocket (AES-256-GCM encrypted).

## What it does

- **Notifications** — forwards phone notifications to the desktop in real-time
- **Media playback** — sends now-playing info (title, artist, album) and receives play/pause/next/prev commands from the desktop
- **Battery status** — sends battery level and charging state (throttled to avoid spam)
- **Wallpaper sharing** — sends the current phone wallpaper to the desktop
- **Auto-reconnect** — reconnects to the laptop with exponential backoff (up to 15 attempts)

## Permissions required

- **Notification Listener** — needed to read notifications and control media sessions. Must be manually enabled in Settings > Special Access > Notification Access
- **Foreground Service** — keeps the connection alive in the background

## Setup

1. Grant Notification Listener access when prompted
2. Pair the app with the LinuxSync desktop app
3. The phone will automatically connect and start syncing
