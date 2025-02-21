package com.cursery.enchant;

import com.cursery.Cursery;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import  java.util.function.Supplier;
import java.util.*;

/**
 * Apply random curses upon enchanting
 */
public class CurseEnchantmentHelper
{
    public static  boolean delayNext = false;
    public static  Item    delayItem;
    private static Random  rand      = new Random();

    public static Map<Enchantment, Integer> curseWeightMap   = new HashMap<>();
    public static int                       totalCurseWeight = 0;

    public static ItemStack                 notifyStack;
    public static ServerPlayer              notifyPlayer;
    public static Map<Enchantment, Integer> prevEnchants;

    /**
     * Checks the stack for applying a random curse
     *
     * @param stack       stack to check
     * @param previous    previous enchantments on the stack
     * @param newEnchants new enchantments on the stack
     * @return true if applied at least one curse
     */
    public static boolean checkForRandomCurse(final ItemStack stack, final Map<Enchantment, Integer> previous, final Map<Enchantment, Integer> newEnchants)
    {
        // Case for anvil repairs, we delay applying the curse till item is taken out
        if (delayNext)
        {
            delayNext = false;
            if (stack.getItem() == delayItem)
            {
                return false;
            }

            delayItem = null;
        }

        if (stack == null || stack.isEmpty() || previous == null || newEnchants == null)
        {
            return false;
        }

        if (stack.getItem() == Items.BOOK || stack.getItem() == Items.ENCHANTED_BOOK)
        {
            return false;
        }

        if (!previous.isEmpty() && Cursery.config.getCommonConfig().onlyUnEnchanted)
        {
            return false;
        }

        int levelSum = 0;
        // Sum all levels
        for (final Map.Entry<Enchantment, Integer> newEnchant : newEnchants.entrySet())
        {
            if (!newEnchant.getKey().isCurse() && !(Cursery.config.getCommonConfig().excludeTreasure && newEnchant.getKey().isTreasureOnly()))
            {
                levelSum += newEnchant.getValue();
            }
        }

        final List<Integer> addedLevels = new ArrayList<>();

        boolean isCurseApplied = false;
        // Compare enchants
        for (final Map.Entry<Enchantment, Integer> newEnchant : newEnchants.entrySet())
        {
            int newLevel = previous.containsKey(newEnchant.getKey()) ? newEnchant.getValue() - previous.get(newEnchant.getKey()) : newEnchant.getValue();
            if (newLevel <= 0)
            {
                continue;
            }

            // Skip treasure enchants if configured
            if (Cursery.config.getCommonConfig().excludeTreasure && newEnchant.getKey().isTreasureOnly())
            {
                continue;
            }

            if (!newEnchant.getKey().isCurse())
            {
                addedLevels.add(newLevel);
            }
        }

        for (final Integer newLevel : addedLevels)
        {
            if (rollAndApplyCurseTo(stack, newLevel, levelSum - newLevel, newEnchants))
            {
                isCurseApplied = true;
            }
        }

        // Remember it to allow notifying players, particles purple color and a text: The wheel of fortune turns. Ein hauch von schicksal. The dark etc
        if (isCurseApplied)
        {
            if (stack == notifyStack && notifyPlayer != null)
            {
                PlayerVisualHelper.randomNotificationOnCurseApply(notifyPlayer, notifyStack);
                notifyPlayer = null;
                notifyStack = null;
            }
        }
        else
        {
            if (stack == notifyStack && notifyPlayer != null)
            {
                PlayerVisualHelper.enchantSuccess(notifyPlayer, notifyStack);
            }
        }

        return isCurseApplied;
    }

