package com.overhaul.module.backpack;

import java.util.List;
import java.util.function.Consumer;

import com.overhaul.core.CarriedContainer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A wearable-sized container opened from the hand or with the backpack key.
 *
 * <p>Each tier is its own item with its own row count, which is what lets {@link CarriedContainer}
 * do all the work: a backpack is a container item like any other, and differs from a shulker box
 * only in how many rows it has and in what it refuses to hold.
 */
public class BackpackItem extends Item {
	private final int rows;

	public BackpackItem(Properties properties, int rows) {
		super(properties);
		this.rows = Math.max(1, Math.min(6, rows));
	}

	public int rows() {
		return rows;
	}

	public int slotCount() {
		return rows * 9;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		open(player, stack);
		return InteractionResult.CONSUME;
	}

	/** Opens the given backpack for the player. Shared by the hand interaction and the keybind. */
	public static void open(Player player, ItemStack stack) {
		if (!(stack.getItem() instanceof BackpackItem backpack)) {
			return;
		}

		CarriedContainer.open(player, stack, backpack.rows(), BackpackItem::allowedInside);
	}

	/**
	 * Nesting bags inside bags makes items very easy to lose track of, and lets a small tier hold
	 * arbitrarily much. Vanilla applies the same rule to shulker boxes.
	 */
	private static boolean allowedInside(ItemStack stack) {
		return BackpackModule.allowNesting() || !(stack.getItem() instanceof BackpackItem);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
			Consumer<Component> lines, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);

		ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		long used = contents.nonEmptyItemCopyStream().count();

		lines.accept(Component.translatable("tooltip.overhaul.backpack.contents", used, slotCount())
				.withStyle(ChatFormatting.GRAY));

		List<ItemStack> preview = contents.nonEmptyItemCopyStream().limit(4).toList();

		for (ItemStack item : preview) {
			lines.accept(Component.literal(" - ")
					.append(item.getHoverName())
					.append(item.getCount() > 1 ? Component.literal(" x" + item.getCount()) : Component.empty())
					.withStyle(ChatFormatting.DARK_GRAY));
		}

		if (used > preview.size()) {
			lines.accept(Component.literal(" ...").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
