package me.smart_gap.smart_gap.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Main client-side initializer for the Smart Gap mod.
 * This class handles keybinding registration, command registration,
 * configuration loading/saving, and the core logic for automatic
 * golden apple consumption and weapon switching.
 */
public class Smart_gapClient implements ClientModInitializer {

    private static KeyBinding gapKey;
    private boolean wasEating = false;
    private int lastUseTime = 0;
    private int lastStackCount = -1;
    private int delayTimer = -1;

    /**
     * --- CONFIGURATION VALUES ---
     * These static fields manage the mod's settings, including
     * auto-switch behavior, delay, and preferred item slots.
     */
    public static ConfigData config = new ConfigData();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("smart_gap.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * A list of available options for weapon/tool selection in the configuration GUI.
     * Includes common weapon types and direct hotbar slot selections.
     */
    public static final List<String> OPTIONS = Arrays.asList(
            "Sword", "Axe", "Spear", "Trident", "Mace",
            "Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5", "Slot 6", "Slot 7", "Slot 8", "Slot 9"
    );

    /**
     * Data class representing the mod's configuration.
     * This class is used for JSON serialization and deserialization.
     */
    public static class ConfigData {
        /**
         * If true, the mod will automatically switch back to a weapon after eating a golden apple.
         */
        public boolean switchBackEnabled = true;
        /**
         * The delay in ticks before switching back to a weapon after eating.
         * A value of 0 means no delay.
         */
        public int switchDelay = 0;
        /**
         * The index of the primary item/slot preference from the {@link #OPTIONS} list.
         */
        public int primaryIndex = 0;
        /**
         * The index of the backup item/slot preference from the {@link #OPTIONS} list.
         * Used if the primary item/slot is not found.
         */
        public int backupIndex = 1;
    }

    /**
     * Called when the client-side mod is initialized.
     * This method sets up keybindings, registers client commands,
     * and hooks into the client tick event for continuous logic.
     */
    @Override
    public void onInitializeClient() {
        loadConfig(); // Load configuration settings when the mod starts.

        // Register the keybinding for manually activating the Smart Gap feature.
        gapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Use Smart Gap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, KeyBinding.Category.MISC));

