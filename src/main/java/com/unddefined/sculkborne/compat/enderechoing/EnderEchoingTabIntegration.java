package com.unddefined.sculkborne.compat.enderechoing;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

public final class EnderEchoingTabIntegration {
    private static final String MOD_ID = "enderechoing";
    private static final ResourceLocation[] ITEMS = {
            id("ender_echoing_core"),
            id("warp_core"),
            id("ender_echo_tune_chamber"),
            id("ender_echo_crystal"),
            id("ender_echo_compass"),
            id("ender_echoing_pearl"),
            id("ender_echoing_eye")
    };

    public static void addItems(CreativeModeTab.Output output) {
        if (!ModList.get().isLoaded(MOD_ID)) return;

        output.accept(Items.ENDER_PEARL);
        for (ResourceLocation itemId : ITEMS) {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item != Items.AIR) output.accept(item);
        }
        output.accept(Items.RECOVERY_COMPASS);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private EnderEchoingTabIntegration() {}
}
