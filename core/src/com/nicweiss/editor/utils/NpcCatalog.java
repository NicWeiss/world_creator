package com.nicweiss.editor.utils;

import java.util.LinkedHashMap;

/**
 * Справочник типов NPC — аналог {@link ItemModifierCatalog} для сущностей. Не хранит роллнутые
 * значения, только базовые правила: сколько здоровья/урона/скорости у типа на уровне 1 и как это
 * растёт с уровнем. Конкретные боевые характеристики роллит {@link NpcGenerator} при спавне.
 *
 * Нейтральные NPC (торговцы/кузнецы/жители) расставляются вручную (см. NpcEditorWindow) и боевых
 * характеристик не имеют — они просто размечают тип/текстуру для флейвора. Воины/маги/монстры
 * (ALLY/ENEMY/MONSTER) порождаются спавнерами (см. SpawnManager) и подчиняются единым правилам
 * тиров вне зависимости от стороны.
 */
public class NpcCatalog {

    public enum Faction { NEUTRAL, ALLY, ENEMY, MONSTER }
    public enum Role { NONE, WARRIOR, MAGE, BEAST }
    /** Тир юнита — обычный/старший/прайм, см. TIER-константы и NpcGenerator.rollTier. */
    public enum Tier { NORMAL, ELDER, PRIME }

    // Стихии сопротивлений старших/прайм юнитов — свой список, не переиспользует
    // ItemModifierCatalog.School (та система заточена под требования силы/магии предметов).
    public static final String[] ELEMENTS = {"physical", "fire", "cold", "lightning"};

    public static class TypeDef {
        public final String key;
        public final String label;
        public final Faction faction;
        public final Role role;
        public final String imageFolder; // assets/creations/<folder>/default.png, null = без авто-текстуры

        // Боевые базовые показатели на 1 уровне и прирост за уровень — null-эквивалент для
        // нейтральных (role=NONE): используется фиксированное здоровье NEUTRAL_HEALTH, без урона/скорости.
        public final float baseHealth, healthPerLevel;
        public final float baseDamage, damagePerLevel;
        public final float speedMultiplier; // относительно базовой скорости игрока/юнита-война (1.0)

        TypeDef(String key, String label, Faction faction, Role role, String imageFolder,
                float baseHealth, float healthPerLevel, float baseDamage, float damagePerLevel, float speedMultiplier) {
            this.key = key;
            this.label = label;
            this.faction = faction;
            this.role = role;
            this.imageFolder = imageFolder;
            this.baseHealth = baseHealth;
            this.healthPerLevel = healthPerLevel;
            this.baseDamage = baseDamage;
            this.damagePerLevel = damagePerLevel;
            this.speedMultiplier = speedMultiplier;
        }

        public int healthAtLevel(int level) {
            return Math.round(baseHealth + healthPerLevel * Math.max(0, level - 1));
        }

        public int damageAtLevel(int level) {
            return Math.round(baseDamage + damagePerLevel * Math.max(0, level - 1));
        }
    }

    /** Множители тира поверх healthAtLevel/damageAtLevel — см. TypeDef. */
    public static class TierDef {
        public final Tier tier;
        public final float healthMult, damageMult;
        public final int resistCount;     // сколько стихий сопротивления роллится
        public final int resistMinPct, resistMaxPct;

        TierDef(Tier tier, float healthMult, float damageMult, int resistCount, int resistMinPct, int resistMaxPct) {
            this.tier = tier;
            this.healthMult = healthMult;
            this.damageMult = damageMult;
            this.resistCount = resistCount;
            this.resistMinPct = resistMinPct;
            this.resistMaxPct = resistMaxPct;
        }
    }

    public static final LinkedHashMap<Tier, TierDef> TIERS = new LinkedHashMap<>();
    static {
        TIERS.put(Tier.NORMAL, new TierDef(Tier.NORMAL, 1.0f, 1.0f, 0, 0, 0));
        // Старший: большое здоровье, значительный урон, сопротивление 30-70% к ОДНОЙ стихии.
        TIERS.put(Tier.ELDER,  new TierDef(Tier.ELDER,  2.5f, 1.6f, 1, 30, 70));
        // Прайм: ещё больше здоровья и урона, чем у старшего, 30-70% к ДВУМ стихиям.
        TIERS.put(Tier.PRIME,  new TierDef(Tier.PRIME,  4.0f, 2.2f, 2, 30, 70));
    }

    // Вероятности тира при спавне (см. NpcGenerator.rollTier): "1 к 50" / "1 к 100".
    public static final double ELDER_CHANCE = 1.0 / 50.0;
    public static final double PRIME_CHANCE = 1.0 / 100.0;

    // Фиксированное здоровье нейтральных NPC — небольшое, без урона/скорости/сопротивлений.
    public static final int NEUTRAL_HEALTH = 20;

