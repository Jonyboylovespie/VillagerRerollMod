package com.villagerreroll.screen;

import java.util.List;

import com.villagerreroll.autolibrarian.AutoLibrarianController;
import com.villagerreroll.config.TargetBook;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TargetBookListScreen extends Screen {
	private static final int SIDE_MARGIN = 32;
	private static final int TOP = 48;
	private static final int ROW_HEIGHT = 22;
	private static final int COLUMN_GAP = 8;

	private final Screen parentScreen;
	private final AutoLibrarianController controller;

	private Button editButton;
	private Button removeButton;
	private Button lockInButton;
	private Button startButton;
	private Button stopButton;
	private int selectedIndex = -1;

	public TargetBookListScreen(Screen parentScreen, AutoLibrarianController controller) {
		super(Component.literal("Villager Reroll"));
		this.parentScreen = parentScreen;
		this.controller = controller;
	}

	@Override
	protected void init() {
		List<TargetBook> targets = controller.getTargets();
		if(targets.isEmpty())
			selectedIndex = -1;
		else if(selectedIndex < 0 || selectedIndex >= targets.size())
			selectedIndex = 0;

		addTargetButtons(targets);

		int buttonY = height - 52;
		int footerY = height - 26;
		int left = width / 2 - 155;

		addRenderableWidget(Button.builder(Component.literal("Add"), b -> openAddScreen())
			.bounds(left, buttonY, 100, 20).build());
		editButton = addRenderableWidget(Button.builder(Component.literal("Edit"), b -> openEditScreen())
			.bounds(left + 105, buttonY, 100, 20).build());
		removeButton = addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeSelected())
			.bounds(left + 210, buttonY, 100, 20).build());
		lockInButton = addRenderableWidget(Button.builder(lockInLabel(), b -> toggleLockInTrade())
			.bounds(left + 315, buttonY, 120, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Close"), b -> closeAndSave())
			.bounds(left, footerY, 100, 20).build());
		startButton = addRenderableWidget(Button.builder(Component.literal("Start"), b -> startRolling())
			.bounds(width / 2 - 50, footerY, 100, 20).build());
		stopButton = addRenderableWidget(Button.builder(Component.literal("Stop"), b -> stopRolling())
			.bounds(left + 210, footerY, 100, 20).build());

		updateButtonStates();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
		extractMenuBackground(context);
		context.centeredText(font, title, width / 2, 12, 0xFFFFFF);
		context.centeredText(font, "Click a target to select it. Edit only changes emerald cost.", width / 2, 22,
			0xA0A0A0);
		context.centeredText(font, "Start closes this screen and waits for a lectern click.", width / 2, 32,
			0xA0A0A0);
		context.centeredText(font, "Lock in trade: " + (controller.isLockInTradeEnabled() ? "ON" : "OFF"),
			width / 2, 40, 0xA0A0A0);
		if(controller.getTargets().isEmpty())
			context.centeredText(font, "No targets added.", width / 2, height / 2, 0xA0A0A0);
		else if(selectedIndex >= 0 && selectedIndex < controller.getTargets().size())
			context.centeredText(font, "Selected: " + targetLabel(controller.getTargets().get(selectedIndex)),
				width / 2, height - 76, 0xFFFF55);
		if(controller.isAwaitingLectern())
			context.centeredText(font, "Waiting for lectern click...", width / 2, height - 88, 0xFFFF55);

		super.extractRenderState(context, mouseX, mouseY, partialTicks);
	}

	void refreshEntries() {
		if(minecraft != null)
			minecraft.setScreen(this);
	}

	private void addTargetButtons(List<TargetBook> targets) {
		int availableHeight = Math.max(ROW_HEIGHT, height - TOP - 118);
		int rowCount = Math.max(1, availableHeight / ROW_HEIGHT);
		int columnCount = Math.max(1, (int)Math.ceil(targets.size() / (double)rowCount));
		int availableWidth = Math.max(120, width - SIDE_MARGIN * 2 - COLUMN_GAP * (columnCount - 1));
		int buttonWidth = Math.max(120, availableWidth / columnCount);

		for(int i = 0; i < targets.size(); i++) {
			TargetBook target = targets.get(i);
			int index = i;
			int column = i / rowCount;
			int row = i % rowCount;
			int x = SIDE_MARGIN + column * (buttonWidth + COLUMN_GAP);
			int y = TOP + row * ROW_HEIGHT;
			addRenderableWidget(Button.builder(Component.literal(rowLabel(index, target)),
				b -> selectTarget(index)).bounds(x, y, buttonWidth, 20).build());
		}
	}

	private void selectTarget(int index) {
		selectedIndex = index;
		minecraft.setScreen(this);
	}

	private void openAddScreen() {
		minecraft.setScreen(new EnchantmentSelectScreen(this, controller));
	}

	private void openEditScreen() {
		TargetBook selected = selectedTarget();
		if(selected == null)
			return;

		minecraft.setScreen(new TargetBookCostScreen(this, controller, selectedIndex, selected));
	}

	private void removeSelected() {
		if(selectedTarget() == null)
			return;

		controller.removeTarget(selectedIndex);
		if(selectedIndex >= controller.getTargets().size())
			selectedIndex = controller.getTargets().size() - 1;
		minecraft.setScreen(this);
	}

	private void closeAndSave() {
		controller.saveConfig();
		minecraft.setScreen(parentScreen);
	}

	private void startRolling() {
		controller.saveConfig();
		if(controller.armForLectern(minecraft))
			minecraft.setScreen(parentScreen);
	}

	private void stopRolling() {
		controller.stop(minecraft, "Auto Librarian stopped.");
		updateButtonStates();
	}

	private void toggleLockInTrade() {
		controller.setLockInTradeEnabled(!controller.isLockInTradeEnabled());
		refreshEntries();
	}

	private TargetBook selectedTarget() {
		List<TargetBook> targets = controller.getTargets();
		if(selectedIndex < 0 || selectedIndex >= targets.size())
			return null;
		return targets.get(selectedIndex);
	}

	private void updateButtonStates() {
		if(editButton == null || removeButton == null || lockInButton == null || startButton == null
			|| stopButton == null)
			return;

		boolean hasSelection = selectedTarget() != null;
		editButton.active = hasSelection;
		removeButton.active = hasSelection;
		lockInButton.active = true;
		lockInButton.setMessage(lockInLabel());
		startButton.active = !controller.isRunning() && !controller.isAwaitingLectern()
			&& !controller.getTargets().isEmpty();
		stopButton.active = controller.isRunning() || controller.isAwaitingLectern();
	}

	private String rowLabel(int index, TargetBook target) {
		String marker = index == selectedIndex ? "> " : "  ";
		return marker + targetLabel(target);
	}

	private String targetLabel(TargetBook target) {
		return target.enchantmentId() + "  " + target.maxPrice() + " emeralds";
	}

	private Component lockInLabel() {
		return Component.literal("Lock in trade: " + (controller.isLockInTradeEnabled() ? "ON" : "OFF"));
	}
}
