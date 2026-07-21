package com.nicweiss.editor.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/**
 * Каталог умений — статические данные (формулы эффектов по уровню 1-20), без единой зависимости
 * от Player/simulation. Тот же принцип, что ItemModifierCatalog: чистый реестр, чтобы этой же
 * таблицей мог пользоваться будущий каст NPC, а не только UI игрока (см. SkillDef.compute()).
 *
 * Источник данных — /home/nic/Downloads/skills.txt (предоставлено пользователем). Направление
 * "Вестник" в файле озаглавлено "(7 умений)", но таблица формул содержит только 6 аур — по решению
 * пользователя каталог содержит РОВНО 6, седьмая аура не выдумана.
 *
 * Дополнительно (отдельным запросом): "warrior_strike" ("Удар") — базовое умение атаки, по
 * умолчанию уже вложено на 1 уровень и привязано (см. Player) — единственное, что не с нуля.
 */
public class SkillCatalog {

    public enum Branch { WARRIOR, HERALD, ELEMENTALIST }

    public enum SkillKind { ACTIVE, SUSTAINED, STANCE, PASSIVE, AURA, TACTIC }

    /** Один вычисляемый параметр умения на уровне L (1..20). */
    public static class SkillStat {
        public final String key;    // стабильный id, напр. "damage_pct"
        public final String label;  // русская подпись для UI
        public final String unit;   // "%", "сек", "м", "" ...
        public final IntToDoubleFunction formula;

        public SkillStat(String key, String label, String unit, IntToDoubleFunction formula) {
            this.key = key;
            this.label = label;
            this.unit = unit;
            this.formula = formula;
        }

        public double at(int level) {
            return formula.applyAsDouble(Math.max(1, Math.min(20, level)));
        }
    }

    public static class SkillDef {
        public final String id;
        public final String name;
        public final Branch branch;
        public final SkillKind kind;
        public final String element;      // "fire" | "cold" | "lightning" | null (Воитель/Вестник)
        public final String description;
        public final List<SkillStat> stats;
        // Фиксированные (не масштабируемые уровнем) параметры — напр. мана расходников, макс. стаки.
        public final LinkedHashMap<String, Double> fixed;
        public static final int MAX_LEVEL = 20;

        // Имя файла иконки в assets/skills/ (напр. "warrior_1.png"), null = ещё нет картинки.
        // Полный путь резолвится там, где реально грузится текстура (PlayerHud/SystemUI), тем же
        // приёмом, что ItemGenerator.applyDefaultImage — здесь только имя файла, без Gdx-вызовов,
        // чтобы каталог оставался чистыми данными (см. класс-комментарий).
        public String imageFile;

        // Уровень ИГРОКА (не самого умения), с которого умение вообще доступно для прокачки.
        // Предыдущие умения на ветке дерева (см. SystemUI — деревья умений) — их ДОЛЖНО быть
        // вложено хотя бы 1 очко, иначе умение недоступно для прокачки, даже если уровень
        // игрока уже достаточен (см. isUnlocked ниже). Пустой список = нет предпосылок (корень ветки).
        public int unlockLevel = 0;
        public List<String> prerequisites = new ArrayList<>();

        public SkillDef(String id, String name, Branch branch, SkillKind kind, String element,
                         String description, List<SkillStat> stats, LinkedHashMap<String, Double> fixed) {
            this.id = id;
            this.name = name;
            this.branch = branch;
            this.kind = kind;
            this.element = element;
            this.description = description;
            this.stats = stats;
            this.fixed = fixed;
        }

        public SkillDef withImage(String imageFile) {
            this.imageFile = imageFile;
            return this;
        }

        public SkillDef withUnlock(int level, String... prereqIds) {
            this.unlockLevel = level;
            this.prerequisites = Arrays.asList(prereqIds);
            return this;
        }

        /** Значения всех масштабируемых статов на уровне level (1..20), в порядке объявления. */
        public LinkedHashMap<String, Double> compute(int level) {
            LinkedHashMap<String, Double> out = new LinkedHashMap<>();
            for (SkillStat s : stats) out.put(s.key, s.at(level));
            return out;
        }
    }

    public static final LinkedHashMap<String, SkillDef> SKILLS = new LinkedHashMap<>();

    static {
        register(warriorSkills());
        register(heraldSkills());
        register(elementalistSkills());
    }

    private static void register(List<SkillDef> defs) {
        for (SkillDef d : defs) SKILLS.put(d.id, d);
    }