    // Целевой размер NPC на экране — множитель store.tileSizeWidth (см. Creation.targetMaxScreenSize).
    // "Не больше деревьев, а лучше меньше" — деревья (gp_2/3/4) занимают на экране ~2.2 тайла по
    // большей стороне (350px исходник / tileDownScale=3 ≈ 117px при tileSizeWidth≈53px). История:
    // 1.8 → 1.26 (×0.7, "всё ещё гигантские") → 0.882 (×0.7 ещё раз, по просьбе пользователя).
    public static final float NPC_SIZE_TILE_MULT = 0.882f;

    public static final LinkedHashMap<String, TypeDef> TYPES = new LinkedHashMap<>();

    private static void neutral(String key, String label, String imageFolder) {
        TYPES.put(key, new TypeDef(key, label, Faction.NEUTRAL, Role.NONE, imageFolder, NEUTRAL_HEALTH, 0, 0, 0, 1f));
    }

    private static void combat(String key, String label, Faction faction, Role role, String imageFolder,
                                float baseHealth, float healthPerLevel, float baseDamage, float damagePerLevel, float speedMultiplier) {
        TYPES.put(key, new TypeDef(key, label, faction, role, imageFolder,
            baseHealth, healthPerLevel, baseDamage, damagePerLevel, speedMultiplier));
    }

    // Базовые показатели воинов/магов/монстров на 1 уровне и прирост за уровень. Воины ощутимо
    // прочнее магов (здоровье ~вдвое выше), маги ощутимо быстрее воинов (speedMultiplier).
    private static final float WARRIOR_HP = 50f, WARRIOR_HP_LVL = 10f, WARRIOR_DMG = 5f, WARRIOR_DMG_LVL = 1.0f;
    private static final float MAGE_HP    = 25f, MAGE_HP_LVL    = 5f,  MAGE_DMG    = 6f, MAGE_DMG_LVL    = 1.2f;
    private static final float BEAST_HP   = 35f, BEAST_HP_LVL   = 7f,  BEAST_DMG   = 5f, BEAST_DMG_LVL   = 1.1f;
    private static final float WARRIOR_SPEED = 1.0f, MAGE_SPEED = 1.35f, BEAST_SPEED = 1.0f;

    static {
        // ───────────────────────── НЕЙТРАЛЬНЫЕ ─────────────────────────
        // Без выделенных текстурных папок (не заведены в ТЗ) — картинка выбирается вручную
        // в редакторе, как и раньше (см. NpcEditorWindow.openAssetPicker).
        neutral("neutral_trader",     "Торговец", null);
        neutral("neutral_blacksmith", "Кузнец",   null);
        neutral("neutral_villager",   "Житель",   null);

        // ───────────────────────── СОЮЗНИКИ ─────────────────────────
        combat("ally_warrior", "Воин-союзник", Faction.ALLY, Role.WARRIOR, "alliance_warrior",
            WARRIOR_HP, WARRIOR_HP_LVL, WARRIOR_DMG, WARRIOR_DMG_LVL, WARRIOR_SPEED);
        combat("ally_mage", "Маг-союзник", Faction.ALLY, Role.MAGE, "alliance_mage",
            MAGE_HP, MAGE_HP_LVL, MAGE_DMG, MAGE_DMG_LVL, MAGE_SPEED);

        // ───────────────────────── ВРАГИ ─────────────────────────
        combat("enemy_warrior", "Воин-враг", Faction.ENEMY, Role.WARRIOR, "enemy_warrior",
            WARRIOR_HP, WARRIOR_HP_LVL, WARRIOR_DMG, WARRIOR_DMG_LVL, WARRIOR_SPEED);
        combat("enemy_mage", "Маг-враг", Faction.ENEMY, Role.MAGE, "enemy_mage",
            MAGE_HP, MAGE_HP_LVL, MAGE_DMG, MAGE_DMG_LVL, MAGE_SPEED);

        // ───────────────────────── МОНСТРЫ ─────────────────────────
        // Монстры не делятся на воинов/магов (одна текстурная папка "beast" в ТЗ) — враждебны всем.
        combat("monster_beast", "Монстр", Faction.MONSTER, Role.BEAST, "beast",
            BEAST_HP, BEAST_HP_LVL, BEAST_DMG, BEAST_DMG_LVL, BEAST_SPEED);
    }

    private NpcCatalog() {}

    public static TypeDef get(String key) {
        return key != null ? TYPES.get(key) : null;
    }

    /** Ключ типа для случайной роли спавнера: enemy/ally — 50/50 воин/маг, monster — всегда beast. */
    public static String randomCombatType(NpcCatalog.Faction faction, java.util.Random random) {
        switch (faction) {
            case ALLY:    return random.nextBoolean() ? "ally_warrior"  : "ally_mage";
            case ENEMY:   return random.nextBoolean() ? "enemy_warrior" : "enemy_mage";
            case MONSTER: return "monster_beast";
            default:      return null;
        }
    }
}