        // Register a client-side command to open the mod's configuration screen.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("smartgap").executes(context -> {
                MinecraftClient client = MinecraftClient.getInstance();
                // Ensure GUI operations are performed on the main client thread.
                client.execute(() -> client.setScreen(new ConfigScreen()));
                return 1; // Indicate successful command execution.
            }));
        });

        // Register a tick event listener to run logic every client tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return; // Only proceed if a player exists.

            // Handle manual key press for golden apple consumption.
            while (gapKey.wasPressed()) {
                selectBestGap(client);
            }
            // Handle automatic weapon switching after golden apple consumption.
            handleAutoWeaponSwitch(client);
        });
    }

    /**
     * --- CONFIGURATION SAVE/LOAD LOGIC ---
     * Methods for persisting and retrieving the mod's settings to/from a JSON file.
     */

    /**
     * Saves the current configuration to the smart_gap.json file.
     * The configuration is serialized using Gson with pretty printing.
     */
    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            // Print stack trace if an error occurs during file writing.
            e.printStackTrace();
        }
    }

    /**
     * Loads the configuration from the smart_gap.json file.
     * If the file does not exist, default configuration values are used.
     * The configuration is deserialized using Gson.
     */
    private void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, ConfigData.class);
                // If deserialization fails or returns null, ensure config is re-initialized to defaults.
                if (config == null) {
                    config = new ConfigData();
                }
            } catch (IOException e) {
                // Print stack trace if an error occurs during file reading.
                e.printStackTrace();
                // Fallback to default configuration in case of read error.
                config = new ConfigData();
            }
        }
    }

    /**
     * --- CORE MOD LOGIC ---
     * These methods implement the main functionality of the Smart Gap mod.
     */

    /**
     * Selects the best golden apple (enchanted first, then regular) from the hotbar
     * and switches the player's selected slot to it.
     *
     * @param client The MinecraftClient instance.
     */
    private void selectBestGap(MinecraftClient client) {
        int bestSlot = -1;
        // Iterate through the hotbar slots (0-8).
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            // Prioritize enchanted golden apples.
            if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                bestSlot = i;
                break; // Found the best, no need to check further.
            }
            // If no enchanted golden apple is found yet, consider a regular golden apple.
            else if (stack.isOf(Items.GOLDEN_APPLE) && bestSlot == -1) {
                bestSlot = i;
            }
        }
        // If a golden apple was found, switch to its slot.
        if (bestSlot != -1) {
            client.player.getInventory().setSelectedSlot(bestSlot);
        }
    }

    /**
     * Handles the automatic weapon switching logic after a golden apple has been consumed.
     * This method tracks eating status, item use time, and stack count to determine
     * when a golden apple has been eaten and then triggers a switch after a configurable delay.
     *
     * @param client The MinecraftClient instance.
     */
    private void handleAutoWeaponSwitch(MinecraftClient client) {
        // Only proceed if auto-switch is enabled and the player exists.
        if (!config.switchBackEnabled || client.player == null) return;

        ItemStack activeItem = client.player.getActiveItem();
        // Check if the player is currently using (eating) a golden apple.
        boolean isCurrentlyEating = client.player.isUsingItem() &&
                (activeItem.isOf(Items.GOLDEN_APPLE) || activeItem.isOf(Items.ENCHANTED_GOLDEN_APPLE));

        int currentStackCount = activeItem.getCount();

        // If eating and the stack count decreased, it means an apple was consumed.
        if (isCurrentlyEating && lastStackCount != -1 && currentStackCount < lastStackCount) {
            delayTimer = config.switchDelay;
        }

        // If the player just stopped eating and the item use time was sufficient (31 ticks for golden apples).
        if (!isCurrentlyEating && wasEating) {
            if (lastUseTime >= 31) { // Golden apples take 32 ticks to eat.
                delayTimer = config.switchDelay;
            }
        }

        // Update state variables for the next tick.
        wasEating = isCurrentlyEating;
        lastUseTime = client.player.getItemUseTime();
        lastStackCount = isCurrentlyEating ? currentStackCount : -1;

        // Handle the delay timer.
        if (delayTimer == 0) {
            findAndPerformSwitch(client); // Perform the switch when the delay is over.
            delayTimer = -1; // Reset timer.
        } else if (delayTimer > 0) {
            delayTimer--; // Decrement timer if still counting down.
        }
    }

    /**
     * Finds and performs the weapon switch based on primary and backup configuration.
     * It first attempts to find the primary item/slot, and if not found,
     * it tries the backup item/slot.
     *
     * @param client The MinecraftClient instance.
     */
    private void findAndPerformSwitch(MinecraftClient client) {
        // Attempt to find the primary configured item/slot.
        int slot = findSlotByMode(client, OPTIONS.get(config.primaryIndex));
        // If primary not found, try the backup.
        if (slot == -1) {
            slot = findSlotByMode(client, OPTIONS.get(config.backupIndex));
        }
        // If a valid slot was found, switch to it.
        if (slot != -1) {
            client.player.getInventory().setSelectedSlot(slot);
        }
    }

    /**
     * Finds the hotbar slot index for a given item mode (e.g., "Sword", "Slot 1").
     *
     * @param client The MinecraftClient instance.
     * @param mode   The string representing the desired item type or slot number.
     * @return The hotbar slot index (0-8) if found, otherwise -1.
     */
    private int findSlotByMode(MinecraftClient client, String mode) {
        // Handle direct slot selection (e.g., "Slot 1").
        if (mode.startsWith("Slot")) {
            return Integer.parseInt(mode.split(" ")[1]) - 1; // Convert "Slot X" to 0-indexed slot.
        }
        // Iterate through hotbar to find item by type.
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.isEmpty()) continue; // Skip empty slots.

            String itemName = s.getItem().toString().toLowerCase();
            // Check for specific item types.
            if (mode.equals("Sword") && itemName.contains("sword")) return i;
            if (mode.equals("Axe") && itemName.contains("_axe")) return i;
            if (mode.equals("Spear") && itemName.contains("spear")) return i;
            if (mode.equals("Trident") && s.isOf(Items.TRIDENT)) return i;
            if (mode.equals("Mace") && itemName.contains("mace")) return i;
        }
        return -1; // Item/slot not found.
    }

    /**
     * --- GRAPHICAL USER INTERFACE (GUI) ---
     * Classes for displaying and managing the mod's configuration screens.
     */

    /**
     * The main configuration screen for the Smart Gap mod.
     * Allows users to toggle auto-switch, set primary/backup items, and adjust delay.
     */
    public static class ConfigScreen extends Screen {
        /**
         * Constructs a new ConfigScreen.
         */
        public ConfigScreen() {
            super(Text.literal("Smart Gap Settings"));
        }

        /**
         * Initializes the screen, adding buttons for various configuration options.
         */
        @Override
        protected void init() {
            int x = this.width / 2 - 100; // Center the buttons horizontally.

            // Button to toggle auto-switch functionality.
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Auto-Switch: " + (config.switchBackEnabled ? "§aON" : "§cOFF")), b -> {
                config.switchBackEnabled = !config.switchBackEnabled;
                b.setMessage(Text.literal("Auto-Switch: " + (config.switchBackEnabled ? "§aON" : "§cOFF")));
                saveConfig(); // Save changes immediately.
            }).dimensions(x, 40, 200, 20).build());

            // Button to open the selection screen for the primary item/slot.
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Primary: §b" + OPTIONS.get(config.primaryIndex)), b -> client.setScreen(new SelectionScreen(this, true))).dimensions(x, 70, 200, 20).build());
            // Button to open the selection screen for the backup item/slot.
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Backup: §b" + OPTIONS.get(config.backupIndex)), b -> client.setScreen(new SelectionScreen(this, false))).dimensions(x, 100, 200, 20).build());

            // Button to adjust the switch delay. Cycles through 0-10 ticks.
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Delay: " + config.switchDelay + " Ticks"), b -> {
                config.switchDelay = (config.switchDelay + 1) % 11; // Cycle delay from 0 to 10.
                b.setMessage(Text.literal("Delay: " + config.switchDelay + " Ticks"));
                saveConfig(); // Save changes immediately.
            }).dimensions(x, 130, 200, 20).build());

            // "Done" button to close the configuration screen.
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.client.setScreen(null)).dimensions(x, 170, 200, 20).build());
        }

        /**
         * Renders the screen's background, title, and widgets.
         *
         * @param context The drawing context.
         * @param mouseX  The X coordinate of the mouse.
         * @param mouseY  The Y coordinate of the mouse.
         * @param delta   The time elapsed since the last frame.
         */
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderInGameBackground(context); // Render the standard Minecraft background.
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF); // Draw the screen title.
            super.render(context, mouseX, mouseY, delta); // Render all added widgets.
        }

        /**
         * Determines if the game should pause when this screen is open.
         *
         * @return False, meaning the game will not pause.
         */
        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    /**
     * A selection screen used to choose a primary or backup item/slot from a list of options.
     */
    public static class SelectionScreen extends Screen {
        private final Screen parent;
        private final boolean isPrimary;

        /**
         * Constructs a new SelectionScreen.
         *
         * @param parent    The parent screen to return to after selection.
         * @param isPrimary True if this screen is for selecting the primary item, false for backup.
         */
        public SelectionScreen(Screen parent, boolean isPrimary) {
            super(Text.literal(isPrimary ? "Select Primary" : "Select Backup"));
            this.parent = parent;
            this.isPrimary = isPrimary;
        }

        /**
         * Initializes the screen, adding buttons for each available option.
         */
        @Override
        protected void init() {
            int bw = 100, bh = 20, sp = 4, cols = 3; // Button width, height, spacing, columns.
            int tw = (cols * bw) + ((cols - 1) * sp); // Total width of buttons.
            int sx = (this.width - tw) / 2, sy = 40; // Starting X and Y for buttons.

            // Create a button for each option in the OPTIONS list.
            for (int i = 0; i < OPTIONS.size(); i++) {
                int index = i;
                int r = i / cols, c = i % cols; // Calculate row and column for button placement.
                this.addDrawableChild(ButtonWidget.builder(Text.literal(OPTIONS.get(i)), b -> {
                    // Set the selected index based on whether it's primary or backup.
                    if (isPrimary) config.primaryIndex = index;
                    else config.backupIndex = index;
                    saveConfig(); // Save changes immediately.
                    client.setScreen(parent); // Return to the parent configuration screen.
                }).dimensions(sx + c * (bw + sp), sy + r * (bh + sp), bw, bh).build());
            }
            // "Cancel" button to return to the parent screen without making a selection.
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> client.setScreen(parent)).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
        }

        /**
         * Renders the screen's background, title, and widgets.
         *
         * @param context The drawing context.
         * @param mouseX  The X coordinate of the mouse.
         * @param mouseY  The Y coordinate of the mouse.
         * @param delta   The time elapsed since the last frame.
         */
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderInGameBackground(context); // Render the standard Minecraft background.
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF); // Draw the screen title.
            super.render(context, mouseX, mouseY, delta); // Render all added widgets.
        }

        /**
         * Determines if the game should pause when this screen is open.
         *
         * @return False, meaning the game will not pause.
         */
        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}