    public static List<SkillDef> byBranch(Branch b) {
        List<SkillDef> out = new ArrayList<>();
        for (SkillDef d : SKILLS.values()) if (d.branch == b) out.add(d);
        return out;
    }

    /** Перезарядка умения на уровне level, в секундах — "cooldown" встречается двумя способами:
     *  как масштабируемый по уровню стат (см. warrior_blade_dash — "чем выше уровень, тем короче
     *  КД") или как фиксированное значение в def.fixed (не растёт с уровнем). 0 — умение вообще без
     *  перезарядки (напр. базовый warrior_strike). Используется SkillCaster для гейтинга повторного
     *  каста, см. Player.skillCooldowns. */
    public static double cooldownSeconds(SkillDef def, int level) {
        if (def == null) return 0.0;
        for (SkillStat s : def.stats) {
            if ("cooldown".equals(s.key)) return s.at(level);
        }
        Double fixed = def.fixed.get("cooldown");
        return fixed != null ? fixed : 0.0;
    }

    /** Доступно ли умение для прокачки: уровень игрока не ниже unlockLevel, И на всех умениях-
     *  предпосылках (prerequisites) вложено хотя бы 1 очко. Чистая функция от переданных данных —
     *  не зависит от Player, чтобы её мог использовать и будущий NPC-подбор умений. */
    public static boolean isUnlocked(SkillDef def, int playerLevel, java.util.Map<String, Integer> skillLevels) {
        if (def == null) return false;
        if (playerLevel < def.unlockLevel) return false;
        for (String prereq : def.prerequisites) {
            if (skillLevels.getOrDefault(prereq, 0) < 1) return false;
        }
        return true;
    }

    private static SkillStat stat(String key, String label, String unit, IntToDoubleFunction f) {
        return new SkillStat(key, label, unit, f);
    }

    private static LinkedHashMap<String, Double> fixedOf(Object... kv) {
        LinkedHashMap<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        return m;
    }

