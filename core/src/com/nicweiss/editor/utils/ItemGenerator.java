package com.nicweiss.editor.utils;

import com.badlogic.gdx.Gdx;
import com.nicweiss.editor.utils.ItemModifierCatalog.ModifierDef;
import com.nicweiss.editor.utils.ItemModifierCatalog.RarityDef;
import com.nicweiss.editor.utils.ItemModifierCatalog.School;
import com.nicweiss.editor.utils.ItemModifierCatalog.TypeDef;

import java.io.File;
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
        applyType(template, typeKey, 99);
    }

    /**
     * То же самое, но уровень предмета ограничен сверху maxLevel (1..maxLevel) — используется
     * рантайм-дропом, где уровень предмета не может превышать уровень убитого врага/сундука.
     */
    public static void applyType(LinkedHashMap template, String typeKey, int maxLevel) {
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

        applyDefaultImage(template, type, classKey);

        rollLevelAndRarity(template, maxLevel);
        rollModifiers(template);
    }

    public static void rollLevelAndRarity(LinkedHashMap template) {
        rollLevelAndRarity(template, 99);
    }

    public static void rollLevelAndRarity(LinkedHashMap template, int maxLevel) {
        maxLevel = clamp(maxLevel, 1, 99);
        template.put("__itemLevel__", 1 + RANDOM.nextInt(maxLevel));
        int roll = RANDOM.nextInt(100);
        String rarityKey = roll < 60 ? "common" : roll < 90 ? "rare" : "unique";
        template.put("__rarity__", rarityKey);
    }

    /**
     * Меняет размер предмета; для классов, определяемых размером (чармы), пересчитывает класс.
     * Если класс при этом реально меняется (другой размер чарма) — пул модификаторов меняется
     * полностью, поэтому старые статы могли стать невалидными и роллятся заново.
     */
    public static void setSize(LinkedHashMap template, int w, int h) {
        template.put("__width__", w);
        template.put("__height__", h);

        TypeDef type = currentType(template);
        if (type != null && type.classDerivedFromSize) {
            String classKey = type.classForSize(w, h);
            String prevClass = (String) template.get("__itemClass__");
            if (classKey != null && !classKey.equals(prevClass)) {
                template.put("__itemClass__", classKey);
                applyDefaultImage(template, type, classKey);
                rollModifiers(template);
            }
        }
    }

    /**
     * Ручная смена класса. Пул допустимых модификаторов класс-специфичен (см. ModifierDef.classKey),
     * поэтому уже накатанные статы могли стать невалидными для нового класса — рероллим их заново.
     * Картинка по умолчанию тоже переключается на соответствующую новому классу (если для него есть папка).
     */
    public static void setClass(LinkedHashMap template, String classKey) {
        template.put("__itemClass__", classKey);
        applyDefaultImage(template, currentType(template), classKey);
        rollModifiers(template);
    }

    /**
     * Подставляет картинку по умолчанию (assets/items/&lt;папка&gt;/default.png) для типа/класса,
     * если для них задана папка в каталоге и файл реально существует. Не трогает картинку, если
     * подходящей папки/файла нет — оставляет то, что было (или ничего).
     */
    public static void applyDefaultImage(LinkedHashMap template, TypeDef type, String classKey) {
        String folder = ItemModifierCatalog.resolveImageFolder(type, classKey);
        if (folder == null) return;

        File file = Gdx.files.internal("assets/items/" + folder + "/default.png").file();
        if (file.exists()) {
            template.put("__image__", file.getAbsolutePath());
        }
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

    // "Качество" — центр (0..0.9) распределения ролла значения внутри диапазона модификатора.
    // Чем выше уровень предмета и реже редкость — тем выше центр и тем ближе значения к максимуму.
    // На 1 уровне у Common-редкости quality ~0 — ролл должен кучковаться у минимума, а не быть равномерным.
    public static double currentQuality(LinkedHashMap template) {
        RarityDef rarity = currentRarity(template);
        double levelFraction = Math.min(1.0, currentItemLevel(template) / 99.0);
        return Math.min(0.9, rarity.qualityBonus + levelFraction * 0.5);
    }

    // Насколько широко значение может отклониться от quality в обе стороны (после clamp в [0,1]).
    private static final double ROLL_SPREAD = 0.3;

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
        rollMainStat(template);
        recomputeRequirements(template);
    }

    /**
     * Роллит "основную характеристику" (урон/защита — смысл зависит от типа предмета) от уровня
     * предмета и качества (редкость+уровень): чем выше оба — тем выше показатель.
     */
    public static void rollMainStat(LinkedHashMap template) {
        if (currentType(template) == null) return;
        int itemLevel = currentItemLevel(template);
        double quality = currentQuality(template);
        int base = 4 + Math.round(itemLevel * 1.5f);
        int value = Math.round(base * (0.6f + (float) quality * 0.8f));
        template.put("__mainStat__", clamp(value, 1, 500));
    }

    public static LinkedHashMap rolledStat(ModifierDef def, double quality, String rarityKey) {
        int effectiveMax = def.effectiveMax(rarityKey);
        int value = def.min;
        if (effectiveMax > def.min) {
            // roll кучкуется ВОКРУГ quality (а не "не ниже quality") — на низком уровне/редкости
            // значения должны тянуться к минимуму диапазона, а не быть равномерно случайными.
            double roll = quality + (RANDOM.nextDouble() - 0.5) * ROLL_SPREAD;
            roll = Math.max(0.0, Math.min(1.0, roll));
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

        // Чармы не требуют силы и магии — только уровень
        boolean isCharm = "charm".equals(typeKey);
        template.put("__reqStrength__", isCharm ? 0 : clamp(strengthReq, 0, 300));
        template.put("__reqMagic__",    isCharm ? 0 : clamp(magicReq,    0, 300));
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