    /**
     * Rolls the curses and applies them, according to the total level and newly applied level of enchants.
     *
     * @param stack       item
     * @param newLevel    additional enchant levels added
     * @param levelSum    total sum of existing enchants
     * @param newEnchants new enchantments on the stack
     * @return true if curse is applied.
     */
    private static boolean rollAndApplyCurseTo(
      final ItemStack stack,
      final int newLevel,
      final int levelSum,
      final Map<Enchantment, Integer> newEnchants)
    {

        boolean isCurseApplied = false;
        int guaranteedCurseInterval = Cursery.config.getCommonConfig().curseEveryXLevels;

        // Checks how many curses in the interval have been passed.
        // Only makes a difference when curseEveryXLevels is enabled (i.e > 0)
        int existingGuaranteedCursesPassed = guaranteedCurseInterval == 0 ? 0 : (int) Math.ceil((double) (levelSum + 1) / guaranteedCurseInterval);
        int totalGuaranteedCursesPassed = guaranteedCurseInterval == 0 ? 0 : (int) Math.floor((double) (levelSum + newLevel) / guaranteedCurseInterval);
        int guaranteedCursesToApply = guaranteedCurseInterval == 0 ? 0 : totalGuaranteedCursesPassed - existingGuaranteedCursesPassed + 1;

        if (guaranteedCursesToApply == 0)
        {
            if (Cursery.config.getCommonConfig().debugTries)
                Cursery.LOGGER.info("CurseEveryXLevels override is FALSE.");
        }
        else
        {
            if (Cursery.config.getCommonConfig().debugTries)
                Cursery.LOGGER.info("CurseEveryXLevels override is TRUE.");

            for (int i = 0; i < guaranteedCursesToApply; i++)
            {
                if (Cursery.config.getCommonConfig().debugTries)
                {
                    Cursery.LOGGER.info("Rolling new curse for " + stack + " guaranteedCursesToApply: " + guaranteedCursesToApply
                            + " totalEnchantLevels: " + levelSum + " curseChance overridden by curseEveryXLevels");
                }
                isCurseApplied = applyCurseTo(stack, newEnchants);
            }
        }

        // Makes sure the item is not cursed again for each level the guaranteed curse was applied.
        int levelsLeftToApply = newLevel - guaranteedCursesToApply;

        Supplier<Integer> curseChance;
        int minCurseChance = Cursery.config.getCommonConfig().baseCurseChance - (stack.getEnchantmentValue() >> 1);
        int curseChanceRange = Cursery.config.getCommonConfig().maxCurseChance - Cursery.config.getCommonConfig().baseCurseChance;

        if (Cursery.config.getCommonConfig().curseChanceScales)
        {
            // Scaling rate varies with the marked number.
            // Ideally should be kept between -0.0125 <= X <= -0.0175
            // Anything greater or lower will lead to extremely fast or slow scaling rates respectively.
            curseChance = () -> (int) Math.ceil(
                    minCurseChance + curseChanceRange * (1 - Math.exp(/*Important*/-0.015/*Important*/ * levelSum))
            );
        }
        else
            curseChance = () -> minCurseChance;


        // Each level has the same chance, so its the same to apply enchant V vs I to V
        for (int i = 0; i < levelsLeftToApply; i++)
        {
            if (Cursery.config.getCommonConfig().debugTries)
                Cursery.LOGGER.info("Rolling new curse for " + stack + " addedEnchLevels: " + levelsLeftToApply
                        + " totalEnchantLevels: " + levelSum + " chance:" + curseChance.get());

            if (rand.nextInt(100) < curseChance.get())
                isCurseApplied = applyCurseTo(stack, newEnchants);
        }
        return isCurseApplied;
    }