    // ── Направление 1: Воитель (8 умений — 7 из ТЗ + базовая атака "Удар", физический ближний бой) ─
    private static List<SkillDef> warriorSkills() {
        List<SkillDef> l = new ArrayList<>();

        // Базовое умение атаки — по умолчанию уже вложено на 1 уровень и привязано (см. Player) —
        // единственное отличие от остальных 7 (из /home/nic/Downloads/skills.txt), которые
        // стартуют невыученными. Табличное значение 100%→150% на L=20 взято как основное (по
        // формуле 5%×(L-1) вышло бы 195% — пользователь подтвердил использовать табличное 150%,
        // отсюда коэффициент 50/19 за уровень, а не 5).
        l.add(new SkillDef("warrior_strike", "Удар", Branch.WARRIOR, SkillKind.ACTIVE, null,
            "Ближняя атака — наносит физический урон.",
            Arrays.asList(
                stat("damage_pct", "Урон", "%", L -> 100 + (50.0 / 19.0) * (L - 1))
            ), fixedOf("mana", 0)).withImage("warrior_0.png").withUnlock(0));

        l.add(new SkillDef("warrior_blade_dash", "Рывок клинка", Branch.WARRIOR, SkillKind.ACTIVE, null,
            "Стремительное сближение с выбранной целью и нанесение физического урона.",
            Arrays.asList(
                stat("damage_pct", "Урон", "%", L -> 100 + 5 * (L - 1)),
                stat("cooldown", "Перезарядка", "сек", L -> 8.0 - 0.2 * (L - 1))
            ), fixedOf("mana", 15)).withImage("warrior_1.png").withUnlock(3, "warrior_strike"));

        l.add(new SkillDef("warrior_shadow_blade", "Теневой клинок", Branch.WARRIOR, SkillKind.ACTIVE, null,
            "Герой пролетает насквозь через группу врагов, нанося урон всем на линии.",
            Arrays.asList(
                stat("damage_pct", "Урон", "%", L -> 120 + 6 * (L - 1)),
                stat("range", "Дальность", "м", L -> 6.0 + 0.2 * (L - 1))
            ), fixedOf("mana", 25, "cooldown", 10)).withImage("warrior_2.png").withUnlock(10, "warrior_blade_dash"));

        l.add(new SkillDef("warrior_death_whirl", "Вихрь смерти", Branch.WARRIOR, SkillKind.SUSTAINED, null,
            "Поддерживаемое вращение, наносящее урон на ходу (тратит ресурс в секунду).",
            Arrays.asList(
                stat("dps_pct", "Урон/сек", "%", L -> 60 + 4 * (L - 1)),
                stat("run_speed_pct", "Скорость бега", "%", L -> 70 + 1.5 * (L - 1)),
                stat("mana_per_sec", "Мана/сек", "", L -> 12 - 0.3 * (L - 1))
            ), fixedOf()).withImage("warrior_3.png").withUnlock(15, "warrior_strike"));

        l.add(new SkillDef("warrior_madness", "Безумие", Branch.WARRIOR, SkillKind.STANCE, null,
            "Активируемый стойка-режим. Даёт +20% к скорости атаки, но тратит здоровье при каждом "
                + "ударе, конвертируя его в дополнительный процентный урон.",
            Arrays.asList(
                stat("damage_bonus_per_hp_pct", "Бонус урона за 1 HP", "%", L -> 10 + 1 * L)
            ), fixedOf("hp_drain_pct", 3, "attack_speed_pct", 20, "mana", 0, "cooldown", 1)).withImage("warrior_4.png").withUnlock(20, "warrior_death_whirl"));

        l.add(new SkillDef("warrior_splash", "Широкий взмах (Сплеш)", Branch.WARRIOR, SkillKind.PASSIVE, null,
            "Автоатаки всегда наносят урон по площади соседним врагам.",
            Arrays.asList(
                stat("splash_damage_pct", "Урон по площади", "%", L -> 30 + 2 * (L - 1))
            ), fixedOf()).withImage("warrior_5.png").withUnlock(5));

        l.add(new SkillDef("warrior_stun", "Тяжелая рука (Оглушение)", Branch.WARRIOR, SkillKind.PASSIVE, null,
            "Шанс оглушить цель при нанесении любого физического урона.",
            Arrays.asList(
                stat("stun_chance_pct", "Шанс оглушения", "%", L -> 5 + 0.75 * (L - 1)),
                stat("stun_duration_sec", "Длительность", "сек", L -> 1.0 + 0.05 * (L - 1))
            ), fixedOf()).withImage("warrior_6.png").withUnlock(9, "warrior_splash"));

        l.add(new SkillDef("warrior_crit", "Смертоносность (Крит)", Branch.WARRIOR, SkillKind.PASSIVE, null,
            "Повышает шанс нанести критический удар.",
            Arrays.asList(
                stat("crit_chance_pct", "Шанс крита", "%", L -> 1.0 * L),
                stat("crit_damage_pct", "Крит. урон", "%", L -> 2.5 * L)
            ), fixedOf()).withImage("warrior_7.png").withUnlock(13, "warrior_stun"));

        return l;
    }

    // ── Направление 2: Вестник (6 аур — см. примечание о расхождении в шапке класса) ───────────
    // Дерево (все 6 аур — ОДНА связная структура, узел "Стальной Воли" — общая точка-мердж, из
    // которой идёт единственная дальнейшая ветка, а не дублируется): Защиты->Стальной Воли,
    // Исцеления->Стальной Воли, Стальной Воли->Уклонения, Оцепенения->Подавления->Уклонения (см.
    // SystemUI — рендер дерева строится напрямую из prerequisites ниже, отдельного списка
    // "веток" для отображения нет).
    private static List<SkillDef> heraldSkills() {
        List<SkillDef> l = new ArrayList<>();

        l.add(new SkillDef("herald_heal", "Аура Исцеления", Branch.HERALD, SkillKind.AURA, null,
            "Накладывает постоянную регенерацию здоровья на героя.",
            Arrays.asList(stat("heal_pct_maxhp_per_sec", "Восстановление HP/сек", "% от макс. HP", L -> 0.5 + 0.1 * L)),
            fixedOf("mana_reserve_pct", 20)).withImage("aura_1.png").withUnlock(3));

        l.add(new SkillDef("herald_defense", "Аура Защиты", Branch.HERALD, SkillKind.AURA, null,
            "Значительно увеличивает показатель физической брони.",
            Arrays.asList(stat("armor_bonus_pct", "Бонус к физ. броне", "%", L -> 10 + 1.5 * L)),
            fixedOf("mana_reserve_pct", 15)).withImage("aura_2.png").withUnlock(1));

        l.add(new SkillDef("herald_evasion", "Аура Уклонения", Branch.HERALD, SkillKind.AURA, null,
            "Снижает рейтинг атаки (шанс попадания) у врагов вокруг.",
            Arrays.asList(stat("enemy_accuracy_reduction_pct", "Снижение точности врагов", "%", L -> 5 + 1.0 * L)),
            fixedOf("mana_reserve_pct", 15)).withImage("aura_3.png").withUnlock(13, "herald_steel_will", "herald_suppression"));

        l.add(new SkillDef("herald_steel_will", "Аура Стальной Воли", Branch.HERALD, SkillKind.AURA, null,
            "Повышает броню и отражает часть полученного физического урона обратно во врага.",
            Arrays.asList(stat("reflect_pct", "Отражение физ. урона", "%", L -> 10 + 1.5 * L)),
            fixedOf("mana_reserve_pct", 20)).withImage("aura_4.png").withUnlock(7, "herald_defense", "herald_heal"));

        l.add(new SkillDef("herald_suppression", "Аура Подавления", Branch.HERALD, SkillKind.AURA, null,
            "Снижает физическое и стихийное сопротивление у всех врагов в области действия.",
            Arrays.asList(stat("defense_shred_pct", "Срез брони/резистов врагов", "%", L -> 10 + 1.25 * L)),
            fixedOf("mana_reserve_pct", 25)).withImage("aura_5.png").withUnlock(7, "herald_stupor"));

        l.add(new SkillDef("herald_stupor", "Аура Оцепенения", Branch.HERALD, SkillKind.AURA, null,
            "Замедляет скорость передвижения и атаки у всех врагов поблизости.",
            Arrays.asList(stat("slow_pct", "Замедление скорости/атаки врагов", "%", L -> 10 + 1.5 * L)),
            fixedOf("mana_reserve_pct", 20)).withImage("aura_6.png").withUnlock(5));

        return l;
    }

