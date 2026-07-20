package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.ItemGenerator;
import com.nicweiss.editor.utils.ItemModifierCatalog;
import com.nicweiss.editor.utils.Transform;
import com.nicweiss.editor.utils.Uuid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * Спавнит лут (предметы и золото) при открытии сундуков и убийстве врагов.
 *
 * Использует {@link ItemGenerator} для ролла предмета: тип/класс, уровень (1..уровень врага),
 * основная характеристика, модификаторы и требования — всё по тем же правилам, что в редакторе.
 * Учитывает характеристики игрока (поиск вещей/золота — Magic Find/Gold Find, см. Player) и
 * уровень врага (определяет верхнюю границу уровня предмета).
 *
 * Заспавненные объекты попадают в store.drops — отдельный список объектов карты, с которыми
 * можно взаимодействовать (показывают подпись при приближении игрока, см. Drop.draw).
 */
public class DropManager {
    public static Store store;

    private static final Random RANDOM = new Random();
    // "Падать можно только на поверхности или на совсем низкие предметы" — порог высоты тайла.
    static final int MAX_SURFACE_HEIGHT = 3;
    // Вода (см. Editor.WATER_TEXTURE_ID) и мостик (gp_12.png, см. Player.BRIDGE_TEXTURE_ID) — лут
    // не должен падать в воду, куда игрок не может дойти (кроме как по мостику).
    private static final int WATER_TEXTURE_ID  = 10;
    private static final int BRIDGE_TEXTURE_ID = 12;
    // Дальность разлёта в тайлах: база 1.5, +60% по требованию.
    static final float MIN_SCATTER_TILES = 0.6f;
    static final float MAX_SCATTER_TILES = 1.5f * 1.6f;

    private DropManager() {}

    static final int MAX_ITEMS_PER_DROP = 4;
    static final float GOLD_DROP_CHANCE = 0.9f;
    static final int GOLD_BASE_MIN = 3, GOLD_BASE_SPAN = 5;
    private static final float ITEM_BASE_CHANCE = 0.35f, ITEM_MF_GAIN = 200f, ITEM_CHANCE_CAP = 0.9f, ITEM_CHANCE_FALLOFF = 0.5f;

    /**
     * Главная точка входа: роллит и спавнит лут в точке (tileX, tileY) — индексы store.objectedMap,
     * там, где был открыт сундук или убит враг уровня enemyLevel.
     *
     * Золото и предметы считаются полностью независимо друг от друга:
     *  - золото роллится один раз с источника дропа, сразу всей суммой (своя вероятность и размер);
     *  - количество предметов роллится отдельно, затем КАЖДЫЙ предмет отдельно прогоняется
     *    через ItemGenerator (свои тип/класс/уровень/моды/требования).
     * Уровень врага и характеристики игрока (поиск вещей/золота) влияют и на качество, и на количество.
     */
    public static void dropLoot(int enemyLevel, int tileX, int tileY) {
        if (store.objectedMap == null) return;
        enemyLevel = Math.max(1, enemyLevel);

        rollGold(enemyLevel, tileX, tileY);

        int itemCount = rollItemCount(enemyLevel);
        for (int i = 0; i < itemCount; i++) {
            spawnItemDrop(rollItemTemplate(enemyLevel), tileX, tileY);
        }

        rollPotionsAndScroll(tileX, tileY);
        rollTorchDrop(tileX, tileY);
    }

    // ── Факел ────────────────────────────────────────────────────────────────
    // Независимый вероятностный ролл на каждое убийство — НЕ счётчик и НЕ гарантированный порог.
    // Ориентиры пользователя ("падает раз на 5-10 врагов" и т.д.) переведены в шанс на одно
    // убийство = 1 / среднее(диапазона): это даёт нужную частоту "в среднем", но каждый килл —
    // независимое событие (дроп может не выпасть очень долго или выпасть подряд — это осознанно,
    // именно так и должна работать вероятность, а не жёсткая привязка к количеству смертей).
    static final int[] COMMON_TORCH_RANGE = {5, 10}, RARE_TORCH_RANGE = {50, 100}, UNIQUE_TORCH_RANGE = {150, 300};

    // Базовый шанс (при 0 Magic Find) = 1 / среднее(диапазона). Раньше это была static final
    // константа — MF на факелы вообще не влиял, в отличие от ВСЕГО остального лута с редкостью
    // (обычное снаряжение — через rollRarityKey, зелье восстановления/свиток — через
    // mfScaledChance). Факел — тоже предмет с редкостью (common/rare/unique), поэтому по той же
    // логике должен точно так же реагировать на Magic Find — используем тот же идиома
    // "* (1 + MF/100)", капнутый на MF_SCALED_CHANCE_CAP (см. mfScaledChance).
    static final float COMMON_TORCH_BASE_CHANCE = torchChanceFromRange(COMMON_TORCH_RANGE);
    static final float RARE_TORCH_BASE_CHANCE   = torchChanceFromRange(RARE_TORCH_RANGE);
    static final float UNIQUE_TORCH_BASE_CHANCE = torchChanceFromRange(UNIQUE_TORCH_RANGE);

    private static float torchChanceFromRange(int[] range) {
        return 1f / ((range[0] + range[1]) / 2f);
    }

    private static void rollTorchDrop(int tileX, int tileY) {
        float magicFind = store.player != null ? store.player.magicFind : 0f;
        if (RANDOM.nextFloat() < mfScaledChance(COMMON_TORCH_BASE_CHANCE, magicFind)) spawnTorchOfRarity("common", tileX, tileY);
        if (RANDOM.nextFloat() < mfScaledChance(RARE_TORCH_BASE_CHANCE, magicFind))   spawnTorchOfRarity("rare", tileX, tileY);
        if (RANDOM.nextFloat() < mfScaledChance(UNIQUE_TORCH_BASE_CHANCE, magicFind)) spawnTorchOfRarity("unique", tileX, tileY);
    }

