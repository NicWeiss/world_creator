package com.nicweiss.editor.utils;

import com.nicweiss.editor.utils.ItemModifierCatalog.ModifierDef;
import com.nicweiss.editor.utils.ItemModifierCatalog.RarityDef;
import com.nicweiss.editor.utils.ItemModifierCatalog.School;
import com.nicweiss.editor.utils.ItemModifierCatalog.TypeDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * Генератор предметов поверх {@link ItemModifierCatalog}. Работает напрямую с шаблоном предмета
 * (LinkedHashMap с полями __type__/__itemClass__/__stats__/... — той же структурой, что хранится
 * в itemTemplates и в сохранённых .wcf), без зависимостей от UI.
 *
 * Используется и редактором (ItemsEditorWindow), и далее — рантайм-дропом/симуляцией лута,
 * чтобы правила ролла жили в одном месте.
 */
public class ItemGenerator {
    private static final Random RANDOM = new Random();

    private ItemGenerator() {}

    // ---- Тип / класс / размер ----

    /**
     * Назначает предмету тип "с нуля": размер по умолчанию для типа, класс (роллится случайно
     * либо выводится из размера для чармов), новый уровень/редкость и сгенерированные модификаторы.
     * Это "первый ролл" нового предмета — см. rollLevelAndRarity.
     */
    public static void applyType(LinkedHashMap template, String typeKey) {
        template.put("__type__", typeKey);

        TypeDef type = ItemModifierCatalog.TYPES.get(typeKey);
        int[] defSize = type.sizes.get(0);
        template.put("__width__", defSize[0]);
        template.put("__height__", defSize[1]);

        String classKey = null;
        if (type.classDerivedFromSize) {
            classKey = type.classForSize(defSize[0], defSize[1]);
        } else if (!type.subtypes.isEmpty()) {
            classKey = type.subtypes.get(RANDOM.nextInt(type.subtypes.size())).key;
        }
        if (classKey != null) {
            template.put("__itemClass__", classKey);
        } else {
            template.remove("__itemClass__");
        }

        rollLevelAndRarity(template);
        rollModifiers(template);
    }

    public static void rollLevelAndRarity(LinkedHashMap template) {
        template.put("__itemLevel__", 1 + RANDOM.nextInt(99));
        int roll = RANDOM.nextInt(100);
        String rarityKey = roll < 60 ? "common" : roll < 90 ? "rare" : "unique";
        template.put("__rarity__", rarityKey);
    }

    /** Меняет размер предмета; для классов, определяемых размером (чармы), пересчитывает класс. */
    public static void setSize(LinkedHashMap template, int w, int h) {
        template.put("__width__", w);
        template.put("__height__", h);

        TypeDef type = currentType(template);
        if (type != null && type.classDerivedFromSize) {
            String classKey = type.classForSize(w, h);
            if (classKey != null) template.put("__itemClass__", classKey);
        }
    }

    /** Ручная смена класса — не трогает уже накатанные модификаторы (для этого есть rollModifiers). */
    public static void setClass(LinkedHashMap template, String classKey) {
        template.put("__itemClass__", classKey);
    }

    public static void setRarity(LinkedHashMap template, String rarityKey) {
        template.put("__rarity__", rarityKey);
    }

    public static TypeDef currentType(LinkedHashMap template) {
        if (!template.containsKey("__type__")) return null;
        return ItemModifierCatalog.TYPES.get(template.get("__type__"));
    }

    public static RarityDef currentRarity(LinkedHashMap template) {
        String rarityKey = template.containsKey("__rarity__") ? (String) template.get("__rarity__") : "common";
        RarityDef rarity = ItemModifierCatalog.RARITIES.get(rarityKey);
        return rarity != null ? rarity : ItemModifierCatalog.RARITIES.get("common");
    }

    public static int currentItemLevel(LinkedHashMap template) {
        return template.containsKey("__itemLevel__") ? (int) template.get("__itemLevel__") : 1;
    }

    // "Качество" роллов 0..0.9: чем выше уровень предмета и реже редкость — тем ближе значения к максимуму диапазона.
    public static double currentQuality(LinkedHashMap template) {
        RarityDef rarity = currentRarity(template);
        double levelFraction = Math.min(1.0, currentItemLevel(template) / 99.0);
        return Math.min(0.9, rarity.qualityBonus + levelFraction * 0.5);
    }

    // ---- Модификаторы ----

