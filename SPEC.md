# MINT SMP — MASTER SPEC (authoritative)

Economy-grind + PvP Paper plugin. Paper **1.21.11 / Java 21**. Single bundled jar:
`target/MintSMP-1.0.0.jar`. This file is the source of truth for every milestone;
where the original build prompt and the expansion conflict, the **expansion wins**
and the resolution is noted inline.

---

## 0. FAIRNESS INVARIANT (non-negotiable)
Ranks and the webstore may change **cosmetics and convenience ONLY**. They may never
grant more money, a higher sell multiplier, exclusive power gear, or any economic edge.
- The sell multiplier (§4) is earned by ALL players via volume sold, per item — it is
  **not** a rank/store perk. This keeps the invariant intact.
- Store-purchasable crates (Gold/Amethyst) grant cosmetic/convenience items
  (spawner of choice, amethyst tools that self-destruct in 3 days) — no permanent edge.

---

## 1. DUAL CURRENCY

| | Money | Shards |
|---|---|---|
| Role | Primary trade currency | Shop-only secondary |
| Earned by | Selling items (`/sell`) | 1/min AFK, 10/PvP kill |
| Tradeable to players | Yes (`/pay`) | No |
| Spent on | `/ah`, `/orders`, `/shop`*, bounties | Spawners + keys (and `/shop`) |
| Behavior | Inflates | Stable |

*Note: original `/shop` was a money buy-shop; expansion calls `/shop` the **Shard Shop**.
Resolution: `/shop` = **Shard Shop** (shards). Keep the money buy-shop available via
`/store`/webstore and `/orders`. See §5.

Money formatted `$1,234.50`; shards `⧫ 1,234`.

### Amount shorthand
`/pay`, `/bounty`, `/ah sell`, `/orders`, etc. accept `1k`, `2m`, `1.5b`
(k=1e3, m=1e6, b=1e9). Parser lives in `util/Amounts`.

---

## 2. FULL COMMAND CATALOG
(Declared in `plugin.yml` as each milestone implements it. Permissions default `true`
unless marked. `mint.admin` = op.)

**Economy:** `/bal` `/bal <player>` `/baltop` `/pay <player> <amt>` `/sell` `/sellmulti`
`/ah` (`sell <price>` `search <q>` `selling` `sold` `expired`) `/orders` `/shop` `/shards`
**Teleport:** `/rtp` (`overworld` `nether` `end`) `/spawn` `/home` `/home <name>`
`/sethome [name]` `/delhome <name>` `/tpa` `/tpahere` `/tpaccept` `/tpdeny` `/tpauto`
`/warp [name]`
**Combat:** `/duel <player>` `/duel draw <player>` `/bounty <player> <amt>`
**AFK/Shards:** `/afk` `/afk <number>`
**Social:** `/msg` (`/tell`) `/r` `/ignore` `/unignore` `/msgtoggle` `/togglechat` `/list`
`/ping` `/seen` `/team` `/voicechat` (Simple Voice Chat hook, Java only)
**Stats/utility:** `/stats` `/stats <player>` `/leaderboard` `/findplayer` (`/fp`) `/rules`
`/report` `/settings` `/discord` `/store` (`/buy`) `/website`
**Core:** `/mintsmp` (`reload` `help` `version`, `mint.admin`/`mint.help`)

---

## 3. MARKETPLACES
- **Auction House** `/ah` — money. Sub: `sell <price>`, `search`, `selling`, `sold`,
  `expired`. Listing fee or sale tax sink, max listings, expiry sweep → collection.
- **Orders** `/orders` — buy orders: players post "WTB X at price"; sellers fill them.
- **Quick-Sell** `/sell` — instant sell to server at `prices.yml × sellMultiplier`.
- **Shard Shop** `/shop` — buy with shards (keys, etc.).
- **Discord Market** (off-server) — spawners + other non-`/ah` items. **Scamming is
  rules-legal** (document this in `/rules`). Plugin does NOT mediate these trades.

---

## 4. SELL MULTIPLIER
Per-item-type progression **1.0× → 3.0×**, raised by cumulative volume sold of that item.
Affects `/sell` ONLY. Viewed via `/sellmulti` (GUI/list of your per-item tiers + progress).
Stored per (uuid, material). Thresholds configurable. NOT affected by rank or store.

---

## 5. SHARD EARNING & SINKS
- Earn: **+1 shard / minute AFK** (only inside the AFK Zone, see §9), **+10 shards / PvP kill**.
- Shards are **not** tradeable between players (no shard `/pay`).
- Sinks: spawners (flat **1,500 shards** each), crate keys, Shard Shop.

---

## 6. CUSTOM SPAWNERS (non-vanilla — replaces original §5.5)
Do **not** spawn mobs. **Right-click → drops directly**, with a per-spawner cooldown.
Flat price **1,500 shards**. **Cannot** be `/sell`'d or listed on `/ah` — traded via the
**Discord Market only**. PDC-tagged so they survive and can't be sold.
Types: **Iron Golem, Cow, Skeleton, Zombie, Creeper, Blaze, Spider, Enderman**.
Config: `spawners.yml` (per type: drop table, cooldown).