    /** Спавнит факел заданной редкости — используется и авто-дропом выше, и отладочным дропом (см. UserInterface). */
    public static void spawnTorchOfRarity(String rarityKey, int tileX, int tileY) {
        int playerLevel = store.player != null ? store.player.level : 1;
        spawnItemDrop(rollTorchTemplate(rarityKey, playerLevel), tileX, tileY);
    }

    private static LinkedHashMap rollTorchTemplate(String rarityKey, int playerLevel) {
        LinkedHashMap template = new LinkedHashMap();
        template.put("__uuid__", Uuid.generate());
        // __itemLevel__ ограничен уровнем игрока (не роллится 1..99 вслепую, как для обычного
        // снаряжения) — currentQuality() растёт от itemLevel/99, а от неё зависит центр ролла силы
        // света (rollRanged в ItemGenerator.rollMainStat "torch"). Чем выше уровень игрока, тем выше
        // потолок itemLevel — тем выше ШАНС на бОльшую силу света (не гарантия, разброс сохраняется).
        ItemGenerator.applyType(template, "torch", Math.max(1, playerLevel));
        ItemGenerator.setRarity(template, rarityKey);
        ItemGenerator.rollModifiers(template); // силу света/требования/скрытый цвет — под нужную редкость и уровень
        template.put("__name__", ItemModifierCatalog.TYPES.get("torch").label);
        return template;
    }

    // Один дроп (один убитый враг) — общая сумма опыта дробится на случайное число сфер,
    // а не всегда одно и то же: сумма их значений всегда равна результату Player.experienceForKill.
    private static final int EXP_ORB_MIN = 1;
    private static final int EXP_ORB_MAX = 5;

    /**
     * Спавнит 1-5 сфер опыта в точке (tileX, tileY), поровну делящих общую сумму одного дропа.
     * Отдельная от dropLoot точка входа — сумма считается из уровня врага и множителя
     * (см. Player.experienceForKill: множитель > 1 пригодится для мини-боссов/элит того же
     * уровня, что дают больше опыта). Разлёт/бросок каждой сферы — как у лута (см. spawnItemDrop),
     * но сферы не укладываются на землю (см. Drop.expAmount).
     */
    public static void dropExperience(int enemyLevel, float multiplier, int tileX, int tileY) {
        if (store.objectedMap == null) return;
        int total = Player.experienceForKill(Math.max(1, enemyLevel), multiplier);
        int orbCount = EXP_ORB_MIN + RANDOM.nextInt(EXP_ORB_MAX - EXP_ORB_MIN + 1);
        int base = total / orbCount;
        int remainder = total % orbCount;
        for (int i = 0; i < orbCount; i++) {
            spawnExpDrop(base + (i < remainder ? 1 : 0), tileX, tileY);
        }
    }

    /** Золото: один независимый ролл вероятности и размера на весь источник дропа. */
    private static void rollGold(int enemyLevel, int tileX, int tileY) {
        float goldFind = store.player != null ? store.player.goldFind : 0f;

        if (RANDOM.nextFloat() >= GOLD_DROP_CHANCE) return; // золото выпадает не всегда

        int base = enemyLevel * (GOLD_BASE_MIN + RANDOM.nextInt(GOLD_BASE_SPAN));
        int amount = Math.round(base * (1f + goldFind / 100f));
        if (amount > 0) spawnGoldDrop(amount, tileX, tileY);
    }

    /**
     * Сколько предметов выпадет (0..MAX_ITEMS_PER_DROP) — независимо от золота. Каждый
     * следующий предмет роллится со снижающимся шансом; поиск вещей повышает базовый шанс.
     */
    private static int rollItemCount(int enemyLevel) {
        float magicFind = store.player != null ? store.player.magicFind : 0f;
        float chance = itemChance(magicFind);

        int count = 0;
        for (int i = 0; i < MAX_ITEMS_PER_DROP; i++) {
            if (RANDOM.nextFloat() >= chance) break;
            count++;
            chance *= ITEM_CHANCE_FALLOFF; // каждый следующий предмет — вдвое менее вероятен
        }
        return count;
    }

    /** Шанс ролла N-го предмета (chances[0] — первый предмет, ...) — для отображения в UI (см. SystemUI). */
    static float[] itemCountChances(float magicFind) {
        float chance = itemChance(magicFind);
        float[] chances = new float[MAX_ITEMS_PER_DROP];
        for (int i = 0; i < MAX_ITEMS_PER_DROP; i++) {
            chances[i] = chance;
            chance *= ITEM_CHANCE_FALLOFF;
        }
        return chances;
    }

    private static float itemChance(float magicFind) {
        return Math.min(ITEM_CHANCE_CAP, ITEM_BASE_CHANCE + magicFind / ITEM_MF_GAIN);
    }

    /** Роллит шаблон предмета целиком через ItemGenerator: тип, класс, уровень, основной показатель, моды, требования. */
    private static LinkedHashMap rollItemTemplate(int enemyLevel) {
        LinkedHashMap template = new LinkedHashMap();
        template.put("__uuid__", Uuid.generate());

        // Расходники (зелья/свитки) — отдельная категория лута со своим роллом (см.
        // rollPotionsAndScroll), сюда, в обычный ролл снаряжения, не попадают.
        String typeKey = rollEquipmentTypeKey();

        int playerLevel = store.player != null ? store.player.level : enemyLevel;
        float magicFind = store.player != null ? store.player.magicFind : 0f;
        ItemGenerator.applyType(template, typeKey, enemyLevel, playerLevel, magicFind);
        template.put("__name__", ItemModifierCatalog.TYPES.get(typeKey).label);
        return template;
    }

