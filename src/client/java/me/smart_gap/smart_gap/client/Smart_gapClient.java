package me.smart_gap.smart_gap.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

public class Smart_gapClient implements ClientModInitializer {

    private static KeyBinding gapKey;
    private boolean wasEating = false;

    @Override
    public void onInitializeClient() {

        gapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smart_gap.eat",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KeyBinding.Category.MISC
        ));

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
        ItemStack activeItem = client.player.getActiveItem();

        boolean isCurrentlyEating =
                client.player.isUsingItem() &&
                        (activeItem.isOf(Items.GOLDEN_APPLE) ||
                                activeItem.isOf(Items.ENCHANTED_GOLDEN_APPLE));

        if (!isCurrentlyEating && wasEating) {
            switchToBestWeapon(client);
        }

        wasEating = isCurrentlyEating;
    }

    private void switchToBestWeapon(MinecraftClient client) {

        int swordSlot = -1;
        int axeSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);

            // Schwerter direkt über Items checken
            if (isSword(stack)) {
                swordSlot = i;
                break;
            }

            // Äxte fallback
            if (isAxe(stack) && axeSlot == -1) {
                axeSlot = i;
            }
        }

        if (swordSlot != -1) {
            client.player.getInventory().setSelectedSlot(swordSlot);
        } else if (axeSlot != -1) {
            client.player.getInventory().setSelectedSlot(axeSlot);
        }
    }

    private boolean isSword(ItemStack stack) {
        return stack.isOf(Items.WOODEN_SWORD) ||
                stack.isOf(Items.STONE_SWORD) ||
                stack.isOf(Items.IRON_SWORD) ||
                stack.isOf(Items.GOLDEN_SWORD) ||
                stack.isOf(Items.DIAMOND_SWORD) ||
                stack.isOf(Items.NETHERITE_SWORD);
    }

    private boolean isAxe(ItemStack stack) {
        return stack.isOf(Items.WOODEN_AXE) ||
                stack.isOf(Items.STONE_AXE) ||
                stack.isOf(Items.IRON_AXE) ||
                stack.isOf(Items.GOLDEN_AXE) ||
                stack.isOf(Items.DIAMOND_AXE) ||
                stack.isOf(Items.NETHERITE_AXE);
    }
}