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
        applyTypeInternal(template, typeKey, maxLevel, -1, 0f);
    }

    /**
     * Вариант для рантайм-дропа (см. DropManager): редкость роллится по кривой вероятности
     * от уровня игрока и Magic Find — см. {@link #rollLevelAndRarity(LinkedHashMap, int, int, float)}.
     * Для редактора (playerLevel неизвестен) используется плоский ролл через двухпараметровый applyType.
     */
    public static void applyType(LinkedHashMap template, String typeKey, int maxLevel, int playerLevel, float magicFind) {
        applyTypeInternal(template, typeKey, maxLevel, playerLevel, magicFind);
    }

    private static void applyTypeInternal(LinkedHashMap template, String typeKey, int maxLevel, int playerLevel, float magicFind) {
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

        // playerLevel < 0 — сигнал "нет игрока" (вызов из редактора) → плоский ролл редкости.
        if (playerLevel >= 0) {
            rollLevelAndRarity(template, maxLevel, playerLevel, magicFind);
        } else {
            rollLevelAndRarity(template, maxLevel);
        }
        rollModifiers(template);
    }

    public static void rollLevelAndRarity(LinkedHashMap template) {
        rollLevelAndRarity(template, 99);
    }

    /** Плоский ролл редкости (60/30/10) — используется редактором, где нет контекста игрока. */
    public static void rollLevelAndRarity(LinkedHashMap template, int maxLevel) {
        maxLevel = clamp(maxLevel, 1, 99);
        template.put("__itemLevel__", 1 + RANDOM.nextInt(maxLevel));
        int roll = RANDOM.nextInt(100);
        String rarityKey = roll < 60 ? "common" : roll < 90 ? "rare" : "unique";
        template.put("__rarity__", rarityKey);
    }

    /**
     * Ролл редкости дропа по гладкой кривой вероятности от уровня игрока (1..99) и Magic Find (%, 0+).
     * Целевые ориентиры (примерная частота выпадения на один сгенерированный предмет):
     *  - уровень 10,  MF 0%   → rare ~ раз в 10-50,  unique ~ раз в 200-400;
     *  - уровень 10,  MF 300%+ → rare ~ раз в 10-30, unique ~ раз в 20-50;
     *  - уровень 99,  MF 300%+ → rare и unique ~ раз в 7-15 (примерно равны).
     * Кривая непрерывная (без ступенек): растёт с каждым уровнем и с каждым процентом MF,
     * при этом MF даёт убывающую отдачу (насыщение), а не жёсткий потолок.
     */
    public static void rollLevelAndRarity(LinkedHashMap template, int maxLevel, int playerLevel, float magicFind) {
        maxLevel = clamp(maxLevel, 1, 99);
        template.put("__itemLevel__", 1 + RANDOM.nextInt(maxLevel));
        template.put("__rarity__", rollRarityKey(playerLevel, magicFind));
    }

    // Вклад уровня игрока и MF% в шанс rare/unique. UNIQUE_SYNERGY — дополнительная прибавка,
    // когда высоки ОБА фактора сразу (без неё на низком уровне высокий MF задирал бы unique
    // почти так же сильно, как на высоком уровне — а по ТЗ разрыв должен быть заметным).
    private static final double RARE_MIN = 0.02, RARE_LEVEL_GAIN = 0.07, RARE_MF_GAIN = 0.03;
    private static final double UNIQUE_MIN = 0.0025, UNIQUE_LEVEL_GAIN = 0.005, UNIQUE_MF_GAIN = 0.04, UNIQUE_SYNERGY = 0.145;
    private static final double MF_SATURATION = 300.0; // MF%, дающий половину предельной прибавки от MF

    private static String rollRarityKey(int playerLevel, float magicFind) {
        double levelFraction = clamp01((playerLevel - 1) / 98.0);
        double mfFraction = magicFind > 0 ? magicFind / (magicFind + MF_SATURATION) : 0.0;

        double rareChance = RARE_MIN + RARE_LEVEL_GAIN * levelFraction + RARE_MF_GAIN * mfFraction;
        double uniqueChance = UNIQUE_MIN + UNIQUE_LEVEL_GAIN * levelFraction + UNIQUE_MF_GAIN * mfFraction
                + UNIQUE_SYNERGY * levelFraction * mfFraction;

        // Защита от патологических входных данных за пределами реальных диапазонов (уровень 1-99, MF 0-500%).
        double total = rareChance + uniqueChance;
        if (total > 0.6) {
            double scale = 0.6 / total;
            rareChance *= scale;
            uniqueChance *= scale;
        }

        double roll = RANDOM.nextDouble();
        if (roll < uniqueChance) return "unique";
        if (roll < uniqueChance + rareChance) return "rare";
        return "common";
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Возвращает текущие вероятности {common, rare, unique} по той же кривой, что и rollRarityKey —
     * без ролла, для отображения в UI (см. SystemUI — вкладка "Дроп").
     */
    public static double[] rarityChances(int playerLevel, float magicFind) {
        double levelFraction = clamp01((playerLevel - 1) / 98.0);
        double mfFraction = magicFind > 0 ? magicFind / (magicFind + MF_SATURATION) : 0.0;

        double rareChance = RARE_MIN + RARE_LEVEL_GAIN * levelFraction + RARE_MF_GAIN * mfFraction;
        double uniqueChance = UNIQUE_MIN + UNIQUE_LEVEL_GAIN * levelFraction + UNIQUE_MF_GAIN * mfFraction
                + UNIQUE_SYNERGY * levelFraction * mfFraction;

        double total = rareChance + uniqueChance;
        if (total > 0.6) {
            double scale = 0.6 / total;
            rareChance *= scale;
            uniqueChance *= scale;
        }

        return new double[]{1.0 - rareChance - uniqueChance, rareChance, uniqueChance};
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

    // ---- Расходники (зелья/свитки) ----

    /**
     * Назначает предмету тип расходника (см. TypeDef.isConsumable) "с нуля": 1x1, без класса,
     * редкости и модификаторов, тир по умолчанию x1 (низший). Используется редактором — тир
     * дальше выбирается вручную через setConsumableTier.
     */
    public static void applyConsumableType(LinkedHashMap template, String typeKey) {
        applyConsumableTypeInternal(template, typeKey, 1);
    }

    /**
     * Вариант для рантайм-дропа (см. DropManager): тир роллится по гладкой кривой от уровня
     * игрока — см. {@link #rollConsumableTier}.
     */
    public static void applyConsumableType(LinkedHashMap template, String typeKey, int playerLevel) {
        TypeDef type = ItemModifierCatalog.TYPES.get(typeKey);
        int tier = type != null ? rollConsumableTier(type.maxTier, playerLevel) : 1;
        applyConsumableTypeInternal(template, typeKey, tier);
    }

    private static void applyConsumableTypeInternal(LinkedHashMap template, String typeKey, int tier) {
        template.put("__type__", typeKey);
        template.put("__width__", 1);
        template.put("__height__", 1);
        template.remove("__itemClass__");
        template.remove("__rarity__");
        template.remove("__stats__"); // расходники без модификаторов — __stats__ им не нужен

        TypeDef type = ItemModifierCatalog.TYPES.get(typeKey);
        applyDefaultImage(template, type, null);

        // Расходники доступны с любого уровня — требований нет.
        template.put("__reqLevel__", 1);
        template.put("__reqStrength__", 0);
        template.put("__reqMagic__", 0);

        setConsumableTier(template, tier);
    }

    /**
     * Ручная/повторная установка тира расходника (x1..maxTier). Пересчитывает __mainStat__ из
     * TypeDef.tierValues (очки эффекта на этот тир), если для типа они заданы. Для нетировых
     * расходников (maxTier=1, например свиток) __tier__/__mainStat__ не хранятся вовсе.
     */
    public static void setConsumableTier(LinkedHashMap template, int tier) {
        TypeDef type = currentType(template);
        if (type == null || !type.isConsumable) return;

        if (type.maxTier <= 1) {
            template.remove("__tier__");
            template.remove("__mainStat__");
            return;
        }

        tier = clamp(tier, 1, type.maxTier);
        template.put("__tier__", tier);
        if (type.tierValues != null && tier - 1 < type.tierValues.length) {
            template.put("__mainStat__", type.tierValues[tier - 1]);
        } else {
            template.remove("__mainStat__");
        }
    }

    /**
     * Гладкий ролл тира расходника (1..maxTier) от уровня игрока (1..100): непрерывная позиция
     * тира растёт линейно с уровнем, а фактический тир — "дизеринг" вокруг неё (шанс округлить
     * вверх = дробная часть позиции). Так следующий тир начинает попадаться всё чаще по мере
     * роста уровня, пока не станет основным, без жёстких уровневых порогов.
     */
    public static int rollConsumableTier(int maxTier, int playerLevel) {
        if (maxTier <= 1) return 1;
        int level = clamp(playerLevel, 1, 100);
        double continuousTier = 1.0 + ((level - 1) / 99.0) * (maxTier - 1); // 1..maxTier
        int lowerTier = clamp((int) Math.floor(continuousTier), 1, maxTier);
        int upperTier = Math.min(maxTier, lowerTier + 1);
        double frac = continuousTier - lowerTier;
        return RANDOM.nextDouble() < frac ? upperTier : lowerTier;
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
     * Роллит "основную характеристику" от уровня предмета и качества (редкость+уровень): чем выше
     * оба — тем выше показатель. Смысл характеристики зависит от типа предмета:
     *  - пояс: ёмкость (кратна 4, 4..16) — под стеки (см. SystemUI/стеки);
     *  - обувь: скорость передвижения (1..40);
     *  - перчатки: рейтинг атаки (10..500);
     *  - всё остальное: урон/защита (1..500, старая формула без изменений).
     */
    public static void rollMainStat(LinkedHashMap template) {
        if (currentType(template) == null) return;
        String typeKey = (String) template.get("__type__");
        double quality = currentQuality(template);

        int value;
        switch (typeKey) {
            case "belt": {
                // Ёмкость — всегда кратна 4: тир 1..4 роллится тем же способом, что и обычные
                // модификаторы (вокруг quality), затем масштабируется на 4 → {4, 8, 12, 16}.
                int tier = rollRanged(quality, 1, 4);
                value = tier * 4;
                break;
            }
            case "boots":
                value = rollRanged(quality, 1, 40);
                break;
            case "gloves":
                value = rollRanged(quality, 10, 500);
                break;
            case "torch": {
                // Сила света — общий диапазон 5..11, но фактический ролл сужен редкостью (выше
                // редкость — выше "пол" и "потолок"), требуемый уровень считается от неё отдельно
                // (см. recomputeRequirements).
                String torchRarityKey = currentRarity(template).key;
                int torchMin, torchMax;
                switch (torchRarityKey) {
                    case "unique": torchMin = 9; torchMax = 11; break;
                    case "rare":   torchMin = 7; torchMax = 9;  break;
                    default:       torchMin = 5; torchMax = 7;  break;
                }
                value = rollRanged(quality, torchMin, torchMax);
                break;
            }
            default: {
                int itemLevel = currentItemLevel(template);
                int base = 4 + Math.round(itemLevel * 1.5f);
                value = Math.round(base * (0.6f + (float) quality * 0.8f));
                value = clamp(value, 1, 500);
                break;
            }
        }
        template.put("__mainStat__", value);
    }

    // Роллит целое в [min, max], кучкуясь вокруг quality — та же логика, что и rolledStat,
    // но без ModifierDef (для __mainStat__ типов с собственным диапазоном).
    private static int rollRanged(double quality, int min, int max) {
        if (max <= min) return min;
        double roll = quality + (RANDOM.nextDouble() - 0.5) * ROLL_SPREAD;
        roll = Math.max(0.0, Math.min(1.0, roll));
        int value = min + (int) Math.round((max - min) * roll);
        return clamp(value, min, max);
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

        boolean isTorch = "torch".equals(typeKey);
        int levelReq;
        if (isTorch) {
            // Только уровень игрока, растёт от силы света (5→1) до (20→50), плюс добавка редкости —
            // см. описание задачи: "чем выше сила света факела - тем выше требуемый уровень",
            // "требование к уровню игрока растёт исходя из силы света и редкости". Диапазон силы
            // света — общий 5..11, фактический ролл сужен по редкости (см. rollMainStat "torch").
            // Итог делится пополам (/2) — по требованию пользователя ("требования очень высокие,
            // послабить раза в 2") исходная кривая была слишком жёсткой для предмета этого класса.
            int lightPower = clamp(mainStat, 5, 11);
            double base = 1.0 + (lightPower - 5) / 6.0 * 49.0;
            levelReq = (int) Math.round((base + rarity.reqBonus) / 2.0);
            applyTorchRarityStats(template, rarity);
        } else {
            levelReq = Math.round(itemLevel * 0.85f) + rarity.reqBonus / 2;
        }

        // Чармы и факелы не требуют силы и магии — только уровень
        boolean isCharm = "charm".equals(typeKey);
        template.put("__reqStrength__", (isCharm || isTorch) ? 0 : clamp(strengthReq, 0, 300));
        template.put("__reqMagic__",    (isCharm || isTorch) ? 0 : clamp(magicReq,    0, 300));
        template.put("__reqLevel__", clamp(levelReq, 1, 99));
    }

    // Common: чадящий тусклый оранжевый; Rare: светлый тёплый; Unique: холодный яркий с синим
    // отливом — цвет свечения СКРЫТЫЙ параметр (не отображается в тултипе, см. SystemUI), меняется
    // только редкостью, не роллится как обычный стат. Время горения — тоже жёстко по редкости.
    public static void applyTorchRarityStats(LinkedHashMap template, RarityDef rarity) {
        float r, g, b;
        int burnSeconds;
        switch (rarity.key) {
            case "unique":
                r = 0.55f; g = 0.75f; b = 1.00f;
                burnSeconds = 30 * 60;
                break;
            case "rare":
                r = 1.00f; g = 0.85f; b = 0.55f;
                burnSeconds = 15 * 60;
                break;
            default:
                r = 1.00f; g = 0.55f; b = 0.15f;
                burnSeconds = 3 * 60;
                break;
        }
        template.put("__glowColorR__", r);
        template.put("__glowColorG__", g);
        template.put("__glowColorB__", b);
        template.put("__torchBurnTime__", burnSeconds);
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
