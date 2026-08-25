#!/usr/bin/env bash
# Builds assets/overhaul/lang/en_us.json from the item and block names the mod registers.
# Run from the repository root:  bash tools/genlang.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/overhaul/lang/en_us.json"
mkdir -p "$(dirname "$OUT")"

ITEMS="tomato lettuce corn chili_pepper cooked_corn popcorn flour dough cheese pasta toast tomato_sauce jam chocolate fried_egg honey_glazed_ham trail_mix
tomato_seeds lettuce_seeds corn_seeds chili_pepper_seeds
salad stew sandwich skewer pie elote
backpack copper_backpack iron_backpack gold_backpack diamond_backpack netherite_backpack backpack_upgrade_smithing_template"

BLOCKS="tomato lettuce corn chili_pepper"

title() {
	echo "$1" | tr '_' ' ' | awk '{ for (i = 1; i <= NF; i++) $i = toupper(substr($i, 1, 1)) substr($i, 2); print }'
}

{
	printf '{\n'

	for name in $ITEMS; do
		printf '  "item.overhaul.%s": "%s",\n' "$name" "$(title "$name")"
	done

	for name in $BLOCKS; do
		printf '  "block.overhaul.%s": "%s Crop",\n' "$name" "$(title "$name")"
	done

	cat <<'JSON'
  "tooltip.overhaul.meal.ingredients": "Made with:",
  "tooltip.overhaul.meal.effect": "%s (%s)",
  "tooltip.overhaul.meal.nutrition": "Nutrition %s, saturation %s",
  "tooltip.overhaul.backpack.slots": "%s slots",
  "tooltip.overhaul.backpack.contents": "%s / %s slots used",
  "tooltip.overhaul.backpack.open": "Right click to open",
  "tooltip.overhaul.template.applies": "Applies to",
  "tooltip.overhaul.template.ingredients": "Ingredients",
  "tooltip.overhaul.template.backpack": "Backpack",
  "tooltip.overhaul.template.upgrade_material": "Copper, iron, gold, diamond or netherite ingot",
  "container.overhaul.backpack": "Backpack",
  "key.overhaul.open_backpack": "Open Backpack",
  "key.categories.overhaul": "Overhaul",
  "item.overhaul.backpack_upgrade_smithing_template.applies_to": "Backpack",
  "item.overhaul.backpack_upgrade_smithing_template.ingredients": "Copper, iron, gold, diamond or netherite ingot",
  "upgrade.overhaul.backpack_upgrade": "Backpack Upgrade",
  "subtitles.overhaul.backpack.open": "Backpack opens",
  "subtitles.overhaul.backpack.close": "Backpack closes"
}
JSON
} > "$OUT"

echo "Wrote $OUT"
