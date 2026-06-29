# Mint SMP

All-in-one **economy-grind + PvP** Paper plugin (Donut-style), themed green. **Not
lifesteal** — no hearts/lives. Targets **Paper 1.21.11 / Java 21**, builds to a single
`target/MintSMP-1.0.0.jar`.

> **Fairness invariant:** ranks and the webstore change **cosmetics & convenience only**.
> Never more money, a higher sell multiplier, exclusive power gear, or any economic edge.
> The per-item sell multiplier is earned by all players via volume sold — not a rank perk.

`SPEC.md` is the authoritative design (reconciles the original build prompt with the
expanded feature set; see its §11 for resolved conflicts).

## Build it (the way you build PixelForge — no local Maven needed)

1. Create a GitHub repo and upload this project so that **`pom.xml` sits at the repo root**
   (not nested in a subfolder). The included workflow warns about this — it's your usual
   upload gotcha.
2. GitHub Actions runs automatically on push (`.github/workflows/build.yml`), or trigger it
   manually from the **Actions** tab → *Build MintSMP* → *Run workflow*.
3. When it finishes, download the jar from the run's **Artifacts** → `MintSMP-jar`.
4. Drop `MintSMP-1.0.0.jar` into your server's `plugins/` folder and start Paper 1.21.11.

Local build (if you ever set up JDK 21 + Maven): `mvn clean package`.

## What's in this build (Milestone 1 + full spec/config)

**Working now:** plugin loads clean, SQLite (`players` table, WAL, async), `messages.yml`
(MiniMessage), `/mintsmp help|version|reload`, green startup banner, per-player join
tracking.

**Data/config already filled in** (used by later milestones): `prices.yml` (1,390 sell
values, Elytra = 50,000), `shop.yml`, `spawners.yml` (8 custom drop-spawners),
`crates.yml` (5 crates, pick-1-of-7, no RNG, hourly keyall), `amethyst.yml` (5 items,
3-day self-destruct), and an expanded `config.yml` (dual currency, shard earning,
sell multiplier 1.0×→3.0×, AFK zone, RTP regions, world rules, combat/duel/bounty).

## Roadmap (compile a jar after each — see SPEC.md §10)

1. ✅ Core skeleton  ·  2. Economy + Vault  ·  3. Sell + multiplier  ·  4. Scoreboard/PAPI/tablist
·  5. Shard Shop + links  ·  6. Stats/leaderboards  ·  7. Teleport (rtp/home/tpa/warp)  ·
8. Combat tag + duel + bounty  ·  9. Shard earning (AFK zone, PvP)  ·  10. Amethyst items  ·
11. Crates  ·  12. Custom spawners  ·  13. Auction House + Orders  ·  14. Social/Teams/QoL  ·
15. Polish.

## Notes
- `/voicechat` will integrate **Simple Voice Chat** if installed (proximity, Java only); it
  degrades gracefully when absent. The plugin builds no custom audio.
- Discord Market trades (spawners + non-`/ah` items) are off-server; the plugin doesn't
  mediate them and scamming is rules-legal — state that in `/rules`.
- Soft-depends (Vault, PlaceholderAPI, LuckPerms) are optional; missing ones degrade
  gracefully. Their Maven deps get uncommented in `pom.xml` at Milestone 2.
