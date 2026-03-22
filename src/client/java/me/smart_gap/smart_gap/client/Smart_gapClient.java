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

public class Smart_gapClient implements ClientModInitializer {

    private static KeyBinding gapKey;
    private boolean wasEating = false;
    private int lastUseTime = 0;
    private int lastStackCount = -1;
    private int delayTimer = -1;

    // --- CONFIG VALUES ---
    public static ConfigData config = new ConfigData();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("smart_gap.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<String> OPTIONS = Arrays.asList(
            "Sword", "Axe", "Spear", "Trident", "Mace",
            "Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5", "Slot 6", "Slot 7", "Slot 8", "Slot 9"
    );

    // Data class for JSON
    public static class ConfigData {
        public boolean switchBackEnabled = true;
        public int switchDelay = 0;
        public int primaryIndex = 0;
        public int backupIndex = 1;
    }

    @Override
    public void onInitializeClient() {
        loadConfig(); // Load on startup

        gapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Use Smart Gap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, KeyBinding.Category.MISC));

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

    // --- SAVE LOGIC ---
    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, ConfigData.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // --- LOGIC ---
    private void selectBestGap(MinecraftClient client) {
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) { bestSlot = i; break; }
            else if (stack.isOf(Items.GOLDEN_APPLE) && bestSlot == -1) { bestSlot = i; }
        }
        if (bestSlot != -1) client.player.getInventory().setSelectedSlot(bestSlot);
    }

    private void handleAutoWeaponSwitch(MinecraftClient client) {
        if (!config.switchBackEnabled || client.player == null) return;

        ItemStack activeItem = client.player.getActiveItem();
        boolean isCurrentlyEating = client.player.isUsingItem() &&
                (activeItem.isOf(Items.GOLDEN_APPLE) || activeItem.isOf(Items.ENCHANTED_GOLDEN_APPLE));

        int currentStackCount = activeItem.getCount();

        if (isCurrentlyEating && lastStackCount != -1 && currentStackCount < lastStackCount) {
            delayTimer = config.switchDelay;
        }

        if (!isCurrentlyEating && wasEating) {
            if (lastUseTime >= 31) delayTimer = config.switchDelay;
        }

        wasEating = isCurrentlyEating;
        lastUseTime = client.player.getItemUseTime();
        lastStackCount = isCurrentlyEating ? currentStackCount : -1;

        if (delayTimer == 0) { findAndPerformSwitch(client); delayTimer = -1; }
        else if (delayTimer > 0) delayTimer--;
    }

    private void findAndPerformSwitch(MinecraftClient client) {
        int slot = findSlotByMode(client, OPTIONS.get(config.primaryIndex));
        if (slot == -1) slot = findSlotByMode(client, OPTIONS.get(config.backupIndex));
        if (slot != -1) client.player.getInventory().setSelectedSlot(slot);
    }

    private int findSlotByMode(MinecraftClient client, String mode) {
        if (mode.startsWith("Slot")) return Integer.parseInt(mode.split(" ")[1]) - 1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String itemName = s.getItem().toString().toLowerCase();
            if (mode.equals("Sword") && itemName.contains("sword")) return i;
            if (mode.equals("Axe") && itemName.contains("_axe")) return i;
            if (mode.equals("Spear") && itemName.contains("spear")) return i;
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
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Auto-Switch: " + (config.switchBackEnabled ? "§aON" : "§cOFF")), b -> {
                config.switchBackEnabled = !config.switchBackEnabled;
                b.setMessage(Text.literal("Auto-Switch: " + (config.switchBackEnabled ? "§aON" : "§cOFF")));
                saveConfig();
            }).dimensions(x, 40, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Primary: §b" + OPTIONS.get(config.primaryIndex)), b -> client.setScreen(new SelectionScreen(this, true))).dimensions(x, 70, 200, 20).build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Backup: §b" + OPTIONS.get(config.backupIndex)), b -> client.setScreen(new SelectionScreen(this, false))).dimensions(x, 100, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Delay: " + config.switchDelay + " Ticks"), b -> {
                config.switchDelay = (config.switchDelay + 1) % 11;
                b.setMessage(Text.literal("Delay: " + config.switchDelay + " Ticks"));
                saveConfig();
            }).dimensions(x, 130, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.client.setScreen(null)).dimensions(x, 170, 200, 20).build());
        }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) { this.renderInGameBackground(context); context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF); super.render(context, mouseX, mouseY, delta); }
        @Override public boolean shouldPause() { return false; }
    }

    public static class SelectionScreen extends Screen {
        private final Screen parent;
        private final boolean isPrimary;
        public SelectionScreen(Screen parent, boolean isPrimary) { super(Text.literal(isPrimary ? "Select Primary" : "Select Backup")); this.parent = parent; this.isPrimary = isPrimary; }
        @Override
        protected void init() {
            int bw = 100, bh = 20, sp = 4, cols = 3;
            int tw = (cols * bw) + ((cols - 1) * sp);
            int sx = (this.width - tw) / 2, sy = 40;
            for (int i = 0; i < OPTIONS.size(); i++) {
                int index = i;
                int r = i / cols, c = i % cols;
                this.addDrawableChild(ButtonWidget.builder(Text.literal(OPTIONS.get(i)), b -> {
                    if (isPrimary) config.primaryIndex = index; else config.backupIndex = index;
                    saveConfig(); // Save on selection
                    client.setScreen(parent);
                }).dimensions(sx + c * (bw + sp), sy + r * (bh + sp), bw, bh).build());
            }
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> client.setScreen(parent)).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
        }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) { this.renderInGameBackground(context); context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF); super.render(context, mouseX, mouseY, delta); }
        @Override public boolean shouldPause() { return false; }
    }
}