package com.nicweiss.editor.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Справочник классов предметов и модификаторов (на основе items_modificators.md).
 * Предметы НЕ хранят правила ролла — они лишь ссылаются на ключи модификаторов
 * (ModifierDef.key) из этого каталога и хранят свои конкретные (роллнутые) значения.
 */
public class ItemModifierCatalog {

    /** Школа модификатора — определяет, какое требование (сила/магия) он поднимает. */
    public enum School { PHYSICAL, MAGIC, NEUTRAL }

    public static class Subtype {
        public final String key;
        public final String label;
        public final int[] size; // не null только для классов, определяемых размером (чармы)
        public String imageFolder; // папка в assets/items/, переопределяет TypeDef.imageFolder для этого класса

        public Subtype(String key, String label) {
            this(key, label, null);
        }

        public Subtype(String key, String label, int[] size) {
            this.key = key;
            this.label = label;
            this.size = size;
        }
    }

    public static class ModifierDef {
        public final String key;       // уникальный стабильный id, на него ссылаются предметы
        public final String name;
        public final int min;
        public final int max;
        public final String unit;      // суффикс для отображения ("%", "сек", "" ...)
        public final String classKey;  // null = модификатор доступен для любого класса этого типа
        public final School school;

        // Гейтинг по редкости/уровню ("...только на Rare и Unique высокого уровня").
        public String minRarity;          // null = без ограничения; "rare" = Rare и выше; "unique" = только Unique
        public boolean requiresHighLevel; // "...высокого уровня" в документе

        // Резисты: "все" и "к одной стихии" взаимоисключающие, но несколько "к одной стихии" — могут сосуществовать.
        public boolean isResist;
        public boolean isAllResist; // true = вариант "все сопротивления", false = "к одной стихии"

        // Пул здоровья/маны: прирост (health/mana) и лич (life/mana leech) взаимоисключающие;
        // пассивная регенерация (replenish) в группу не входит и совместима с обоими.
        public String exclusiveGroup; // например "life_pool" / "mana_pool"

        // Чарм: скиллер блокирует все прочие обычные модификаторы, кроме здоровья-компаньона.
        public boolean isSkiller;
        public boolean isHealthCompanion;

        // Потолок значения зависит от редкости предмета (например "контейнеры на поясе"):
        // null = не используется, берётся обычный max. Иначе — effectiveMax(rarityKey) переопределяет max.
        public Integer commonMax, rareMax, uniqueMax;

        // Модификатор существует в каталоге и доступен для ручного добавления в редакторе,
        // но генератор (ItemGenerator.rollModifiers) никогда не выбирает его сам.
        public boolean excludeFromGenerator;

        public ModifierDef(String key, String name, int min, int max, String unit, String classKey, School school) {
            this.key = key;
            this.name = name;
            this.min = min;
            this.max = max;
            this.unit = unit;
            this.classKey = classKey;
            this.school = school;
        }

        public ModifierDef gated(String minRarity) {
            this.minRarity = minRarity;
            this.requiresHighLevel = true;
            return this;
        }

        public ModifierDef resist(boolean isAll) {
            this.isResist = true;
            this.isAllResist = isAll;
            return this;
        }

        public ModifierDef exclusive(String group) {
            this.exclusiveGroup = group;
            return this;
        }

        public ModifierDef skiller() {
            this.isSkiller = true;
            return this;
        }

        public ModifierDef healthCompanion() {
            this.isHealthCompanion = true;
            return this;
        }

        public ModifierDef rarityScaledMax(int commonMax, int rareMax, int uniqueMax) {
            this.commonMax = commonMax;
            this.rareMax = rareMax;
            this.uniqueMax = uniqueMax;
            return this;
        }

        public ModifierDef manualOnly() {
            this.excludeFromGenerator = true;
            return this;
        }

        /** Действующий потолок значения для данной редкости (с учётом rarityScaledMax, иначе обычный max). */
        public int effectiveMax(String rarityKey) {
            if (commonMax == null) return max;
            if ("unique".equals(rarityKey)) return uniqueMax;
            if ("rare".equals(rarityKey)) return rareMax;
            return commonMax;
        }
    }

