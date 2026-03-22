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

public class Smart_gapClient implements ClientModInitializer {

    private static KeyBinding gapKey;
    private boolean wasEating = false;
    private int delayTimer = -1;

    // --- CONFIG EINSTELLUNGEN (Statisch, damit das GUI darauf zugreifen kann) ---
    public static boolean switchBackEnabled = true;
    public static int primaryWeaponSlot = 0; // Slot 1
    public static int backupWeaponSlot = 1;  // Slot 2
    public static int switchDelay = 0;       // Verzögerung in Ticks

    @Override
    public void onInitializeClient() {
        // Keybinding registrieren (Standard: X)
        gapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Use Smart Gap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KeyBinding.Category.MISC
        ));

        // Befehl /smartgap registrieren
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("smartgap")
                    .executes(context -> {
                        // WICHTIG: MinecraftClient.execute verhindert den Thread-Crash
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> {
                            client.setScreen(new ConfigScreen());
                        });
                        return 1;
                    }));
        });

        // Haupt-Logik pro Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (gapKey.wasPressed()) {
                selectBestGap(client);
            }

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
        if (bestSlot != -1) {
            client.player.getInventory().setSelectedSlot(bestSlot);
        }
    }

    private void handleAutoWeaponSwitch(MinecraftClient client) {
        if (!switchBackEnabled) return;

        ItemStack activeItem = client.player.getActiveItem();
        boolean isCurrentlyEating = client.player.isUsingItem() &&
                (activeItem.isOf(Items.GOLDEN_APPLE) || activeItem.isOf(Items.ENCHANTED_GOLDEN_APPLE));

        // Erkennung: Essen wurde gerade beendet
        if (!isCurrentlyEating && wasEating) {
            delayTimer = switchDelay;
        }

        // Delay-Logik (Wartet X Ticks bis zum Switch)
        if (delayTimer == 0) {
            performSwitch(client);
            delayTimer = -1;
        } else if (delayTimer > 0) {
            delayTimer--;
        }

        wasEating = isCurrentlyEating;
    }

    private void performSwitch(MinecraftClient client) {
        if (client.player == null) return;

        ItemStack primary = client.player.getInventory().getStack(primaryWeaponSlot);
        // Wenn der Primär-Slot nicht leer ist, dorthin wechseln. Sonst Backup.
        if (!primary.isEmpty()) {
            client.player.getInventory().setSelectedSlot(primaryWeaponSlot);
        } else {
            client.player.getInventory().setSelectedSlot(backupWeaponSlot);
        }
    }

    // --- INTERNE GUI KLASSE (Muss static sein, wenn sie hier drin steht) ---
    public static class ConfigScreen extends Screen {
        public ConfigScreen() {
            super(Text.literal("Smart Gap Config"));
        }

        @Override
        protected void init() {
            int x = this.width / 2 - 100;

            // Schalter: Auto-Switch
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Auto-Switch: " + (switchBackEnabled ? "§aAN" : "§cAUS")), b -> {
                        switchBackEnabled = !switchBackEnabled;
                        b.setMessage(Text.literal("Auto-Switch: " + (switchBackEnabled ? "§aAN" : "§cAUS")));
                    }).dimensions(x, 40, 200, 20).build());

            // Primär-Slot Selector
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Primary Slot: " + (primaryWeaponSlot + 1)), b -> {
                        primaryWeaponSlot = (primaryWeaponSlot + 1) % 9;
                        b.setMessage(Text.literal("Primary Slot: " + (primaryWeaponSlot + 1)));
                    }).dimensions(x, 70, 200, 20).build());

            // Backup-Slot Selector
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Backup Slot: " + (backupWeaponSlot + 1)), b -> {
                        backupWeaponSlot = (backupWeaponSlot + 1) % 9;
                        b.setMessage(Text.literal("Backup Slot: " + (backupWeaponSlot + 1)));
                    }).dimensions(x, 100, 200, 20).build());

            // Delay Selector
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Delay: " + switchDelay + " Ticks"), b -> {
                        switchDelay = (switchDelay + 1) % 11;
                        b.setMessage(Text.literal("Delay: " + switchDelay + " Ticks"));
                    }).dimensions(x, 130, 200, 20).build());

            // Schließen-Button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.client.setScreen(null))
                    .dimensions(x, 170, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Hintergrund rendern (Fix für 1.21.1)
            this.renderInGameBackground(context);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() {
            return false; // Spiel läuft im Hintergrund weiter (wichtig für Multiplayer)
        }
    }
}