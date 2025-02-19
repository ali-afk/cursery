package com.cursery.config;

import com.cupboard.config.ICommonConfig;
import com.cursery.Cursery;
import com.cursery.enchant.CurseEnchantmentHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

import static com.cursery.enchant.CurseEnchantmentHelper.curseWeightMap;
import static com.cursery.enchant.CurseEnchantmentHelper.totalCurseWeight;

public class CommonConfiguration implements ICommonConfig
{
    public boolean      debugTries      = false;
    public boolean      showDesc        = true;

    public List<String> excludedCurses  = new ArrayList<>();
    public boolean      excludeTreasure = false;

    public boolean      onlyUnEnchanted = false;
    public boolean      visualSuccess   = true;

    public boolean      curseChanceScales = true;
    public int          baseCurseChance = 5;
    public int          maxCurseChance = 75;

    public int          curseEveryXLevels = 0;


    public CommonConfiguration()
    {
        excludedCurses.add("minecraft:vanishing_curse");
    }

    public JsonObject serialize()
    {
        final JsonObject root = new JsonObject();

        final JsonObject entry0 = new JsonObject();
        entry0.addProperty("desc:", "Whether to log debug messages about curse chances being rolled. Default: false");
        entry0.addProperty("debugTries", debugTries);
        root.add("debugTries", entry0);

        final JsonObject entry1 = new JsonObject();
        entry1.addProperty("desc:", "Should enchanted books show a hint for curse magic. Default: true");
        entry1.addProperty("showDesc", showDesc);
        root.add("showDesc", entry1);

        final JsonObject entry2 = new JsonObject();
        entry2.addProperty("desc:", "Add a curse id here to exclude it from being applied. "
                                      + "To put multiple values separate them by commas like this:  [\"minecraft:curse\", \"mod:curse;\"] ");
        final JsonArray list1 = new JsonArray();
        for (final String name : excludedCurses)
        {
            list1.add(name);
        }
        entry2.add("excludedCurses", list1);
        root.add("excludedCurses", entry2);

        final JsonObject entry3 = new JsonObject();
        entry3.addProperty("desc:", "Should applying treasure enchants be excluded. Default: false");
        entry3.addProperty("excludeTreasure", excludeTreasure);
        root.add("excludeTreasure", entry3);

        final JsonObject entry4 = new JsonObject();
        entry4.addProperty("desc:", "Should curses only be applied on enchanting unenchanted items, recommended to increase base chance when enabling. Default: false");
        entry4.addProperty("onlyUnEnchanted", onlyUnEnchanted);
        root.add("onlyUnEnchanted", entry4);

        final JsonObject entry5 = new JsonObject();
        entry5.addProperty("desc:", "Should enchanting success play a sound and show particles. Default: true");
        entry5.addProperty("visualSuccess", visualSuccess);
        root.add("visualSuccess", entry5);

        final JsonObject entry6 = new JsonObject();
        entry6.addProperty("desc:", "Whether curse chance should scale the more enchantment levels an item has, "
                + "If FALSE, curseChance = baseCurseChance. Default: true");
        entry6.addProperty("curseChanceScales", curseChanceScales);
        root.add("curseChanceScales", entry6);

        final JsonObject entry7 = new JsonObject();
        entry7.addProperty("desc:", "Base curse application chance. Default: 5 %");
        entry7.addProperty("baseCurseChance", baseCurseChance);
        root.add("baseCurseChance", entry7);

        final JsonObject entry8 = new JsonObject();
        entry8.addProperty("desc:", "Maximum curse application chance, ignored if curseChanceScales is FALSE. Default: 75 %");
        entry8.addProperty("maxCurseChance", maxCurseChance);
        root.add("maxCurseChance", entry8);

        final JsonObject entry9 = new JsonObject();
        entry9.addProperty("desc:", "Applies a curse every X enchantment levels, "
                + "no other curses are applied by curseChance each time this occurs. Disabled if X = 0. Default: 0 ");
        entry9.addProperty("curseEveryXLevels", curseEveryXLevels);
        root.add("curseEveryXLevels", entry9);


        return root;
    }