    public static class TypeDef {
        public final String key;
        public final String label;
        public final List<Subtype> subtypes = new ArrayList<>();
        public final List<int[]> sizes = new ArrayList<>();
        public final List<ModifierDef> modifiers = new ArrayList<>();
        public boolean classDerivedFromSize = false;
        public Integer maxModifiers = null; // жёсткий потолок количества модов вне зависимости от редкости
        public String imageFolder; // папка в assets/items/ по умолчанию для типа (если класс не переопределяет)

        public TypeDef(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String labelForClass(String classKey) {
            if (classKey == null) return null;
            for (Subtype s : subtypes) {
                if (s.key.equals(classKey)) return s.label;
            }
            return classKey;
        }

        public String classForSize(int w, int h) {
            for (Subtype s : subtypes) {
                if (s.size != null && s.size[0] == w && s.size[1] == h) return s.key;
            }
            return null;
        }

        public List<ModifierDef> modifiersFor(String classKey) {
            List<ModifierDef> result = new ArrayList<>();
            for (ModifierDef m : modifiers) {
                if (m.classKey == null || m.classKey.equals(classKey)) {
                    result.add(m);
                }
            }
            return result;
        }

        // Модификаторы, доступные генератору при данных классе/редкости/уровне предмета
        // (без учёта скиллер-лока и групп; excludeFromGenerator сюда не попадают —
        // такие моды доступны только через ручное добавление в редакторе).
        public List<ModifierDef> eligibleModifiers(String classKey, String rarityKey, int itemLevel) {
            List<ModifierDef> result = new ArrayList<>();
            for (ModifierDef m : modifiersFor(classKey)) {
                if (m.excludeFromGenerator) continue;
                if (isEligible(m, rarityKey, itemLevel)) result.add(m);
            }
            return result;
        }
    }

    /** "Высокий уровень" из формулировок документа вида "...высокого уровня". */
    public static final int HIGH_LEVEL_THRESHOLD = 50;

    public static int rarityRank(String rarityKey) {
        if ("unique".equals(rarityKey)) return 2;
        if ("rare".equals(rarityKey)) return 1;
        return 0;
    }

    public static boolean isEligible(ModifierDef def, String rarityKey, int itemLevel) {
        if (def.minRarity != null && rarityRank(rarityKey) < rarityRank(def.minRarity)) return false;
        if (def.requiresHighLevel && itemLevel < HIGH_LEVEL_THRESHOLD) return false;
        return true;
    }

    /** Папка в assets/items/ для картинки по умолчанию: класс переопределяет тип, если задан. */
    public static String resolveImageFolder(TypeDef type, String classKey) {
        if (type == null) return null;
        if (classKey != null) {
            for (Subtype s : type.subtypes) {
                if (s.key.equals(classKey) && s.imageFolder != null) return s.imageFolder;
            }
        }
        return type.imageFolder;
    }

    /**
     * Совместим ли модификатор def с уже выбранным набором currentStats предмета
     * (резисты "все" vs "к стихии" и пары прирост/лич — взаимоисключающие группы).
     */
    public static boolean isCompatible(ModifierDef def, String typeKey, LinkedHashMap currentStats) {
        for (Object k : currentStats.keySet()) {
            ModifierDef existing = findModifier(typeKey, k.toString());
            if (existing == null || existing == def) continue;

            if (def.isResist && existing.isResist && (def.isAllResist || existing.isAllResist)) {
                return false;
            }
            if (def.exclusiveGroup != null && def.exclusiveGroup.equals(existing.exclusiveGroup)) {
                return false;
            }
        }
        return true;
    }

    /** Параметры ролла редкости: сколько модификаторов даётся и насколько щедро роллятся значения. */
    public static class RarityDef {
        public final String key;
        public final String label;
        public final int minMods;
        public final int maxMods;
        public final double qualityBonus; // 0..1, добавка к "качеству" роллов значений
        public final int reqBonus;        // плоская добавка к требованиям силы/магии/уровня

