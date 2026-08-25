#!/usr/bin/env bash
# Regenerates the flat, mechanical parts of the resource pack: item model definitions, item
# models, crop blockstates and crop block models. Textures are produced by TextureGen.java.
#
# Run from the repository root:  bash tools/genassets.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$ROOT/src/main/resources/assets/overhaul"

FOODS="tomato lettuce corn chili_pepper cooked_corn popcorn flour dough cheese pasta toast tomato_sauce jam chocolate fried_egg honey_glazed_ham trail_mix"
SEEDS="tomato_seeds lettuce_seeds corn_seeds chili_pepper_seeds"
MEALS="salad stew sandwich skewer pie elote"
BACKPACKS="backpack copper_backpack iron_backpack gold_backpack diamond_backpack netherite_backpack backpack_upgrade_smithing_template"
CROPS="tomato lettuce corn chili_pepper"

mkdir -p "$ASSETS/items" "$ASSETS/models/item" "$ASSETS/models/block" "$ASSETS/blockstates"

flat_item() {
	local name="$1"
	cat > "$ASSETS/items/$name.json" <<JSON
{
  "model": {
    "type": "minecraft:model",
    "model": "overhaul:item/$name"
  }
}
JSON
	cat > "$ASSETS/models/item/$name.json" <<JSON
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "overhaul:item/$name"
  }
}
JSON
}

for name in $FOODS $SEEDS $MEALS $BACKPACKS; do
	flat_item "$name"
done

# Crops: eight growth stages share four textures, the way beetroot reuses its art.
stage_for_age() {
	case "$1" in
		0|1) echo 0 ;;
		2|3) echo 1 ;;
		4|5|6) echo 2 ;;
		*) echo 3 ;;
	esac
}

for crop in $CROPS; do
	{
		printf '{\n  "variants": {\n'
		for age in 0 1 2 3 4 5 6 7; do
			stage="$(stage_for_age "$age")"
			printf '    "age=%s": { "model": "overhaul:block/%s_stage%s" }' "$age" "$crop" "$stage"
			if [ "$age" -lt 7 ]; then printf ',\n'; else printf '\n'; fi
		done
		printf '  }\n}\n'
	} > "$ASSETS/blockstates/$crop.json"

	for stage in 0 1 2 3; do
		cat > "$ASSETS/models/block/${crop}_stage${stage}.json" <<JSON
{
  "parent": "minecraft:block/crop",
  "textures": {
    "crop": "overhaul:block/${crop}_stage${stage}"
  }
}
JSON
	done
done

echo "Wrote item definitions for $(echo $FOODS $SEEDS $MEALS $BACKPACKS | wc -w) items and $(echo $CROPS | wc -w) crops."