---

## 7. AMETHYST ITEMS (replaces original §5.10 permanence)
Five exclusive items from the **Amethyst Crate**. Each carries a **3-day self-destruct
timer that follows the item** (persists across drops/storage; item is removed on expiry).

| Item | Effect |
|---|---|
| Amethyst Pickaxe | Mines 3×3×3 per swing |
| Amethyst Axe | Fells an entire tree (~500-block cap) |
| Amethyst Shovel | Digs 3×3×3 (soft blocks only) |
| Amethyst Bucket | Drains 27 water blocks |
| Amethyst Shard Booster | Consumable: 4× shards for 24h |

Config: `amethyst.yml` (effects, caps, timer). All amethyst items are non-sellable (PDC).

---

## 8. CRATES & KEYS (replaces original §5.6 RNG)
Five crates at `/warp crates`. **You pick 1 of 7 presented rewards — NO randomness**
(gambling RNG removed after a past dupe incident). **Hourly keyall** grants every online
player one free key.

| Crate | Source | Loot |
|---|---|---|
| Common | Keyall / 200 shards / store | Enchanted diamond gear |
| Prime | Keyall (low) / 1,500 shards / store | Enchanted Netherite (+Mace) |
| Crimson | 1,500 shards / store | Enchanted Netherite |
| Gold | Store only | Spawner of choice |
| Amethyst | Store only | Amethyst Items |

Config: `crates.yml`.

---

## 9. OTHER NON-VANILLA SYSTEMS
- **AFK Zone** — idle shard farming (the +1/min from §5 applies only here). `/afk`,
  `/afk <number>` (set custom afk threshold/minutes).
- **RTP regions/proxies** — `/rtp overworld|nether|end`, teleport **warmup** before TP.
- **No land claim** — raiding + griefing fully allowed. **Ender chests are the only
  protected storage.** Document in `/rules`.
- **Teams** — `/team` with a **shared team home**.
- **Crystal PvP** — standard end-crystal combat (no anti-crystal restrictions).
- **`/sell price` tooltip** — `/settings` toggle to show an item's `/sell` value in its
  tooltip/lore.
- **Bounties** — `/bounty <player> <amt>` (money), paid out to the killer.
- **Duels** — `/duel <player>`, `/duel draw <player>`.
- **Voice** — `/voicechat`: hook **Simple Voice Chat** if present (proximity, Java only);
  degrade gracefully if absent.
- **Webstore links** — `/store` (`/buy`), `/website`, `/discord`: open configured URLs.
- **`/report`** — forward a report to staff (config: log + optional Discord webhook).

---

## 10. REVISED MILESTONE ROADMAP (compile a jar after EACH)
1. **Core skeleton** — pom, plugin.yml, Main, Storage (players), Msg, `/mintsmp`. ✅ built
2. Economy + Vault — `/bal` `/pay` (shorthand) `/baltop` `/shards` `/eco`; dual-currency tables.
3. Sell + multiplier — `/sell` `/sellmulti` `/worth`, `prices.yml`, per-item tiers, `/sellboost`.
4. Scoreboard + PAPI + tablist (money/shards/kd live).
5. Shard Shop `/shop` + Shard sinks; webstore link commands (`/store` `/website` `/discord`).
6. Stats + `/leaderboard` + `/findplayer` `/seen` `/list` `/ping`.
7. Teleport — `/rtp` regions + warmup, `/spawn` `/home` `/sethome` `/delhome` `/warp`,
   tpa family + `/tpauto`.
8. Combat tag + combat-log punish; Crystal PvP rules; `/duel`; `/bounty`.
9. Shards earning — AFK Zone + `/afk` + PvP-kill shards.
10. Amethyst items (5) + 3-day self-destruct timer.
11. Crates (pick-1-of-7, no RNG) + keys + hourly keyall.
12. Custom drop-spawners (8 types, cooldowns, no-sell/no-ah, 1,500 shards).
13. Auction House `/ah` + `/orders` marketplace.
14. Social/QoL — `/msg` `/r` `/ignore` `/msgtoggle` `/togglechat`, Teams + shared home,
    `/settings` (incl. `/sell price` tooltip), `/rules` `/report`, `/voicechat` hook.
15. Polish — sounds, `messages.yml`, README, final `mvn clean package`.

---

## 11. CONFLICTS RESOLVED (summary)
| Topic | Original prompt | Expansion | Resolution |
|---|---|---|---|
| Sell multiplier | None (flat) | 1.0×→3.0× per item by volume | Keep expansion; multiplier is volume-earned, not rank/store → still fair |
| `/shop` currency | Money buy-shop | Shard Shop | `/shop` = shards; money buying via `/store`/`/orders` |
| Spawners | Vanilla placeable | Custom drop-on-rightclick, 1,500 shards, Discord-only | Keep expansion |
| Crates | Animated weighted RNG | Pick 1 of 7, no RNG | Keep expansion (dupe-incident decision) |
| Amethyst items | Permanent, auto-repair | 3-day self-destruct | Keep expansion |
| Amethyst pickaxe | 3×3 plane | 3×3×3 cube | Keep expansion |