    // Веса типов предмета при ролле — раньше все типы были строго равновероятны (см. историю).
    // По требованию пользователя чарм и артефакт понижены относительно остальных (вес 100 —
    // "базовая" полная вероятность обычного типа снаряжения): чарм = 20% от базовой, артефакт =
    // 40% от базовой — они реже выпадают как случайный ролл типа, чем weapon/shield/helmet/etc.
    private static final int DEFAULT_TYPE_WEIGHT = 100;
    private static final java.util.Map<String, Integer> TYPE_WEIGHT_OVERRIDES = new java.util.LinkedHashMap<>();
    static {
        TYPE_WEIGHT_OVERRIDES.put("charm", 20);
        TYPE_WEIGHT_OVERRIDES.put("artifact", 40);
    }

    private static int typeWeight(String key) {
        return TYPE_WEIGHT_OVERRIDES.getOrDefault(key, DEFAULT_TYPE_WEIGHT);
    }

    private static String rollEquipmentTypeKey() {
        String[] keys = equipmentTypeKeys();
        int totalWeight = 0;
        for (String key : keys) totalWeight += typeWeight(key);

        int roll = RANDOM.nextInt(totalWeight);
        int cumulative = 0;
        for (String key : keys) {
            cumulative += typeWeight(key);
            if (roll < cumulative) return key;
        }
        return keys[keys.length - 1]; // не должно достигаться
    }

    /** Текущие вероятности каждого типа (в том же порядке, что equipmentTypeKeys()) — для
     *  отображения в UI (см. SystemUI — вкладка "Дроп"), без ролла. */
    static float[] typeChances() {
        String[] keys = equipmentTypeKeys();
        int totalWeight = 0;
        for (String key : keys) totalWeight += typeWeight(key);

        float[] chances = new float[keys.length];
        for (int i = 0; i < keys.length; i++) chances[i] = (float) typeWeight(keys[i]) / totalWeight;
        return chances;
    }

    private static String[] equipmentTypeKeysCache;
    static String[] equipmentTypeKeys() {
        if (equipmentTypeKeysCache == null) {
            List<String> keys = new ArrayList<>();
            for (String key : ItemModifierCatalog.TYPES.keySet()) {
                // Факел не роллится как обычное снаряжение — у него свой счётчик убийств
                // (см. rollTorchDrop), а не общая вероятность "предмет выпал/не выпал".
                if (!ItemModifierCatalog.isConsumableType(key) && !"torch".equals(key)) keys.add(key);
            }
            equipmentTypeKeysCache = keys.toArray(new String[0]);
        }
        return equipmentTypeKeysCache;
    }

    // ── Зелья и свитки ───────────────────────────────────────────────────────────
    // Считаются полностью независимо от обычного лута/золота (см. dropLoot). Здоровье/мана —
    // относительно частые (~раз в 5 врагов, шанс НЕ растёт от MF — в ТЗ это оговорено только для
    // восстановления и свитка); ставка здоровья явно не задана в ТЗ — принята симметричной мане.
    // Восстановление и свиток — редкие (~раз в 50), шанс растёт от Magic Find (та же идиома
    // "* (1 + stat/100)", что и rollGold ниже). Не более MAX_POTIONS_PER_DROP зелий за раз
    // (любая комбинация типов); свиток роллится отдельно и в этот лимит не входит.
    static final float HEALTH_POTION_CHANCE = 0.2f;
    static final float MANA_POTION_CHANCE = 0.2f;
    static final float RECOVERY_POTION_BASE_CHANCE = 0.02f;
    static final float SCROLL_BASE_CHANCE = 0.02f;
    static final float MF_SCALED_CHANCE_CAP = 0.5f;
    static final int MAX_POTIONS_PER_DROP = 2;

    private static void rollPotionsAndScroll(int tileX, int tileY) {
        int playerLevel = store.player != null ? store.player.level : 1;
        float magicFind = store.player != null ? store.player.magicFind : 0f;

        List<String> potionHits = new ArrayList<>();
        if (RANDOM.nextFloat() < HEALTH_POTION_CHANCE) potionHits.add("potion_health");
        if (RANDOM.nextFloat() < MANA_POTION_CHANCE) potionHits.add("potion_mana");
        float recoveryChance = mfScaledChance(RECOVERY_POTION_BASE_CHANCE, magicFind);
        if (RANDOM.nextFloat() < recoveryChance) potionHits.add("potion_recovery");

        if (potionHits.size() > MAX_POTIONS_PER_DROP) {
            Collections.shuffle(potionHits, RANDOM);
            potionHits = potionHits.subList(0, MAX_POTIONS_PER_DROP);
        }
        for (String typeKey : potionHits) {
            spawnItemDrop(rollConsumableTemplate(typeKey, playerLevel), tileX, tileY);
        }

        float scrollChance = mfScaledChance(SCROLL_BASE_CHANCE, magicFind);
        if (RANDOM.nextFloat() < scrollChance) {
            spawnItemDrop(rollConsumableTemplate("scroll_teleport", playerLevel), tileX, tileY);
        }
    }

    /** Тот же идиома "* (1 + MF/100)", капнутая на MF_SCALED_CHANCE_CAP — для recovery-потиона и свитка (см. rollPotionsAndScroll и SystemUI). */
    static float mfScaledChance(float baseChance, float magicFind) {
        return Math.min(MF_SCALED_CHANCE_CAP, baseChance * (1f + magicFind / 100f));
    }

    /** Роллит шаблон расходника: тир — по гладкой кривой от уровня игрока (см. ItemGenerator). */
    private static LinkedHashMap rollConsumableTemplate(String typeKey, int playerLevel) {
        LinkedHashMap template = new LinkedHashMap();
        template.put("__uuid__", Uuid.generate());
        ItemGenerator.applyConsumableType(template, typeKey, playerLevel);
        template.put("__name__", ItemModifierCatalog.TYPES.get(typeKey).label);
        return template;
    }

