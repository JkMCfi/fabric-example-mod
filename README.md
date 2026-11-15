# Stellar

Stellar is a client-side Fabric music companion for Minecraft 1.21.11, with a Valence-inspired split player/settings screen, Spotify playback control, and timestamped LRCLIB lyrics.

## Build

Use Java 21 and run `./gradlew build`. The remapped mod jar is `build/libs/stellar-1.0.0.jar`.

## Spotify setup

1. Create a Spotify application in the Spotify Developer Dashboard.
2. Add `http://127.0.0.1:8888/callback` as a Redirect URI in its settings.
3. Open Stellar from the Minecraft title or pause menu, select **Settings**, enter the application's Client ID and select **Connect Spotify**.
4. Approve access in the browser. OAuth Authorization Code with PKCE is used; a client secret is not requested.
5. Use the Settings tab to search tracks. Playback polling and transport controls require an active Spotify Premium playback device/account as required by Spotify's API.

The local configuration and OAuth tokens are stored in `.minecraft/config/stellar.json`. Keep that file private. Lyrics are queried from LRCLIB and `.lrc` files can be imported from the Lyrics tab.

## UI notes

The screen uses a centered 1280x720 virtual canvas and reverses the scale/offset transform for pointer hit testing. The split floating player and right-hand glass window follow the supplied Valence screenshot's layout and colors. Spotify artwork URL metadata is synchronized; artwork texture rendering and a custom WorldRenderer sky hook remain follow-up work.
