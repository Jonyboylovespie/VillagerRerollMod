package com.villagerreroll.screen;

import com.villagerreroll.autolibrarian.AutoLibrarianController;
import com.villagerreroll.config.TargetBook;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class TargetBookCostScreen extends Screen {
	private final Screen parentScreen;
	private final AutoLibrarianController controller;
	private final int editIndex;
	private final TargetBook initialTarget;

	private int cost;
	private EditBox costField;
	private Button saveButton;

	public TargetBookCostScreen(Screen parentScreen, AutoLibrarianController controller,
		int editIndex, TargetBook initialTarget) {
		super(Component.literal(editIndex < 0 ? "Max Price" : "Edit Cost"));
		this.parentScreen = parentScreen;
		this.controller = controller;
		this.editIndex = editIndex;
		this.initialTarget = initialTarget;
		this.cost = initialTarget.maxPrice();
	}

	@Override
	protected void init() {
		int center = width / 2;
		int buttonY = 112;

		costField = addRenderableWidget(new EditBox(font, center - 50, 82, 100, 20,
			Component.literal("Max Price")));
		costField.setMaxLength(2);
		costField.setValue(String.valueOf(cost));
		costField.setResponder(value -> updateSaveState());

		addRenderableWidget(Button.builder(Component.literal("-10"), b -> adjustCost(-10))
			.bounds(center - 106, buttonY, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("-1"), b -> adjustCost(-1))
			.bounds(center - 52, buttonY, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+1"), b -> adjustCost(1))
			.bounds(center + 2, buttonY, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+10"), b -> adjustCost(10))
			.bounds(center + 56, buttonY, 50, 20).build());

		saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
			.bounds(center - 102, height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> closeWithoutSaving())
			.bounds(center + 2, height - 28, 100, 20).build());

		updateSaveState();
		setInitialFocus(costField);
	}

	@Override
	protected void setInitialFocus() {
		if(costField != null) {
			super.setInitialFocus(costField);
			return;
		}

		super.setInitialFocus();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if(event.key() == GLFW.GLFW_KEY_ESCAPE) {
			closeWithoutSaving();
			return true;
		}

		if(event.key() == GLFW.GLFW_KEY_ENTER && saveButton.active) {
			saveAndClose();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
		extractMenuBackground(context);
		context.centeredText(font, title, width / 2, 12, 0xFFFFFF);
		context.centeredText(font, "Set the max emerald cost. Level stays at max.", width / 2, 22, 0xA0A0A0);
		context.centeredText(font, initialTarget.enchantmentId(), width / 2, 42, 0xFFFFFF);
		context.centeredText(font, "Max level: " + initialTarget.level(), width / 2, 58, 0xA0A0A0);
		context.centeredText(font, "Max Price", width / 2, 72, 0xC0C0C0);
		super.extractRenderState(context, mouseX, mouseY, partialTicks);
	}

	private void adjustCost(int delta) {
		cost = Math.max(1, Math.min(64, readCostOrDefault() + delta));
		if(costField != null)
			costField.setValue(String.valueOf(cost));
		updateSaveState();
	}

	private void updateSaveState() {
		Integer parsed = parseCost();
		if(parsed != null)
			cost = parsed;
		if(saveButton != null)
			saveButton.active = parsed != null && parsed >= 1 && parsed <= 64;
	}

	private void saveAndClose() {
		Integer parsed = parseCost();
		if(parsed == null || parsed < 1 || parsed > 64)
			return;

		cost = parsed;
		TargetBook updated = new TargetBook(initialTarget.enchantmentId(), initialTarget.level(), cost);
		if(editIndex < 0) {
			controller.addTarget(updated);
		} else {
			boolean replaced = controller.replaceTarget(editIndex, updated);
			if(!replaced)
				controller.addTarget(updated);
		}
		controller.saveConfig();
		if(parentScreen instanceof TargetBookListScreen listScreen)
			listScreen.refreshEntries();
		minecraft.setScreen(parentScreen);
	}

	private int readCostOrDefault() {
		Integer parsed = parseCost();
		if(parsed == null)
			return cost;
		return parsed;
	}

	private Integer parseCost() {
		if(costField == null)
			return cost;

		String value = costField.getValue().trim();
		if(value.isEmpty())
			return null;
		try {
			return Integer.parseInt(value);
		}catch(NumberFormatException e) {
			return null;
		}
	}

	private void closeWithoutSaving() {
		if(parentScreen instanceof TargetBookListScreen listScreen)
			listScreen.refreshEntries();
		minecraft.setScreen(parentScreen);
	}
}