    // Создание Texture требует активного GL-контекста, который привязан к рендер-потоку.
    // dropLoot может быть вызван из любого потока (фоновый AI/таймер) — поэтому весь спавн,
    // включая загрузку текстуры, откладывается на GL-поток через postRunnable.
    public static void spawnItemDrop(LinkedHashMap itemTemplate, int tileX, int tileY) {
        Gdx.app.postRunnable(() -> {
            Drop drop = createDrop(tileX, tileY);
            if (drop == null) return;

            drop.itemData = itemTemplate;
            drop.setTexture(loadItemTexture(itemTemplate));

            String typeKey = (String) itemTemplate.get("__type__");
            if (ItemModifierCatalog.isConsumableType(typeKey)) {
                applyConsumableLabel(drop, itemTemplate, typeKey);
            } else {
                String rarityKey = itemTemplate.containsKey("__rarity__") ? (String) itemTemplate.get("__rarity__") : "common";
                String name = (String) itemTemplate.get("__name__");
                // Чёрный полупрозрачный фон у всех предметов, цвет текста — по редкости.
                drop.setLabel(name != null ? name : "Предмет", ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, rarityTextColor(rarityKey));
                // Rare — пульсирующая обводка, Unique — аддитивное свечение-гало (эффектнее,
                // подчёркивает разницу редкостей, см. Drop.LabelGlow/drawLabelAt).
                if ("unique".equals(rarityKey))      drop.setLabelGlow(Drop.LabelGlow.HALO);
                else if ("rare".equals(rarityKey))   drop.setLabelGlow(Drop.LabelGlow.OUTLINE);
                else                                  drop.setLabelGlow(Drop.LabelGlow.NONE);
            }
        });
    }

    // Лейблы зелий/свитков: символ-маркер (иконка, т.к. используемый шрифт не содержит пиктограмм
    // ♥/⚡/★, см. Font) + "xN" по объёму; текст/обводка (см. Drop.drawLabelAt) — в цвет иконки.
    // Свиток — просто текст без объёма (всегда один вариант, см. ItemModifierCatalog).
    private static final float[] HEALTH_LABEL_COLOR   = {1f, 0.15f, 0.15f};   // ярко-красный
    private static final float[] MANA_LABEL_COLOR     = {0.15f, 0.5f, 1f};    // ярко-синий
    private static final float[] RECOVERY_LABEL_COLOR = {0.65f, 0.25f, 0.95f}; // ярко-фиолетовый
    private static final float[] SCROLL_LABEL_COLOR   = {0.15f, 0.9f, 0.15f};  // зелёный

    // Золото — тот же оттенок, что и заливка монетки (см. buildGoldTexture): тёплый металлический
    // жёлто-янтарный, заметно темнее/приглушённее лимонного (rare) и оранжевее unique не выглядит —
    // не путается ни с редкостями предметов, ни с зелёным свитка.
    private static final float[] GOLD_LABEL_COLOR = {0.95f, 0.78f, 0.15f};

    private static void applyConsumableLabel(Drop drop, LinkedHashMap itemTemplate, String typeKey) {
        int tier = itemTemplate.containsKey("__tier__") ? (int) itemTemplate.get("__tier__") : 1;
        switch (typeKey) {
            case "potion_health":
                drop.setLabel("x" + tier, ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, HEALTH_LABEL_COLOR, heartIconTexture());
                break;
            case "potion_mana":
                drop.setLabel("x" + tier, ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, MANA_LABEL_COLOR, sparkIconTexture());
                break;
            case "potion_recovery":
                drop.setLabel("x" + tier, ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, RECOVERY_LABEL_COLOR, starIconTexture());
                break;
            case "scroll_teleport":
                drop.setLabel("Свиток", ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, SCROLL_LABEL_COLOR);
                break;
            default:
                String name = (String) itemTemplate.get("__name__");
                drop.setLabel(name != null ? name : "Предмет", ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, new float[]{1f, 1f, 1f});
        }
    }

    public static void spawnGoldDrop(int amount, int tileX, int tileY) {
        if (amount <= 0) return;
        Gdx.app.postRunnable(() -> {
            Drop drop = createDrop(tileX, tileY);
            if (drop == null) return;

            drop.goldAmount = amount;
            drop.setTexture(goldTexture());
            // Чёрный фон 60% alpha, золотистый текст (см. GOLD_LABEL_COLOR) — как у предметов, но свой цвет.
            drop.setLabel("$ " + amount, ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, GOLD_LABEL_COLOR);
        });
    }

    public static void spawnExpDrop(int amount, int tileX, int tileY) {
        if (amount <= 0) return;
        Gdx.app.postRunnable(() -> {
            Drop drop = createDrop(tileX, tileY);
            if (drop == null) return;

            drop.expAmount = amount;
            drop.setTexture(expTexture());
            // Без подписи — сфера сама по себе достаточно узнаваема, подпись только мешала бы на кучке из нескольких сфер.
        });
    }

    // ── Подписи: видимость, фокус, раскладка без перекрытия ("кирпичики") ──────

    private static final float ITEM_LABEL_BG_ALPHA = 0.6f;
    private static final float[] ITEM_LABEL_BG = {0f, 0f, 0f};
    private static final float PROXIMITY_RADIUS_FACTOR = 2.5f; // в тайлах — видимость подписи рядом с игроком
    private static final float HOVER_RADIUS_FACTOR = 0.6f;     // в тайлах — радиус наводки мышью
    private static final float LABEL_GAP = 6f;                 // отступ подписи от иконки
    private static final float LABEL_ROW_GAP = 3f;             // отступ между "кирпичиками" подписей

    /**
     * Единый проход по всем дропам: решает, чьи подписи видимы (рядом с игроком, под курсором,
     * сфокусированный, либо все — если зажат Alt/LB), укладывает их без перекрытия и рисует.
     * Вызывать один раз за кадр, после основного цикла отрисовки тайлов/существ.
     */
    // Порог подбора: расстояние в изометрических пикселях, в пределах которого золото подбирается.
    private static final float PICKUP_RADIUS_FACTOR = 1.0f;

