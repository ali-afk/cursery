package com.cursery.mixin;

import com.cursery.Cursery;
import com.cursery.enchant.CurseEnchantmentHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.infinitelimit.kintsugi.menus.RemixEnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(RemixEnchantmentMenu.class)
public abstract class KintsugiRemixEnchantmentMenuMixin
{
    private Map<Enchantment, Integer> previousEnchants;

    @Inject(method = "calculateResultItem", at = @At("HEAD"), remap = false)
    private void delayApplyingCurse(final ItemStack itemStack, final ItemStack fuelStack, final CallbackInfo ci) {
        CurseEnchantmentHelper.delayNext = true;
        CurseEnchantmentHelper.delayItem = itemStack.getItem();
        previousEnchants = itemStack.getAllEnchantments();
    }

    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void curseItemOnTake(final Player pPlayer, final ItemStack pStack, final CallbackInfo ci) {
        if (Cursery.config.getCommonConfig().debugTries) {
            Cursery.LOGGER.info("calculateResultItem: delayed the curse.");
            Cursery.LOGGER.info("Previous Enchantments:" + formatEnchantments(previousEnchants));
            Cursery.LOGGER.info("onTake: Retrieving new enchantments.");
        }

        Map<Enchantment, Integer> existingEnchants = pStack.getAllEnchantments();

        if (Cursery.config.getCommonConfig().debugTries) {
            Cursery.LOGGER.info("New Enchantments:" + formatEnchantments(existingEnchants));
            Cursery.LOGGER.info("Checking for random curse for stack: " + pStack);
        }

        boolean isCurseApplied = CurseEnchantmentHelper.checkForRandomCurse(pStack, previousEnchants, existingEnchants);

        if (Cursery.config.getCommonConfig().debugTries) {
            Cursery.LOGGER.info(isCurseApplied ? "Curse applied!" : "Curse was not applied.");
        }
    }


    /**
     * Helper method to format enchantments map into a readable string.
     */
    private String formatEnchantments(Map<Enchantment, Integer> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return " None";
        }
        StringBuilder sb = new StringBuilder();
        enchantments.forEach((enchant, level) -> sb.append("\n - ").append(enchant.getDescriptionId()).append(": Level ").append(level));
        return sb.toString();
    }

}