    /**
     * Applies curse to item. Helper method to rollAndApplyCurseTo()
     *
     * @param stack       item
     * @param newEnchants new enchantments on the stack
     * @return true if curse is applied.
     */
    private static boolean applyCurseTo(
            final ItemStack stack,
            final Map<Enchantment, Integer> newEnchants)
    {
        if (Cursery.config.getCommonConfig().debugTries)
            Cursery.LOGGER.info("Trying to apply curse to: " + stack);

        for (int j = 0; j < 15; j++)
        {
            final Enchantment curse = getRandomCurse();
            if (curse == null)
            {
                continue;
            }

            final int currentLevel = newEnchants.getOrDefault(curse, 0);
            if (currentLevel < curse.getMaxLevel() && curse.canEnchant(stack) && isCompatibleWithAll(curse, newEnchants))
            {
                if (Cursery.config.getCommonConfig().debugTries)
                    Cursery.LOGGER.info("Applying curse " + ForgeRegistries.ENCHANTMENTS.getKey(curse) + " to: " + stack);

                enchantManually(stack, curse, currentLevel + 1);
                newEnchants.put(curse, currentLevel + 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an enchantment can be applied to the existing
     *
     * @param enchantment
     * @param newEnchants
     * @return true if possible
     */
    private static boolean isCompatibleWithAll(final Enchantment enchantment, final Map<Enchantment, Integer> newEnchants)
    {
        for (final Map.Entry<Enchantment, Integer> entry : newEnchants.entrySet())
        {
            // Makes sure to break (and hence return true) if an existing curse is rolled.
            if (enchantment == entry.getKey())
                break;

            if (!entry.getKey().isCompatibleWith(enchantment))
            {
                if (Cursery.config.getCommonConfig().debugTries)
                    Cursery.LOGGER.info("Curse " + ForgeRegistries.ENCHANTMENTS.getKey(enchantment) +
                            " is not compatible with " + ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey()));

                return false;
            }
        }

        return true;
    }

    /**
     * Get a weighted random curse
     *
     * @return
     */
    private static Enchantment getRandomCurse()
    {
        if (totalCurseWeight == 0)
        {
            return null;
        }

        final int chosen = Cursery.rand.nextInt(totalCurseWeight);
        Enchantment curse = null;
        int currentWeight = 0;
        for (final Map.Entry<Enchantment, Integer> entry : curseWeightMap.entrySet())
        {
            if (chosen < entry.getValue() + currentWeight)
            {
                curse = entry.getKey();
                break;
            }
            currentWeight += entry.getValue();
        }

        return curse;
    }

    /**
     * NBT helper to apply the actual enchant, avoids recursive application
     *
     * @param stack
     * @param enchantment
     * @param level
     */
    public static void enchantManually(final ItemStack stack, Enchantment enchantment, int level)
    {
        stack.getOrCreateTag();
        if (!stack.getTag().contains("Enchantments", 9))
        {
            stack.getTag().put("Enchantments", new ListTag());
        }

        ListTag listnbt = stack.getTag().getList("Enchantments", 10);

        // Makes sure to remove nbt tag of the existing enchantment with its previous level
        if (level > 1)
        {
            if (Cursery.config.getCommonConfig().debugTries) {
                Cursery.LOGGER.info("Removing enchantment: " + enchantment.getDescriptionId()
                        + ",\n with level: " + (level - 1) + " from item to replace it with level: " + level);
            }
            CompoundTag compoundnbt = new CompoundTag();
            compoundnbt.putString("id", String.valueOf((Object) ForgeRegistries.ENCHANTMENTS.getKey(enchantment)));
            compoundnbt.putShort("lvl", (short) ((byte) level - 1));
            listnbt.remove(compoundnbt);
        }

        if (Cursery.config.getCommonConfig().debugTries) {
            Cursery.LOGGER.info("Adding back enchantment: " + enchantment.getDescriptionId()
                    + ",\n with level: " + level);
        }
        CompoundTag compoundnbt = new CompoundTag();
        compoundnbt.putString("id", String.valueOf((Object) ForgeRegistries.ENCHANTMENTS.getKey(enchantment)));
        compoundnbt.putShort("lvl", (short) ((byte) level));

        listnbt.add(compoundnbt);
    }

    /**
     * Weight calc, just vanilla now
     *
     * @param enchantment
     * @return weight
     */
    public static int calculateWeightFor(final Enchantment enchantment)
    {
        return enchantment.getRarity().getWeight();
    }
}