    /**
     * Проверяет, наступил/дотронулся ли игрок до кучки золота или сферы опыта (isLanded,
     * goldAmount > 0 либо expAmount > 0) — оба подбираются автоматически касанием, без инвентаря.
     * При касании: gold → player.gold / exp → player.addExperience(...), дроп убирается из
     * store.drops, сетка надписей пересчитается сама — renderLabels перечитывает store.drops
     * каждый кадр с нуля. Вызывается на GL-потоке из renderLabels, поэтому потокобезопасно.
     */
    private static void checkPickups() {
        if (store.player == null || !store.player.isInitialized()) return;

        float[] iso = Transform.cartesianToIsometric(store.player.worldX, store.player.worldY);
        float playerIsoX = iso[0];
        float playerIsoY = iso[1];
        float pickupRadius = store.tileSizeWidth * PICKUP_RADIUS_FACTOR;

        for (int i = 0; i < store.drops.length; i++) {
            Drop d = store.drops[i];
            if (d == null || !d.isLanded || (d.goldAmount <= 0 && d.expAmount <= 0)) continue;

            float dx = playerIsoX - d.getWorldIsoX();
            float dy = playerIsoY - d.getWorldIsoY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= pickupRadius) {
                if (d.goldAmount > 0) store.player.gold += d.goldAmount;
                if (d.expAmount > 0) store.player.addExperience(d.expAmount);
                store.drops[i] = null;
            }
        }
    }

    public static void renderLabels(SpriteBatch batch) {
        checkPickups();

        List<Drop> visible = new ArrayList<>();
        for (Drop d : store.drops) {
            if (d != null && d.isLanded && d.hasLabel() && isOnScreen(d)) visible.add(d);
        }
        if (visible.isEmpty()) return;

        boolean playerReady = store.player != null && store.player.isInitialized();
        float playerScreenX = 0, playerScreenY = 0;
        if (playerReady) {
            float[] iso = Transform.cartesianToIsometric(store.player.worldX, store.player.worldY);
            playerScreenX = iso[0] + store.shiftX;
            playerScreenY = iso[1] + store.shiftY;
        }

        Drop iconHovered = findHovered(visible);

        // shown — всё, что РИСУЕМ (revealAll расширяет этот список чисто визуально).
        // interactable — из чего считаем hover/focus для подбора (клик/кнопка A) — специально
        // НЕ зависит от revealAll, иначе Alt/L1 (показать все подписи) начинает подмешивать
        // далёкие предметы в расчёт фокуса и ломает подбор того, что реально под курсором/рядом
        // с игроком (см. запрос пользователя: "подсветка всех лейблов не должна мешать кнопкам").
        boolean revealAll = store.revealAllDropLabels;
        List<Drop> shown = new ArrayList<>();
        java.util.Set<Drop> interactable = new java.util.HashSet<>();
        for (Drop d : visible) {
            boolean near = playerReady && isWithinProximity(d, playerScreenX, playerScreenY);
            boolean isIconHovered = d == iconHovered;
            if (revealAll || near || isIconHovered) shown.add(d);
            if (near || isIconHovered) interactable.add(d);
        }
        if (shown.isEmpty()) return;

        // Идеальная позиция каждой надписи — по центру своего предмета.
        int n = shown.size();
        float[] lx = new float[n], ly = new float[n], lw = new float[n], lh = new float[n];
        for (int i = 0; i < n; i++) {
            Drop d = shown.get(i);
            lw[i] = d.getLabelWidth();
            lh[i] = d.getLabelHeight();
            lx[i] = d.getIconScreenCenterX() - lw[i] / 2f;
            ly[i] = d.getIconScreenCenterY() - lh[i] / 2f;
        }

        // Симметричное попарное расталкивание: каждая пара перекрывающихся надписей
        // разводится в стороны — ОБОИХ двигаем, по оси с наименьшим перекрытием.
        // Надписи расходятся облаком вокруг своих предметов, а не тянутся в одну сторону.
        for (int iter = 0; iter < 40; iter++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (lx[i] >= lx[j] + lw[j] || lx[i] + lw[i] <= lx[j] ||
                        ly[i] >= ly[j] + lh[j] || ly[i] + lh[i] <= ly[j]) continue;

                    float ciX = lx[i] + lw[i] * 0.5f, cjX = lx[j] + lw[j] * 0.5f;
                    float ciY = ly[i] + lh[i] * 0.5f, cjY = ly[j] + lh[j] * 0.5f;
                    float dx = ciX - cjX;
                    float dy = ciY - cjY;

                    // Перекрытие по каждой оси (с запасом LABEL_ROW_GAP)
                    float ovX = (lw[i] + lw[j]) * 0.5f + LABEL_ROW_GAP - Math.abs(dx);
                    float ovY = (lh[i] + lh[j]) * 0.5f + LABEL_ROW_GAP - Math.abs(dy);

                    if (ovX <= ovY) {
                        // Разводим по X: меньший сдвиг
                        float push = ovX * 0.5f;
                        if (dx >= 0) { lx[i] += push; lx[j] -= push; }
                        else          { lx[i] -= push; lx[j] += push; }
                    } else {
                        // Разводим по Y
                        float push = ovY * 0.5f;
                        if (dy >= 0) { ly[i] += push; ly[j] -= push; }
                        else          { ly[i] -= push; ly[j] += push; }
                    }
                    moved = true;
                }
            }
            if (!moved) break;
        }

        // Фокус: сначала — наведение мышью на прямоугольник надписи,
        // потом — наведение на иконку, потом — ближайший к игроку.
        // Используем playerPositionX/Y — viewport-space координаты курсора (из unproject),
        // они совпадают с пространством лейблов (isoX + shiftX), в отличие от mouseX/Y (UI-пиксели).
        float curX = store.playerPositionX;
        float curY = store.playerPositionY;
        Drop hoverCandidate = null;
        for (int i = 0; i < n; i++) {
            if (!interactable.contains(shown.get(i))) continue; // см. комментарий выше про revealAll
            if (curX >= lx[i] && curX <= lx[i] + lw[i] &&
                curY >= ly[i] && curY <= ly[i] + lh[i]) {
                hoverCandidate = shown.get(i);
                break;
            }
        }
        if (hoverCandidate == null) hoverCandidate = iconHovered != null && interactable.contains(iconHovered) ? iconHovered : null;

        Drop focus = hoverCandidate;
        if (focus == null && playerReady) {
            float best = Float.MAX_VALUE;
            for (Drop d : interactable) {
                float dx = playerScreenX - d.getIconScreenCenterX();
                float dy = playerScreenY - d.getIconScreenCenterY();
                float dist = dx * dx + dy * dy;
                if (dist < best) { best = dist; focus = d; }
            }
        }

        // hoveredDrop — строго наведение курсором (без фолбэка на "ближайший к игроку"),
        // именно его подбираем по клику/кнопке A. focusedDrop — для подсветки (с фолбэком).
        hoveredDrop = hoverCandidate;
        focusedDrop = focus;

        for (int i = 0; i < n; i++) {
            shown.get(i).drawLabelAt(batch, lx[i], ly[i], shown.get(i) == focus);
        }
    }

    // ── Подбор предмета в инвентарь ─────────────────────────────────────────────

    // Дроп, чья подпись сейчас подсвечена (с фолбэком на ближайшего к игроку) — для рендера.
    public static Drop focusedDrop = null;
    // Дроп строго под курсором (лейбл или иконка), без фолбэка — именно его подбираем по клику/A.
    public static Drop hoveredDrop = null;

    private static final int INV_COLS = 12;
    private static final int INV_ROWS = 4;

    /** Кнопка A геймпада: подбирает предмет в фокусе (с фолбэком на ближайший к игроку — нет курсора). */
    public static void tryPickupFocused() {
        tryPickup(focusedDrop);
    }

    /** ЛКМ: подбирает предмет, только если курсор реально наведён на его лейбл/иконку. */
    public static void tryPickupHovered() {
        tryPickup(hoveredDrop);
    }

    /**
     * Золото подбирается само наступанием (см. checkPickups), сюда попадают только предметы.
     * Зелья/свитки сначала пробуют попасть в стек (см. StackManager.tryAddToStack — свободная
     * ячейка, потом неполная того же семейства); если стеки не приняли (или предмет не расходник) —
     * обычный инвентарь: ищет свободный прямоугольник под размер предмета, сканируя от краёв к
     * центру (сверху-слева построчно). Если и там места нет — дроп просто подпрыгивает на месте
     * (см. Drop.bounce).
     */
    private static void tryPickup(Drop d) {
        if (d == null || !d.isLanded || d.itemData == null) return;

        if (StackManager.tryAddToStack(d.itemData)) {
            for (int i = 0; i < store.drops.length; i++) {
                if (store.drops[i] == d) { store.drops[i] = null; break; }
            }
            focusedDrop = null;
            return;
        }

        int w = d.itemData.containsKey("__width__") ? (int) d.itemData.get("__width__") : 1;
        int h = d.itemData.containsKey("__height__") ? (int) d.itemData.get("__height__") : 1;

        int[] slot = findInventorySlot(w, h);
        if (slot == null) {
            d.bounce();
            return;
        }

        for (int c = slot[0]; c < slot[0] + w; c++) {
            for (int r = slot[1]; r < slot[1] + h; r++) {
                store.inventoryGrid[c][r] = true;
            }
        }
        d.itemData.put("__inv_x__", slot[0]);
        d.itemData.put("__inv_y__", slot[1]);
        String uuid = (String) d.itemData.get("__uuid__");
        store.inventory.put(uuid != null ? uuid : com.nicweiss.editor.utils.Uuid.generate(), d.itemData);

        for (int i = 0; i < store.drops.length; i++) {
            if (store.drops[i] == d) { store.drops[i] = null; break; }
        }
        focusedDrop = null;
    }

    /** Ищет первый свободный прямоугольник w×h, сканируя построчно сверху-слева — от краёв к центру. */
    private static int[] findInventorySlot(int w, int h) {
        for (int r = 0; r <= INV_ROWS - h; r++) {
            for (int c = 0; c <= INV_COLS - w; c++) {
                if (slotFree(c, r, w, h)) return new int[]{c, r};
            }
        }
        return null;
    }

    private static boolean slotFree(int col, int row, int w, int h) {
        for (int c = col; c < col + w; c++) {
            for (int r = row; r < row + h; r++) {
                if (store.inventoryGrid[c][r]) return false;
            }
        }
        return true;
    }

    private static Drop findHovered(List<Drop> candidates) {
        // playerPositionX/Y — viewport-space координаты курсора (unproject от Main.viewport),
        // совпадают с пространством иконок (isoX + shiftX). mouseX/Y — UI-пиксели, не подходят.
        float curX = store.playerPositionX;
        float curY = store.playerPositionY;
        Drop best = null;
        float bestDist = Float.MAX_VALUE;
        for (Drop d : candidates) {
            float dx = curX - d.getIconScreenCenterX();
            float dy = curY - d.getIconScreenCenterY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float hoverRadius = Math.max(d.getIconFootprint(), store.tileSizeWidth) * HOVER_RADIUS_FACTOR;
            if (dist <= hoverRadius && dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    private static boolean isWithinProximity(Drop d, float playerScreenX, float playerScreenY) {
        float dx = playerScreenX - d.getIconScreenCenterX();
        float dy = playerScreenY - d.getIconScreenCenterY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist <= store.tileSizeWidth * PROXIMITY_RADIUS_FACTOR;
    }

    private static boolean isOnScreen(Drop d) {
        Float dispW = store.display.get("width");
        Float dispH = store.display.get("height");
        if (dispW == null || dispH == null) return true;

        float cx = d.getIconScreenCenterX();
        float cy = d.getIconScreenCenterY();
        float margin = store.tileSizeWidth * 3f;
        return cx > -margin && cx < dispW + margin && cy > -margin && cy < dispH + margin;
    }

    /** Выбрасывает предмет из инвентаря на землю рядом с игроком. */
    public static void spawnDropAtPlayer(LinkedHashMap itemData) {
        if (store.player == null || store.tileSizeWidth == 0) return;
        int tileX = (int)(store.player.worldX / store.tileSizeWidth);
        int tileY = (int)(store.player.worldY / store.tileSizeHeight);
        spawnItemDrop(itemData, tileX, tileY);
    }

    // Скорость притяжения сферы опыта к игроку (тайлов/сек) — см. magnetizeExpOrb.
    private static final float EXP_MAGNET_SPEED_TILES = 12f;

    /**
     * Продвигает анимации всех активных дропов на dt секунд. Вызывается из CreationThread.
     * Сферы опыта получают dt и после приземления — иначе не покачивались бы (см. Drop.updateThrow).
     */
    public static void update(float dt) {
        for (Drop d : store.drops) {
            if (d == null) continue;
            if (!d.isLanded || d.expAmount > 0) {
                d.updateThrow(dt);
            }
            if (d.isLanded && d.expAmount > 0) {
                magnetizeExpOrb(d, dt);
            }
        }
    }

    /**
     * Сфера опыта в радиусе видимости подписи лута (см. PROXIMITY_RADIUS_FACTOR) сама летит к
     * игроку — не нужно бегать по карте за каждой отдельно; добор довершает checkPickups
     * (у него радиус меньше — PICKUP_RADIUS_FACTOR).
     */
    private static void magnetizeExpOrb(Drop d, float dt) {
        if (store.player == null || !store.player.isInitialized()) return;

        float[] iso = Transform.cartesianToIsometric(store.player.worldX, store.player.worldY);
        float dx = iso[0] - d.getWorldIsoX();
        float dy = iso[1] - d.getWorldIsoY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1f || dist > store.tileSizeWidth * PROXIMITY_RADIUS_FACTOR) return;

        float move = Math.min(store.tileSizeWidth * EXP_MAGNET_SPEED_TILES * dt, dist);
        d.moveBy(dx / dist * move, dy / dist * move);
    }

    // ── Внутреннее ────────────────────────────────────────────────────────────

    private static Drop createDrop(int originTileX, int originTileY) {
        int slot = nextFreeSlot();
        if (slot < 0) return null;

        int[] target = pickScatterTile(originTileX, originTileY);

        // Якорь позиции — тот же, что у тайлов (MapObject.calcPosition) и существ (Creation):
        // cellIndex_1based * tileSize, без дополнительного центрирования (см. Store.TILE_INDEX_BASE).
        float[] startIso = Transform.cartesianToIsometric(
            (originTileX + store.TILE_INDEX_BASE) * store.tileSizeWidth,
            (originTileY + store.TILE_INDEX_BASE) * store.tileSizeHeight
        );
        float[] endIso = Transform.cartesianToIsometric(
            (target[0] + store.TILE_INDEX_BASE) * store.tileSizeWidth,
            (target[1] + store.TILE_INDEX_BASE) * store.tileSizeHeight
        );

        Drop drop = new Drop();
        drop.mapCellX = target[0] + store.TILE_INDEX_BASE;
        drop.mapCellY = target[1] + store.TILE_INDEX_BASE;
        drop.initThrow(startIso[0], startIso[1], endIso[0], endIso[1]);

        store.drops[slot] = drop;
        if (slot > store.dropCount) store.dropCount = slot;
        return drop;
    }

    private static int nextFreeSlot() {
        for (int i = 0; i < store.drops.length; i++) {
            if (store.drops[i] == null) return i;
        }
        return -1;
    }

    /** Роллит случайное направление и дальность разлёта от точки спавна, ищет подходящий тайл рядом. */
    private static int[] pickScatterTile(int originX, int originY) {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        float dist = MIN_SCATTER_TILES + RANDOM.nextFloat() * (MAX_SCATTER_TILES - MIN_SCATTER_TILES);
        int candidateX = originX + Math.round((float) Math.cos(angle) * dist);
        int candidateY = originY + Math.round((float) Math.sin(angle) * dist);

        if (isLandable(candidateX, candidateY)) return new int[]{candidateX, candidateY};
        return findLandingTile(candidateX, candidateY);
    }

    /** Ищет ближайший подходящий для падения тайл: только поверхности/совсем низкие объекты. */
    private static int[] findLandingTile(int originX, int originY) {
        if (isLandable(originX, originY)) return new int[]{originX, originY};

        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int tx = originX + dx, ty = originY + dy;
                    if (isLandable(tx, ty)) return new int[]{tx, ty};
                }
            }
        }
        return new int[]{originX, originY};
    }

    private static boolean isLandable(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= store.mapHeight || ty >= store.mapWidth) return false;
        com.nicweiss.editor.objects.MapObject tile = store.objectedMap[tx][ty];
        // objectHeight — игровая "высота препятствия" тайла, а не getHeight() (унаследован от
        // BaseObject — пиксельный размер спрайта, ~50-120px, почти всегда >= MAX_SURFACE_HEIGHT
        // для уже отрисованных тайлов — см. тот же баг, найденный и исправленный в SpawnManager).
        if (tile.objectHeight >= MAX_SURFACE_HEIGHT) return false;
        // Вода — не место для дропа, если до неё нельзя дойти (см. Player.isCollidingAt — то же
        // правило: мостик на воде делает её проходимой, значит и годной для приземления лута).
        if (tile.getSurfaceId() == WATER_TEXTURE_ID && tile.getTextureId() != BRIDGE_TEXTURE_ID) return false;
        return true;
    }

    private static Texture loadItemTexture(LinkedHashMap itemTemplate) {
        String imagePath = (String) itemTemplate.get("__image__");
        if (imagePath != null) {
            try {
                return new Texture(Gdx.files.absolute(imagePath));
            } catch (Exception ignored) {}
        }
        return fallbackTexture();
    }

    private static Texture fallback;
    private static Texture fallbackTexture() {
        if (fallback == null) fallback = new Texture("items_button.png");
        return fallback;
    }

    private static Texture goldTexture;
    /** Иконка монеты — переиспользуется в инвентаре для ячейки золота (см. SystemUI.renderGoldPocket). */
    public static Texture goldTexture() {
        if (goldTexture == null) goldTexture = buildGoldTexture();
        return goldTexture;
    }

    /** Простая процедурная текстура монеты — до появления готового арта (см. Player.buildTexture). */
    private static Texture buildGoldTexture() {
        int s = 32;
        Pixmap pmap = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pmap.setColor(0, 0, 0, 0);
        pmap.fill();
        pmap.setColor(0.95f, 0.78f, 0.15f, 1f);
        pmap.fillCircle(s / 2, s / 2, s / 2 - 2);
        pmap.setColor(0.6f, 0.45f, 0.05f, 1f);
        pmap.drawCircle(s / 2, s / 2, s / 2 - 2);
        Texture t = new Texture(pmap);
        pmap.dispose();
        return t;
    }

    private static Texture expTexture;
    private static Texture expTexture() {
        if (expTexture == null) expTexture = buildExpTexture();
        return expTexture;
    }

    /** Маленькая неоново-синяя сфера опыта — процедурная текстура (до появления готового арта). */
    private static Texture buildExpTexture() {
        int s = 24;
        Pixmap pmap = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pmap.setColor(0, 0, 0, 0);
        pmap.fill();
        pmap.setColor(0.15f, 0.55f, 1f, 1f);
        pmap.fillCircle(s / 2, s / 2, s / 2 - 1);
        pmap.setColor(0.55f, 0.85f, 1f, 1f);
        pmap.fillCircle(s / 2, s / 2, s / 2 - 4);
        pmap.setColor(0.9f, 0.98f, 1f, 1f); // яркое ядро — "неоновый" блик
        pmap.fillCircle(s / 2, s / 2, s / 5);
        Texture t = new Texture(pmap);
        pmap.dispose();
        return t;
    }

    private static Texture heartIcon, sparkIcon, starIcon;

    /** Иконка-сердце для лейбла зелья здоровья (см. applyConsumableLabel). */
    private static Texture heartIconTexture() {
        if (heartIcon == null) heartIcon = buildHeartTexture();
        return heartIcon;
    }

    private static Texture buildHeartTexture() {
        int s = 24;
        Pixmap pmap = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pmap.setColor(0, 0, 0, 0);
        pmap.fill();
        pmap.setColor(HEALTH_LABEL_COLOR[0], HEALTH_LABEL_COLOR[1], HEALTH_LABEL_COLOR[2], 1f);
        pmap.fillCircle(8, 9, 7);
        pmap.fillCircle(16, 9, 7);
        pmap.fillTriangle(1, 10, 23, 10, 12, 23);
        Texture t = new Texture(pmap);
        pmap.dispose();
        return t;
    }

    /** Иконка-искра для лейбла зелья маны (см. applyConsumableLabel). */
    private static Texture sparkIconTexture() {
        if (sparkIcon == null) sparkIcon = buildSparkTexture();
        return sparkIcon;
    }

    private static Texture buildSparkTexture() {
        int s = 24;
        Pixmap pmap = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pmap.setColor(0, 0, 0, 0);
        pmap.fill();
        pmap.setColor(MANA_LABEL_COLOR[0], MANA_LABEL_COLOR[1], MANA_LABEL_COLOR[2], 1f);
        // Зигзаг — классический силуэт молнии из двух треугольников.
        pmap.fillTriangle(14, 1, 5, 13, 13, 13);
        pmap.fillTriangle(10, 10, 20, 10, 11, 23);
        Texture t = new Texture(pmap);
        pmap.dispose();
        return t;
    }

    /** Иконка-звезда для лейбла зелья восстановления (см. applyConsumableLabel). */
    private static Texture starIconTexture() {
        if (starIcon == null) starIcon = buildStarTexture();
        return starIcon;
    }

    private static Texture buildStarTexture() {
        int s = 24;
        Pixmap pmap = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pmap.setColor(0, 0, 0, 0);
        pmap.fill();
        pmap.setColor(RECOVERY_LABEL_COLOR[0], RECOVERY_LABEL_COLOR[1], RECOVERY_LABEL_COLOR[2], 1f);
        // 4-лучевая "искорка" — два скрещенных ромба (вертикальный + горизонтальный).
        pmap.fillTriangle(12, 0, 16, 12, 8, 12);
        pmap.fillTriangle(8, 12, 16, 12, 12, 24);
        pmap.fillTriangle(12, 8, 24, 12, 12, 16);
        pmap.fillTriangle(0, 12, 12, 8, 12, 16);
        Texture t = new Texture(pmap);
        pmap.dispose();
        return t;
    }

    // Фон у всех предметов одинаковый (чёрный, alpha 60%) — редкость различается цветом текста.
    // Циан и малиновый — единственные свободные "холодные" оттенки: жёлтый/оранжевый были заняты
    // золотом и красным здоровья (см. GOLD_LABEL_COLOR, HEALTH_LABEL_COLOR) и сливались с ними.
    private static float[] rarityTextColor(String rarityKey) {
        switch (rarityKey) {
            case "rare":   return new float[]{0.15f, 0.95f, 0.9f};  // электрик-циан
            case "unique": return new float[]{1f, 0.1f, 0.75f};    // малиновый (magenta)
            default:       return new float[]{1f, 1f, 1f};         // белый текст
        }
    }

}
