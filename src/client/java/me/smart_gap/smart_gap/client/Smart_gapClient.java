package me.smart_gap.smart_gap.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;

public class Smart_gapClient implements ClientModInitializer {

    private static KeyBinding gapKey;
    private boolean wasEating = false;
    private int delayTimer = -1;

    // --- CONFIG ---
    public static boolean switchBackEnabled = true;
    public static int switchDelay = 0;

    // Optionen für das GUI
    public static List<String> selectionOptions = Arrays.asList(
            "Sword", "Axe", "Trident", "Mace",
            "Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5", "Slot 6", "Slot 7", "Slot 8", "Slot 9"
    );
    public static int primaryIndex = 0; // Default: Sword
    public static int backupIndex = 1;  // Default: Axe

    @Override
    public void onInitializeClient() {
        gapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Use Smart Gap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KeyBinding.Category.MISC
        ));

        // Befehl /smartgap sicher registrieren
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("smartgap").executes(context -> {
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(new ConfigScreen()));
                return 1;
            }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (gapKey.wasPressed()) selectBestGap(client);
            handleAutoWeaponSwitch(client);
        });
    }

    private void selectBestGap(MinecraftClient client) {
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                bestSlot = i;
                break;
            } else if (stack.isOf(Items.GOLDEN_APPLE) && bestSlot == -1) {
                bestSlot = i;
            }
        }
        if (bestSlot != -1) client.player.getInventory().setSelectedSlot(bestSlot);
    }

    private void handleAutoWeaponSwitch(MinecraftClient client) {
        if (!switchBackEnabled) return;

        ItemStack activeItem = client.player.getActiveItem();
        boolean isCurrentlyEating = client.player.isUsingItem() &&
                (activeItem.isOf(Items.GOLDEN_APPLE) || activeItem.isOf(Items.ENCHANTED_GOLDEN_APPLE));

        if (!isCurrentlyEating && wasEating) {
            delayTimer = switchDelay;
        }

        if (delayTimer == 0) {
            findAndPerformSwitch(client);
            delayTimer = -1;
        } else if (delayTimer > 0) {
            delayTimer--;
        }

        wasEating = isCurrentlyEating;
    }

    private void findAndPerformSwitch(MinecraftClient client) {
        int slot = findSlotByMode(client, selectionOptions.get(primaryIndex));
        // Wenn Primary nicht gefunden wurde, Backup versuchen
        if (slot == -1 || client.player.getInventory().getStack(slot).isEmpty()) {
            slot = findSlotByMode(client, selectionOptions.get(backupIndex));
        }

        if (slot != -1) {
            client.player.getInventory().setSelectedSlot(slot);
        }
    }

    private int findSlotByMode(MinecraftClient client, String mode) {
        // Falls ein fester Slot gewählt wurde
        if (mode.startsWith("Slot")) {
            return Integer.parseInt(mode.split(" ")[1]) - 1;
        }

        // Suche nach Item-Typen über den internen Namen (verhindert Import-Fehler)
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;

            String itemName = s.getItem().toString().toLowerCase();

            if (mode.equals("Sword") && itemName.contains("sword")) return i;
            if (mode.equals("Axe") && itemName.contains("_axe")) return i;
            if (mode.equals("Trident") && s.isOf(Items.TRIDENT)) return i;
            if (mode.equals("Mace") && itemName.contains("mace")) return i;
        }
        return -1;
    }

    // --- GUI ---
    public static class ConfigScreen extends Screen {
        public ConfigScreen() { super(Text.literal("Smart Gap Settings")); }

        @Override
        protected void init() {
            int x = this.width / 2 - 100;

            // Toggle Auto-Switch
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Auto-Switch: " + (switchBackEnabled ? "§aON" : "§cOFF")), b -> {
                        switchBackEnabled = !switchBackEnabled;
                        b.setMessage(Text.literal("Auto-Switch: " + (switchBackEnabled ? "§aON" : "§cOFF")));
                    }).dimensions(x, 40, 200, 20).build());

            // Primary Selection
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Primary: " + selectionOptions.get(primaryIndex)), b -> {
                        primaryIndex = (primaryIndex + 1) % selectionOptions.size();
                        b.setMessage(Text.literal("Primary: " + selectionOptions.get(primaryIndex)));
                    }).dimensions(x, 70, 200, 20).build());

            // Backup Selection
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Backup: " + selectionOptions.get(backupIndex)), b -> {
                        backupIndex = (backupIndex + 1) % selectionOptions.size();
                        b.setMessage(Text.literal("Backup: " + selectionOptions.get(backupIndex)));
                    }).dimensions(x, 100, 200, 20).build());

            // Delay Selection
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Delay: " + switchDelay + " Ticks"), b -> {
                        switchDelay = (switchDelay + 1) % 11;
                        b.setMessage(Text.literal("Delay: " + switchDelay + " Ticks"));
                    }).dimensions(x, 130, 200, 20).build());

            // Done Button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.client.setScreen(null))
                    .dimensions(x, 170, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderInGameBackground(context);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() { return false; }
    }
}