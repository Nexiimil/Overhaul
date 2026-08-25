package com.overhaul.module.backpack;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A wearable-sized container opened from the hand or with the backpack key.
 *
 * <p>Each tier is its own item with its own row count, which is what lets the vanilla chest
 * screens do all the rendering: one to six rows map exactly onto {@code GENERIC_9x1} through
 * {@code GENERIC_9x6}, so there is no custom screen, no custom texture and nothing to keep in
 * sync with resource pack changes.
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

		int size = backpack.slotCount();

		for (ItemStack lost : BackpackContainer.overflow(stack, size)) {
			player.getInventory().placeItemBackInInventory(lost);
		}

		BackpackContainer container = new BackpackContainer(stack, size, BackpackModule.allowNesting());

		player.openMenu(new SimpleMenuProvider(
				(containerId, inventory, owner) -> new ChestMenu(
						menuTypeFor(backpack.rows()), containerId, inventory, container, backpack.rows()),
				stack.getHoverName()));

		player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 0.9F);
	}

	private static MenuType<ChestMenu> menuTypeFor(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
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
