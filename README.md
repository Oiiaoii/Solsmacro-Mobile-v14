# Sol Macro (Android)

Fishing and selling helper for Sol's RNG. Not affiliated with Roblox. Macros can break game rules and get accounts banned.

## How the fishing detection works (measured from a screen recording of the game)
- The reeling bar is a long horizontal bar with a dark green zone and a blue fill. The right end of the blue fill is the marker.
- The blue fill drains by itself (about 215 px/s on a 2340 px wide screen) and each touch lifts it about 58 px. The app taps fast while the marker is well left of the middle of the green zone, slower when slightly left, and not at all once past the middle, using the median of the last 3 readings. After each reel the log shows how long the marker stayed in the zone.
- "Search the whole screen" finds the green zone anywhere (long, thin, near the middle of the screen) and the blue fill beside it. No bar area needs picking. The default colors come from the recording.
- After every reel (caught or failed) the close (X) button of the result panel is tapped twice. A "Fishing Failed" screen (big red X) is counted as a fail; anything else counts as a catch (assumed). Set the X position with SET (9th spot).
- Whole-screen mode needs Fast capture (MediaProjection). Screenshots alone are too slow for reeling.

## Tested
- The detection algorithm was tested in a Python copy against the recording (no false bar before the bar appears, tip and zone found while it is on screen, fail screen found).
- The Android app itself has NOT been compiled or run by the author. Test on the phone.

## Included
- Features inspired by SolsScope (the PC macro), adapted to what Android allows: several webhook URLs, anti-kick, periodic screenshots to the webhooks, biome watch (reads the biome name from the screen with on-device text recognition and sends a webhook message when it changes), and 3 timed tasks that run your own scripts (use them for auto craft, buying, biome items). Taps in scripts can use extra points P1-P6 or raw pixels (tap 540 1200).
- Start is blocked until the button positions are set (Settings > Set button positions, 9 taps). Tapping Tap to Start before that shows a notice, shakes the button and opens Settings. Positions saved by an older version need to be set once again.
- OpenCV bar finder (color mask, morphology, contours) used for the whole-screen search, with the built-in detector as fallback (switch "OpenCV detect" in Macro Settings). Once the bar is found only a small box around it is scanned, with finer steps.
- Reel controller: proportional tap rate around the hold rate (about drain/gain taps per second) with marker speed look-ahead instead of fixed fast/slow/off steps.
- Animated Tap to Start button: breathing glow, orbiting planet, twinkling sparkles, press squish, ripple on tap, play to stop morph, spinning arc while running (amber and still when paused).
- Floating round icon button (Brry logo by default, "Use This Icon" to change). Tap it to open the Brry panel (Macro, Stats, Settings, Credits with a big play button, settings toggles and a stats bar). Tap it again to close.
- No color picking: the reeling bar colors are built in (measured from a recording).
- Debug overlay, log with Copy button, Discord webhook, auto sell with movement/camera/Shift Lock scripts, failsafe.

## Not included
- Aura detection (SolsScope reads Roblox log files on PC), merchant stock OCR, automatic crafting recipes, plugins, trained AI, OpenCV template matching, automatic seller detection, earnings graphs, CPU or battery graphs.

## Build
Upload every file to a GitHub repository (keep folders, including .github/workflows). The Actions tab builds app-debug.apk.