        public RarityDef(String key, String label, int minMods, int maxMods, double qualityBonus, int reqBonus) {
            this.key = key;
            this.label = label;
            this.minMods = minMods;
            this.maxMods = maxMods;
            this.qualityBonus = qualityBonus;
            this.reqBonus = reqBonus;
        }
    }

    public static final LinkedHashMap<String, TypeDef> TYPES = new LinkedHashMap<>();
    public static final LinkedHashMap<String, RarityDef> RARITIES = new LinkedHashMap<>();

    static {
        // Common: 0-3 модификатора, рандомны. Rare: 4-8, рандомны. Unique: много, фиксированы
        // (в редакторе фиксированность эмулируется тем, что реролл — отдельное явное действие).
        RARITIES.put("common", new RarityDef("common", "Обычный (Common)", 0, 3, 0.0, 0));
        RARITIES.put("rare",   new RarityDef("rare",   "Редкий (Rare)",    4, 8, 0.18, 8));
        RARITIES.put("unique", new RarityDef("unique", "Уникальный (Unique)", 6, 10, 0.35, 18));
    }

    private static TypeDef type(String key, String label, int[][] sizes) {
        TypeDef t = new TypeDef(key, label);
        for (int[] sz : sizes) t.sizes.add(sz);
        TYPES.put(key, t);
        return t;
    }

    private static void sub(TypeDef t, String key, String label) {
        t.subtypes.add(new Subtype(key, label));
    }

    private static void sub(TypeDef t, String key, String label, String imageFolder) {
        Subtype s = new Subtype(key, label);
        s.imageFolder = imageFolder;
        t.subtypes.add(s);
    }

    private static void subSized(TypeDef t, String key, String label, int w, int h) {
        t.subtypes.add(new Subtype(key, label, new int[]{w, h}));
    }

    private static ModifierDef mod(TypeDef t, String key, String name, int min, int max, String unit, School school) {
        ModifierDef def = new ModifierDef(key, name, min, max, unit, null, school);
        t.modifiers.add(def);
        return def;
    }

    private static ModifierDef mod(TypeDef t, String key, String name, int min, int max, String unit, String classKey, School school) {
        ModifierDef def = new ModifierDef(key, name, min, max, unit, classKey, school);
        t.modifiers.add(def);
        return def;
    }

    // Список стихий из документа: "Сопротивление к одной стихии" — это не один обезличенный мод,
    // а отдельный (именованный) для каждой стихии. Несколько разных одновременно совместимы
    // (см. ItemModifierCatalog.isCompatible), "все сопротивления" исключает любую из них.
    private static final String[][] ELEMENTS = {{"fire", "Огню"}, {"lightning", "Молнии"}, {"cold", "Холоду"}};

    private static void oneRes(TypeDef t, String prefix, int min, int max, School school) {
        for (String[] el : ELEMENTS) {
            mod(t, prefix + "_res_" + el[0], "Сопротивление к " + el[1], min, max, "%", school).resist(false);
        }
    }

    private static void oneRes(TypeDef t, String prefix, int min, int max, String classKey, School school) {
        for (String[] el : ELEMENTS) {
            mod(t, prefix + "_res_" + el[0], "Сопротивление к " + el[1], min, max, "%", classKey, school).resist(false);
        }
    }

    public static ModifierDef findModifier(String typeKey, String modKey) {
        TypeDef t = TYPES.get(typeKey);
        if (t == null) return null;
        for (ModifierDef m : t.modifiers) {
            if (m.key.equals(modKey)) return m;
        }
        return null;
    }

    private static final School PHYSICAL = School.PHYSICAL;
    private static final School MAGIC = School.MAGIC;
    private static final School NEUTRAL = School.NEUTRAL;

