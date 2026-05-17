package com.villagerreroll.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.villagerreroll.autolibrarian.AutoLibrarianController;
import com.villagerreroll.config.TargetBook;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

public final class EnchantmentSelectScreen extends Screen {
	private static final int SIDE_MARGIN = 32;
	private static final int TOP = 42;
	private static final int ROW_HEIGHT = 22;
	private static final int COLUMN_GAP = 8;

	private final Screen parentScreen;
	private final AutoLibrarianController controller;
	private final List<SelectableEnchantment> enchantments = new ArrayList<>();

	public EnchantmentSelectScreen(Screen parentScreen, AutoLibrarianController controller) {
		super(Component.literal("Select Enchantment"));
		this.parentScreen = parentScreen;
		this.controller = controller;
	}

	@Override
	protected void init() {
		loadEnchantments();
		addEnchantmentButtons();

		int buttonY = height - 28;
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(parentScreen))
			.bounds(width / 2 - 50, buttonY, 100, 20).build());
	}

	@Override
	protected void setInitialFocus() {
		clearFocus();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
		extractMenuBackground(context);
		context.centeredText(font, title, width / 2, 12, 0xFFFFFF);
		context.centeredText(font, "Click an enchantment to set the max price.", width / 2, 22, 0xA0A0A0);
		super.extractRenderState(context, mouseX, mouseY, partialTicks);
	}

	private void loadEnchantments() {
		enchantments.clear();
		if(minecraft.level == null)
			return;

		Registry<Enchantment> registry = minecraft.level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT);
		registry.listElements()
			.forEach(holder -> holder.unwrapKey().ifPresent(key ->
				enchantments.add(new SelectableEnchantment(key.identifier(), holder.value()))));

		enchantments.sort(Comparator
			.comparing(SelectableEnchantment::label, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(enchantment -> enchantment.id().toString(), String.CASE_INSENSITIVE_ORDER));
	}

	private void addEnchantmentButtons() {
		int availableHeight = Math.max(ROW_HEIGHT, height - TOP - 74);
		int rowCount = Math.max(1, availableHeight / ROW_HEIGHT);
		int columnCount = Math.max(1, (int)Math.ceil(enchantments.size() / (double)rowCount));
		int availableWidth = Math.max(120, width - SIDE_MARGIN * 2 - COLUMN_GAP * (columnCount - 1));
		int buttonWidth = Math.max(120, availableWidth / columnCount);

		for(int i = 0; i < enchantments.size(); i++) {
			SelectableEnchantment enchantment = enchantments.get(i);
			int column = i / rowCount;
			int row = i % rowCount;
			int x = SIDE_MARGIN + column * (buttonWidth + COLUMN_GAP);
			int y = TOP + row * ROW_HEIGHT;
			addRenderableWidget(Button.builder(Component.literal(enchantment.label()),
				b -> addEnchantment(enchantment)).bounds(x, y, buttonWidth, 20).build());
		}
	}

	private void addEnchantment(SelectableEnchantment enchantment) {
		TargetBook target = new TargetBook(enchantment.id().toString(), enchantment.enchantment().getMaxLevel(), 64);
		minecraft.setScreen(new TargetBookCostScreen(parentScreen, controller, -1, target));
	}

	private record SelectableEnchantment(Identifier id, Enchantment enchantment) {
		private String label() {
			return enchantment.description().getString();
		}
	}
}