    public void deserialize(JsonObject data)
    {
        debugTries = data.get("debugTries").getAsJsonObject().get("debugTries").getAsBoolean();
        showDesc = data.get("showDesc").getAsJsonObject().get("showDesc").getAsBoolean();
        excludedCurses = new ArrayList<>();
        excludeTreasure = data.get("excludeTreasure").getAsJsonObject().get("excludeTreasure").getAsBoolean();
        onlyUnEnchanted = data.get("onlyUnEnchanted").getAsJsonObject().get("onlyUnEnchanted").getAsBoolean();
        visualSuccess = data.get("visualSuccess").getAsJsonObject().get("visualSuccess").getAsBoolean();
        curseChanceScales = data.get("curseChanceScales").getAsJsonObject().get("curseChanceScales").getAsBoolean();
        baseCurseChance = data.get("baseCurseChance").getAsJsonObject().get("baseCurseChance").getAsInt();
        maxCurseChance = data.get("maxCurseChance").getAsJsonObject().get("maxCurseChance").getAsInt();
        curseEveryXLevels = data.get("curseEveryXLevels").getAsJsonObject().get("curseEveryXLevels").getAsInt();
        for (final JsonElement element : data.get("excludedCurses").getAsJsonObject().get("excludedCurses").getAsJsonArray())
        {
            excludedCurses.add(element.getAsString());
        }
        parseConfig();
    }

    public void parseConfig()
    {
        curseWeightMap = new HashMap<>();
        totalCurseWeight = 0;
        final Set<Enchantment> excluded = new HashSet<>();
        for (final String entry : Cursery.config.getCommonConfig().excludedCurses)
        {
            final ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null)
            {
                Cursery.LOGGER.error("Config entry could not be parsed, not a valid resource location " + entry);
                continue;
            }

            final Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (enchantment == null)
            {
                Cursery.LOGGER.error("Config entry could not be parsed, not a valid enchant" + entry);
                continue;
            }

            excluded.add(enchantment);
        }

        // Parse registry entries
        for (final Map.Entry<ResourceKey<Enchantment>, Enchantment> enchantmentEntry : ForgeRegistries.ENCHANTMENTS.getEntries())
        {
            if (enchantmentEntry.getValue().isCurse())
            {
                if (!excluded.contains(enchantmentEntry.getValue()))
                {
                    final int weight = CurseEnchantmentHelper.calculateWeightFor(enchantmentEntry.getValue());
                    totalCurseWeight += weight;
                    CurseEnchantmentHelper.curseWeightMap.put(enchantmentEntry.getValue(), weight);
                }
                else
                    Cursery.LOGGER.info("Excluding curse: " + ForgeRegistries.ENCHANTMENTS.getKey(enchantmentEntry.getValue()) + " as config disables it");
            }
        }

        if (totalCurseWeight == 0)
            Cursery.LOGGER.error("Unable to retrieve curses from registry");

        if (baseCurseChance < 0 || baseCurseChance > 100)
        {
            Cursery.LOGGER.warn(String.format("BaseCurseChance was set to '%d' yet must be within the interval 0 <= X <= 100. Setting back to 5.", baseCurseChance));
            baseCurseChance = 5;
        }

        if (maxCurseChance < 0 || maxCurseChance > 100)
        {
            Cursery.LOGGER.warn(String.format("MaxCurseChance was set to '%d' yet must be within the interval 0 <= X <= 100. Setting back to 75.", maxCurseChance));
            maxCurseChance = 75;
        }

        if (curseEveryXLevels < 0)
        {
            Cursery.LOGGER.warn(String.format("CurseEveryXLevels was set to '%d' yet must be within interval X >= 0. Setting back to 0.", curseEveryXLevels));
            curseEveryXLevels = 0;
        }
    }
}
