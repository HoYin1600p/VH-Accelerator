# VH Accelerator 1.1.2 — critical fix

Fixes parts of the world vanishing for players who run Farsight. Everyone on
1.1.0 or 1.1.1 with Farsight installed should update.

- Blocks could disappear and stay gone until you relogged, often vault
  hallways and portal rooms on the way back. You could not walk on the
  missing blocks. They now come back as soon as you return.
- If you set `boundFarsightChunkRetention = false` to work around it, you can
  set it back to `true` after updating.

[Read the detailed changelog on GitHub](https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.1.2)
