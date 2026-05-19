# Villager Reroll

Automates librarian lectern rerolls. Configure a list of desired enchanted books, then the mod will repeatedly break and replace a lectern until one of those books appears in the villager's trades.

## Usage

1. **Open the config screen** — Press **R** (configurable in Controls → Miscellaneous → Open Villager Reroll).
2. **Add target books** — Click **Add**, pick an enchantment, and set the maximum emerald price you're willing to pay.
3. **Lock in trade (optional)** — Toggle this on to automatically lock in the trade when a match is found.
4. **Start** — Click **Start**, then **right-click a lectern**. The mod will:
   - Open the trade and check the villager's offers
   - If no match, break the lecturn and place a new one from your invetory
   - Repeat until a match is found or you stop the mod
5. **Stop** — Click Stop in the GUI, or the mod stops automatically when a match is found.

## Controls

| Key | Action |
|-----|--------|
| R | Open Villager Reroll config screen |

The hotkey can be rebound in Options → Controls → Miscellaneous → Open Villager Reroll.

## Config

Saved to `config/villager-reroll.json`:
- **targets** — List of enchantments to search for, with max price per book
- **range** — Search radius for the villager (default: 6)
- **jobSite** — Lectern position (set automatically when you start)
- **lockInTrade** — Whether to auto-lock the trade on match