    static {
        // ───────────────────────── ОРУЖИЕ ─────────────────────────
        TypeDef weapon = type("weapon", "Оружие", new int[][]{{1,2},{1,3},{1,4},{2,3},{2,4}});
        sub(weapon, "melee", "Ближний бой", "sword");
        sub(weapon, "caster", "Магический бой", "staff");
        mod(weapon, "weapon_ed", "Повышенный урон", 10, 400, "%", PHYSICAL);
        mod(weapon, "weapon_ias", "Скорость атаки", 1, 40, "%", "melee", PHYSICAL);
        mod(weapon, "weapon_fcr", "Скорость каста", 1, 40, "%", "caster", MAGIC);
        mod(weapon, "weapon_ar", "Рейтинг атаки", 10, 450, "", PHYSICAL);
        mod(weapon, "weapon_flat_min_dmg", "Доп. физ. урон", 15, 30, "", "melee", PHYSICAL);
        mod(weapon, "weapon_fire_dmg", "Стихийный урон: Огонь", 1, 200, "", "melee", MAGIC);
        mod(weapon, "weapon_lightning_dmg", "Стихийный урон: Молния", 1, 480, "", "melee", MAGIC);
        mod(weapon, "weapon_cold_dmg", "Стихийный урон: Холод", 1, 120, "", "melee", MAGIC);
        mod(weapon, "weapon_life_leech", "Похищение жизни", 1, 10, "%", "melee", PHYSICAL);
        mod(weapon, "weapon_mana_leech", "Похищение маны", 1, 10, "%", "melee", MAGIC);
        mod(weapon, "weapon_itd", "Игнорирование защиты цели", 5, 25, "%", "melee", PHYSICAL);
        mod(weapon, "weapon_itr", "Игнорирование сопротивлений цели", 5, 25, "%", "caster", MAGIC);
        mod(weapon, "weapon_blind", "Ослепление цели", 1, 3, "сек", NEUTRAL);
        mod(weapon, "weapon_freeze", "Замораживание цели", 1, 3, "сек", "melee", MAGIC);
        mod(weapon, "weapon_prevent_heal", "Запрет лечения для противников", 1, 1, "", NEUTRAL);
        mod(weapon, "weapon_all_skills", "Все навыки", 1, 2, "", MAGIC).gated("rare");
        mod(weapon, "weapon_elemental_branch", "Навыки ветки", 1, 4, "", MAGIC).gated("rare");

        // ───────────────────────── ЩИТ ─────────────────────────
        TypeDef shield = type("shield", "Щит", new int[][]{{2,2},{2,4}});
        sub(shield, "shield", "Щит", "shield");
        sub(shield, "grimoire", "Гримуар", "grimuare");
        mod(shield, "shield_ed_def", "Повышенная защита", 10, 200, "%", PHYSICAL);
        mod(shield, "shield_flat_def", "Плоская защита", 1, 40, "", PHYSICAL);
        mod(shield, "shield_block_chance", "Шанс блока", 1, 30, "%", PHYSICAL);
        mod(shield, "shield_allres", "Все сопротивления", 1, 20, "%", MAGIC).resist(true);
        oneRes(shield, "shield", 1, 40, MAGIC);
        mod(shield, "shield_phys_reduction", "Снижение физ. урона", 1, 70, "", "shield", PHYSICAL);
        mod(shield, "shield_magic_reduction", "Снижение магич. урона", 1, 30, "", "grimoire", MAGIC);
        mod(shield, "shield_strength", "Сила", 1, 15, "", "shield", PHYSICAL);
        mod(shield, "shield_magic", "Магия", 1, 15, "", "grimoire", MAGIC);
        mod(shield, "shield_health", "Здоровье", 1, 60, "", "shield", NEUTRAL);
        mod(shield, "shield_mana", "Мана", 1, 200, "", "grimoire", MAGIC);
        mod(shield, "shield_thorns", "Шипы", 7, 10, "", "shield", PHYSICAL);
        mod(shield, "shield_all_skills", "Все навыки", 1, 2, "", MAGIC).gated("rare");
        mod(shield, "shield_branch_skills", "Навыки ветки", 1, 3, "", MAGIC).gated("rare");

        // ───────────────────────── ШЛЕМ ─────────────────────────
        TypeDef helmet = type("helmet", "Шлем", new int[][]{{2,2}});
        sub(helmet, "helmet", "Шлем", "helmet");
        sub(helmet, "tiara", "Тиара", "tiara");
        mod(helmet, "helmet_all_skills", "Все навыки", 1, 2, "", MAGIC).gated("rare");
        mod(helmet, "helmet_single_skill", "Навыки ветки", 1, 3, "", MAGIC).gated("rare");
        mod(helmet, "helmet_fcr", "Скорость каста", 1, 20, "%", "tiara", MAGIC);
        mod(helmet, "helmet_ed_def", "Повышенная защита", 10, 200, "%", "helmet", PHYSICAL);
        mod(helmet, "helmet_flat_def", "Плоская защита", 1, 30, "", "helmet", PHYSICAL);
        mod(helmet, "helmet_str", "Сила", 1, 30, "", "helmet", PHYSICAL);
        mod(helmet, "helmet_mgc", "Магия", 1, 30, "", "tiara", MAGIC);
        mod(helmet, "helmet_health", "Здоровье", 1, 60, "", "tiara", NEUTRAL);
        mod(helmet, "helmet_mana", "Мана", 1, 90, "", "tiara", MAGIC);
        mod(helmet, "helmet_life_leech", "Похищение жизни", 1, 8, "%", "helmet", PHYSICAL);
        mod(helmet, "helmet_mana_leech", "Похищение маны", 1, 6, "%", "helmet", MAGIC);
        mod(helmet, "helmet_allres", "Все Сопротивления", 1, 20, "%", "tiara", MAGIC).resist(true);
        oneRes(helmet, "helmet", 1, 30, "helmet", MAGIC);
        mod(helmet, "helmet_mf", "Поиск предметов", 1, 35, "%", NEUTRAL);
        mod(helmet, "helmet_gf", "Поиск золота", 1, 80, "%", NEUTRAL);

        // ───────────────────────── БРОНЯ ─────────────────────────
        TypeDef armor = type("armor", "Броня", new int[][]{{2,4}});
        sub(armor, "armor", "Броня", "armor");
        sub(armor, "robe", "Мантия", "robe");
        mod(armor, "armor_def_plate", "Защита", 10, 300, "%", "armor", PHYSICAL);
        mod(armor, "armor_def_robe", "Защита", 5, 50, "%", "robe", MAGIC);
        mod(armor, "armor_health", "Здоровье", 1, 60, "", "armor", NEUTRAL);
        mod(armor, "armor_health_robe", "Здоровье", 1, 100, "", "robe", NEUTRAL);
        mod(armor, "armor_str", "Сила", 1, 30, "", "armor", PHYSICAL);
        mod(armor, "armor_mgc", "Магия", 1, 30, "", "robe", MAGIC);
        mod(armor, "armor_phys_red", "Снижение физ. урона", 1, 70, "", "armor", PHYSICAL);
        mod(armor, "robe_phys_red", "Снижение физ. урона", 1, 20, "", "robe", PHYSICAL);
        mod(armor, "robe_magic_red", "Снижение магич. урона", 1, 70, "", "robe", MAGIC);
        mod(armor, "armor_magic_red", "Снижение магич. урона", 1, 10, "", "armor", MAGIC);
        mod(armor, "armor_allres", "Все сопротивления", 1, 30, "%", MAGIC).resist(true);
        oneRes(armor, "armor", 1, 40, MAGIC);
        mod(armor, "armor_gf", "Поиск золота", 1, 100, "%", NEUTRAL);
        mod(armor, "armor_mf", "Поиск предметов", 1, 25, "%", NEUTRAL);

        // ───────────────────────── ПЕРЧАТКИ ─────────────────────────
        TypeDef gloves = type("gloves", "Перчатки", new int[][]{{2,2}});
        gloves.imageFolder = "glove";
        mod(gloves, "gloves_ias", "Скорость атаки", 1, 20, "%", PHYSICAL);
        mod(gloves, "gloves_all_skills", "Все навыки", 1, 2, "", MAGIC).gated("rare");
        mod(gloves, "gloves_single_skill", "Конкретный навык", 1, 3, "", MAGIC).gated("rare");
        mod(gloves, "gloves_life_leech", "Похищение жизни", 1, 5, "%", PHYSICAL);
        mod(gloves, "gloves_mana_leech", "Похищение маны", 1, 5, "%", MAGIC);
        mod(gloves, "gloves_strength", "Сила", 1, 15, "", PHYSICAL);
        mod(gloves, "gloves_magic", "Магия", 1, 15, "", MAGIC);
        oneRes(gloves, "gloves", 1, 30, MAGIC);
        mod(gloves, "gloves_gf", "Поиск золота", 1, 80, "%", NEUTRAL);
        mod(gloves, "gloves_mf", "Поиск предметов", 1, 25, "%", NEUTRAL);

        // ───────────────────────── САПОГИ ─────────────────────────
        TypeDef boots = type("boots", "Сапоги", new int[][]{{2,2}});
        boots.imageFolder = "boots";
        mod(boots, "boots_frw", "Скорость бега", 1, 30, "%", PHYSICAL);
        mod(boots, "boots_resist", "Сопротивления к", 1, 40, "%", MAGIC);
        mod(boots, "boots_magick", "Магия", 1, 9, "", PHYSICAL);
        mod(boots, "boots_stamina_attr", "Выносливость", 1, 9, "", PHYSICAL);
        mod(boots, "boots_mf", "Поиск предметов", 1, 25, "%", NEUTRAL);
        mod(boots, "boots_gf", "Поиск золота", 1, 80, "%", NEUTRAL);
        mod(boots, "boots_ed_def", "Повышенная защита", 10, 200, "%", PHYSICAL);
        mod(boots, "boots_flat_def", "Защита", 1, 30, "", PHYSICAL);
        mod(boots, "boots_replenish_life", "Восстановление здоровья", 1, 5, "", NEUTRAL);
        mod(boots, "boots_max_stamina", "Максимальная стамина", 1, 30, "", PHYSICAL);
        mod(boots, "boots_stamina_regen", "Скорость восстановления стамины", 1, 25, "%", PHYSICAL);

        // ───────────────────────── ПОЯС ─────────────────────────
        TypeDef belt = type("belt", "Пояс", new int[][]{{2,1}});
        belt.imageFolder = "belt";
        mod(belt, "belt_health", "Здоровье", 1, 60, "", NEUTRAL);
        mod(belt, "belt_strength", "Сила", 1, 30, "", PHYSICAL);
        mod(belt, "belt_mana", "Мана", 1, 30, "", MAGIC);
        oneRes(belt, "belt", 1, 30, MAGIC);
        mod(belt, "belt_gf", "Поиск золота", 1, 80, "%", NEUTRAL);
        mod(belt, "belt_mf", "Поиск предметов", 1, 30, "%", NEUTRAL);
        mod(belt, "belt_ed_def", "Повышенная защита", 10, 200, "%", PHYSICAL);
        mod(belt, "belt_flat_def", "Защита", 1, 30, "", PHYSICAL);
        mod(belt, "belt_replenish_mana", "Восстановление маны", 1, 9, "", MAGIC);
        // Потолок зависит от редкости: Common ≤2, Rare ≤3, Unique ≤5.
        mod(belt, "belt_containers", "Дополнительные контейнеры", 1, 5, "", NEUTRAL).rarityScaledMax(2, 3, 5);

        // ───────────────────────── АМУЛЕТ ─────────────────────────
        TypeDef amulet = type("amulet", "Амулет", new int[][]{{1,1}});
        amulet.imageFolder = "amulet";
        mod(amulet, "amulet_all_skills", "Все навыки", 1, 2, "", MAGIC);
        mod(amulet, "amulet_elem_branch", "Навыки стихийной ветки", 1, 3, "", MAGIC);
        mod(amulet, "amulet_fcr", "Скорость каста (FCR)", 1, 10, "%", MAGIC);
        mod(amulet, "amulet_strength", "Сила", 1, 30, "", PHYSICAL);
        mod(amulet, "amulet_dexterity", "Ловкость", 1, 30, "", PHYSICAL);
        mod(amulet, "amulet_energy", "Энергия", 1, 20, "", MAGIC);
        mod(amulet, "amulet_health", "Здоровье", 1, 60, "", NEUTRAL).exclusive("life_pool");
        mod(amulet, "amulet_mana", "Мана", 1, 90, "", MAGIC).exclusive("mana_pool");
        mod(amulet, "amulet_allres", "Все сопротивления", 1, 20, "%", MAGIC).resist(true);
        oneRes(amulet, "amulet", 1, 40, MAGIC);
        mod(amulet, "amulet_life_leech", "Похищение жизни", 1, 6, "%", PHYSICAL).exclusive("life_pool");
        mod(amulet, "amulet_mana_leech", "Похищение маны", 1, 8, "%", MAGIC).exclusive("mana_pool");
        mod(amulet, "amulet_mf", "Поиск предметов", 1, 35, "%", NEUTRAL);
        mod(amulet, "amulet_gf", "Поиск золота", 1, 80, "%", NEUTRAL);
        mod(amulet, "amulet_damage", "Урон", 1, 15, "", PHYSICAL);
        mod(amulet, "amulet_phys_red", "Снижение физ. урона", 1, 4, "", PHYSICAL);
        mod(amulet, "amulet_magic_red", "Снижение магич. урона", 1, 3, "", MAGIC);

        // ───────────────────────── АРТЕФАКТ ─────────────────────────
        TypeDef artifact = type("artifact", "Артефакт", new int[][]{{1,1}});
        artifact.maxModifiers = 5; // "на артефактах не более 5 модификаторов вне зависимости от редкости"
        artifact.imageFolder = "artifact";
        mod(artifact, "artifact_light_radius", "Радиус света", 1, 10, "", NEUTRAL).gated("unique");
        mod(artifact, "artifact_fcr", "Скорость каста", 1, 10, "%", MAGIC);
        mod(artifact, "artifact_ar", "Рейтинг атаки", 10, 120, "", PHYSICAL);
        mod(artifact, "artifact_life_leech", "Похищение жизни", 1, 8, "%", PHYSICAL).exclusive("life_pool");
        mod(artifact, "artifact_mana_leech", "Похищение маны", 1, 6, "%", MAGIC).exclusive("mana_pool");
        mod(artifact, "artifact_strength", "Сила", 1, 20, "", PHYSICAL);
        mod(artifact, "artifact_magic", "Магия", 1, 15, "", MAGIC);
        mod(artifact, "artifact_energy", "Энергия", 1, 15, "", MAGIC);
        mod(artifact, "artifact_health", "Здоровье", 1, 40, "", NEUTRAL).exclusive("life_pool");
        mod(artifact, "artifact_mana", "Мана", 1, 90, "", MAGIC).exclusive("mana_pool");
        mod(artifact, "artifact_allres", "Все опротивления", 1, 11, "%", MAGIC).resist(true);
        oneRes(artifact, "artifact", 1, 30, MAGIC);
        mod(artifact, "artifact_mf", "Поиск предметов", 1, 15, "%", NEUTRAL);
        mod(artifact, "artifact_gf", "Поиск золота", 1, 40, "%", NEUTRAL);
        mod(artifact, "artifact_damage", "Урон", 1, 9, "", PHYSICAL);
        mod(artifact, "artifact_replenish_life", "Восстановление здоровья", 1, 9, "", NEUTRAL);
        mod(artifact, "artifact_replenish_mana", "Восстановление маны", 1, 9, "", MAGIC);
        mod(artifact, "artifact_all_skills", "Все навыки", 1, 1, "", MAGIC).gated("rare");

        // ───────────────────────── ЧАРМ ─────────────────────────
        // Класс чарма строго определяется размером — не роллится отдельно.
        // "Не более 3 модификаторов вне зависимости от редкости" — действует на все 3 класса чарма.
        TypeDef charm = type("charm", "Чарм", new int[][]{{1,1},{1,2},{1,3}});
        charm.classDerivedFromSize = true;
        charm.maxModifiers = 3;
        charm.imageFolder = "charm";
        subSized(charm, "small", "Малый чарм", 1, 1);
        subSized(charm, "large", "Крупный чарм", 1, 2);
        subSized(charm, "grand", "Огромный чарм", 1, 3);

        mod(charm, "charm_small_health", "Здоровье", 1, 20, "", "small", NEUTRAL);
        mod(charm, "charm_small_mana", "Мана", 1, 17, "", "small", MAGIC);
        mod(charm, "charm_small_frw", "Скорость бега (FRW)", 1, 3, "%", "small", PHYSICAL);
        mod(charm, "charm_small_stamina_regen", "Регенерация стамины", 1, 5, "%", "small", PHYSICAL);
        mod(charm, "charm_small_allres", "Сопротивления", 1, 5, "%", "small", MAGIC).resist(true);
        oneRes(charm, "charm_small", 1, 11, "small", MAGIC);
        mod(charm, "charm_small_maxdmg", "Макс. урон", 1, 3, "", "small", PHYSICAL);
        mod(charm, "charm_small_ar", "Рейтинг атаки", 1, 20, "", "small", PHYSICAL);
        mod(charm, "charm_small_elem_dmg", "Стихийный урон", 1, 100, "", "small", MAGIC);
        mod(charm, "charm_small_mf", "Поиск предметов", 1, 7, "%", "small", NEUTRAL);
        mod(charm, "charm_small_gf", "Поиск золота", 1, 10, "%", "small", NEUTRAL);

        mod(charm, "charm_large_health", "Здоровье", 1, 35, "", "large", NEUTRAL);
        mod(charm, "charm_large_mana", "Мана", 1, 30, "", "large", MAGIC);
        mod(charm, "charm_large_fcr", "Скорость каста", 1, 5, "%", "large", MAGIC);
        mod(charm, "charm_large_allres", "Все сопротивления", 1, 8, "%", "large", MAGIC).resist(true);
        oneRes(charm, "charm_large", 1, 15, "large", MAGIC);
        mod(charm, "charm_large_damage", "Урон", 1, 6, "", "large", PHYSICAL);
        mod(charm, "charm_large_ar_flat", "Рейтинг атаки", 1, 48, "", "large", PHYSICAL);
        mod(charm, "charm_large_gf", "Поиск золота", 1, 22, "%", "large", NEUTRAL);
        mod(charm, "charm_large_mf", "Поиск предметов", 1, 6, "%", "large", NEUTRAL);

        // "Если выпал скилл — вторым модификатором может быть только здоровье, другие не могут быть.
        //  Здоровье без скилла может выпасть вместе с остальными." См. ItemsEditorWindow.rollModifiers.
        mod(charm, "charm_grand_elem_branch", "Навыки ветки", 1, 2, "", "grand", MAGIC).gated("rare").skiller();
        mod(charm, "charm_grand_all_skills", "Все навыки", 1, 1, "", "grand", MAGIC).gated("rare").skiller();
        mod(charm, "charm_grand_health", "Здоровье", 1, 45, "", "grand", NEUTRAL).healthCompanion();
        mod(charm, "charm_grand_damage", "Урон", 1, 10, "", "grand", PHYSICAL);
        mod(charm, "charm_grand_ar", "Рейтинг атаки", 1, 76, "", "grand", PHYSICAL);
        mod(charm, "charm_grand_fcr", "Скорость каста", 1, 15, "%", "grand", MAGIC);
        mod(charm, "charm_grand_allres", "Все сопротивления", 1, 15, "%", "grand", MAGIC).resist(true);
        oneRes(charm, "charm_grand", 1, 30, "grand", MAGIC);
        mod(charm, "charm_grand_gf", "Поиск золота", 1, 40, "%", "grand", NEUTRAL);
        mod(charm, "charm_grand_mf", "Поиск предметов", 1, 12, "%", "grand", NEUTRAL);

        // Общие моды чармов (любой размер/класс) — существуют только для ручной сборки уникальных
        // предметов в редакторе, генератор их никогда не роллит.
        mod(charm, "charm_experience", "Увеличение получаемого опыта", 1, 5, "%", NEUTRAL).manualOnly();
        mod(charm, "charm_all_attributes", "Все характеристики", 1, 30, "", NEUTRAL).manualOnly();
    }
}