    /**
     * Перегенерирует набор модификаторов с нуля под текущие тип/класс/уровень/редкость предмета
     * и пересчитывает требования. Уровень и редкость при этом не трогаются — их рерольнуть может
     * только applyType (смена типа) либо ручная правка через setRarity/__itemLevel__.
     */
    public static void rollModifiers(LinkedHashMap template) {
        TypeDef type = currentType(template);
        LinkedHashMap stats = new LinkedHashMap();

        if (type != null) {
            String typeKey = (String) template.get("__type__");
            String classKey = (String) template.get("__itemClass__");
            RarityDef rarity = currentRarity(template);
            String rarityKey = rarity.key;
            int itemLevel = currentItemLevel(template);
            double quality = currentQuality(template);

            List<ModifierDef> eligible = type.eligibleModifiers(classKey, rarityKey, itemLevel);
            List<ModifierDef> skillerMods = new ArrayList<>();
            List<ModifierDef> normalMods = new ArrayList<>();
            for (ModifierDef def : eligible) {
                if (def.isSkiller) skillerMods.add(def); else normalMods.add(def);
            }

            // "Если выпал скилл — вторым модификатором может быть только здоровье, другие не могут быть."
            // Скиллер роллится отдельным эксклюзивным путём: либо скиллер (+опционально health-компаньон),
            // либо обычный набор модификаторов без скиллера вовсе.
            boolean skillerRolled = false;
            if (!skillerMods.isEmpty()) {
                double chance = Math.min(0.9, skillerMods.size() / (double) eligible.size());
                skillerRolled = RANDOM.nextDouble() < chance;
            }

            if (skillerRolled) {
                ModifierDef skiller = skillerMods.get(RANDOM.nextInt(skillerMods.size()));
                stats.put(skiller.key, rolledStat(skiller, quality, rarityKey));

                ModifierDef healthCompanion = null;
                for (ModifierDef def : normalMods) {
                    if (def.isHealthCompanion) { healthCompanion = def; break; }
                }
                if (healthCompanion != null && RANDOM.nextBoolean()) {
                    stats.put(healthCompanion.key, rolledStat(healthCompanion, quality, rarityKey));
                }
            } else {
                int min = rarity.minMods, max = rarity.maxMods;
                if (type.maxModifiers != null) {
                    min = Math.min(min, type.maxModifiers);
                    max = Math.min(max, type.maxModifiers);
                }
                // Минимум 1 модификатор на предмете — даже у Common не должно выпасть пусто.
                min = Math.max(1, min);
                max = Math.max(min, max);
                int span = Math.max(0, max - min);
                int count = min + (span > 0 ? RANDOM.nextInt(span + 1) : 0);

                Collections.shuffle(normalMods, RANDOM);
                for (ModifierDef def : normalMods) {
                    if (stats.size() >= count) break;
                    if (!ItemModifierCatalog.isCompatible(def, typeKey, stats)) continue;
                    stats.put(def.key, rolledStat(def, quality, rarityKey));
                }
            }
        }

        template.put("__stats__", stats);
        recomputeRequirements(template);
    }

    public static LinkedHashMap rolledStat(ModifierDef def, double quality, String rarityKey) {
        int effectiveMax = def.effectiveMax(rarityKey);
        int value = def.min;
        if (effectiveMax > def.min) {
            double roll = quality + RANDOM.nextDouble() * (1 - quality);
            value = def.min + (int) Math.round((effectiveMax - def.min) * roll);
            value = clamp(value, def.min, effectiveMax);
        }
        LinkedHashMap stat = new LinkedHashMap();
        stat.put("__modId__", def.key);
        stat.put("__value__", value);
        return stat;
    }

    /** Добавляет конкретный модификатор (ручной выбор) и пересчитывает требования. */
    public static void addModifier(LinkedHashMap template, String modKey) {
        String typeKey = (String) template.get("__type__");
        ModifierDef def = ItemModifierCatalog.findModifier(typeKey, modKey);
        if (def == null) return;

        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        stats.put(def.key, rolledStat(def, currentQuality(template), currentRarity(template).key));
        recomputeRequirements(template);
    }

    // ---- Требования (сила/магия/уровень) ----

    /**
     * Пересчитывает требуемые силу/магию/уровень из текущих модификаторов и основной характеристики.
     * Физические модификаторы поднимают требуемую силу, магические — требуемую магию.
     * Уровень предмета и редкость дают базовую добавку к минимально требуемому уровню игрока.
     */
    public static void recomputeRequirements(LinkedHashMap template) {
        TypeDef type = currentType(template);
        if (type == null) return;

        String typeKey = (String) template.get("__type__");
        String classKey = (String) template.get("__itemClass__");
        RarityDef rarity = currentRarity(template);
        int itemLevel = currentItemLevel(template);

        int strengthReq = 0, magicReq = 0;
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        for (Object k : stats.keySet()) {
            String statKey = k.toString();
            LinkedHashMap stat = (LinkedHashMap) stats.get(statKey);
            ModifierDef def = ItemModifierCatalog.findModifier(typeKey, statKey);
            if (def == null) continue;
            int value = (int) stat.get("__value__");
            int effectiveMax = def.effectiveMax(rarity.key);
            int contribution = effectiveMax > 0 ? Math.round((value / (float) effectiveMax) * 20f) : 0;
            if (def.school == School.PHYSICAL) strengthReq += contribution;
            else if (def.school == School.MAGIC) magicReq += contribution;
        }

        int mainStat = template.containsKey("__mainStat__") ? (int) template.get("__mainStat__") : 0;
        if (mainStat > 0) {
            int contribution = Math.round(mainStat * 0.25f);
            if (mainStatSchool(typeKey, classKey) == School.MAGIC) magicReq += contribution;
            else strengthReq += contribution;
        }

        if (strengthReq > 0) strengthReq += rarity.reqBonus;
        if (magicReq > 0) magicReq += rarity.reqBonus;

        int levelReq = Math.round(itemLevel * 0.85f) + rarity.reqBonus / 2;

        template.put("__reqStrength__", clamp(strengthReq, 0, 300));
        template.put("__reqMagic__", clamp(magicReq, 0, 300));
        template.put("__reqLevel__", clamp(levelReq, 1, 99));
    }

    // Какое требование поднимает "основная характеристика" — зависит от типа/класса предмета.
    private static School mainStatSchool(String typeKey, String classKey) {
        if (typeKey == null) return School.PHYSICAL;
        switch (typeKey) {
            case "weapon": return "caster".equals(classKey) ? School.MAGIC : School.PHYSICAL;
            case "shield": return "grimoire".equals(classKey) ? School.MAGIC : School.PHYSICAL;
            case "helmet": return "tiara".equals(classKey) ? School.MAGIC : School.PHYSICAL;
            case "armor": return "robe".equals(classKey) ? School.MAGIC : School.PHYSICAL;
            case "amulet": case "artifact": return School.MAGIC;
            default: return School.PHYSICAL;
        }
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