    // ── Направление 3: Стихийник (9 умений, по 3 на стихию: Соло / АоЕ / Тактика) ──────────────
    private static List<SkillDef> elementalistSkills() {
        List<SkillDef> l = new ArrayList<>();

        // 🔥 Огонь
        l.add(new SkillDef("elem_fire_ball", "Огненный Шар", Branch.ELEMENTALIST, SkillKind.ACTIVE, "fire",
            "Быстрый снаряд, наносящий прямой урон и поджигающий цель.",
            Arrays.asList(
                stat("direct_damage", "Прямой урон", "", L -> 100 + 15 * (L - 1)),
                stat("burn_dps", "Урон горения/сек (3 сек)", "", L -> 20 + 4 * (L - 1)),
                stat("mana", "Мана", "", L -> 15 + 1 * (L - 1))
            ), fixedOf("cooldown", 0.5)).withImage("mage_1.png").withUnlock(1));

        l.add(new SkillDef("elem_fire_wave", "Волна Огня", Branch.ELEMENTALIST, SkillKind.TACTIC, "fire",
            "Направленный широкий конус пламени, поражающий нескольких врагов перед магом.",
            Arrays.asList(
                stat("cone_damage", "Урон конусом", "", L -> 80 + 12 * (L - 1)),
                stat("burn_dps", "Горение/сек (3 сек)", "", L -> 15 + 3 * (L - 1)),
                stat("angle_deg", "Угол охвата", "°", L -> 45 + 2 * (L - 1)),
                stat("mana", "Мана", "", L -> 30 + 2 * (L - 1))
            ), fixedOf("cooldown", 3.0)).withImage("mage_2.png").withUnlock(5, "elem_fire_ball"));

        l.add(new SkillDef("elem_fire_doom", "ДУМ", Branch.ELEMENTALIST, SkillKind.TACTIC, "fire",
            "Зажигает землю в радиусе вокруг героя. Враги на горящей области получают периодический урон.",
            Arrays.asList(
                stat("dps", "Урон/сек (6 сек)", "", L -> 40 + 10 * (L - 1)),
                stat("radius_m", "Радиус зоны", "м", L -> 3.0 + 0.1 * (L - 1))
            ), fixedOf("mana", 60, "cooldown", 12)).withImage("mage_3.png").withUnlock(10, "elem_fire_wave"));

        // ❄️ Холод
        l.add(new SkillDef("elem_cold_spike", "Ледяной Шип", Branch.ELEMENTALIST, SkillKind.ACTIVE, "cold",
            "Точечный снаряд, наносящий урон и сильно замедляющий цель.",
            Arrays.asList(
                stat("damage", "Урон", "", L -> 80 + 12 * (L - 1)),
                stat("slow_pct", "Замедление", "%", L -> 30 + 1.5 * (L - 1)),
                stat("slow_duration_sec", "Длительность замедления", "сек", L -> 2.0 + 0.1 * (L - 1)),
                stat("mana", "Мана", "", L -> 15 + 1 * (L - 1))
            ), fixedOf("cooldown", 0.8)).withImage("mage_4.png").withUnlock(1));

        l.add(new SkillDef("elem_cold_mist", "Ледяной Туман", Branch.ELEMENTALIST, SkillKind.TACTIC, "cold",
            "Создаёт на местности область тумана. Враги внутри получают постоянный урон и замедляются.",
            Arrays.asList(
                stat("tick_damage", "Урон каждые 0.5 сек (5 сек)", "", L -> 20 + 5 * (L - 1)),
                stat("slow_pct", "Замедление", "%", L -> 20 + 1.5 * (L - 1)),
                stat("radius_m", "Радиус", "м", L -> 4.0 + 0.1 * (L - 1)),
                stat("mana", "Мана", "", L -> 40 + 2 * (L - 1))
            ), fixedOf("cooldown", 8.0)).withImage("mage_5.png").withUnlock(5, "elem_cold_spike"));

        l.add(new SkillDef("elem_cold_fragility", "Хрупкость", Branch.ELEMENTALIST, SkillKind.TACTIC, "cold",
            "Накопительный (stacking) эффект на группе врагов, постепенно разрушающий броню и резисты.",
            Arrays.asList(
                stat("shred_per_stack_pct", "Срез защиты за 1 стак", "%", L -> 1.5 + 0.25 * (L - 1))
            ), fixedOf("max_stacks", 5, "stack_duration_sec", 6, "mana", 30, "cooldown", 4.0)).withImage("mage_6.png").withUnlock(10, "elem_cold_mist"));

        // ⚡ Электро
        l.add(new SkillDef("elem_lightning_chain", "Цепной Разряд", Branch.ELEMENTALIST, SkillKind.ACTIVE, "lightning",
            "Молния бьёт во врага и перескакивает на соседние цели.",
            Arrays.asList(
                stat("base_damage", "Базовый урон", "", L -> 85 + 12 * (L - 1)),
                stat("max_targets", "Макс. целей", "", L -> 3 + Math.floor((L - 1) / 3.0)),
                stat("jump_damage_bonus_pct", "Прирост урона за скачок", "%", L -> 2.0 * L),
                stat("mana", "Мана", "", L -> 20 + 1.5 * (L - 1))
            ), fixedOf("cooldown", 1.5)).withImage("mage_7.png").withUnlock(1));

        l.add(new SkillDef("elem_lightning_storm", "Гроза", Branch.ELEMENTALIST, SkillKind.TACTIC, "lightning",
            "Вызывает шторм в указанной области. Множественные молнии бьют по земле, нанося урон по площади.",
            Arrays.asList(
                stat("strikes_count", "Ударов молний за 4 сек", "", L -> 5 + Math.floor(0.5 * L)),
                stat("strike_damage", "Урон 1 молнии", "", L -> 45 + 8 * (L - 1))
            ), fixedOf("mana", 70, "cooldown", 10.0)).withImage("mage_8.png").withUnlock(5, "elem_lightning_chain"));

        // SkillKind.STANCE — активируемый тумблер (см. SkillCaster.cast: AURA/SUSTAINED/STANCE
        // переключаются через Player.activeAuras), а не разовый эффект, как обычный TACTIC.
        // duration_sec — в исходном ТЗ не было длительности щита вообще; по требованию пользователя
        // добавлена: 60 сек на 1 уровне, 600 сек на 20-м, линейная прогрессия между ними (см.
        // Player.toggleRemainingTime/tickToggleDurations — автоматическое отключение по истечении).
        l.add(new SkillDef("elem_lightning_shield", "Электро-Щит", Branch.ELEMENTALIST, SkillKind.STANCE, "lightning",
            "Активируемый бафф-щит. Поглощает процент входящего урона, расходуя на рассеивание ману вместо здоровья.",
            Arrays.asList(
                stat("absorb_pct", "Поглощение урона", "%", L -> 20 + 3 * (L - 1)),
                stat("mana_cost_per_10_dmg", "Расход маны за 10 урона", "", L -> 5.0 - 0.2 * (L - 1)),
                stat("duration_sec", "Длительность", "сек", L -> 60.0 + (600.0 - 60.0) / 19.0 * (L - 1))
            ), fixedOf("activation_mana", 20, "cooldown", 1.0)).withImage("mage_9.png").withUnlock(10, "elem_lightning_storm"));

        return l;
    }
}
