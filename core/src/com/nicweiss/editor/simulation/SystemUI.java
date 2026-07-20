package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Main;
import com.nicweiss.editor.utils.ItemGenerator;
import com.nicweiss.editor.utils.ItemModifierCatalog;
import com.nicweiss.editor.utils.SkillCatalog;
import com.nicweiss.editor.utils.SkillSlot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Системный интерфейс игрока: ЗАДАНИЯ | ИНВЕНТАРЬ | НАВЫКИ | МЕНЮ
 *
 * Клавиатура:  I → Задания,  Tab → Инвентарь,  K → Навыки,  ESC → Меню
 * Геймпад:     Start → Инвентарь,  LT/RT → вкладки,  ↑↓ / A — навигация меню
 * Мышь:        клик по вкладке / по кнопке меню
 */
public class SystemUI {
    public static Store store;

    // ── Вкладки ───────────────────────────────────────────────────────────────
    public enum Tab { QUESTS, INVENTORY, STATS, SKILLS, DROP, MENU }
    private static final String[] TAB_LABELS = {"ЗАДАНИЯ", "ИНВЕНТАРЬ", "СТАТЫ", "НАВЫКИ", "ДРОП", "МЕНЮ"};

    private boolean isOpen    = false;
    private Tab     activeTab = Tab.INVENTORY;

    // ── Меню-кнопки ───────────────────────────────────────────────────────────
    private static final String[] MENU_ITEMS = {"Сохранить", "Загрузить", "Настройки", "Выход"};
    private int menuFocus = 0;

    // ── Размеры ───────────────────────────────────────────────────────────────
    // Изначально подобрана под EQ_TOTAL_W=550 (см. EQ_SLOTS) с запасом ~16px на сторону (582px).
    // Расширена на четверть (×1.25), т.к. с добавлением вкладки "Дроп" 6 вкладок перестали
    // помещаться по ширине. Сетки инвентаря/снаряжения центрируются формулой (PANEL_W - W)/2 —
    // при расширении панели остаются на месте относительно общего центра, просто с большими полями.
    private static final float PANEL_W  = 582f * 1.25f;
    // Подобрана так, чтобы отступ от ячейки золота до нижнего края панели равнялся PAD —
    // тому же отступу, что сверху между вкладками и слотами снаряжения (см. renderGoldPocket).
    private static final float PANEL_H  = 798f;
    private static final float TAB_H    = 44f;
    private static final float BTN_W    = 240f;
    private static final float BTN_H    = 44f;
    private static final float BTN_GAP  = 12f;

    // ── Инвентарь ────────────────────────────────────────────────────────────
    private static final int CELL     = 47;   // пикселей на ячейку (+30% от 36)
    private static final int INV_COLS = 12;
    private static final int INV_ROWS = 4;
    private static final int INV_GAP  = 40;  // отступ от снаряжения до инвентаря
    private static final int PAD      = 18;  // внутренний отступ панели

    // ── Ячейка золота (под сеткой инвентаря) ────────────────────────────────
    private static final float GOLD_CELL_W = 3 * CELL;
    private static final float GOLD_CELL_H = CELL;
    private static final float GOLD_GAP    = 18f; // отступ от сетки инвентаря (= PAD, для ритма)

    /**
     * Слоты снаряжения в пиксельных координатах: {xPx, yPx, wCells, hCells}.
     * xPx, yPx — позиция от верхнего-левого угла области снаряжения (↓, →).
     * CELL=47px. Все "обычные" зазоры = GAP=16px — КРОМЕ зазоров вокруг Амулета (см. ниже),
     * это единственное несимметричное место в раскладке (по ТЗ).
     *
     * 5 симметричных колонок слева направо (Шлем/Броня/Пояс центрированы внутри Кол.3, амулет
     * не в счёт — см. ниже), Оружие/Кол.2 и Щит/Кол.4 зеркальны относительно центра панели:
     *   Кол.1 (x=0,    w=47 )  — 3 артефакта, у низа колонки, с зазорами между собой
     *   Кол.2 (x=63,   w=94 )  — Оружие (2x4), Перчатки (2x2), по центру высоты колонки
     *   Кол.3 (x=173,  w=188)  — Шлем/Броня/Пояс (94px) центрированы в колонке (x=220, отступ
     *                             (188-94)/2=47), 4 ячейки стека (вплотную) — на всю ширину 188
     *   Кол.4 (x=393,  w=94 )  — Щит (2x4), Сапоги (2x2), по центру высоты колонки
     *   Кол.5 (x=503,  w=47 )  — 3 артефакта (симметрично колонке 1)
     * EQ_TOTAL_W = 503+47 = 550
     *
     * Амулет — единственное исключение из симметрии: отдельная узкая "колонка" между правым краем
     * Шлема (x=220+94=314) и Кол.4, ближе к Шлему и дальше от Щита (GAP_AMULET_NEAR=8 < GAP=16 <
     * GAP_AMULET_FAR=24): xAmulet = 314 + 8 = 322;  xКол.4 = 322 + 47 + 24 = 393.
     * ВАЖНО: считается от РЕАЛЬНОГО края Шлема (314), а не от края бокса Кол.3 (361) — иначе
     * амулет визуально отрывается от Шлема на полный отступ центрирования (47px лишних).
     * По вертикали — рядом со Шлемом, но ниже его середины (yAmulet=47=94/2 — верх амулета
     * вровень с серединой Шлема, низ — вровень с его нижним краем, см. ТЗ "сбоку и снизу от шлема").
     *
     * Колонка 3 (самая высокая, задаёт EQ_TOTAL_H): Шлем(94) + GAP + Броня(188) + GAP +
     * Пояс(47) + GAP + Стеки(47) = 94+16+188+16+47+16+47 = 424 → EQ_TOTAL_H=424.
     * Колонки 2/4 (298 высотой: Оружие/Щит 188 + GAP + Перчатки/Сапоги 94) центрируются по
     * вертикали в 424 → отступ (424-298)/2=63. Колонки 1/5 (173: 3×47 + 2×16) прижаты к низу →
     * старт y=424-173=251 (шаг между артефактами 47+16=63 — совпадает с рядами Пояс/Стеки).
     *
     * Ячейки стека (см. StackManager) НЕ входят в equipmentSlots — это отдельная механика
     * (store.stacks), рисуются/хит-тестятся отдельно (см. STACK_X/Y, drawStackRow).
     */
    private static final int EQ_TOTAL_W = 550;
    private static final int EQ_TOTAL_H = 424;

    private static final int[][] EQ_SLOTS = {
        // {xPx, yPx, wCells, hCells}   CELL=47, GAP=16 (кроме Амулета, см. комментарий выше)
        { 63,  63, 2, 4},  //  0  Оружие
        { 63, 267, 2, 2},  //  1  Перчатки

        {227,   0, 2, 2},  //  2  Шлем           центрирован в колонке 3 (188px), сдвиг (188-94)/2=47
        {329,  47, 1, 1},  //  3  Амулет         вплотную к правому краю Шлема (314+8), ниже середины
        {227, 110, 2, 4},  //  4  Броня          центрирован
        {227, 314, 2, 1},  //  5  Пояс           центрирован

        {  0, 251, 1, 1},  //  6  Арт. 1 (лев.)
        {  0, 314, 1, 1},  //  7  Арт. 2 (лев.)
        {  0, 377, 1, 1},  //  8  Арт. 3 (лев.)
        {503, 251, 1, 1},  //  9  Арт. 4 (прав.)
        {503, 314, 1, 1},  // 10  Арт. 5 (прав.)
        {503, 377, 1, 1},  // 11  Арт. 6 (прав.)

        {393,  63, 2, 4},  // 12  Щит
        {393, 267, 2, 2},  // 13  Сапоги
    };
    private static final String[] EQ_NAMES = {
        "Оружие","Перчатки","Шлем","Амулет","Броня","Пояс",
        "","","","","","",
        "Щит","Сапоги"
    };

    // Ряд из 4 ячеек стека (см. StackManager) — вплотную друг к другу, под поясом в колонке 3.
    private static final int STACK_X = 181, STACK_Y = 377;
    private static final int STACK_COUNT = 4;

    // Базовое количество доступных контейнеров-артефактов даже без пояса (см. ТЗ: "по умолчанию
    // сразу доступно 2 контейнера — сделать базовым значением у игрока").
    private static final int BASE_CONTAINERS = 2;

    // ── Цвета ─────────────────────────────────────────────────────────────────
    private static final Color C_SLOT_BG   = new Color(0.04f, 0.05f, 0.07f, 1f);
    private static final Color C_SLOT_LINE = new Color(0.22f, 0.27f, 0.35f, 1f);
    private static final Color C_BG        = new Color(0.06f, 0.07f, 0.10f, 0.94f);
    private static final Color C_TAB_ACT   = new Color(0.18f, 0.22f, 0.30f, 1f);
    private static final Color C_TAB_IDLE  = new Color(0.09f, 0.10f, 0.14f, 1f);
    private static final Color C_TAB_LINE  = new Color(0.50f, 0.75f, 1.00f, 1f);
    private static final Color C_BORDER    = new Color(0.28f, 0.33f, 0.42f, 1f);
    private static final Color C_CONTENT   = new Color(0.08f, 0.10f, 0.14f, 1f);
    private static final Color C_BTN       = new Color(0.13f, 0.15f, 0.20f, 1f);
    private static final Color C_BTN_FOCUS = new Color(0.18f, 0.22f, 0.30f, 1f);
    private static final Color C_FOCUS_OUT = new Color(0.55f, 0.80f, 1.00f, 1f);
    private static final Color C_TEXT      = new Color(0.85f, 0.88f, 0.95f, 1f);
    private static final Color C_TEXT_DIM  = new Color(0.45f, 0.50f, 0.60f, 1f);
    private static final Color C_TEXT_ACT  = new Color(1.00f, 1.00f, 1.00f, 1f);
    private static final Color C_HIGHLIGHT_OK   = new Color(0.10f, 0.85f, 0.20f, 0.40f); // зелёный — можно надеть
    private static final Color C_HIGHLIGHT_WARN = new Color(0.90f, 0.50f, 0.05f, 0.45f); // рыжий — тип подходит, нет статов
    private static final Color C_HIGHLIGHT_BAD  = new Color(0.90f, 0.15f, 0.10f, 0.40f); // красный — тип не совпадает
    private static final Color C_TOOLTIP_BG    = new Color(0f,    0f,    0f,    0.80f);

    // ── Геймпад edge-detect ───────────────────────────────────────────────────
    private boolean prevLT = false, prevRT = false, prevStart = false, prevB = false, prevBX = false;
    private boolean prevY = false, prevAGp = false;

    // ── Сравнение предметов ───────────────────────────────────────────────────
    private boolean compareMode = false;      // Shift удержан / Y на геймпаде
    private float   holdATimer  = 0f;         // сколько A уже зажата
    private boolean holdAFired  = false;      // долгое нажатие A уже сработало
    private static final float HOLD_A_SWAP = 0.3f; // секунд до быстрой замены/укладки в стек

    // ── Прокачка с геймпада (вкладка Статы) ──────────────────────────────────
    // Фокус среди 3 качаемых статов (0=Сила,1=Магия,2=Ловкость) — двигается D-pad'ом
    // (см. gamepadNavigate), не стиком (стик на этой вкладке уже скроллит список).
    private int statsFocusIndex = 0;
    private float   statsHoldATimer = 0f;
    private boolean statsHoldAFired = false;
    private static final int MULTI_SPEND_AMOUNT = 5; // очков за удержание A / Shift+клик

    // ── Вкладка Навыки ────────────────────────────────────────────────────────
    private static final float SKILLS_SUBTAB_H = 36f;
    private static final float SKILLS_SQUARE = 64f;
    private static final float SKILLS_ROW_GAP = 46f; // вертикальный зазор между строками дерева
    private static final float SKILL_PLUS_BTN_SIZE = 30f; // крупнее, чем STAT_BTN_SIZE на вкладке Статы
    // Цвет "ветки" дерева — крупные пунктирные точки между иконками (см. drawBranchConnector).
    // Тусклее (dimColor), если умение-потомок ещё не разблокировано — визуально видно, куда ведёт
    // ветка, даже если открыть её пока нельзя.
    private static final Color C_TREE_LINE     = new Color(0.45f, 0.60f, 0.85f, 0.95f);
    private static final Color C_TREE_LINE_DIM = new Color(0.30f, 0.34f, 0.40f, 0.70f);
    // Умение с вложенными очками, но временно недоступное для прокачки (см. drawSkillSquare) —
    // при наведении курсора подсвечивается этим оранжевым, вместо обычного полного цвета.
    private static final Color C_SKILL_HOVER_LOCKED = new Color(1f, 0.55f, 0.15f, 1f);

    private int skillsSubtab = 0; // 0=Воитель,1=Вестник,2=Стихийник
    // Фокус геймпада/навигации — координаты в ГРАФЕ текущей подвкладки (см. rowsFor): skillsFocusRow —
    // индекс Y-лэйна (строки), skillsFocusCol — позиция умения внутри этого лэйна (отсортированы по
    // требуемому уровню, то же самое, что и порядок по X, см. renderSkills).
    private int skillsFocusRow = 0;
    private int skillsFocusCol = 0;
    private float   skillsHoldATimer = 0f;
    private boolean skillsHoldAFired = false;
    // Хит-тест квадратов умений — {x,y,w,h,row,col}, пересобирается каждый renderSkills. Клик по
    // квадрату (ПКМ) открывает привязку; клик по красной кнопке "+" (ЛКМ, см. skillPlusHotspots) —
    // тратит очко.
    private final java.util.List<float[]> skillsHotspots = new java.util.ArrayList<>();
    // Хит-тест кнопок "+" — {x,y,w,h,row,col}, рисуются только пока есть нераспределённые очки
    // И умение разблокировано (см. isSkillUnlocked).
    private final java.util.List<float[]> skillPlusHotspots = new java.util.ArrayList<>();

    // ── Дерево умений — НАСТОЯЩИЙ граф, узел на умение строго один (общий предок нескольких
    // цепочек рисуется ОДИН раз, а не дублируется на каждой ветке). Рёбра берутся напрямую из
    // SkillCatalog.SkillDef.prerequisites — отдельного списка "веток для отображения" больше нет,
    // каталог и рендер используют одни и те же данные. X-координата узла — % от требуемого уровня
    // умения относительно максимального требуемого уровня в подвкладке (см. renderSkills). Y-лэйн —
    // ручная раскладка ниже (нужна, т.к. в графе есть и форки (1 родитель → неск. детей, напр.
    // "Удар"), и мерджи (неск. родителей → 1 ребёнок, напр. "Аура Уклонения"/"Аура Стальной Воли") —
    // общий алгоритм авторазметки DAG избыточен для 6-9 умений на подвкладку. Дробные значения
    // (0.5) — узел-развилка, зажатый между двумя лэйнами своих потомков/предков.
    private static final Map<String, Float> SKILL_LANE = new HashMap<>();
    static {
        // Воитель: "Удар" — общий предок 2 цепочек (blade_dash.. и death_whirl..), поэтому его
        // лэйн — посередине между ними; "Широкий взмах" — независимая 3-я цепочка.
        SKILL_LANE.put("warrior_strike", 0.5f);
        SKILL_LANE.put("warrior_blade_dash", 0f);
        SKILL_LANE.put("warrior_shadow_blade", 0f);
        SKILL_LANE.put("warrior_death_whirl", 1f);
        SKILL_LANE.put("warrior_madness", 1f);
        SKILL_LANE.put("warrior_splash", 2f);
        SKILL_LANE.put("warrior_stun", 2f);
        SKILL_LANE.put("warrior_crit", 2f);
        // Вестник: Защиты и Исцеления — оба родители Стальной Воли (мердж); дальше Стальной Воли
        // ведёт к Уклонению; Оцепенения->Подавления->Уклонения — отдельная цепочка, сливающаяся
        // со Стальной Волей в Уклонении (второй мердж).
        SKILL_LANE.put("herald_defense", 0f);
        SKILL_LANE.put("herald_steel_will", 0.5f);
        SKILL_LANE.put("herald_heal", 1f);
        SKILL_LANE.put("herald_evasion", 1f);
        SKILL_LANE.put("herald_stupor", 2f);
        SKILL_LANE.put("herald_suppression", 2f);
        // Стихийник: 3 независимые цепочки, по одной на стихию.
        SKILL_LANE.put("elem_fire_ball", 0f);
        SKILL_LANE.put("elem_fire_wave", 0f);
        SKILL_LANE.put("elem_fire_doom", 0f);
        SKILL_LANE.put("elem_cold_spike", 1f);
        SKILL_LANE.put("elem_cold_mist", 1f);
        SKILL_LANE.put("elem_cold_fragility", 1f);
        SKILL_LANE.put("elem_lightning_chain", 2f);
        SKILL_LANE.put("elem_lightning_storm", 2f);
        SKILL_LANE.put("elem_lightning_shield", 2f);
    }
    private static float laneOf(String skillId) { return SKILL_LANE.getOrDefault(skillId, 0f); }

    private static SkillCatalog.Branch branchFor(int subtab) {
        return subtab == 0 ? SkillCatalog.Branch.WARRIOR
             : subtab == 1 ? SkillCatalog.Branch.HERALD
             :                SkillCatalog.Branch.ELEMENTALIST;
    }

    /** Умения подвкладки как настоящий граф, сгруппированный на "строки" по Y-лэйну (см.
     *  SKILL_LANE) — нужно только для 2D-навигации геймпада (skillsNavigate/handleSkills*Click),
     *  каждое умение встречается РОВНО один раз (см. класс-комментарий у SKILL_LANE). Внутри
     *  строки узлы отсортированы по требуемому уровню — тем же порядком, что и по X (см.
     *  renderSkills). */
    private List<List<SkillCatalog.SkillDef>> rowsFor(int subtab) {
        List<SkillCatalog.SkillDef> defs = SkillCatalog.byBranch(branchFor(subtab));
        java.util.List<Float> lanes = new java.util.ArrayList<>();
        for (SkillCatalog.SkillDef d : defs) {
            float l = laneOf(d.id);
            if (!lanes.contains(l)) lanes.add(l);
        }
        java.util.Collections.sort(lanes);
        java.util.List<List<SkillCatalog.SkillDef>> rows = new java.util.ArrayList<>();
        for (float lane : lanes) {
            java.util.List<SkillCatalog.SkillDef> row = new java.util.ArrayList<>();
            for (SkillCatalog.SkillDef d : defs) if (laneOf(d.id) == lane) row.add(d);
            row.sort((a, b) -> Integer.compare(a.unlockLevel, b.unlockLevel));
            rows.add(row);
        }
        return rows;
    }

    // ── Окно привязки умения к кнопке (модальное поверх вкладки Навыки) ────────
    private boolean pickerOpen = false;
    private String  pickerSkillId = null;
    private int pickerFocusRow = 0; // 0=основной,1=комбо
    private int pickerFocusCol = 0; // 0-5
    private boolean captureMode = false;
    private float   captureTimer = 0f;
    private static final float CAPTURE_TIMEOUT = 10f;
    private final java.util.List<float[]> pickerHotspots = new java.util.ArrayList<>(); // {x,y,w,h,row,col}

    // ── Кэшированная геометрия панели (обновляется каждый render) ────────────
    private float _px = 0, _py = 0;
    private float _invGridX = 0, _invTop = 0;
    private float _eqGridX  = 0, _eqTop  = 0;

    // ── Курсор геймпада (LibGDX Y-up, экранные пиксели) ──────────────────────
    private float gpX = 0f, gpY = 0f;
    private static final float GP_SPEED = 500f;
    private boolean gpInitialized = false;

    // ── Скролл вкладки статов ────────────────────────────────────────────────
    private float statsScroll = 0f;
    private static final float STATS_SCROLL_SPEED = 300f;
    private static final float STATS_LINE_H       = 24f;
    private static final float STATS_PAD          = 16f;

    // ── Скролл вкладки дропа (те же размеры строк/паддинга, что у статов) ──────
    private float dropScroll = 0f;

    // ── Буфер переноса ───────────────────────────────────────────────────────
    // Предмет в буфере ВСЕГДА вне store.inventory и store.equipmentSlots.
    // Подобран → удалён из источника. Положен → добавлен в цель. Просто.
    private LinkedHashMap draggedItem = null;

    // ── GL-ресурсы ────────────────────────────────────────────────────────────
    private final Texture    pixel;
    private final BitmapFont font;
    private final GlyphLayout layout;

    // Кэш текстур иконок предметов по пути __image__ — чтобы не грузить PNG заново каждый кадр.
    private static final Map<String, Texture> iconCache = new HashMap<>();

    public SystemUI() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        pixel  = new Texture(pm);
        pm.dispose();
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("Fonts/Roboto-Medium.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = 18;
        p.color = Color.WHITE;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS
            + "абвгдеёжзийклмнопрстуфхцчшщъьыэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЬЫЭЮЯ";
        font = gen.generateFont(p);
        gen.dispose();
        layout = new GlyphLayout();
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    public boolean isOpen()    { return isOpen; }
    public boolean isMenuOpen(){ return isOpen && activeTab == Tab.MENU; }
    public boolean isStatsOpen(){ return isOpen && activeTab == Tab.STATS; }
    public boolean isSkillsOpen(){ return isOpen && activeTab == Tab.SKILLS; }
    /** true — открыто окно привязки умения и сейчас идёт ожидание нажатия клавиши/кнопки (см.
     *  openKeybindPicker/tick) — SimulationInputThread должен "проглатывать" обычный ввод. */
    public boolean isCapturingKeybind() { return pickerOpen && captureMode; }

    /** Синхронизирует edge-detect поля геймпада (prevBX/prevAGp) под ТЕКУЩЕЕ физическое состояние
     *  кнопок — вызывается SimulationInputThread СРАЗУ после выхода из режима захвата (см.
     *  isCapturingKeybind), пока pollGamepad несколько кадров вообще не вызывался. Без этого, если
     *  игрок ещё держит кнопку, которой только что забиндил умение (например X), она читалась бы
     *  как "новое нажатие" по устаревшему prevBX=false и сразу открывала бы НОВОЕ окно привязки. */
    public void syncGamepadButtonState(boolean bx, boolean a) {
        prevBX = bx;
        prevAGp = a;
    }

    /** Клавиша поглощена → возвращает true */
    public boolean handleKeyDown(int keyCode) {
        if (keyCode == 37)  { toggle(Tab.QUESTS);    return true; } // I
        if (keyCode == 39)  { toggle(Tab.SKILLS);    return true; } // K
        if (keyCode == 61)  { toggle(Tab.INVENTORY); return true; } // Tab
        if (keyCode == 111) { toggle(Tab.MENU);      return true; } // ESC
        return false;
    }

    /** Клик мышью. button — libGDX-код кнопки (0=ЛКМ, 1=ПКМ). @return true — поглощён */
    public boolean handleClick(float mx, float my, float sw, float sh, int button) {
        if (!isOpen) return false;
        store.isGamepadMode = false;

        // Окно привязки умения — модальное, поверх остального содержимого (см. openKeybindPicker).
        // Выбор ячейки — любой кнопкой, различие ЛКМ/ПКМ тут не нужно.
        if (pickerOpen) return handlePickerClick(mx, my, sw, sh);

        float px = (sw - PANEL_W) / 2f, py = (sh - PANEL_H) / 2f;

        // Клик вне панели
        if (mx < px || mx > px + PANEL_W || my < py || my > py + PANEL_H) {
            if (draggedItem != null) {
                dropDraggedToGround(); // выбрасываем предмет, но окно не закрываем
            } else {
                isOpen = false;
            }
            return false;
        }

        // Клик по вкладкам
        float tabY = py + PANEL_H - TAB_H;
        float tabW = PANEL_W / TAB_LABELS.length;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            if (mx >= px + i*tabW && mx <= px + (i+1)*tabW && my >= tabY && my <= tabY + TAB_H) {
                activeTab = Tab.values()[i];
                return true;
            }
        }

        // Клик по кнопкам меню (только ЛКМ)
        if (activeTab == Tab.MENU && button == 0) {
            float cx = px + (PANEL_W - BTN_W) / 2f;
            for (int i = 0; i < MENU_ITEMS.length; i++) {
                float by = menuButtonY(i, py, PANEL_H - TAB_H);
                if (mx >= cx && mx <= cx + BTN_W && my >= by && my <= by + BTN_H) {
                    menuFocus = i;
                    activateMenuItem(i);
                    return true;
                }
            }
        }

        // Клик по кнопкам "+" вкладки Статы (левел-ап/распределение очков) — только ЛКМ
        if (activeTab == Tab.STATS && button == 0 && handleStatsButtonClick(mx, my)) return true;

        // Вкладка Навыки: ПКМ по квадрату — открыть окно привязки (вне зависимости от очков);
        // ЛКМ — подвкладки/кнопка "+" (трата очков).
        if (activeTab == Tab.SKILLS) {
            if (button == 1) {
                handleSkillsRmbClick(mx, my);
                return true;
            }
            if (handleSkillsSubtabClick(mx, my, px, py)) return true;
            if (handleSkillsButtonClick(mx, my)) return true;
        }

        // Клик в зоне инвентаря (только ЛКМ)
        if (activeTab == Tab.INVENTORY && button == 0) {
            boolean shift = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                         || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
            if (shift && draggedItem == null) quickActionAt(mx, my);
            else                              tryInteractAt(mx, my);
        }

        return true;
    }

    /** Опрос кнопок геймпада. stickX/Y — левый стик [-1..1]. a/y/bx — кнопки A, Y, X. */
    public void pollGamepad(boolean start, boolean lt, boolean rt, boolean b,
                            boolean bx, boolean y, boolean a, float stickX, float stickY) {
        float dt = Gdx.graphics.getDeltaTime();

        if (start && !prevStart) {
            if (isOpen) { compareMode = false; cancelDrag(); isOpen = false; }
            else        { toggle(Tab.INVENTORY); }
        }
        if (lt && !prevLT && isOpen) switchTab(-1);
        if (rt && !prevRT && isOpen) switchTab(+1);
        if (b  && !prevB  && isOpen) { compareMode = false; cancelDrag(); isOpen = false; }

        // X — выбросить перетаскиваемый предмет на землю (короткое нажатие, буфер не пуст)
        if (bx && !prevBX && isOpen && draggedItem != null) dropDraggedToGround();

        // Вкладка Навыки: X (короткое нажатие, вне пикера) — открыть окно привязки для
        // сфокусированного умения, вне зависимости от очков (аналог ПКМ мышью).
        if (bx && !prevBX && isOpen && activeTab == Tab.SKILLS && !pickerOpen) {
            openPickerForFocusedSkill();
        }

        // Y — переключить режим сравнения (инвентарь) / скролл вниз (статы)
        if (y && !prevY && isOpen && activeTab == Tab.INVENTORY) compareMode = !compareMode;

        // Скролл вкладки статов левым стиком
        if (isOpen && activeTab == Tab.STATS && Math.abs(stickY) > 0.12f) {
            statsScroll = Math.max(0, statsScroll - stickY * STATS_SCROLL_SPEED * dt);
        }
        // Скролл вкладки дропа левым стиком (направление инвертировано относительно статов —
        // по требованию пользователя).
        if (isOpen && activeTab == Tab.DROP && Math.abs(stickY) > 0.12f) {
            dropScroll = Math.max(0, dropScroll + stickY * STATS_SCROLL_SPEED * dt);
        }

        if (isOpen && activeTab == Tab.INVENTORY) {
            float contentH = PANEL_H - TAB_H - 1;
            if (Math.abs(stickX) > 0.12f) gpX += stickX * GP_SPEED * dt;
            if (Math.abs(stickY) > 0.12f) gpY -= stickY * GP_SPEED * dt;
            gpX = Math.max(_px, Math.min(_px + PANEL_W - CELL, gpX));
            gpY = Math.max(_py, Math.min(_py + contentH - CELL, gpY));

            // A: долгое удержание — быстрое действие над наведённым предметом (расходники — в
            // стек, остальное — быстрый своп со сравниваемым, см. quickActionAt); короткое —
            // обычный подбор.
            if (a) {
                holdATimer += dt;
                if (!holdAFired && holdATimer >= HOLD_A_SWAP) {
                    quickActionAt(gpX + CELL * 0.5f, gpY + CELL * 0.5f);
                    holdAFired = true;
                }
            } else {
                if (prevAGp && !holdAFired) {
                    // Отпускание A без долгого удержания — обычное взаимодействие
                    tryInteractAt(gpX + CELL * 0.5f, gpY + CELL * 0.5f);
                }
                holdATimer = 0f;
                holdAFired = false;
            }
        } else {
            holdATimer = 0f; holdAFired = false;
        }

        // Вкладка Статы: A на сфокусированной (D-pad'ом, см. gamepadNavigate) строке качаемого
        // стата — короткое нажатие тратит 1 очко, удержание (тот же порог, что и в инвентаре) —
        // сразу MULTI_SPEND_AMOUNT очков (по требованию: "по одному вводить неудобно").
        if (isOpen && activeTab == Tab.STATS) {
            if (a) {
                statsHoldATimer += dt;
                if (!statsHoldAFired && statsHoldATimer >= HOLD_A_SWAP) {
                    spendStatPoints(statsFocusIndex, MULTI_SPEND_AMOUNT);
                    statsHoldAFired = true;
                }
            } else {
                if (prevAGp && !statsHoldAFired) {
                    spendStatPoints(statsFocusIndex, 1);
                }
                statsHoldATimer = 0f;
                statsHoldAFired = false;
            }
        } else {
            statsHoldATimer = 0f; statsHoldAFired = false;
        }

        // Вкладка Навыки: если открыт пикер привязки — A подтверждает выбор ячейки (запускает
        // ожидание нажатия кнопки, см. pickerConfirm). Иначе — короткое A тратит 1 очко умения,
        // удержание — MULTI_SPEND_AMOUNT очков разом (открыть пикер — X, см. выше).
        if (isOpen && activeTab == Tab.SKILLS) {
            if (pickerOpen) {
                if (a && !prevAGp && !captureMode) pickerConfirm();
            } else if (a) {
                skillsHoldATimer += dt;
                if (!skillsHoldAFired && skillsHoldATimer >= HOLD_A_SWAP) {
                    gamepadActivateFocusedSkill(MULTI_SPEND_AMOUNT);
                    skillsHoldAFired = true;
                }
            } else {
                if (prevAGp && !skillsHoldAFired) {
                    gamepadActivateFocusedSkill(1);
                }
                skillsHoldATimer = 0f;
                skillsHoldAFired = false;
            }
        } else {
            skillsHoldATimer = 0f; skillsHoldAFired = false;
        }

        prevStart = start; prevLT = lt; prevRT = rt; prevB = b; prevBX = bx;
        prevY = y; prevAGp = a;
    }

    /** Скролл колёсиком мыши (amountY > 0 = вниз). */
    public boolean handleScroll(float amountY) {
        if (!isOpen) return false;
        if (activeTab == Tab.STATS) {
            statsScroll = Math.max(0, statsScroll + amountY * STATS_LINE_H * 3);
            return true;
        }
        if (activeTab == Tab.DROP) {
            // Направление инвертировано относительно статов — по требованию пользователя.
            dropScroll = Math.max(0, dropScroll - amountY * STATS_LINE_H * 3);
            return true;
        }
        return false;
    }

    /** Навигация по кнопкам меню (D-pad / стрелки) — либо по строкам качаемых статов на вкладке
     *  Статы, либо по строкам сетки умений/ячеек пикера на вкладке Навыки. */
    public void gamepadNavigate(int dir) {
        if (isMenuOpen()) {
            menuFocus = (menuFocus + dir + MENU_ITEMS.length) % MENU_ITEMS.length;
            return;
        }
        if (isOpen && activeTab == Tab.STATS) {
            statsFocusIndex = (statsFocusIndex + dir + 3) % 3;
            return;
        }
        if (isSkillsOpen()) {
            if (pickerOpen) pickerNavigate(dir, 0);
            else            skillsNavigate(dir, 0);
        }
    }

    /** Навигация по СТОЛБЦАМ (D-pad Left/Right) — только вкладка Навыки (сетка умений/пикер),
     *  остальные вкладки этой оси не используют. */
    public void gamepadNavigateCol(int dir) {
        if (isSkillsOpen()) {
            if (pickerOpen) pickerNavigate(0, dir);
            else            skillsNavigate(0, dir);
        }
    }

    /** Активация кнопки в фокусе (кнопка A) — только для MENU вкладки.
     *  INVENTORY обрабатывается внутри pollGamepad (различие короткого/долгого нажатия). */
    public void gamepadActivate() {
        if (!isOpen) return;
        if (activeTab == Tab.MENU) activateMenuItem(menuFocus);
        // INVENTORY: handled in pollGamepad via hold-A detection
    }

    // ── Рендер ────────────────────────────────────────────────────────────────

    public void render(SpriteBatch batch, float sw, float sh) {
        if (!isOpen) return;
        float px = (sw - PANEL_W) / 2f, py = (sh - PANEL_H) / 2f;

        // Рамка + фон
        col(batch, C_BORDER);
        batch.draw(pixel, px-1, py-1, PANEL_W+2, PANEL_H+2);
        col(batch, C_BG);
        batch.draw(pixel, px, py, PANEL_W, PANEL_H);

        renderTabs(batch, px, py);
        renderContent(batch, px, py);
        batch.setColor(1,1,1,1);

        if (pickerOpen) renderKeybindPicker(batch, sw, sh);
    }

    private void renderTabs(SpriteBatch batch, float px, float py) {
        float tabW = PANEL_W / TAB_LABELS.length;
        float tabY = py + PANEL_H - TAB_H;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            boolean active = Tab.values()[i] == activeTab;

            col(batch, active ? C_TAB_ACT : C_TAB_IDLE);
            batch.draw(pixel, px + i*tabW, tabY, tabW - 1, TAB_H);

            // Цветная линия снизу активной вкладки
            if (active) {
                col(batch, C_TAB_LINE);
                batch.draw(pixel, px + i*tabW, tabY, tabW-1, 2f);
            }

            // Текст вкладки
            font.setColor(active ? C_TEXT_ACT : C_TEXT_DIM);
            layout.setText(font, TAB_LABELS[i]);
            font.draw(batch, TAB_LABELS[i],
                px + i*tabW + (tabW - layout.width)/2f,
                tabY + (TAB_H + layout.height)/2f);
        }

        // Разделительная линия
        col(batch, C_BORDER);
        batch.draw(pixel, px, tabY, PANEL_W, 1);
    }

    private void renderContent(SpriteBatch batch, float px, float py) {
        float cH = PANEL_H - TAB_H - 1;
        col(batch, C_CONTENT);
        batch.draw(pixel, px, py, PANEL_W, cH);

        switch (activeTab) {
            case QUESTS:    renderPlaceholder(batch, px, py, cH, "Активных заданий нет."); break;
            case INVENTORY: renderInventory(batch, px, py, cH);                            break;
            case STATS:     renderStats(batch, px, py, cH);                                break;
            case DROP:      renderDrop(batch, px, py, cH);                                 break;
            case SKILLS:    renderSkills(batch, px, py, cH);                                break;
            case MENU:      renderMenu(batch, px, py, cH);                                  break;
        }
    }

    private void renderPlaceholder(SpriteBatch batch, float px, float py, float cH, String text) {
        font.setColor(C_TEXT_DIM);
        font.draw(batch, text, px + 24f, py + cH - 24f);
    }

    private void renderMenu(SpriteBatch batch, float px, float py, float cH) {
        float cx = px + (PANEL_W - BTN_W) / 2f;

        for (int i = 0; i < MENU_ITEMS.length; i++) {
            float by     = menuButtonY(i, py, cH);
            boolean focus = (i == menuFocus);

            // Фон кнопки
            col(batch, focus ? C_BTN_FOCUS : C_BTN);
            batch.draw(pixel, cx, by, BTN_W, BTN_H);

            // Окантовка фокуса — заметная, двойная
            if (focus) {
                col(batch, C_FOCUS_OUT);
                rect(batch, cx, by, BTN_W, BTN_H);
                rect(batch, cx+2, by+2, BTN_W-4, BTN_H-4); // внутренняя обводка
            }

            // Текст
            font.setColor(focus ? C_TEXT_ACT : C_TEXT);
            layout.setText(font, MENU_ITEMS[i]);
            font.draw(batch, MENU_ITEMS[i],
                cx + (BTN_W - layout.width) / 2f,
                by + (BTN_H + layout.height) / 2f);
        }
    }

    // ── Прокачка (кнопки на вкладке Статы) ─────────────────────────────────────
    private static final float STAT_BTN_SIZE = 22f;
    private static final Color C_XP_BTN   = new Color(0.20f, 0.45f, 0.95f, 1f); // синяя — левел-ап
    private static final Color C_STAT_BTN  = new Color(0.85f, 0.20f, 0.20f, 1f); // красная — потратить очко стата
    private static final Color C_UNSPENT_MSG = new Color(0.95f, 0.80f, 0.25f, 1f);

    private static final int HOTSPOT_XP = 0, HOTSPOT_STR = 1, HOTSPOT_MAGIC = 2, HOTSPOT_DEX = 3;
    // Хитбоксы кнопок "+" вкладки Статы — {x, y, w, h, tag}, пересчитываются каждый кадр в
    // renderStats (только для реально отрисованных, видимых кнопок) — см. handleClick.
    private final java.util.List<float[]> statsHotspots = new java.util.ArrayList<>();

    private void renderStats(SpriteBatch batch, float px, float py, float cH) {
        if (store.player == null) return;
        Player p = store.player;
        recomputePlayerStats();
        statsHotspots.clear();

        // ── Заголовок: уровень (крупно, отдельным нескроллящимся блоком сверху) ─────────────
        boolean showUnspentMsg = p.unspentStatPoints > 0;
        float levelRowH = 40f;
        float msgRowH   = showUnspentMsg ? 24f : 0f;
        float headerH   = levelRowH + msgRowH + STATS_PAD;
        float headerTop = py + cH;

        font.getData().setScale(1.5f);
        layout.setText(font, "Уровень " + p.level);
        font.setColor(C_TEXT_ACT);
        float levelTextY = headerTop - STATS_PAD - (levelRowH - layout.height) / 2f;
        font.draw(batch, "Уровень " + p.level, px + STATS_PAD, levelTextY);
        float xpBtnX = px + STATS_PAD + layout.width + 14f;
        font.getData().setScale(1f);

        // Кнопка "+" — начисляет РОВНО столько опыта, сколько нужно для перехода на след. уровень
        // (см. handleClick/handleStatsButtonClick): 1 клик = гарантированно 1 левел-ап, без остатка.
        float xpBtnY = levelTextY - layout.height * 0.3f;
        col(batch, C_XP_BTN);
        batch.draw(pixel, xpBtnX, xpBtnY, STAT_BTN_SIZE, STAT_BTN_SIZE);
        font.setColor(C_TEXT_ACT);
        layout.setText(font, "+");
        font.draw(batch, "+", xpBtnX + (STAT_BTN_SIZE - layout.width) / 2f, xpBtnY + (STAT_BTN_SIZE + layout.height) / 2f);
        statsHotspots.add(new float[]{xpBtnX, xpBtnY, STAT_BTN_SIZE, STAT_BTN_SIZE, HOTSPOT_XP});

        if (showUnspentMsg) {
            font.setColor(C_UNSPENT_MSG);
            String msg = "Нераспределено очков статов: " + p.unspentStatPoints;
            font.draw(batch, msg, px + STATS_PAD, headerTop - STATS_PAD - levelRowH);
        }

        col(batch, C_BORDER);
        batch.draw(pixel, px, headerTop - headerH, PANEL_W, 1);

        // ── Остальное содержимое — прокачиваемые статы + все прочие, скроллится отдельно ────
        float listCH = cH - headerH;

        // Собираем строки: {label, value, color_r, color_g, color_b, kind}
        // kind: null — обычная строка, STR/MAGIC/DEX — прокачиваемый стат (красная кнопка "+",
        // только пока есть нераспределённые очки), "" (пустая строка) — отступ-разделитель секций.
        java.util.List<String[]> lines = new java.util.ArrayList<>();

        // ── Прокачиваемые статы ──────────────────────────────────────────────
        // entry[1] тут не используется для отображения (значение собирается из base/bonus прямо
        // в цикле рендера — см. ниже), но оставлен для единообразия структуры строки.
        lines.add(new String[]{"Сила",     "", "1","1","1", "STR"});
        lines.add(new String[]{"Магия",    "", "1","1","1", "MAGIC"});
        lines.add(new String[]{"Ловкость", "", "1","1","1", "DEX"});

        lines.add(new String[]{"", "", "0","0","0", null}); // отступ-разделитель

        // ── Остальные, некачаемые статы ──────────────────────────────────────
        lines.add(new String[]{"Здоровье", (int) p.health + " / " + (int) p.maxHealth, "0.88","0.42","0.42", null});
        if (p.maxMana != 0) lines.add(new String[]{"Мана", (int) p.mana + " / " + (int) p.maxMana, "0.42","0.68","0.92", null});

        if (p.physDamage   != 0) lines.add(new String[]{"Физический урон",   "+" + p.physDamage,   "0.91","0.58","0.28", null});
        if (p.magicDamage  != 0) lines.add(new String[]{"Магический урон", "+" + p.magicDamage,  "0.72","0.55","0.90", null});
        if (p.attackRating != 0) lines.add(new String[]{"Рейтинг атаки",   String.valueOf(p.attackRating), "0.91","0.58","0.28", null});
        if (p.attackSpeed  != 0) lines.add(new String[]{"Скорость атаки",  p.attackSpeed  + "%", "0.91","0.58","0.28", null});
        if (p.castSpeed    != 0) lines.add(new String[]{"Скорость каста",  p.castSpeed    + "%", "0.72","0.55","0.90", null});
        if (p.runSpeed     != 0) lines.add(new String[]{"Скорость бега",   p.runSpeed     + "%", "0.85","0.77","0.35", null});

        // Пассивные статы от навыков (Смертоносность/Тяжелая рука, см. recomputePlayerStats)
        if (p.critChance   != 0) lines.add(new String[]{"Шанс крита",             p.critChance   + "%",  "0.91","0.35","0.35", null});
        if (p.critDamage   != 0) lines.add(new String[]{"Крит. урон",             "+" + p.critDamage + "%", "0.91","0.35","0.35", null});
        if (p.stunChance   != 0) lines.add(new String[]{"Шанс оглушения",         p.stunChance   + "%",  "0.85","0.65","0.35", null});
        if (p.stunDuration != 0) lines.add(new String[]{"Длительность оглушения", p.stunDuration + " сек", "0.85","0.65","0.35", null});
        if (p.splashDamage != 0) lines.add(new String[]{"Урон по площади",       p.splashDamage + "%",  "0.55","0.80","1.00", null});

        if (p.defence          != 0) lines.add(new String[]{"Защита",             String.valueOf(p.defence),          "0.35","0.80","0.75", null});
        if (p.defenceRating    != 0) lines.add(new String[]{"Повышение защита",      p.defenceRating    + "%",           "0.35","0.80","0.75", null});
        if (p.physDamageReduce != 0) lines.add(new String[]{"Снижение физического урона",String.valueOf(p.physDamageReduce), "0.35","0.80","0.75", null});
        if (p.magicDamageReduce!= 0) lines.add(new String[]{"Снижение магического урона",String.valueOf(p.magicDamageReduce),"0.35","0.80","0.75", null});

        if (p.fireRes      != 0) lines.add(new String[]{"Сопротивление к огню",   p.fireRes      + "%", "0.35","0.80","0.75", null});
        if (p.coldRes      != 0) lines.add(new String[]{"Сопротивление к холоду", p.coldRes      + "%", "0.35","0.80","0.75", null});
        if (p.lightningRes != 0) lines.add(new String[]{"Сопротивление к молнии", p.lightningRes + "%", "0.35","0.80","0.75", null});

        if (p.lifeLeech != 0) lines.add(new String[]{"Похищение жизни", p.lifeLeech + "%", "0.88","0.42","0.55", null});
        if (p.manaLeech != 0) lines.add(new String[]{"Похищение маны",  p.manaLeech + "%", "0.88","0.42","0.55", null});

        if (p.magicFind  != 0) lines.add(new String[]{"Поиск предметов",  (int)p.magicFind + "%", "0.85","0.77","0.35", null});
        if (p.goldFind   != 0) lines.add(new String[]{"Поиск золота",     (int)p.goldFind  + "%", "0.85","0.77","0.35", null});
        if (p.containers != 0) lines.add(new String[]{"Контейнеры",       String.valueOf(p.containers), "0.85","0.88","0.95", null});

        // ── Рендер (скроллящаяся часть) ──────────────────────────────────────
        float totalH    = lines.size() * STATS_LINE_H + STATS_PAD * 2;
        float maxScroll = Math.max(0, totalH - listCH);
        statsScroll     = Math.min(statsScroll, maxScroll);

        float contentTop = py + listCH - STATS_PAD + statsScroll;
        float colValX    = px + PANEL_W - STATS_PAD;
        float btnColX    = px + PANEL_W - STATS_PAD - STAT_BTN_SIZE;

        // Скролл — координаты строк лишь ПРИБЛИЗИТЕЛЬНО ограничены по STATS_LINE_H (см. break/continue
        // ниже), реальная высота глифов может на пару пикселей вылезать за эту границу (особенно
        // буквы с нижними выносными элементами вроде "р"/"у") — поэтому рисуем под физическим
        // scissor-клиппингом по границам контентной области (см. pushContentScissor/popScissor).
        pushContentScissor(batch, px, py, listCH);
        for (int i = 0; i < lines.size(); i++) {
            String[] entry = lines.get(i);
            float lineY = contentTop - (i + 1) * STATS_LINE_H;
            if (lineY + STATS_LINE_H < py) break;           // ниже видимой области
            if (lineY > py + listCH)       continue;         // выше видимой области
            if (entry[0].isEmpty()) continue;                // строка-отступ — просто пропуск места

            String kind = entry[5];
            boolean hasStatButton = kind != null && p.unspentStatPoints > 0;

            // Подсветка строки в фокусе геймпада (навигация D-pad'ом, см. gamepadNavigate) —
            // только в режиме геймпада, чтобы не мешать при игре мышью. lineY — ВЕРХ текста (текст
            // рисуется ВНИЗ от неё, см. font.draw в этом же файле), поэтому полоса строки — это
            // [lineY-STATS_LINE_H, lineY], а не [lineY, lineY+STATS_LINE_H] — раньше было наоборот,
            // из-за чего подсветка "выше" реального текста и перекрывала строку НАД текущей.
            int kindIdx = "STR".equals(kind) ? 0 : "MAGIC".equals(kind) ? 1 : "DEX".equals(kind) ? 2 : -1;
            if (kindIdx == statsFocusIndex && store.isGamepadMode) {
                col(batch, C_BTN_FOCUS);
                batch.draw(pixel, px + STATS_PAD - 6f, lineY - STATS_LINE_H + 2f + STATS_LINE_H * 0.25f, PANEL_W - STATS_PAD * 2 + 12f, STATS_LINE_H - 4f);
            }

            float r = Float.parseFloat(entry[2]), g = Float.parseFloat(entry[3]), b = Float.parseFloat(entry[4]);
            font.setColor(r, g, b, 1f);
            font.draw(batch, entry[0], px + STATS_PAD, lineY);

            float valueRightEdge = hasStatButton ? btnColX - 10f : colValX;
            if (kind != null) {
                // Прокачиваемый стат: "итого (база + бонус от снаряжения)" — база белым, бонус
                // серым, чтобы сразу было видно, сколько вложено игроком, а сколько дало снаряжение
                // (раньше бонус выводился отдельной строкой снизу, было непонятно, к чему она).
                int base  = "STR".equals(kind) ? p.baseStrength  : "MAGIC".equals(kind) ? p.baseMagic  : p.baseDexterity;
                int total = "STR".equals(kind) ? p.strength      : "MAGIC".equals(kind) ? p.magic      : p.dexterity;
                int bonus = total - base;
                if (bonus != 0) {
                    drawRightAligned(batch, valueRightEdge, lineY,
                        new String[]{String.valueOf(total), " (", String.valueOf(base), " + ", String.valueOf(bonus), ")"},
                        new Color[]{C_TEXT_ACT, C_TEXT_DIM, C_TEXT_ACT, C_TEXT_DIM, C_TEXT_DIM, C_TEXT_DIM});
                } else {
                    layout.setText(font, String.valueOf(total));
                    font.setColor(C_TEXT_ACT);
                    font.draw(batch, String.valueOf(total), valueRightEdge - layout.width, lineY);
                }
            } else {
                layout.setText(font, entry[1]);
                font.draw(batch, entry[1], valueRightEdge - layout.width, lineY);
            }

            if (hasStatButton) {
                float btnY = lineY - STAT_BTN_SIZE * 0.7f;
                col(batch, C_STAT_BTN);
                batch.draw(pixel, btnColX, btnY, STAT_BTN_SIZE, STAT_BTN_SIZE);
                font.setColor(C_TEXT_ACT);
                layout.setText(font, "+");
                font.draw(batch, "+", btnColX + (STAT_BTN_SIZE - layout.width) / 2f, btnY + (STAT_BTN_SIZE + layout.height) / 2f);

                int tag = "STR".equals(kind) ? HOTSPOT_STR : "MAGIC".equals(kind) ? HOTSPOT_MAGIC : HOTSPOT_DEX;
                statsHotspots.add(new float[]{btnColX, btnY, STAT_BTN_SIZE, STAT_BTN_SIZE, tag});
            }
        }
        popContentScissor(batch);

        // Полоса скролла
        if (maxScroll > 0) {
            float trackH  = listCH - STATS_PAD * 2;
            float thumbH  = Math.max(20f, trackH * (listCH / totalH));
            float thumbY  = py + STATS_PAD + (trackH - thumbH) * (1f - statsScroll / maxScroll);
            col(batch, C_BORDER);
            batch.draw(pixel, px + PANEL_W - 6f, py + STATS_PAD, 4f, trackH);
            col(batch, C_FOCUS_OUT);
            batch.draw(pixel, px + PANEL_W - 6f, thumbY, 4f, thumbH);
        }

        batch.setColor(1, 1, 1, 1);
    }

    /** Обрабатывает клик по кнопкам "+" вкладки Статы (левел-ап/распределение очков).
     *  Shift+клик по кнопке стата тратит сразу MULTI_SPEND_AMOUNT очков (то же, что удержание
     *  A на геймпаде, см. pollGamepad) — по одному тратить неудобно. @return true, если клик обработан. */
    private boolean handleStatsButtonClick(float mx, float my) {
        if (store.player == null) return false;
        Player p = store.player;
        boolean shift = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                     || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
        for (float[] h : statsHotspots) {
            if (mx >= h[0] && mx <= h[0] + h[2] && my >= h[1] && my <= h[1] + h[3]) {
                int tag = (int) h[4];
                if (tag == HOTSPOT_XP) {
                    int needed = p.experienceToNextLevel() - p.experience;
                    if (needed > 0) p.addExperience(needed);
                } else {
                    int idx = tag == HOTSPOT_STR ? 0 : tag == HOTSPOT_MAGIC ? 1 : 2;
                    spendStatPoints(idx, shift ? MULTI_SPEND_AMOUNT : 1);
                }
                return true;
            }
        }
        return false;
    }

    /** Тратит до amount нераспределённых очков статов на стат idx (0=Сила,1=Магия,2=Ловкость),
     *  не больше, чем реально доступно. Общая точка для мыши (handleStatsButtonClick),
     *  геймпада (pollGamepad — короткое/долгое A) — единая логика траты очков. */
    private void spendStatPoints(int idx, int amount) {
        if (store.player == null) return;
        Player p = store.player;
        int spend = Math.min(amount, p.unspentStatPoints);
        if (spend <= 0) return;
        if (idx == 0) p.baseStrength  += spend;
        if (idx == 1) p.baseMagic     += spend;
        if (idx == 2) p.baseDexterity += spend;
        p.unspentStatPoints -= spend;
    }

    // ── Навыки ────────────────────────────────────────────────────────────────

    /** Вызывается каждый кадр (см. Editor.renderUI) — считает таймаут ожидания ввода в окне
     *  привязки умения. НЕ блокирует поток — просто убывающий таймер, опрашиваемый по кадрам. */
    public void tick(float dt) {
        if (pickerOpen && captureMode) {
            captureTimer -= dt;
            if (captureTimer <= 0f) captureMode = false; // таймаут — отмена ожидания, пикер остаётся открыт
        }
    }

    /** Разблокировано ли умение для прокачки прямо сейчас (уровень игрока + предпосылки —
     *  см. SkillCatalog.isUnlocked). Небольшая обёртка, чтобы не таскать store.player по всем
     *  местам, где нужна эта проверка. */
    private boolean isSkillUnlocked(SkillCatalog.SkillDef def) {
        if (store.player == null || def == null) return false;
        return SkillCatalog.isUnlocked(def, store.player.level, store.player.skillLevels);
    }

    /** Можно ли ПРЯМО СЕЙЧАС потратить очко в это умение — разблокировано, не на максимум (20) и
     *  есть хотя бы 1 нераспределённое очко. Единая точка правды для всей раскраски/кнопки "+" на
     *  вкладке Навыки (см. drawSkillSquare) — "заблокировано", "на максимуме" и "нет свободных
     *  очков" для пользователя визуально означают одно и то же: "прокачать нельзя". */
    private boolean canLevelUp(SkillCatalog.SkillDef def) {
        if (store.player == null || def == null) return false;
        int invested = store.player.skillLevels.getOrDefault(def.id, 0);
        return isSkillUnlocked(def) && invested < SkillCatalog.SkillDef.MAX_LEVEL && store.player.unspentSkillPoints > 0;
    }

    private void renderSkills(SpriteBatch batch, float px, float py, float cH) {
        if (store.player == null) return;
        Player p = store.player;

        String[] subtabLabels = {"Воитель", "Вестник", "Стихийник"};
        float subtabW = PANEL_W / subtabLabels.length;
        float subtabY = py + cH - SKILLS_SUBTAB_H;
        for (int i = 0; i < subtabLabels.length; i++) {
            boolean active = i == skillsSubtab;
            col(batch, active ? C_TAB_ACT : C_TAB_IDLE);
            batch.draw(pixel, px + i * subtabW, subtabY, subtabW - 1, SKILLS_SUBTAB_H);
            if (active) {
                col(batch, C_TAB_LINE);
                batch.draw(pixel, px + i * subtabW, subtabY, subtabW - 1, 2f);
            }
            font.setColor(active ? C_TEXT_ACT : C_TEXT_DIM);
            layout.setText(font, subtabLabels[i]);
            font.draw(batch, subtabLabels[i],
                px + i * subtabW + (subtabW - layout.width) / 2f,
                subtabY + (SKILLS_SUBTAB_H + layout.height) / 2f);
        }
        col(batch, C_BORDER);
        batch.draw(pixel, px, subtabY, PANEL_W, 1);

        List<List<SkillCatalog.SkillDef>> rows = rowsFor(skillsSubtab);
        if (skillsFocusRow >= rows.size()) skillsFocusRow = 0;
        if (!rows.isEmpty() && skillsFocusCol >= rows.get(skillsFocusRow).size()) skillsFocusCol = 0;

        boolean showMsg = p.unspentSkillPoints > 0;
        if (showMsg) {
            font.setColor(C_UNSPENT_MSG);
            font.draw(batch, "Нераспределено очков умений: " + p.unspentSkillPoints,
                px + STATS_PAD, subtabY - 20f);
        }
        float gridTop = subtabY - (showMsg ? 34f : 10f);

        skillsHotspots.clear();
        skillPlusHotspots.clear();

        // X-координата узла — % от требуемого уровня МЕЖДУ минимальным и максимальным требуемым
        // уровнем в этой подвкладке (минимальный = 0% — левый край, максимальный = 100% — правый
        // край, см. уточнение пользователя: считать не от абсолютного 0, а от минимума подвкладки —
        // иначе при плотной группе низких уровней и одном высоком узлы визуально сбивались влево).
        int minUnlock = Integer.MAX_VALUE, maxUnlock = 0;
        for (List<SkillCatalog.SkillDef> row : rows)
            for (SkillCatalog.SkillDef d : row) {
                minUnlock = Math.min(minUnlock, d.unlockLevel);
                maxUnlock = Math.max(maxUnlock, d.unlockLevel);
            }
        int unlockSpan = maxUnlock - minUnlock;
        float usableW = PANEL_W - STATS_PAD * 2 - SKILLS_SQUARE;

        Map<String, float[]> pos = new HashMap<>(); // skillId -> {x, y} (левый нижний угол квадрата)
        for (int r = 0; r < rows.size(); r++) {
            float rowTop = gridTop - r * (SKILLS_SQUARE + SKILLS_ROW_GAP);
            float squareBottom = rowTop - SKILLS_SQUARE;
            for (SkillCatalog.SkillDef d : rows.get(r)) {
                float pct = unlockSpan > 0 ? (d.unlockLevel - minUnlock) / (float) unlockSpan : 0f;
                float sx = px + STATS_PAD + pct * usableW;
                pos.put(d.id, new float[]{sx, squareBottom});
            }
        }

        // Рёбра дерева — напрямую из SkillDef.prerequisites (реальные данные каталога, никакого
        // отдельного списка "веток"), рисуются ДО узлов, чтобы иконки перекрывали линии.
        for (List<SkillCatalog.SkillDef> row : rows) {
            for (SkillCatalog.SkillDef d : row) {
                float[] to = pos.get(d.id);
                if (to == null) continue;
                for (String parentId : d.prerequisites) {
                    float[] from = pos.get(parentId);
                    if (from == null) continue;
                    float x1 = from[0] + SKILLS_SQUARE / 2f, y1 = from[1] + SKILLS_SQUARE / 2f;
                    float x2 = to[0]   + SKILLS_SQUARE / 2f, y2 = to[1]   + SKILLS_SQUARE / 2f;
                    drawBranchConnector(batch, x1, y1, x2, y2, isSkillUnlocked(d));
                }
            }
        }

        for (int r = 0; r < rows.size(); r++) {
            List<SkillCatalog.SkillDef> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                SkillCatalog.SkillDef def = row.get(c);
                float[] xy = pos.get(def.id);
                drawSkillSquare(batch, xy[0], xy[1], def, r, c);
            }
        }

        // Тултип: наведение мышью либо (в режиме геймпада) сфокусированный квадрат.
        float[] hovered = null;
        for (float[] h : skillsHotspots) {
            if (store.mouseX >= h[0] && store.mouseX <= h[0] + h[2]
                    && store.mouseY >= h[1] && store.mouseY <= h[1] + h[3]) {
                hovered = h;
                break;
            }
        }
        if (hovered == null && store.isGamepadMode) {
            for (float[] h : skillsHotspots) {
                if ((int) h[4] == skillsFocusRow && (int) h[5] == skillsFocusCol) { hovered = h; break; }
            }
        }
        if (hovered != null) {
            SkillCatalog.SkillDef def = rows.get((int) hovered[4]).get((int) hovered[5]);
            // Точка привязки — низ иконки, а не центр: тултип обычно раскрывается вверх (см.
            // computeTooltipY), и от центра он перекрывал нижнюю половину самой иконки. Смещение
            // вниз на половину высоты иконки (см. ТЗ) убирает это наложение.
            if (def != null) renderSkillTooltip(batch, def, hovered[0] + hovered[2] / 2f, hovered[1]);
        }

        batch.setColor(1, 1, 1, 1);
    }

    /** Пунктирная линия-"ветка" дерева между двумя узлами — крупные точки, равномерно
     *  расставленные между ними (см. ТЗ: "между иконками скиллов крупными пунктирными точками
     *  путь ветка"). Тусклее, если умение-потомок ещё не разблокировано. */
    private void drawBranchConnector(SpriteBatch batch, float x1, float y1, float x2, float y2, boolean childUnlocked) {
        float dotSize = 9f, gap = 13f;
        float dx = x2 - x1, dy = y2 - y1;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        int count = Math.max(1, Math.round(dist / gap));
        col(batch, childUnlocked ? C_TREE_LINE : C_TREE_LINE_DIM);
        for (int i = 1; i < count; i++) {
            float t = i / (float) count;
            float cx = x1 + dx * t, cy = y1 + dy * t;
            batch.draw(pixel, cx - dotSize / 2f, cy - dotSize / 2f, dotSize, dotSize);
        }
        batch.setColor(1, 1, 1, 1);
    }

    private void drawSkillSquare(SpriteBatch batch, float x, float y, SkillCatalog.SkillDef def, int row, int col) {
        boolean focused = store.isGamepadMode && row == skillsFocusRow && col == skillsFocusCol;
        boolean unlocked = isSkillUnlocked(def);
        int invested = store.player.skillLevels.getOrDefault(def.id, 0);
        boolean canLevel = canLevelUp(def);
        boolean hoveredNow = store.mouseX >= x && store.mouseX <= x + SKILLS_SQUARE
                          && store.mouseY >= y && store.mouseY <= y + SKILLS_SQUARE;

        col(batch, focused ? C_BTN_FOCUS : C_SLOT_BG);
        batch.draw(pixel, x, y, SKILLS_SQUARE, SKILLS_SQUARE);
        col(batch, C_SLOT_LINE);
        rect(batch, x, y, SKILLS_SQUARE, SKILLS_SQUARE);

        // Иконка умения — ячейка тут квадратная (не круглая, как в HUD), обрезка не нужна:
        // достаточно нарисовать картинку на весь квадрат с небольшим отступом от рамки.
        // Раскраска (по требованию пользователя, см. drawSkillSquare-правки):
        //  - можно прокачать ПРЯМО СЕЙЧАС (см. canLevelUp) — всегда полный цвет, даже без вложенных
        //    очков (иначе только что открывшееся умение выглядело бы как заблокированное);
        //  - прокачать нельзя, но очки уже вложены (0 < invested) И где-то ЕЩЁ есть свободные очки
        //    (unspentSkillPoints>0, просто не сюда) — полный цвет, но при наведении курсора
        //    подсвечивается оранжевым (маркер "тут что-то есть, но сейчас недоступно для прокачки");
        //  - прокачать нельзя, очки вложены, но свободных очков нигде вообще нет — просто полный
        //    цвет без оранжевой подсветки (наводить курсор незачем — прокачка сейчас в принципе
        //    невозможна, а не только для этого умения);
        //  - прокачать нельзя и очков 0 — всегда серое/обесцвеченное.
        Texture icon = loadSkillIcon(def.imageFile);
        if (icon != null) {
            float inset = 4f;
            boolean canHoverOrange = invested > 0 && !canLevel && store.player.unspentSkillPoints > 0;
            if (canLevel)                 batch.setColor(1f, 1f, 1f, 1f);
            else if (invested > 0)        batch.setColor(hoveredNow && canHoverOrange ? C_SKILL_HOVER_LOCKED : Color.WHITE);
            else                          batch.setColor(0.4f, 0.4f, 0.42f, 0.55f);
            batch.draw(icon, x + inset, y + inset, SKILLS_SQUARE - inset * 2, SKILLS_SQUARE - inset * 2);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        // Бейдж в правом нижнем углу: разблокировано — уровень умения (база + предметы, см.
        // Player.effectiveSkillLevel); заблокировано — требуемый уровень ИГРОКА, чтобы было видно,
        // чего не хватает (предпосылки-умения показаны в тултипе, см. renderSkillTooltip).
        String badgeText = unlocked ? String.valueOf(store.player.effectiveSkillLevel(def.id)) : ("Ур." + def.unlockLevel);
        layout.setText(font, badgeText);
        float badgeW = layout.width + 8f, badgeH = layout.height + 4f;
        col(batch, C_TOOLTIP_BG);
        batch.draw(pixel, x + SKILLS_SQUARE - badgeW - 2f, y + 2f, badgeW, badgeH);
        if (!unlocked)                                        font.setColor(0.95f, 0.35f, 0.30f, 1f); // непрозрачный красный — C_HIGHLIGHT_BAD слишком тусклый (alpha 0.4) для текста
        else if (store.player.effectiveSkillLevel(def.id) > 0) font.setColor(C_TEXT_ACT);
        else                                                   font.setColor(C_TEXT_DIM);
        font.draw(batch, badgeText, x + SKILLS_SQUARE - badgeW - 2f + 4f, y + 2f + badgeH - 2f);

        // Кнопка "+" (крупнее аналога на вкладке Статы) — только у умений, которые МОЖНО прокачать
        // прямо сейчас (см. canLevelUp — разблокировано, не на максимуме, есть свободные очки).
        // Клик/A — потратить (см. handleSkillsButtonClick/gamepadActivateFocusedSkill); привязка
        // умения к кнопке — отдельно, по ПКМ/X (см. handleSkillsRmbClick/openPickerForFocusedSkill).
        if (canLevel) {
            // Верхний левый угол квадрата (симметрично бейджу уровня в правом нижнем) — целиком
            // внутри квадрата, с небольшим отступом.
            float plusX = x + 2f;
            float plusY = y + SKILLS_SQUARE - SKILL_PLUS_BTN_SIZE - 2f;
            col(batch, C_STAT_BTN);
            batch.draw(pixel, plusX, plusY, SKILL_PLUS_BTN_SIZE, SKILL_PLUS_BTN_SIZE);
            font.setColor(C_TEXT_ACT);
            layout.setText(font, "+");
            font.draw(batch, "+", plusX + (SKILL_PLUS_BTN_SIZE - layout.width) / 2f, plusY + (SKILL_PLUS_BTN_SIZE + layout.height) / 2f);
            skillPlusHotspots.add(new float[]{plusX, plusY, SKILL_PLUS_BTN_SIZE, SKILL_PLUS_BTN_SIZE, row, col});
        }

        skillsHotspots.add(new float[]{x, y, SKILLS_SQUARE, SKILLS_SQUARE, row, col});
    }

    /** Переключение подвкладок (Воитель/Вестник/Стихийник) бамперами L1/R1 (см. pollFrame в
     *  SimulationInputThread) — раньше работало только мышью. */
    public void skillsSwitchSubtab(int dir) {
        if (!isSkillsOpen() || pickerOpen) return;
        skillsSubtab = (skillsSubtab + dir + 3) % 3;
        skillsFocusRow = 0;
        skillsFocusCol = 0;
    }

    /** Навигация по дереву умений текущей подвкладки (или по ячейкам пикера, см. pickerNavigate) —
     *  вызывается из gamepadNavigate/gamepadNavigateCol (D-pad/стик). dRow — смена ветки (строки),
     *  dCol — смена позиции ВНУТРИ текущей ветки (см. treeRowsFor — строки разной длины). */
    public void skillsNavigate(int dRow, int dCol) {
        if (!isSkillsOpen() || pickerOpen) return;
        List<List<SkillCatalog.SkillDef>> rows = rowsFor(skillsSubtab);
        if (rows.isEmpty()) return;
        if (dRow != 0) {
            skillsFocusRow = (skillsFocusRow + dRow + rows.size()) % rows.size();
            skillsFocusCol = Math.min(skillsFocusCol, rows.get(skillsFocusRow).size() - 1);
        }
        if (dCol != 0) {
            int len = rows.get(skillsFocusRow).size();
            skillsFocusCol = (skillsFocusCol + dCol + len) % len;
        }
    }

    private void gamepadActivateFocusedSkill(int amount) {
        List<List<SkillCatalog.SkillDef>> rows = rowsFor(skillsSubtab);
        if (skillsFocusRow >= rows.size() || skillsFocusCol >= rows.get(skillsFocusRow).size()) return;
        spendSkillPoints(rows.get(skillsFocusRow).get(skillsFocusCol).id, amount);
    }

    /** X на геймпаде (короткое нажатие, см. pollGamepad) — открывает окно привязки для СФОКУСИРОВАННОГО
     *  умения, независимо от наличия очков (аналог ПКМ мышью, см. handleSkillsRmbClick). */
    private void openPickerForFocusedSkill() {
        List<List<SkillCatalog.SkillDef>> rows = rowsFor(skillsSubtab);
        if (skillsFocusRow >= rows.size() || skillsFocusCol >= rows.get(skillsFocusRow).size()) return;
        openKeybindPicker(rows.get(skillsFocusRow).get(skillsFocusCol).id);
    }

    private boolean handleSkillsSubtabClick(float mx, float my, float px, float py) {
        float cH = PANEL_H - TAB_H - 1;
        float subtabY = py + cH - SKILLS_SUBTAB_H;
        if (my < subtabY || my > subtabY + SKILLS_SUBTAB_H) return false;
        float subtabW = PANEL_W / 3f;
        for (int i = 0; i < 3; i++) {
            if (mx >= px + i * subtabW && mx <= px + (i + 1) * subtabW) {
                skillsSubtab = i;
                skillsFocusRow = 0;
                skillsFocusCol = 0;
                return true;
            }
        }
        return false;
    }

    /** ЛКМ по красной кнопке "+" (см. drawSkillSquare) — тратит очко(и) умения. Кнопка рисуется
     *  (и хит-тестится) только пока умение разблокировано И есть нераспределённые очки. */
    private boolean handleSkillsButtonClick(float mx, float my) {
        if (store.player == null) return false;
        List<List<SkillCatalog.SkillDef>> rows = rowsFor(skillsSubtab);
        for (float[] h : skillPlusHotspots) {
            if (mx >= h[0] && mx <= h[0] + h[2] && my >= h[1] && my <= h[1] + h[3]) {
                int r = (int) h[4], c = (int) h[5];
                if (r < 0 || r >= rows.size() || c < 0 || c >= rows.get(r).size()) return true;
                boolean shift = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                             || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
                spendSkillPoints(rows.get(r).get(c).id, shift ? MULTI_SPEND_AMOUNT : 1);
                return true;
            }
        }
        return false;
    }

    /** ПКМ по квадрату умения (любому, вне зависимости от очков) — открывает окно привязки к
     *  кнопке (см. ТЗ: "вызов окна связки на ПКМ или на X на геймпаде"). */
    private boolean handleSkillsRmbClick(float mx, float my) {
        List<List<SkillCatalog.SkillDef>> rows = rowsFor(skillsSubtab);
        for (float[] h : skillsHotspots) {
            if (mx >= h[0] && mx <= h[0] + h[2] && my >= h[1] && my <= h[1] + h[3]) {
                int r = (int) h[4], c = (int) h[5];
                if (r >= 0 && r < rows.size() && c >= 0 && c < rows.get(r).size())
                    openKeybindPicker(rows.get(r).get(c).id);
                return true;
            }
        }
        return false;
    }

    /** Тратит до amount нераспределённых очков умений на skillId, капается уровнем 20 и реально
     *  доступными очками — тот же idiom, что и spendStatPoints. Дополнительно: без разблокировки
     *  (недостаточный уровень игрока ИЛИ не вложено хотя бы 1 очко в предпосылку) прокачка вообще
     *  недоступна — см. ТЗ "без прокачки хотя бы одного поинта [в предыдущем] — не доступен для
     *  прокачки". UI и так не рисует кнопку "+" для заблокированных умений (см. drawSkillSquare),
     *  но проверка здесь — на случай прямого вызова (геймпад и т.п.), защита в глубину. */
    private void spendSkillPoints(String skillId, int amount) {
        if (store.player == null) return;
        Player p = store.player;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get(skillId);
        if (!SkillCatalog.isUnlocked(def, p.level, p.skillLevels)) return;
        int current = p.skillLevels.getOrDefault(skillId, 0);
        int room = SkillCatalog.SkillDef.MAX_LEVEL - current;
        int spend = Math.min(amount, Math.min(room, p.unspentSkillPoints));
        if (spend <= 0) return;
        p.skillLevels.put(skillId, current + spend);
        p.unspentSkillPoints -= spend;
    }

    // ── Тултип умения ───────────────────────────────────────────────────────────
    private static final float[] C_TOOLTIP_NEXT = {0.95f, 0.80f, 0.25f};

    private static String kindLabel(SkillCatalog.SkillKind k) {
        switch (k) {
            case ACTIVE:    return "Активный";
            case SUSTAINED: return "Поддерживаемый";
            case STANCE:    return "Стойка";
            case PASSIVE:   return "Пассивный";
            case AURA:      return "Аура";
            case TACTIC:    return "Тактика";
            default:        return "";
        }
    }

    private static String fmtNum(double v) {
        if (Math.abs(v - Math.round(v)) < 0.05) return String.valueOf(Math.round(v));
        return String.format("%.1f", v);
    }

    private static String fixedLabel(String key) {
        switch (key) {
            case "mana":                return "Мана";
            case "mana_per_sec":        return "Мана/сек";
            case "cooldown":            return "Перезарядка";
            case "hp_drain_pct":        return "Списание HP за удар";
            case "attack_speed_pct":    return "Скорость атаки";
            case "mana_reserve_pct":    return "Резерв маны";
            case "max_stacks":          return "Макс. стаков";
            case "stack_duration_sec":  return "Длительность стаков";
            case "activation_mana":     return "Активация (мана)";
            default:                    return key;
        }
    }

    private static String fmtFixed(String key, double v) {
        String num = fmtNum(v);
        if (key.endsWith("_pct")) return num + "%";
        if (key.contains("duration") || "cooldown".equals(key)) return num + " сек";
        return num;
    }

    private void appendStatLines(java.util.List<String[]> lines, SkillCatalog.SkillDef def, int level, float[] color) {
        java.util.LinkedHashMap<String, Double> vals = def.compute(level);
        for (SkillCatalog.SkillStat s : def.stats) {
            Double v = vals.get(s.key);
            String unit = s.unit == null ? "" : s.unit;
            String valStr = fmtNum(v) + ("%".equals(unit) || "°".equals(unit) ? unit : (unit.isEmpty() ? "" : " " + unit));
            lines.add(new String[]{"  " + s.label + ": " + valStr, color[0] + "", color[1] + "", color[2] + ""});
        }
    }

    /** Простой word-wrap по ширине шрифта — для описания умения в тултипе. */
    private java.util.List<String> wrapText(String text, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String candidate = cur.length() == 0 ? w : cur + " " + w;
            layout.setText(font, candidate);
            if (layout.width > maxWidth && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void renderSkillTooltip(SpriteBatch batch, SkillCatalog.SkillDef def, float anchorX, float anchorY) {
        Player p = store.player;
        int level = p.effectiveSkillLevel(def.id);
        float TPAD = 14f, LINE_H = 20f, tw = 320f;

        java.util.List<String[]> lines = new java.util.ArrayList<>(); // {text,r,g,b}
        lines.add(new String[]{def.name, "1", "1", "1"});
        lines.add(new String[]{kindLabel(def.kind), C_TEXT_DIM.r + "", C_TEXT_DIM.g + "", C_TEXT_DIM.b + ""});
        for (String wl : wrapText(def.description, tw - TPAD * 2)) {
            lines.add(new String[]{wl, C_INFO[0] + "", C_INFO[1] + "", C_INFO[2] + ""});
        }

        // Требования разблокировки — показываем, только если ЕЩЁ не выполнены (см. ТЗ:
        // "ограничение прокачки" — уровень игрока + хотя бы 1 очко в предыдущих умениях ветки).
        if (!SkillCatalog.isUnlocked(def, p.level, p.skillLevels)) {
            if (p.level < def.unlockLevel) {
                lines.add(new String[]{"Требуется уровень: " + def.unlockLevel, "0.90", "0.35", "0.35"});
            }
            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String prereqId : def.prerequisites) {
                if (p.skillLevels.getOrDefault(prereqId, 0) < 1) {
                    SkillCatalog.SkillDef prereqDef = SkillCatalog.SKILLS.get(prereqId);
                    missing.add(prereqDef != null ? prereqDef.name : prereqId);
                }
            }
            if (!missing.isEmpty()) {
                // Список предпосылок переносится по словам, как и описание (см. wrapText выше) —
                // иначе длинный список (несколько родителей, см. herald_evasion/herald_steel_will)
                // вылезал за правый край тултипа одной строкой.
                for (String wl : wrapText("Требует: " + String.join(", ", missing), tw - TPAD * 2)) {
                    lines.add(new String[]{wl, "0.90", "0.35", "0.35"});
                }
            }
        }

        if (level > 0) {
            lines.add(new String[]{"Уровень " + level, "0.55", "0.80", "1.00"});
            appendStatLines(lines, def, level, C_INFO);
        } else {
            lines.add(new String[]{"Не изучено", C_TEXT_DIM.r + "", C_TEXT_DIM.g + "", C_TEXT_DIM.b + ""});
        }
        if (p.unspentSkillPoints > 0 && level < SkillCatalog.SkillDef.MAX_LEVEL) {
            lines.add(new String[]{"Следующий уровень " + (level + 1),
                C_TOOLTIP_NEXT[0] + "", C_TOOLTIP_NEXT[1] + "", C_TOOLTIP_NEXT[2] + ""});
            appendStatLines(lines, def, level + 1, C_TOOLTIP_NEXT);
        }
        for (java.util.Map.Entry<String, Double> e : def.fixed.entrySet()) {
            lines.add(new String[]{"  " + fixedLabel(e.getKey()) + ": " + fmtFixed(e.getKey(), e.getValue()),
                C_TEXT_DIM.r + "", C_TEXT_DIM.g + "", C_TEXT_DIM.b + ""});
        }

        float th = TPAD * 2 + lines.size() * LINE_H;
        float ty = computeTooltipY(anchorY, th);
        float tx = Math.max(2f, Math.min(store.uiWidthOriginal - tw - 2f, anchorX - tw * 0.5f));

        col(batch, C_TOOLTIP_BG);
        batch.draw(pixel, tx, ty, tw, th);
        col(batch, C_BORDER);
        rect(batch, tx, ty, tw, th);

        float lineY = ty + th - TPAD - LINE_H * 0.72f;
        for (String[] entry : lines) {
            font.setColor(Float.parseFloat(entry[1]), Float.parseFloat(entry[2]), Float.parseFloat(entry[3]), 1f);
            font.draw(batch, entry[0], tx + TPAD, lineY);
            lineY -= LINE_H;
        }
        batch.setColor(1, 1, 1, 1);
    }

    // ── Окно привязки умения к кнопке ────────────────────────────────────────────

    private void openKeybindPicker(String skillId) {
        // Пассивные умения (Широкий взмах, Тяжелая рука, Смертоносность и т.п.) применяются сами
        // по себе (см. recomputePlayerStats — critChance/stunChance и т.д.), их некуда "нажимать" —
        // привязка к кнопке для них бессмысленна, поэтому окно привязки для PASSIVE не открываем.
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get(skillId);
        if (def != null && def.kind == SkillCatalog.SkillKind.PASSIVE) return;
        // 0 вложенных очков — умение ещё не "изучено" по-настоящему, привязывать его к кнопке нельзя
        // (см. ТЗ: "если в скилле 0 поинтов - его нельзя использовать для привязки").
        if (store.player != null && store.player.skillLevels.getOrDefault(skillId, 0) <= 0) return;

        pickerOpen = true;
        pickerSkillId = skillId;
        captureMode = false;
        captureTimer = 0f;
        pickerFocusRow = 0;
        pickerFocusCol = 0;
    }

    private void closeKeybindPicker() {
        pickerOpen = false;
        pickerSkillId = null;
        captureMode = false;
    }

    /** Навигация по ячейкам пикера (стик/D-pad) — только пока пикер открыт и НЕ в режиме захвата. */
    public void pickerNavigate(int dRow, int dCol) {
        if (!pickerOpen || captureMode) return;
        pickerFocusRow = (pickerFocusRow + dRow + 2) % 2;
        pickerFocusCol = (pickerFocusCol + dCol + 6) % 6;
    }

    /** Подтверждение выбранной ячейки (A на геймпаде) — запускает ожидание ввода кнопки. */
    public void pickerConfirm() {
        if (!pickerOpen || captureMode) return;
        captureMode = true;
        captureTimer = CAPTURE_TIMEOUT;
    }

    private static String shortLabel(String name) {
        return name.length() <= 3 ? name : name.substring(0, 3);
    }

    private void renderKeybindPicker(SpriteBatch batch, float sw, float sh) {
        float w = 520f, h = 260f;
        float x = (sw - w) / 2f, y = (sh - h) / 2f;

        col(batch, C_BORDER);
        batch.draw(pixel, x - 1, y - 1, w + 2, h + 2);
        col(batch, C_BG);
        batch.draw(pixel, x, y, w, h);

        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get(pickerSkillId);
        font.setColor(C_TEXT_ACT);
        String title = "Привязка: " + (def != null ? def.name : String.valueOf(pickerSkillId));
        layout.setText(font, title);
        font.draw(batch, title, x + (w - layout.width) / 2f, y + h - 16f);

        float cellSize = 56f, cellGap = 10f, rowLabelH = 20f;
        float rowsTop = y + h - 50f;
        String[] rowLabels = {"Основной", "Комбинированный"};

        pickerHotspots.clear();
        for (int row = 0; row < 2; row++) {
            float rowY = rowsTop - row * (cellSize + rowLabelH + 18f);
            font.setColor(C_TEXT_DIM);
            layout.setText(font, rowLabels[row]);
            font.draw(batch, rowLabels[row], x + (w - layout.width) / 2f, rowY);

            float cellsW = 6 * cellSize + 5 * cellGap;
            float cellsX = x + (w - cellsW) / 2f;
            float cellY = rowY - rowLabelH - cellSize;

            SkillSlot[] slots = row == 0 ? store.player.mainSkillSlots : store.player.comboSkillSlots;
            for (int c = 0; c < 6; c++) {
                float cx = cellsX + c * (cellSize + cellGap);
                boolean focused = pickerFocusRow == row && pickerFocusCol == c;
                col(batch, focused ? C_BTN_FOCUS : C_SLOT_BG);
                batch.draw(pixel, cx, cellY, cellSize, cellSize);
                col(batch, C_SLOT_LINE);
                rect(batch, cx, cellY, cellSize, cellSize);

                SkillSlot slot = slots[c];
                if (slot.skillId != null) {
                    SkillCatalog.SkillDef bound = SkillCatalog.SKILLS.get(slot.skillId);
                    Texture icon = bound != null ? loadSkillIcon(bound.imageFile) : null;
                    if (icon != null) {
                        float inset = 4f;
                        batch.setColor(1f, 1f, 1f, 1f);
                        batch.draw(icon, cx + inset, cellY + inset, cellSize - inset * 2, cellSize - inset * 2);
                    } else {
                        // Нет иконки (или не загрузилась) — показываем аббревиатуру названия как раньше.
                        String txt = bound != null ? shortLabel(bound.name) : "?";
                        layout.setText(font, txt);
                        font.setColor(C_TEXT);
                        font.draw(batch, txt, cx + (cellSize - layout.width) / 2f, cellY + (cellSize + layout.height) / 2f);
                    }
                }
                pickerHotspots.add(new float[]{cx, cellY, cellSize, cellSize, row, c});
            }
        }

        if (captureMode) {
            String prefix = pickerFocusRow == 1 ? (store.isGamepadMode ? "LT+ " : "Shift+ ") : "";
            String msg = prefix + "Нажмите клавишу/кнопку... (" + (int) Math.ceil(captureTimer) + ")";
            font.setColor(C_UNSPENT_MSG);
            layout.setText(font, msg);
            font.draw(batch, msg, x + (w - layout.width) / 2f, y + 28f);
        } else {
            font.setColor(C_TEXT_DIM);
            String hint = "Выберите ячейку";
            layout.setText(font, hint);
            font.draw(batch, hint, x + (w - layout.width) / 2f, y + 28f);
        }

        batch.setColor(1, 1, 1, 1);
    }

    private boolean handlePickerClick(float mx, float my, float sw, float sh) {
        if (captureMode) return true; // ждём нажатие клавиши/кнопки — обычные клики игнорируем
        float w = 520f, h = 260f;
        float x = (sw - w) / 2f, y = (sh - h) / 2f;
        if (mx < x || mx > x + w || my < y || my > y + h) { closeKeybindPicker(); return true; }
        for (float[] hs : pickerHotspots) {
            if (mx >= hs[0] && mx <= hs[0] + hs[2] && my >= hs[1] && my <= hs[1] + hs[3]) {
                pickerFocusRow = (int) hs[4];
                pickerFocusCol = (int) hs[5];
                captureMode = true;
                captureTimer = CAPTURE_TIMEOUT;
                return true;
            }
        }
        return true;
    }

    /** Клавиатурный код → стабильный inputCode ("KEY_Q" и т.п.), null — не из разрешённого пула
     *  (Q W E R A S D F Z X C V). Публичный статический — переиспользуется SimulationInputThread
     *  для диспетчинга каста умений по тем же кодам. */
    public static String keyToInputCode(int keyCode) {
        if (keyCode == com.badlogic.gdx.Input.Keys.Q) return "KEY_Q";
        if (keyCode == com.badlogic.gdx.Input.Keys.W) return "KEY_W";
        if (keyCode == com.badlogic.gdx.Input.Keys.E) return "KEY_E";
        if (keyCode == com.badlogic.gdx.Input.Keys.R) return "KEY_R";
        if (keyCode == com.badlogic.gdx.Input.Keys.A) return "KEY_A";
        if (keyCode == com.badlogic.gdx.Input.Keys.S) return "KEY_S";
        if (keyCode == com.badlogic.gdx.Input.Keys.D) return "KEY_D";
        if (keyCode == com.badlogic.gdx.Input.Keys.F) return "KEY_F";
        if (keyCode == com.badlogic.gdx.Input.Keys.Z) return "KEY_Z";
        if (keyCode == com.badlogic.gdx.Input.Keys.X) return "KEY_X";
        if (keyCode == com.badlogic.gdx.Input.Keys.C) return "KEY_C";
        if (keyCode == com.badlogic.gdx.Input.Keys.V) return "KEY_V";
        return null;
    }

    /** @return true — код принят (капчур завершён, привязка выполнена); false — код вне пула,
     *  таймер продолжает тикать (см. tick). Вызывается из SimulationInputThread.keyDown, когда
     *  isCapturingKeybind()==true. */
    public boolean captureKeyboardInput(int keyCode) {
        String code = keyToInputCode(keyCode);
        if (code == null) return false;
        bindCapturedInput(code, false);
        return true;
    }

    /** button — libGDX-код кнопки мыши (0=ЛКМ, 1=ПКМ). */
    public boolean captureMouseInput(int button) {
        String code = button == 0 ? "MOUSE_LEFT" : button == 1 ? "MOUSE_RIGHT" : null;
        if (code == null) return false;
        bindCapturedInput(code, false);
        return true;
    }

    /** padCode уже провалидирован вызывающей стороной (SimulationInputThread) — один из
     *  "PAD_X"/"PAD_Y"/"PAD_B"/"PAD_R1"/"PAD_R2"/"PAD_R3". */
    public void captureGamepadInput(String padCode) {
        bindCapturedInput(padCode, true);
    }

    /** Пишет захваченный код в keyboardInputCode ЛИБО gamepadInputCode (см. SkillSlot — раздельные
     *  поля) — строго в пределах ТЕКУЩЕГО ряда (основной/комбо) и ТЕКУЩЕГО типа ввода (клавиатура/
     *  мышь И геймпад считаются РАЗДЕЛЬНО — привязка одного никогда не трогает поля другого):
     *
     *  1. Если ЭТО ЖЕ умение уже было привязано в ДРУГОЙ ячейке этого ряда (тем же типом ввода) —
     *     снимаем оттуда старую привязку (умение может висеть только на одной кнопке каждого типа).
     *  2. Если этот КОД КНОПКИ уже занят ДРУГИМ умением в этом же ряду (тем же типом ввода) —
     *     освобождаем его оттуда (одна кнопка не может звать два разных умения).
     *  3. Если целевая ячейка держала ДРУГОЕ умение — обе привязки сбрасываются (не тащим чужие
     *     бинды за новым умением); если то же самое умение — вторая привязка (другого типа)
     *     добавляется рядом, не трогая первую (см. Player.initDefaultSkills — "Удар" на ЛКМ+B). */
    private void bindCapturedInput(String inputCode, boolean gamepad) {
        if (store.player == null) { closeKeybindPicker(); return; }
        SkillSlot[] slots = pickerFocusRow == 0 ? store.player.mainSkillSlots : store.player.comboSkillSlots;
        SkillSlot slot = slots[pickerFocusCol];

        for (SkillSlot s : slots) {
            if (s == slot) continue;
            boolean sameSkill = pickerSkillId.equals(s.skillId);
            String  boundHere = gamepad ? s.gamepadInputCode : s.keyboardInputCode;
            boolean sameButton = inputCode.equals(boundHere);
            if (sameSkill || sameButton) {
                if (gamepad) s.gamepadInputCode = null; else s.keyboardInputCode = null;
                if (s.keyboardInputCode == null && s.gamepadInputCode == null) s.skillId = null;
            }
        }

        if (!java.util.Objects.equals(pickerSkillId, slot.skillId)) {
            slot.keyboardInputCode = null;
            slot.gamepadInputCode = null;
        }
        slot.skillId = pickerSkillId;
        if (gamepad) slot.gamepadInputCode = inputCode;
        else         slot.keyboardInputCode = inputCode;
        slot.isCombo = pickerFocusRow == 1;
        closeKeybindPicker();
    }

    // ── Дроп ──────────────────────────────────────────────────────────────────
    private static final float[] C_HEADER  = {0.50f, 0.75f, 1.00f};
    private static final float[] C_INFO    = {0.85f, 0.88f, 0.95f};
    private static final float[] C_COMMON  = {1f, 1f, 1f};
    private static final float[] C_RARE    = {0.15f, 0.95f, 0.9f};
    private static final float[] C_UNIQUE  = {1f, 0.1f, 0.75f};

    /**
     * Вкладка "Дроп": все правила и текущие (пересчитанные под игрока) вероятности выпадения
     * лута — см. DropManager.dropLoot. Значения, зависящие от Magic Find/уровня, считаются заново
     * каждый кадр от текущего store.player — отладочный вид, чтобы видеть эффект статов "вживую".
     */
    private void renderDrop(SpriteBatch batch, float px, float py, float cH) {
        float magicFind = store.player != null ? store.player.magicFind : 0f;
        float goldFind  = store.player != null ? store.player.goldFind  : 0f;
        int playerLevel = store.player != null ? store.player.level     : 1;

        java.util.List<String[]> lines = new java.util.ArrayList<>(); // {label, value, r, g, b}
        // Заголовок категории: пустая строка-отступ перед ним (кроме самого первого), чтобы
        // категории визуально не слипались друг с другом (см. запрос пользователя).
        boolean[] firstHeader = {true};
        java.util.function.Consumer<String> header = (text) -> {
            if (!firstHeader[0]) lines.add(new String[]{"", "", "0", "0", "0"}); // пустой отступ-разделитель
            firstHeader[0] = false;
            lines.add(new String[]{text, "", C_HEADER[0]+"", C_HEADER[1]+"", C_HEADER[2]+""});
        };
        // Пояснение под заголовком — приглушённым цветом, без значения справа.
        java.util.function.Consumer<String> note = (text) ->
            lines.add(new String[]{text, "", C_TEXT_DIM.r+"", C_TEXT_DIM.g+"", C_TEXT_DIM.b+""});
        java.util.function.BiConsumer<String, String> row = (label, value) ->
            lines.add(new String[]{label, value, C_INFO[0]+"", C_INFO[1]+"", C_INFO[2]+""});

        // ── Золото ────────────────────────────────────────────────────────────
        header.accept("ЗОЛОТО");
        row.accept("Шанс выпадения", pct(DropManager.GOLD_DROP_CHANCE));
        int goldMin = Math.round(playerLevel * DropManager.GOLD_BASE_MIN * (1f + goldFind / 100f));
        int goldMax = Math.round(playerLevel * (DropManager.GOLD_BASE_MIN + DropManager.GOLD_BASE_SPAN - 1) * (1f + goldFind / 100f));
        row.accept("Сумма (тек. уровень)", goldMin + " - " + goldMax);

        // ── Количество предметов ──────────────────────────────────────────────
        header.accept("ПРЕДМЕТЫ: КОЛИЧЕСТВО");
        note.accept("максимум " + DropManager.MAX_ITEMS_PER_DROP + " предмета за раз");
        float[] itemChances = DropManager.itemCountChances(magicFind);
        for (int i = 0; i < itemChances.length; i++) {
            row.accept((i + 1) + "-й предмет", pct(itemChances[i]));
        }

        // ── Уровень предмета ──────────────────────────────────────────────────
        header.accept("ПРЕДМЕТЫ: УРОВЕНЬ");
        note.accept("ролл 1 - уровень источника дропа (враг/сундук), потолок 99");
        row.accept("Диапазон (тек. уровень как источник)", "1 - " + Math.min(99, Math.max(1, playerLevel)));

        // ── Редкость предмета ─────────────────────────────────────────────────
        header.accept("ПРЕДМЕТЫ: РЕДКОСТЬ");
        note.accept("на каждый сгенерированный предмет");
        double[] rarity = ItemGenerator.rarityChances(playerLevel, magicFind);
        lines.add(new String[]{"Common", pct(rarity[0]), C_COMMON[0]+"", C_COMMON[1]+"", C_COMMON[2]+""});
        lines.add(new String[]{"Rare",   pct(rarity[1]), C_RARE[0]+"",   C_RARE[1]+"",   C_RARE[2]+""});
        lines.add(new String[]{"Unique", pct(rarity[2]), C_UNIQUE[0]+"", C_UNIQUE[1]+"", C_UNIQUE[2]+""});

        // ── Модификаторы по редкости (кол-во роллов статов на предмете) ───────
        header.accept("ПРЕДМЕТЫ: МОДИФИКАТОРЫ");
        note.accept("кол-во статов на предмете зависит от редкости");
        for (String rarityKey : new String[]{"common", "rare", "unique"}) {
            ItemModifierCatalog.RarityDef def = ItemModifierCatalog.RARITIES.get(rarityKey);
            if (def == null) continue;
            float[] c = "common".equals(rarityKey) ? C_COMMON : "rare".equals(rarityKey) ? C_RARE : C_UNIQUE;
            lines.add(new String[]{def.label, def.minMods + " - " + def.maxMods, c[0]+"", c[1]+"", c[2]+""});
        }

        // ── Ограничения: то, что в рандоме не может выпасть вообще (либо только при условии) ──
        header.accept("ПРЕДМЕТЫ: ОГРАНИЧЕНИЯ");
        note.accept("часть модификаторов исключена из рандома полностью или частично");
        row.accept("Чарм 'Опыт'/'Все характеристики'", "0% — только вручную в редакторе");
        row.accept("Артефакт 'Сила света'", "только Unique");
        boolean highLevelPossible = playerLevel >= ItemModifierCatalog.HIGH_LEVEL_THRESHOLD;
        row.accept("'Все навыки'/'Навыки ветки' (Rare+, ур.≥" + ItemModifierCatalog.HIGH_LEVEL_THRESHOLD + ")",
            highLevelPossible ? "возможно" : "СЕЙЧАС НЕТ");

        // ── Типы предметов (чарм/артефакт понижены относительно остальных) ────
        header.accept("ПРЕДМЕТЫ: ТИП");
        note.accept("чарм и артефакт реже — понижены относительно остальных типов");
        String[] typeKeys = DropManager.equipmentTypeKeys();
        float[] typeChances = DropManager.typeChances();
        for (int i = 0; i < typeKeys.length; i++) {
            ItemModifierCatalog.TypeDef type = ItemModifierCatalog.TYPES.get(typeKeys[i]);
            row.accept(type != null ? type.label : typeKeys[i], pct(typeChances[i]));
        }

        // ── Расходники ────────────────────────────────────────────────────────
        header.accept("РАСХОДНИКИ");
        note.accept("не более " + DropManager.MAX_POTIONS_PER_DROP + " зелий за раз, свиток — отдельно");
        row.accept("Зелье здоровья",       pct(DropManager.HEALTH_POTION_CHANCE));
        row.accept("Зелье маны",           pct(DropManager.MANA_POTION_CHANCE));
        row.accept("Зелье восстановления", pct(DropManager.mfScaledChance(DropManager.RECOVERY_POTION_BASE_CHANCE, magicFind)));
        row.accept("Свиток телепортации",  pct(DropManager.mfScaledChance(DropManager.SCROLL_BASE_CHANCE, magicFind)));

        // ── Факелы (независимый шанс на каждое убийство, растёт от Magic Find как у всего лута) ─
        header.accept("ФАКЕЛЫ");
        note.accept("независимый шанс на убийство, растёт от Magic Find");
        row.accept("Обычный (~" + DropManager.COMMON_TORCH_RANGE[0] + "-" + DropManager.COMMON_TORCH_RANGE[1] + ")",
            pct(DropManager.mfScaledChance(DropManager.COMMON_TORCH_BASE_CHANCE, magicFind)));
        row.accept("Редкий (~" + DropManager.RARE_TORCH_RANGE[0] + "-" + DropManager.RARE_TORCH_RANGE[1] + ")",
            pct(DropManager.mfScaledChance(DropManager.RARE_TORCH_BASE_CHANCE, magicFind)));
        row.accept("Уникальный (~" + DropManager.UNIQUE_TORCH_RANGE[0] + "-" + DropManager.UNIQUE_TORCH_RANGE[1] + ")",
            pct(DropManager.mfScaledChance(DropManager.UNIQUE_TORCH_BASE_CHANCE, magicFind)));

        // ── Опыт ──────────────────────────────────────────────────────────────
        header.accept("ОПЫТ");
        row.accept("Сфер за килл", "1 - 5");
        note.accept("сумма опыта фиксирована, дробится на сферы");

        // ── Куда падает лут (геометрические условия, не вероятность) ──────────
        header.accept("КУДА ПАДАЕТ ЛУТ");
        row.accept("Разлёт от точки дропа",
            String.format("%.1f - %.1f тайла", DropManager.MIN_SCATTER_TILES, DropManager.MAX_SCATTER_TILES));
        row.accept("Макс. высота поверхности", "< " + DropManager.MAX_SURFACE_HEIGHT);
        note.accept("вода недоступна, кроме как по мосту");

        // ── Рендер (тот же скролл-механизм, что у статов) ─────────────────────
        float totalH    = lines.size() * STATS_LINE_H + STATS_PAD * 2;
        float maxScroll = Math.max(0, totalH - cH);
        dropScroll      = Math.min(dropScroll, maxScroll);

        float contentTop = py + cH - STATS_PAD + dropScroll;
        float colValX    = px + PANEL_W - STATS_PAD;

        pushContentScissor(batch, px, py, cH);
        for (int i = 0; i < lines.size(); i++) {
            String[] entry = lines.get(i);
            float lineY = contentTop - (i + 1) * STATS_LINE_H;
            if (lineY + STATS_LINE_H < py) break;
            if (lineY > py + cH) continue;

            boolean isHeader = entry[1].isEmpty();
            float r = Float.parseFloat(entry[2]), g = Float.parseFloat(entry[3]), b = Float.parseFloat(entry[4]);
            font.setColor(r, g, b, 1f);
            font.draw(batch, entry[0], px + STATS_PAD, lineY);

            if (!isHeader) {
                layout.setText(font, entry[1]);
                font.draw(batch, entry[1], colValX - layout.width, lineY);
            }
        }
        popContentScissor(batch);

        if (maxScroll > 0) {
            float trackH  = cH - STATS_PAD * 2;
            float thumbH  = Math.max(20f, trackH * (cH / totalH));
            float thumbY  = py + STATS_PAD + (trackH - thumbH) * (1f - dropScroll / maxScroll);
            col(batch, C_BORDER);
            batch.draw(pixel, px + PANEL_W - 6f, py + STATS_PAD, 4f, trackH);
            col(batch, C_FOCUS_OUT);
            batch.draw(pixel, px + PANEL_W - 6f, thumbY, 4f, thumbH);
        }

        batch.setColor(1, 1, 1, 1);
    }

    private static String pct(double chance) {
        return String.format("%.1f%%", chance * 100.0);
    }

    /** Рисует несколько разноцветных кусков текста подряд, выровненных ОБЩИМ блоком по правому
     *  краю rightEdge (см. прокачиваемые статы в renderStats — "итого (база + бонус)"). */
    private void drawRightAligned(SpriteBatch batch, float rightEdge, float y, String[] texts, Color[] colors) {
        float totalW = 0f;
        for (String t : texts) { layout.setText(font, t); totalW += layout.width; }

        float x = rightEdge - totalW;
        for (int i = 0; i < texts.length; i++) {
            font.setColor(colors[i]);
            font.draw(batch, texts[i], x, y);
            layout.setText(font, texts[i]);
            x += layout.width;
        }
    }

    // ── Клиппинг скроллящихся списков (Статы/Дроп) ─────────────────────────────
    // Разрешение "рисовать/не рисовать" по STATS_LINE_H в циклах выше — лишь приближение
    // (реальная высота глифов может отличаться на несколько пикселей, особенно у букв с нижними
    // выносными элементами), поэтому строка на самой границе видимой области может физически
    // вылезти за рамку панели. Физический scissor-клиппинг по границам контента гарантирует, что
    // ничего не нарисуется за пределами (px, py, PANEL_W, cH), независимо от точности расчётов выше.
    private final Rectangle scissorClip = new Rectangle();
    private final Rectangle scissorBounds = new Rectangle();

    private void pushContentScissor(SpriteBatch batch, float px, float py, float cH) {
        scissorBounds.set(px, py, PANEL_W, cH);
        batch.flush();
        ScissorStack.calculateScissors(Main.uiCamera, batch.getTransformMatrix(), scissorBounds, scissorClip);
        ScissorStack.pushScissors(scissorClip);
    }

    private void popContentScissor(SpriteBatch batch) {
        batch.flush();
        ScissorStack.popScissors();
    }

    private void renderInventory(SpriteBatch batch, float px, float py, float cH) {
        // ── Кэшируем геометрию (нужна для hit-testing в handleClick/pollGamepad) ──
        _px = px; _py = py;
        float gridX = px + (PANEL_W - EQ_TOTAL_W) / 2f;
        float eqTop = py + cH - PAD;
        _eqGridX = gridX; _eqTop = eqTop;

        float invGridX = px + (PANEL_W - (float)(INV_COLS * CELL)) / 2f;
        float invTop   = eqTop - EQ_TOTAL_H - INV_GAP;
        _invGridX = invGridX; _invTop = invTop;

        // Пересчитываем статы игрока каждый кадр (снаряжение + чармы)
        recomputePlayerStats();

        // Инициализируем курсор геймпада при первом открытии
        if (!gpInitialized) {
            gpX = invGridX;
            gpY = invTop - CELL;
            gpInitialized = true;
        }

        // ── Слоты снаряжения ──────────────────────────────────────────────────
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            int[] s = EQ_SLOTS[i];
            drawSlotPx(batch, gridX, eqTop, s[0], s[1], s[2], s[3], EQ_NAMES[i]);
        }

        // ── Сетка инвентаря ───────────────────────────────────────────────────
        font.setColor(C_TEXT_DIM);
        layout.setText(font, "Инвентарь");
        font.draw(batch, "Инвентарь", invGridX, invTop + layout.height + 3);

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                drawSlotPx(batch, invGridX, invTop, col * CELL, row * CELL, 1, 1, null);
            }
        }

        // ── Ячейка золота (под сеткой инвентаря) ────────────────────────────
        renderGoldPocket(batch, invGridX, invTop);

        // ── Серые заблокированные слоты артефактов ───────────────────────────
        int availContainers = store.player != null ? store.player.containers : 0;

        // Массив порядка открытия. Индекс в массиве — это (i - 6).
        // Значение — при каком количестве контейнеров этот слот должен быть ОТКРЫТ.
        // slot 6  (i=6,  idx=0) -> нужен 1 контейнер
        // slot 7  (i=7,  idx=1) -> нужно 3 контейнера
        // slot 8  (i=8,  idx=2) -> нужно 5 контейнеров
        // slot 9  (i=9,  idx=3) -> нужен 2 контейнера
        // slot 10 (i=10, idx=4) -> нужно 4 контейнера
        // slot 11 (i=11, idx=5) -> нужно 6 контейнеров
        int[] requiredContainers = {1, 3, 5, 2, 4, 6};

        for (int i = 6; i <= 11; i++) {
            // Если у игрока контейнеров больше или столько же, сколько требуется для этого слота — пропускаем закрашивание
            if (availContainers >= requiredContainers[i - 6]) continue;

            int[] s = EQ_SLOTS[i];
            col(batch, new com.badlogic.gdx.graphics.Color(0.12f, 0.12f, 0.15f, 0.88f));
            batch.draw(pixel, gridX + s[0] + 1, eqTop - s[1] - s[3] * CELL + 1,
                s[2] * CELL - 2, s[3] * CELL - 2);
        }

        batch.setColor(1, 1, 1, 1);

        // ── Ряд ячеек стека (см. StackManager) ────────────────────────────────
        drawStackRow(batch, gridX, eqTop);

        // ── Подсветка целевых клеток при перетаскивании ───────────────────────
        if (draggedItem != null) {
            int dw = draggedItem.containsKey("__width__")  ? (int) draggedItem.get("__width__")  : 1;
            int dh = draggedItem.containsKey("__height__") ? (int) draggedItem.get("__height__") : 1;
            float curX = store.isGamepadMode ? gpX + CELL * 0.5f : store.mouseX;
            float curY = store.isGamepadMode ? gpY + CELL * 0.5f : store.mouseY;

            int[] invCell = getInvCellAt(curX, curY);
            if (invCell != null) {
                int tc = Math.max(0, Math.min(INV_COLS - dw, invCell[0] - dw / 2));
                int tr = Math.max(0, Math.min(INV_ROWS - dh, invCell[1] - dh / 2));
                LinkedHashMap tgt = getSingleItemInArea(tc, tr, dw, dh);
                boolean swapOk = tgt != null && isInvSlotFreeIgnoring(tc, tr, dw, dh, tgt);
                boolean ok = isInvSlotFree(tc, tr, dw, dh) || swapOk;
                drawHighlight(batch,
                    invGridX + tc * CELL, invTop - tr * CELL - dh * CELL,
                    dw * CELL, dh * CELL, ok);
            } else {
                int eq = getEqSlotAt(curX, curY);
                if (eq >= 0) {
                    int[] es = EQ_SLOTS[eq];
                    boolean typeOk = canPlaceInEqSlot(eq, draggedItem);
                    boolean reqOk  = typeOk && (store.player == null
                        || itemMeetsRequirements(draggedItem, store.player));
                    Color hlColor = typeOk ? (reqOk ? C_HIGHLIGHT_OK : C_HIGHLIGHT_WARN) : C_HIGHLIGHT_BAD;
                    col(batch, hlColor);
                    batch.draw(pixel, gridX + es[0] + 1, eqTop - es[1] - es[3] * CELL + 1,
                               es[2] * CELL - 2, es[3] * CELL - 2);
                    batch.setColor(1, 1, 1, 1);
                } else {
                    int stackSlot = getStackSlotAt(curX, curY);
                    if (stackSlot >= 0) {
                        boolean ok = StackManager.canPlaceInSlot(stackSlot, draggedItem);
                        col(batch, ok ? C_HIGHLIGHT_OK : C_HIGHLIGHT_BAD);
                        float sx = gridX + STACK_X + stackSlot * CELL;
                        float sy = eqTop - STACK_Y - CELL;
                        batch.draw(pixel, sx + 1, sy + 1, CELL - 2, CELL - 2);
                        batch.setColor(1, 1, 1, 1);
                    }
                }
            }
        }

        // ── Предметы ─────────────────────────────────────────────────────────
        renderInventoryItems(batch);
        renderEquipmentItems(batch, gridX, eqTop);

        // ── Перетаскиваемый предмет рисуем под курсором ───────────────────────
        if (draggedItem != null) {
            Texture icon = loadIcon((String) draggedItem.get("__image__"));
            if (icon != null) {
                int dw = draggedItem.containsKey("__width__")  ? (int) draggedItem.get("__width__")  : 1;
                int dh = draggedItem.containsKey("__height__") ? (int) draggedItem.get("__height__") : 1;
                float slotW = dw * CELL, slotH = dh * CELL;
                float scale = Math.min(slotW / icon.getWidth(), slotH / icon.getHeight());
                float drawW = icon.getWidth() * scale, drawH = icon.getHeight() * scale;
                float cx = store.isGamepadMode ? gpX + CELL * 0.5f : store.mouseX;
                float cy = store.isGamepadMode ? gpY + CELL * 0.5f : store.mouseY;
                batch.setColor(1, 1, 1, 0.85f);
                batch.draw(icon, cx - drawW / 2f, cy - drawH / 2f, drawW, drawH);
                batch.setColor(1, 1, 1, 1);
            }
        }

        // ── Курсор геймпада ───────────────────────────────────────────────────
        if (store.isGamepadMode) {
            col(batch, C_FOCUS_OUT);
            rect(batch, gpX, gpY, CELL, CELL);
        }

        // ── Детект Shift для режима сравнения (мышь/клавиатура) ──────────────────
        if (!store.isGamepadMode) {
            compareMode = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                       || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
        }

        // ── Тултип (и сравнение) предмета под курсором ────────────────────────
        float tipX = store.isGamepadMode ? gpX + CELL * 0.5f : store.mouseX;
        float tipY = store.isGamepadMode ? gpY + CELL * 0.5f : store.mouseY;
        LinkedHashMap hovered = getHoveredItem(tipX, tipY);
        if (hovered != null) {
            // Сравнение не показываем для уже надетых предметов
            boolean hoveredIsEquipped = isEquippedItem(hovered);
            java.util.List<LinkedHashMap> compItems = (compareMode && !hoveredIsEquipped)
                ? getComparisonItems(hovered) : java.util.Collections.emptyList();
            if (compItems.isEmpty()) {
                renderTooltip(batch, hovered, tipX, tipY, hoveredIsEquipped);
            } else {
                renderTooltipWithComparisons(batch, hovered, compItems, tipX, tipY);
            }
        }
    }

    /** Рисует иконки предметов в обычном инвентаре, пропуская перетаскиваемый. */
    private void renderInventoryItems(SpriteBatch batch) {
        for (Object value : store.inventory.values()) {
            if (!(value instanceof LinkedHashMap)) continue;
            LinkedHashMap itemData = (LinkedHashMap) value;
            if (!itemData.containsKey("__inv_x__") || !itemData.containsKey("__inv_y__")) continue;

            int col = (int) itemData.get("__inv_x__");
            int row = (int) itemData.get("__inv_y__");
            int w = itemData.containsKey("__width__")  ? (int) itemData.get("__width__")  : 1;
            int h = itemData.containsKey("__height__") ? (int) itemData.get("__height__") : 1;

            Texture icon = loadIcon((String) itemData.get("__image__"));
            if (icon == null) continue;

            float slotW = w * CELL, slotH = h * CELL;
            float scale = Math.min(slotW / icon.getWidth(), slotH / icon.getHeight());
            float drawW = icon.getWidth() * scale, drawH = icon.getHeight() * scale;
            float x = _invGridX + col * CELL + (slotW - drawW) / 2f;
            float y = _invTop - row * CELL - h * CELL + (slotH - drawH) / 2f;

            // Чарм с невыполненным уровнем — рыжий (не даёт статы)
            boolean charmInactive = "charm".equals(itemData.get("__type__"))
                && store.player != null
                && !itemMeetsRequirements(itemData, store.player);
            batch.setColor(charmInactive ? 0.75f : 1f, charmInactive ? 0.35f : 1f, charmInactive ? 0.05f : 1f, 1f);
            batch.draw(icon, x, y, drawW, drawH);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** Рисует иконки предметов в слотах снаряжения. Неактивные (нет статов) — тёмно-красные. */
    private void renderEquipmentItems(SpriteBatch batch, float gridX, float eqTop) {
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            LinkedHashMap item = store.equipmentSlots[i];
            if (item == null) continue;
            Texture icon = loadIcon((String) item.get("__image__"));
            if (icon == null) continue;
            int[] s = EQ_SLOTS[i];
            float slotW = s[2] * CELL, slotH = s[3] * CELL;
            float scale = Math.min(slotW / icon.getWidth(), slotH / icon.getHeight());
            float drawW = icon.getWidth() * scale, drawH = icon.getHeight() * scale;
            float x = gridX + s[0] + (slotW - drawW) / 2f;
            float y = eqTop - s[1] - s[3] * CELL + (slotH - drawH) / 2f;
            if (store.inactiveEquipment.contains(item))
                batch.setColor(0.75f, 0.35f, 0.05f, 1f); // рыжий — не выполнены требования
            else
                batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(icon, x, y, drawW, drawH);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /**
     * Ряд из 4 ячеек стека (см. StackManager) под поясом: рамка ячейки + иконка первого предмета
     * в очереди + бейдж "N/ёмкость" в нижнем правом углу — визуально как ячейки артефактов,
     * но вплотную друг к другу (без зазоров).
     */
    private void drawStackRow(SpriteBatch batch, float gridX, float eqTop) {
        int cap = StackManager.capacityPerSlot();
        for (int i = 0; i < STACK_COUNT; i++) {
            drawSlotPx(batch, gridX, eqTop, STACK_X + i * CELL, STACK_Y, 1, 1, null);
            float sx = gridX + STACK_X + i * CELL;
            float sy = eqTop - STACK_Y - CELL; // bottom-left, см. drawSlotPx

            ItemStack stack = store.stacks != null && i < store.stacks.length ? store.stacks[i] : null;
            if (stack == null || stack.items.isEmpty()) {

            }
            else {
                LinkedHashMap item = stack.items.get(0);
                Texture icon = loadIcon((String) item.get("__image__"));
                if (icon != null) {
                    float pad = 5f;
                    float slotSize = CELL - pad * 2;
                    float scale = Math.min(slotSize / icon.getWidth(), slotSize / icon.getHeight());
                    float drawW = icon.getWidth() * scale, drawH = icon.getHeight() * scale;
                    batch.setColor(1f, 1f, 1f, 1f);
                    batch.draw(icon, sx + (CELL - drawW) / 2f, sy + (CELL - drawH) / 2f, drawW, drawH);
                }
            }

            String countText = stack.items.size() + "/" + cap;
            layout.setText(font, countText);
            float tw = layout.width, th = layout.height;
            col(batch, new com.badlogic.gdx.graphics.Color(0f, 0f, 0f, 0.7f));
            batch.draw(pixel, sx + CELL - tw - 5f, sy + 2f, tw + 3f, th + 3f);
            font.setColor(C_TEXT);
            font.draw(batch, countText, sx + CELL - tw - 3f, sy + th + 3f);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** Грузит иконку умения из assets/skills/ (тот же приём разрешения пути, что и
     *  ItemGenerator.applyDefaultImage) — для квадратной сетки на вкладке Навыки обрезка по кругу
     *  не нужна (см. PlayerHud.circularSkillIcon — там она нужна, ячейки HUD круглые). */
    private Texture loadSkillIcon(String imageFile) {
        if (imageFile == null) return null;
        java.io.File file = Gdx.files.internal("assets/skills/" + imageFile).file();
        if (!file.exists()) return null;
        return loadIcon(file.getAbsolutePath());
    }

    /** Грузит и кэширует иконку предмета по абсолютному пути __image__ (см. DropManager.loadItemTexture). */
    private Texture loadIcon(String imagePath) {
        if (imagePath == null) return null;
        Texture cached = iconCache.get(imagePath);
        if (cached != null) return cached;
        try {
            Texture tex = new Texture(Gdx.files.absolute(imagePath));
            iconCache.put(imagePath, tex);
            return tex;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Рисует слот по абсолютным пиксельным координатам от origin.
     * xPx, yPx — пиксельные смещения вправо и вниз от gridOrigin.
     * В libGDX (Y-up): screenY = gridTop - yPx - hPx.
     */
    private void drawSlotPx(SpriteBatch batch, float originX, float originTop,
                             int xPx, int yPx, int wCells, int hCells, String name) {
        float x  = originX + xPx;
        float y  = originTop - yPx - hCells * CELL;  // Y-up
        float sw = wCells * CELL;
        float sh = hCells * CELL;

        col(batch, C_SLOT_BG);
        batch.draw(pixel, x + 1, y + 1, sw - 2, sh - 2);

        col(batch, C_SLOT_LINE);
        for (int ci = 0; ci <= wCells; ci++) batch.draw(pixel, x + ci * CELL, y, 1, sh);
        for (int ri = 0; ri <= hCells; ri++) batch.draw(pixel, x, y + ri * CELL, sw, 1);

        if (name != null && !name.isEmpty()) {
            layout.setText(font, name);
            if (layout.width < sw - 4) {
                font.setColor(C_TEXT_DIM);
                font.draw(batch, name,
                    x + (sw - layout.width) / 2f,
                    y + (sh + layout.height) / 2f);
            }
        }
    }

    /** Отдельная ячейка золота под сеткой инвентаря — иконка монеты (см. DropManager.goldTexture) + сумма. */
    // Тот же янтарный, что у лейбла золота на земле (см. DropManager.GOLD_LABEL_COLOR).
    private static final float[] GOLD_TEXT_COLOR = {0.95f, 0.78f, 0.15f};

    private void renderGoldPocket(SpriteBatch batch, float invGridX, float invTop) {
        float invBottom = invTop - INV_ROWS * CELL;
        float gx = invGridX + (INV_COLS * CELL - GOLD_CELL_W) / 2f;
        float gy = invBottom - GOLD_GAP - GOLD_CELL_H;

        col(batch, C_SLOT_BG);
        batch.draw(pixel, gx + 1, gy + 1, GOLD_CELL_W - 2, GOLD_CELL_H - 2);
        col(batch, C_SLOT_LINE);
        batch.draw(pixel, gx, gy, GOLD_CELL_W, 1);
        batch.draw(pixel, gx, gy + GOLD_CELL_H - 1, GOLD_CELL_W, 1);
        batch.draw(pixel, gx, gy, 1, GOLD_CELL_H);
        batch.draw(pixel, gx + GOLD_CELL_W - 1, gy, 1, GOLD_CELL_H);

        int gold = store.player != null ? store.player.gold : 0;
        Texture icon = DropManager.goldTexture();
        float iconSize = (GOLD_CELL_H - 12f) / 2f;
        String text = String.valueOf(gold);
        layout.setText(font, text);
        float contentW = iconSize + 8f + layout.width;
        float startX = gx + (GOLD_CELL_W - contentW) / 2f;

        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(icon, startX, gy + (GOLD_CELL_H - iconSize) / 2f, iconSize, iconSize);
        font.setColor(GOLD_TEXT_COLOR[0], GOLD_TEXT_COLOR[1], GOLD_TEXT_COLOR[2], 1f);
        font.draw(batch, text, startX + iconSize + 8f, gy + (GOLD_CELL_H + layout.height) / 2f);
        batch.setColor(1, 1, 1, 1);
    }

    // ── Приватные утилиты ─────────────────────────────────────────────────────

    private void toggle(Tab tab) {
        if (isOpen && activeTab == tab) {
            cancelDrag();
            isOpen = false;
        } else {
            if (!isOpen) gpInitialized = false; // сбросить курсор при открытии
            isOpen = true;
            activeTab = tab;
        }
    }

    private void switchTab(int dir) {
        if (!isOpen) return; // не открывать если закрыт
        Tab[] tabs = Tab.values();
        activeTab = tabs[(activeTab.ordinal() + dir + tabs.length) % tabs.length];
    }

    private void activateMenuItem(int idx) {
        if (idx == 3 && store.stopSimulationAction != null) { // Выход
            store.stopSimulationAction.run();
            isOpen = false;
        }
        // idx 0,1,2 — Сохранить, Загрузить, Настройки — заглушки
    }

    /**
     * Y-позиция кнопки меню. Элемент 0 — сверху (максимальный Y в Y-up),
     * элемент N — снизу. Центрированы вертикально в content-области.
     */
    private float menuButtonY(int idx, float contentY, float contentH) {
        float total  = MENU_ITEMS.length * BTN_H + (MENU_ITEMS.length - 1) * BTN_GAP;
        float topOfButtons = contentY + contentH - (contentH - total) / 2f; // верх блока кнопок
        return topOfButtons - (idx + 1) * BTN_H - idx * BTN_GAP;
    }

    private void col(SpriteBatch b, Color c) { b.setColor(c.r, c.g, c.b, c.a); }

    private void rect(SpriteBatch b, float x, float y, float w, float h) {
        b.draw(pixel, x,       y,       w, 1);
        b.draw(pixel, x,       y+h-1,   w, 1);
        b.draw(pixel, x,       y,       1, h);
        b.draw(pixel, x+w-1,   y,       1, h);
    }

    // ── Взаимодействие с предметами ───────────────────────────────────────────
    // Предмет в буфере (draggedItem) ВСЕГДА вне store.inventory и store.equipmentSlots.
    // Подобран — удалён из источника. Положен — добавлен в цель. Никаких флагов.

    /** Клик/A: подобрать если буфер пуст, положить если есть предмет в буфере. */
    private void tryInteractAt(float cx, float cy) {
        if (activeTab != Tab.INVENTORY) return;
        if (draggedItem != null) tryPlaceAt(cx, cy);
        else                      tryPickupAt(cx, cy);
    }

    /** Подобрать предмет — удаляем из источника, кладём в буфер. */
    private void tryPickupAt(float cx, float cy) {
        int[] cell = getInvCellAt(cx, cy);
        if (cell != null) {
            LinkedHashMap item = getItemAt(cell[0], cell[1]);
            if (item != null) {
                int w = item.containsKey("__width__")  ? (int) item.get("__width__")  : 1;
                int h = item.containsKey("__height__") ? (int) item.get("__height__") : 1;
                String key = getItemKey(item);
                if (key != null) store.inventory.remove(key);
                clearInvGrid((int) item.get("__inv_x__"), (int) item.get("__inv_y__"), w, h);
                draggedItem = item;
                return;
            }
        }
        int eq = getEqSlotAt(cx, cy);
        if (eq >= 0 && store.equipmentSlots[eq] != null) {
            draggedItem = store.equipmentSlots[eq];
            store.equipmentSlots[eq] = null;
            return;
        }

        // Клик по занятой ячейке стека — вынимает первый предмет очереди в буфер (без применения
        // эффекта), дальше с ним работают общие правила переноса (см. StackManager.removeFirst).
        int stackSlot = getStackSlotAt(cx, cy);
        if (stackSlot >= 0) {
            LinkedHashMap item = StackManager.removeFirst(stackSlot);
            if (item != null) draggedItem = item;
        }
    }

    /** Положить буферный предмет: вставка на свободные клетки или своп. */
    private void tryPlaceAt(float cx, float cy) {
        if (draggedItem == null) return;
        int dw = draggedItem.containsKey("__width__")  ? (int) draggedItem.get("__width__")  : 1;
        int dh = draggedItem.containsKey("__height__") ? (int) draggedItem.get("__height__") : 1;

        int[] cell = getInvCellAt(cx, cy);
        if (cell != null) {
            int tc = Math.max(0, Math.min(INV_COLS - dw, cell[0] - dw / 2));
            int tr = Math.max(0, Math.min(INV_ROWS - dh, cell[1] - dh / 2));

            if (isInvSlotFree(tc, tr, dw, dh)) {
                putInInventory(draggedItem, tc, tr);
                draggedItem = null;
                return;
            }

            // Своп: в целевой области ровно один предмет, и после его изъятия область свободна
            LinkedHashMap target = getSingleItemInArea(tc, tr, dw, dh);
            if (target != null && isInvSlotFreeIgnoring(tc, tr, dw, dh, target)) {
                int bw = target.containsKey("__width__")  ? (int) target.get("__width__")  : 1;
                int bh = target.containsKey("__height__") ? (int) target.get("__height__") : 1;
                int bCol = (int) target.get("__inv_x__"), bRow = (int) target.get("__inv_y__");
                String key = getItemKey(target);
                if (key != null) store.inventory.remove(key);
                clearInvGrid(bCol, bRow, bw, bh);
                putInInventory(draggedItem, tc, tr);
                draggedItem = target; // target уходит в буфер
            }
            return;
        }

        int eq = getEqSlotAt(cx, cy);
        if (eq >= 0) {
            if (canPlaceInEqSlot(eq, draggedItem)) {
                // Проверяем требования: нельзя надеть если не хватает статов
                if (store.player != null && !itemMeetsRequirements(draggedItem, store.player)) return;
                LinkedHashMap existing = store.equipmentSlots[eq];
                store.equipmentSlots[eq] = draggedItem;
                draggedItem = existing;
            }
            return;
        }

        // Ячейка стека: кладём, только если по логике стека там есть место (см. canPlaceInSlot).
        // Свопа нет — если места нет, предмет просто остаётся в буфере (та же семантика, что у eq-слотов).
        int stackSlot = getStackSlotAt(cx, cy);
        if (stackSlot >= 0 && StackManager.tryAddToSlot(stackSlot, draggedItem)) {
            draggedItem = null;
        }
    }

    /** Добавляет предмет в store.inventory на позицию (col,row), заполняет сетку. */
    private void putInInventory(LinkedHashMap item, int col, int row) {
        int w = item.containsKey("__width__")  ? (int) item.get("__width__")  : 1;
        int h = item.containsKey("__height__") ? (int) item.get("__height__") : 1;
        item.put("__inv_x__", col);
        item.put("__inv_y__", row);
        String uuid = (String) item.get("__uuid__");
        if (uuid == null) uuid = com.nicweiss.editor.utils.Uuid.generate();
        store.inventory.put(uuid, item);
        fillInvGrid(col, row, w, h);
    }

    /** При закрытии инвентаря: если в буфере что-то есть — ищем место или бросаем на землю. */
    private void cancelDrag() {
        if (draggedItem != null) {
            int dw = draggedItem.containsKey("__width__")  ? (int) draggedItem.get("__width__")  : 1;
            int dh = draggedItem.containsKey("__height__") ? (int) draggedItem.get("__height__") : 1;
            int[] slot = findFreeInvSlot(dw, dh);
            if (slot != null) putInInventory(draggedItem, slot[0], slot[1]);
            else               DropManager.spawnDropAtPlayer(draggedItem);
            draggedItem = null;
        }
    }

    private void dropDraggedToGround() {
        if (draggedItem == null) return;
        DropManager.spawnDropAtPlayer(draggedItem);
        draggedItem = null;
    }

    /** Первый свободный прямоугольник w×h (сканирование от края к центру). */
    private int[] findFreeInvSlot(int w, int h) {
        for (int r = 0; r <= INV_ROWS - h; r++)
            for (int c = 0; c <= INV_COLS - w; c++)
                if (isInvSlotFree(c, r, w, h)) return new int[]{c, r};
        return null;
    }

    // ── Система статов ────────────────────────────────────────────────────────

    /**
     * Пересчитывает эффективные статы игрока:
     *   base + чармы в инвентаре (всегда активны) + снаряжение (активно если требования выполнены).
     * Снаряжение активируется итеративно: надетый предмет может давать стату, нужную другому.
     * Неактивные предметы заносятся в store.inactiveEquipment.
     */
    private void recomputePlayerStats() {
        if (store.player == null) return;
        Player p = store.player;

        // Сброс к базовым значениям. maxHealth/maxMana не сбрасываются здесь — они не копят
        // бонусы предметов сами по себе, а считаются заново в конце метода по формуле
        // (сила/магия * множитель) + flatHealthBonus/flatManaBonus, когда итоговые
        // сила/магия и плоские бонусы уже известны.
        p.strength = p.baseStrength; p.magic = p.baseMagic; p.dexterity = p.baseDexterity;
        p.energy = 0;
        p.flatHealthBonus = 0f; p.flatManaBonus = 0f;
        p.fireRes = 0; p.coldRes = 0; p.lightningRes = 0;
        p.attackSpeed = 0; p.castSpeed = 0; p.runSpeed = 0;
        p.attackRating = 0; p.physDamage = 0; p.magicDamage = 0;
        p.defence = 0; p.defenceRating = 0;
        p.physDamageReduce = 0; p.magicDamageReduce = 0;
        // По умолчанию доступны BASE_CONTAINERS контейнера-артефакта даже без пояса (см. ТЗ) —
        // пояс с belt_containers добавляет сверху (см. applyMod).
        p.containers = BASE_CONTAINERS;
        p.beltCapacity = 0;
        p.lifeLeech = 0; p.manaLeech = 0;
        p.lifeRegen = 0f; p.manaRegen = 0f;
        p.magicFind = 0f; p.goldFind = 0f;
        p.lightPower = 0; p.torchGlowR = 0f; p.torchGlowG = 0f; p.torchGlowB = 0f;

        // Чармы — активны если уровень игрока >= требуемого
        for (Object v : store.inventory.values()) {
            if (!(v instanceof LinkedHashMap)) continue;
            LinkedHashMap item = (LinkedHashMap) v;
            if (item == draggedItem) continue;
            if ("charm".equals(item.get("__type__")) && itemMeetsRequirements(item, p))
                applyItemStats(item, p);
        }

        // Единый итеративный проход по ВСЕМУ снаряжению (включая артефакты/чармы) до фиксированной
        // точки: требования по статам и по контейнерам (для артефактов) могут быть ВЗАИМНО
        // зависимы — например, магия артефакта открывает требование пояса, а контейнеры пояса
        // открывают слот артефакта. Раньше артефакты (слоты 6-11) считались отдельной "фазой 2"
        // строго ПОСЛЕ завершения фазы обычного снаряжения — из-за этого бонусы артефактов
        // НИКОГДА не учитывались при проверке требований обычного снаряжения (баг: пояс с
        // требованием 21 магии оставался неактивным/оранжевым даже когда магия артефакта суммарно
        // это давало, хотя сам дроп в слот уже разрешался — drag-n-drop проверка читала статы
        // прошлого полного пересчёта, где артефакт был учтён, а этот пересчёт — нет). Теперь оба
        // типа слотов крутятся в одном цикле, пока что-то меняется — бонусы текут в обе стороны.
        int[] requiredContainers = {1, 3, 5, 2, 4, 6}; // индекс = i - 6, порядок открытия слотов артефактов

        boolean[] active = new boolean[store.equipmentSlots.length];
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < store.equipmentSlots.length; i++) {
                LinkedHashMap item = store.equipmentSlots[i];
                if (item == null || item == draggedItem || active[i]) continue;

                boolean isArtifactSlot = i >= 6 && i <= 11;
                boolean slotUnlocked = !isArtifactSlot || p.containers >= requiredContainers[i - 6];

                if (slotUnlocked && itemMeetsRequirements(item, p)) {
                    applyItemStats(item, p);
                    active[i] = true;
                    changed = true;
                }
            }
        }

        // Помечаем неактивные слоты
        store.inactiveEquipment.clear();
        for (int i = 0; i < store.equipmentSlots.length; i++) {
            LinkedHashMap item = store.equipmentSlots[i];
            if (item != null && item != draggedItem && !active[i])
                store.inactiveEquipment.add(item);
        }

        // Ёмкость здоровья/маны = (финальные сила/магия * множитель) + плоский бонус с модификаторов
        // предметов (flatHealthBonus/flatManaBonus) — пересчитывается каждый раз заново.
        p.maxHealth = p.strength * HEALTH_PER_STRENGTH + p.flatHealthBonus;
        p.maxMana   = p.magic    * MANA_PER_MAGIC      + p.flatManaBonus;

        // Рейтинг атаки = (финальная ловкость * множитель) + бонус с модификаторов предметов
        // (тот же принцип, что здоровье/мана от силы/магии выше) — p.attackRating к этому моменту
        // уже содержит сумму item-бонусов (см. applyMod "_ar", applyMainStat "gloves" выше по циклу).
        p.attackRating += Math.round(p.dexterity * ATTACK_RATING_PER_DEXTERITY);

        // Пассивные боевые статы от умений (Воитель: Смертоносность/Тяжелая рука) — см.
        // SkillCatalog.warriorSkills(), формулы 1:1 из skills.txt. Активные умения/ауры сюда пока
        // не входят (применение их эффектов — отдельная задача, см. SkillCaster).
        int critLevel = p.effectiveSkillLevel("warrior_crit");
        p.critChance = critLevel > 0 ? 1f * critLevel : 0f;
        p.critDamage = critLevel > 0 ? 2.5f * critLevel : 0f;

        int stunLevel = p.effectiveSkillLevel("warrior_stun");
        p.stunChance   = stunLevel > 0 ? 5f + 0.75f * (stunLevel - 1) : 0f;
        p.stunDuration = stunLevel > 0 ? 1.0f + 0.05f * (stunLevel - 1) : 0f;

        // Широкий взмах — без шанса срабатывания (см. SkillCatalog.warrior_splash): раз умение
        // вложено, урон по площади применяется на КАЖДОЙ автоатаке.
        int splashLevel = p.effectiveSkillLevel("warrior_splash");
        p.splashDamage = splashLevel > 0 ? 30f + 2f * (splashLevel - 1) : 0f;

        if (p.pendingInitialFill) {
            // При входе в симуляцию — стартуем с половиной ёмкости (см. Player.pendingInitialFill).
            p.health = p.maxHealth / 2f;
            p.mana   = p.maxMana   / 2f;
            p.pendingInitialFill = false;
        } else {
            // Текущие здоровье/мана не должны превышать пересчитанный максимум (например, после снятия предмета).
            p.health = Math.min(p.health, p.maxHealth);
            p.mana   = Math.min(p.mana,   p.maxMana);
        }

        // Ёмкость стеков зависит от beltCapacity, только что пересчитанного выше — если пояс
        // сняли/сменили на менее ёмкий, лишнее из стеков нужно тут же выложить в инвентарь/на землю.
        StackManager.enforceCapacity();
    }

    // 8 очков здоровья за 1 силы, 4 очка маны за 1 магии, 6 очков рейтинга атаки за 1 ловкости —
    // см. recomputePlayerStats().
    private static final float HEALTH_PER_STRENGTH        = 8f;
    private static final float MANA_PER_MAGIC             = 4f;
    private static final float ATTACK_RATING_PER_DEXTERITY = 6f;

    /** Пересчитывает статы игрока — вызывается HUD-ом каждый кадр, чтобы значения были
     *  актуальны даже когда инвентарь закрыт (иначе статы пересчитывались только на вкладках
     *  ИНВЕНТАРЬ/СТАТЫ, см. renderInventory/renderStats). */
    public void recomputeStats() {
        recomputePlayerStats();
    }

    /** Проверяет, выполняет ли игрок требования предмета по текущим эффективным статам. */
    private static boolean itemMeetsRequirements(LinkedHashMap item, Player p) {
        if (item == null) return true;
        if (item.containsKey("__reqLevel__")    && toInt(item.get("__reqLevel__"))    > p.level)    return false;
        if (item.containsKey("__reqStrength__") && toInt(item.get("__reqStrength__")) > p.strength) return false;
        if (item.containsKey("__reqMagic__")    && toInt(item.get("__reqMagic__"))    > p.magic)    return false;
        return true;
    }

    /** Добавляет бонусы всех статов предмета к Player. */
    private static void applyItemStats(LinkedHashMap item, Player p) {
        Object statsObj = item.get("__stats__");
        if (statsObj instanceof LinkedHashMap) {
            for (Object v : ((LinkedHashMap) statsObj).values()) {
                if (!(v instanceof LinkedHashMap)) continue;
                LinkedHashMap stat = (LinkedHashMap) v;
                applyMod((String) stat.get("__modId__"), toInt(stat.get("__value__")), p);
            }
        }
        applyMainStat(item, p);
    }

    /**
     * Основная характеристика пояса/обуви/перчаток идёт напрямую в стату игрока, а не через
     * __stats__/applyMod — это не модификатор из каталога (нет ModifierDef), а часть самого
     * ролла типа предмета (см. ItemGenerator.rollMainStat). У остальных типов __mainStat__ —
     * это урон/защита, который уже учтён в бою через сравнение предметов, а не как Player-стата.
     */
    private static void applyMainStat(LinkedHashMap item, Player p) {
        if (!item.containsKey("__mainStat__")) return;
        int v = toInt(item.get("__mainStat__"));
        String typeKey = (String) item.get("__type__");
        if (typeKey == null) return;
        if      ("boots".equals(typeKey))  p.runSpeed     += v;
        else if ("gloves".equals(typeKey)) p.attackRating += v;
        else if ("belt".equals(typeKey))   p.beltCapacity += v;
        else if ("torch".equals(typeKey)) {
            // Факел один на слот (см. allowedEqSlotsForType) — присваиваем, а не накапливаем.
            // Цвет свечения — скрытый параметр предмета (__glowColorR/G/B__, см.
            // ItemGenerator.applyTorchRarityStats), напрямую переносится в стату игрока.
            p.lightPower = v;
            p.torchGlowR = toFloat(item.get("__glowColorR__"));
            p.torchGlowG = toFloat(item.get("__glowColorG__"));
            p.torchGlowB = toFloat(item.get("__glowColorB__"));
        }
    }

    private static float toFloat(Object v) {
        return v instanceof Number ? ((Number) v).floatValue() : 0f;
    }

    /** Подпись __mainStat__ в тултипе — зависит от типа предмета (см. ItemGenerator.rollMainStat). */
    private static String mainStatLabel(String typeKey) {
        if ("weapon".equals(typeKey)) return "Урон";
        if ("belt".equals(typeKey))   return "Ёмкость";
        if ("boots".equals(typeKey))  return "Скорость передвижения";
        if ("gloves".equals(typeKey)) return "Рейтинг атаки";
        if ("torch".equals(typeKey))  return "Сила света";
        return "Защита";
    }

    /** Время горения факела (секунды → "N мин", см. ItemGenerator.applyTorchRarityStats). */
    private static String formatBurnTime(int seconds) {
        return (seconds / 60) + " мин";
    }

    /**
     * Роутинг мода на поле Player по его ключу из каталога. Каталог придерживается конвенции
     * "модификатор всегда называется <префикс>_<канонический суффикс статы" (см. ItemModifierCatalog) —
     * поэтому здесь матчим по endsWith на ТОЧНЫЙ суффикс, а не contains по произвольной подстроке.
     * contains() ловил "магический урон" (*_magic_damage) как подстроку "_magic" (атрибут магии) —
     * endsWith на непересекающиеся суффиксы структурно исключает такие коллизии, а не полагается
     * на порядок веток. contains() оставлен только там, где семейство ключей действительно не имеет
     * единого суффикса (см. пометку "_def" ниже) — расширять его дальше не стоит.
     */
    private static void applyMod(String k, int v, Player p) {
        if (k == null) return;
        if      (k.endsWith("_phys_red"))    p.physDamageReduce  += v;
        else if (k.endsWith("_magic_red"))   p.magicDamageReduce += v;
        else if (k.endsWith("_strength"))    p.strength      += v;
        else if (k.endsWith("_magic"))       p.magic         += v;
        else if (k.endsWith("_energy"))      p.energy        += v;
        else if (k.endsWith("_dexterity"))   p.dexterity     += v;
        else if (k.endsWith("_replenish_life")) p.lifeRegen  += v;  // очков/сек, см. Player.tickRegen
        else if (k.endsWith("_replenish_mana")) p.manaRegen  += v;
        else if (k.endsWith("_health"))      p.flatHealthBonus += v;  // складывается с формулой от силы, см. recomputePlayerStats
        else if (k.endsWith("_mana"))        p.flatManaBonus += v;    // *_mana_leech/*_replenish_mana уже перехвачены выше
        else if (k.endsWith("_allres"))      { p.fireRes += v; p.coldRes += v; p.lightningRes += v; }
        else if (k.endsWith("_res_fire"))    p.fireRes       += v;
        else if (k.endsWith("_res_cold"))    p.coldRes       += v;
        else if (k.endsWith("_res_lightning")) p.lightningRes += v;
        else if (k.endsWith("_life_leech"))  p.lifeLeech     += v;
        else if (k.endsWith("_mana_leech"))  p.manaLeech     += v;
        else if (k.endsWith("_mf"))          p.magicFind     += v;
        else if (k.endsWith("_gf"))          p.goldFind      += v;
        else if (k.endsWith("_ias"))         p.attackSpeed   += v;
        else if (k.endsWith("_fcr"))         p.castSpeed     += v;
        else if (k.endsWith("_frw"))         p.runSpeed      += v;
        else if (k.endsWith("_ar"))          p.attackRating  += v;
        else if (k.endsWith("_magic_damage") || k.endsWith("_fire_dmg")
              || k.endsWith("_lightning_dmg") || k.endsWith("_cold_dmg")) p.magicDamage += v;
        else if (k.endsWith("_flat_def"))    p.defence       += v;
        // "_def" не вынесен в единый суффикс во всём каталоге (armor_def_plate, armor_def_robe,
        // *_ed_def) — contains() тут безопасен: ни один модификатор другой категории "_def" не содержит.
        else if (k.contains("_def"))         p.defenceRating += v;
        else if (k.endsWith("_damage"))      p.physDamage    += v;  // *_magic_damage уже перехвачен выше
        else if (k.endsWith("_ed"))          p.physDamage    += v;  // weapon_ed; *_ed_def уже перехвачен выше (contains "_def")
        else if (k.endsWith("_attributes"))  { p.strength += v; p.magic += v; p.dexterity += v; }
        else if (k.endsWith("_containers"))  p.containers    += v;
    }

    // ── Hit-testing ───────────────────────────────────────────────────────────

    /** Ячейка инвентаря по экранной точке, или null если вне сетки. */
    private int[] getInvCellAt(float cx, float cy) {
        int col = (int)((cx - _invGridX) / CELL);
        int row = (int)((_invTop - cy)   / CELL);
        if (col < 0 || col >= INV_COLS || row < 0 || row >= INV_ROWS) return null;
        return new int[]{col, row};
    }

    /** Индекс eq-слота по экранной точке, или -1 если вне всех слотов. */
    private int getEqSlotAt(float cx, float cy) {
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            int[] s = EQ_SLOTS[i];
            float sx = _eqGridX + s[0], sy = _eqTop - s[1] - s[3] * CELL;
            float sw = s[2] * CELL, sh = s[3] * CELL;
            if (cx >= sx && cx < sx + sw && cy >= sy && cy < sy + sh) return i;
        }
        return -1;
    }

    /** Индекс ячейки стека (0..STACK_COUNT-1) по экранной точке, или -1 — см. drawStackRow. */
    private int getStackSlotAt(float cx, float cy) {
        float sy = _eqTop - STACK_Y - CELL;
        if (cy < sy || cy >= sy + CELL) return -1;
        for (int i = 0; i < STACK_COUNT; i++) {
            float sx = _eqGridX + STACK_X + i * CELL;
            if (cx >= sx && cx < sx + CELL) return i;
        }
        return -1;
    }

    /** Предмет в инвентаре, занимающий ячейку (col, row). */
    private LinkedHashMap getItemAt(int col, int row) {
        for (Object v : store.inventory.values()) {
            if (!(v instanceof LinkedHashMap)) continue;
            LinkedHashMap item = (LinkedHashMap) v;
            if (!item.containsKey("__inv_x__")) continue;
            int ix = (int) item.get("__inv_x__"), iy = (int) item.get("__inv_y__");
            int iw = item.containsKey("__width__")  ? (int) item.get("__width__")  : 1;
            int ih = item.containsKey("__height__") ? (int) item.get("__height__") : 1;
            if (col >= ix && col < ix + iw && row >= iy && row < iy + ih) return item;
        }
        return null;
    }

    /** Предмет под курсором (в инвентаре или в eq-слоте). */
    private LinkedHashMap getHoveredItem(float cx, float cy) {
        int[] cell = getInvCellAt(cx, cy);
        if (cell != null) return getItemAt(cell[0], cell[1]);
        int eq = getEqSlotAt(cx, cy);
        if (eq >= 0) return store.equipmentSlots[eq];
        int stackSlot = getStackSlotAt(cx, cy);
        if (stackSlot >= 0 && store.stacks != null && !store.stacks[stackSlot].items.isEmpty()) {
            return store.stacks[stackSlot].items.get(0);
        }
        return null;
    }

    /** Ключ (uuid) предмета в store.inventory. */
    private String getItemKey(LinkedHashMap item) {
        for (java.util.Map.Entry<?, ?> e : store.inventory.entrySet()) {
            if (e.getValue() == item) return (String) e.getKey();
        }
        return null;
    }

    // ── Логика слотов ────────────────────────────────────────────────────────

    /** true если прямоугольник w×h начиная с (col,row) свободен (draggedItem считается поднятым). */
    private boolean isInvSlotFree(int col, int row, int w, int h) {
        if (col < 0 || col + w > INV_COLS || row < 0 || row + h > INV_ROWS) return false;
        for (Object v : store.inventory.values()) {
            if (!(v instanceof LinkedHashMap)) continue;
            LinkedHashMap item = (LinkedHashMap) v;
            if (!item.containsKey("__inv_x__")) continue;
            int ix = (int) item.get("__inv_x__"), iy = (int) item.get("__inv_y__");
            int iw = item.containsKey("__width__")  ? (int) item.get("__width__")  : 1;
            int ih = item.containsKey("__height__") ? (int) item.get("__height__") : 1;
            if (col < ix + iw && col + w > ix && row < iy + ih && row + h > iy) return false;
        }
        return true;
    }

    /** true если в целевом прямоугольнике ровно один предмет, и он влезает на старое место draggedItem. */
    /** isInvSlotFree, но игнорирует один конкретный предмет (нужен для проверки свопа). */
    private boolean isInvSlotFreeIgnoring(int col, int row, int w, int h, LinkedHashMap ignore) {
        if (col < 0 || col + w > INV_COLS || row < 0 || row + h > INV_ROWS) return false;
        for (Object v : store.inventory.values()) {
            if (!(v instanceof LinkedHashMap)) continue;
            LinkedHashMap item = (LinkedHashMap) v;
            if (item == ignore) continue;
            if (!item.containsKey("__inv_x__")) continue;
            int ix = (int) item.get("__inv_x__"), iy = (int) item.get("__inv_y__");
            int iw = item.containsKey("__width__")  ? (int) item.get("__width__")  : 1;
            int ih = item.containsKey("__height__") ? (int) item.get("__height__") : 1;
            if (col < ix + iw && col + w > ix && row < iy + ih && row + h > iy) return false;
        }
        return true;
    }

    /** Возвращает единственный предмет, перекрывающий область col×row w×h, или null если 0 или 2+. */
    private LinkedHashMap getSingleItemInArea(int col, int row, int w, int h) {
        LinkedHashMap found = null;
        for (Object v : store.inventory.values()) {
            if (!(v instanceof LinkedHashMap)) continue;
            LinkedHashMap item = (LinkedHashMap) v;
            if (!item.containsKey("__inv_x__")) continue;
            int ix = (int) item.get("__inv_x__"), iy = (int) item.get("__inv_y__");
            int iw = item.containsKey("__width__")  ? (int) item.get("__width__")  : 1;
            int ih = item.containsKey("__height__") ? (int) item.get("__height__") : 1;
            if (col < ix + iw && col + w > ix && row < iy + ih && row + h > iy) {
                if (found == null) found = item;
                else if (found != item) return null;
            }
        }
        return found;
    }

    /** true если draggedItem подходит к eq-слоту по типу и доступности (для артефактов — учитываем containers). */
    private boolean canPlaceInEqSlot(int slotIdx, LinkedHashMap item) {
        String type = (String) item.get("__type__");
        int[] allowed = allowedEqSlotsForType(type);
        for (int s : allowed) {
            if (s != slotIdx) continue;
            // Артефактные слоты 6-11: доступны только если containers достаточно
            if (s >= 6 && s <= 11) {
                int containers = store.player != null ? store.player.containers : 0;

                // Массив требований для горизонтального порядка открытия
                int[] requiredContainers = {1, 3, 5, 2, 4, 6};

                // Слот доступен, если количество контейнеров игрока покрывает требование слота
                return containers >= requiredContainers[s - 6];
            }
            return true;
        }
        return false;
    }

    private static int[] allowedEqSlotsForType(String type) {
        if (type == null) return new int[0];
        switch (type) {
            case "weapon":   return new int[]{0};
            case "shield":   return new int[]{12};
            case "torch":    return new int[]{12}; // альтернатива щиту/гримуару — тот же слот (см. ItemModifierCatalog "torch")
            case "armor":    return new int[]{4};
            case "helmet":   return new int[]{2};
            case "belt":     return new int[]{5};
            case "gloves":   return new int[]{1};
            case "boots":    return new int[]{13};
            case "amulet":   return new int[]{3};
            case "artifact": return new int[]{6, 8, 10, 7, 9, 11};
            default:         return new int[0];
        }
    }

    // ── Утилиты сетки ────────────────────────────────────────────────────────

    private void clearInvGrid(int col, int row, int w, int h) {
        for (int c = col; c < col + w; c++)
            for (int r = row; r < row + h; r++)
                if (c >= 0 && c < INV_COLS && r >= 0 && r < INV_ROWS) store.inventoryGrid[c][r] = false;
    }

    private void fillInvGrid(int col, int row, int w, int h) {
        for (int c = col; c < col + w; c++)
            for (int r = row; r < row + h; r++)
                if (c >= 0 && c < INV_COLS && r >= 0 && r < INV_ROWS) store.inventoryGrid[c][r] = true;
    }

    private void drawHighlight(SpriteBatch batch, float x, float y, float w, float h, boolean ok) {
        col(batch, ok ? C_HIGHLIGHT_OK : C_HIGHLIGHT_BAD);
        batch.draw(pixel, x + 1, y + 1, w - 2, h - 2);
        batch.setColor(1, 1, 1, 1);
    }

    // ── Тултип ───────────────────────────────────────────────────────────────

    // ── Сравнение и быстрый своп ──────────────────────────────────────────────

    /** true если предмет сейчас находится в одном из eq-слотов. */
    private boolean isEquippedItem(LinkedHashMap item) {
        for (LinkedHashMap eq : store.equipmentSlots) {
            if (eq == item) return true;
        }
        return false;
    }

    /**
     * Возвращает все надетые предметы, с которыми можно сравнить item.
     * Для артефактов — все занятые из слотов 6-11 (до 6 штук).
     * Для остальных типов — первый занятый совместимый слот (список из 1 элемента или пуст).
     */
    private java.util.List<LinkedHashMap> getComparisonItems(LinkedHashMap item) {
        java.util.List<LinkedHashMap> result = new java.util.ArrayList<>();
        if (item == null) return result;
        String type = (String) item.get("__type__");
        for (int slotIdx : allowedEqSlotsForType(type)) {
            LinkedHashMap eq = store.equipmentSlots[slotIdx];
            if (eq != null) {
                result.add(eq);
                if (!"artifact".equals(type)) break; // для не-артефактов — только первый
            }
        }
        return result;
    }

    /**
     * Быстрое действие над предметом инвентаря под курсором (долгое A на геймпаде / шифт-клик
     * мышью, см. pollGamepad/handleClick): расходники — попытка уложить в стек (см. quickStackAt),
     * остальное — быстрый своп с надетым предметом того же типа (см. quickSwapWithEquipped).
     */
    private void quickActionAt(float cx, float cy) {
        int[] cell = getInvCellAt(cx, cy);
        LinkedHashMap hovered = cell != null ? getItemAt(cell[0], cell[1]) : null;
        if (hovered != null && com.nicweiss.editor.utils.ItemModifierCatalog.isConsumableType((String) hovered.get("__type__"))) {
            quickStackAt(cx, cy);
        } else {
            quickSwapWithEquipped(cx, cy);
        }
    }

    /**
     * Быстрая укладка предмета инвентаря в стек — та же логика приоритета, что у подбора с земли
     * (см. StackManager.tryAddToStack). Если в стеке нет места — предмет остаётся в инвентаре как есть.
     */
    private void quickStackAt(float cx, float cy) {
        int[] cell = getInvCellAt(cx, cy);
        if (cell == null) return;
        LinkedHashMap inv = getItemAt(cell[0], cell[1]);
        if (inv == null) return;
        String invType = (String) inv.get("__type__");
        if (!com.nicweiss.editor.utils.ItemModifierCatalog.isConsumableType(invType)) return;

        if (StackManager.tryAddToStack(inv)) {
            int iw = inv.containsKey("__width__")  ? (int) inv.get("__width__")  : 1;
            int ih = inv.containsKey("__height__") ? (int) inv.get("__height__") : 1;
            String key = getItemKey(inv);
            if (key != null) store.inventory.remove(key);
            clearInvGrid((int) inv.get("__inv_x__"), (int) inv.get("__inv_y__"), iw, ih);
        }
    }

    /**
     * Быстрый своп: предмет под курсором ↔ надетый предмет того же типа.
     * Надетый идёт в инвентарь на место выбранного (или в первое свободное, или на землю).
     */
    private void quickSwapWithEquipped(float cx, float cy) {
        // Курсор над eq-слотом — пытаемся снять предмет в инвентарь
        int eqIdx = getEqSlotAt(cx, cy);
        if (eqIdx >= 0 && store.equipmentSlots[eqIdx] != null) {
            LinkedHashMap eqItem = store.equipmentSlots[eqIdx];
            int w = eqItem.containsKey("__width__")  ? (int) eqItem.get("__width__")  : 1;
            int h = eqItem.containsKey("__height__") ? (int) eqItem.get("__height__") : 1;
            int[] slot = findFreeInvSlot(w, h);
            if (slot != null) {
                store.equipmentSlots[eqIdx] = null;
                putInInventory(eqItem, slot[0], slot[1]);
            }
            // Места нет — ничего не делаем
            return;
        }

        // Курсор над предметом в инвентаре — экипируем / свапаем
        int[] cell = getInvCellAt(cx, cy);
        if (cell == null) return;
        LinkedHashMap inv = getItemAt(cell[0], cell[1]);
        if (inv == null) return;

        String invType = (String) inv.get("__type__");

        // Артефакты: только занять свободную ячейку, свап по кнопке недоступен
        if ("artifact".equals(invType)) {
            int targetSlot = -1;
            for (int s : allowedEqSlotsForType(invType)) {
                if (store.equipmentSlots[s] == null) { targetSlot = s; break; }
            }
            if (targetSlot < 0) return; // все 5 слотов заняты — ничего не делаем
            int iw = inv.containsKey("__width__")  ? (int) inv.get("__width__")  : 1;
            int ih = inv.containsKey("__height__") ? (int) inv.get("__height__") : 1;
            String key = getItemKey(inv);
            if (key != null) store.inventory.remove(key);
            clearInvGrid((int) inv.get("__inv_x__"), (int) inv.get("__inv_y__"), iw, ih);
            store.equipmentSlots[targetSlot] = inv;
            return;
        }

        // Если ничего не надето — просто экипируем
        java.util.List<LinkedHashMap> compList = getComparisonItems(inv);
        LinkedHashMap comp = compList.isEmpty() ? null : compList.get(0);
        if (comp == null) {
            int[] allowed = allowedEqSlotsForType(invType);
            if (allowed.length == 0) return;
            int targetSlot = -1;
            for (int s : allowed) { if (store.equipmentSlots[s] == null) { targetSlot = s; break; } }
            if (targetSlot < 0) return;
            int iw = inv.containsKey("__width__")  ? (int) inv.get("__width__")  : 1;
            int ih = inv.containsKey("__height__") ? (int) inv.get("__height__") : 1;
            String key = getItemKey(inv);
            if (key != null) store.inventory.remove(key);
            clearInvGrid((int) inv.get("__inv_x__"), (int) inv.get("__inv_y__"), iw, ih);
            store.equipmentSlots[targetSlot] = inv;
            return;
        }

        // Найти eq-слот надетого предмета
        int eqSlot = -1;
        for (int i = 0; i < store.equipmentSlots.length; i++) {
            if (store.equipmentSlots[i] == comp) { eqSlot = i; break; }
        }
        if (eqSlot < 0 || !canPlaceInEqSlot(eqSlot, inv)) return;

        int invW = inv.containsKey("__width__")  ? (int) inv.get("__width__")  : 1;
        int invH = inv.containsKey("__height__") ? (int) inv.get("__height__") : 1;
        int ic   = (int) inv.get("__inv_x__"), ir = (int) inv.get("__inv_y__");
        int compW = comp.containsKey("__width__")  ? (int) comp.get("__width__")  : 1;
        int compH = comp.containsKey("__height__") ? (int) comp.get("__height__") : 1;

        // Убираем inv из инвентаря
        String invKey = getItemKey(inv);
        if (invKey != null) store.inventory.remove(invKey);
        clearInvGrid(ic, ir, invW, invH);

        // Inv → eq-слот
        store.equipmentSlots[eqSlot] = inv;

        // Comp → на место inv, или в свободную ячейку, или на землю
        if (isInvSlotFree(ic, ir, compW, compH)) {
            putInInventory(comp, ic, ir);
        } else {
            int[] freeSlot = findFreeInvSlot(compW, compH);
            if (freeSlot != null) putInInventory(comp, freeSlot[0], freeSlot[1]);
            else                   DropManager.spawnDropAtPlayer(comp);
        }
    }

    // ── Тултип ───────────────────────────────────────────────────────────────

    /** Рисует два тултипа рядом: слева — надетый (с меткой «Экипировано»), справа — под курсором. */
    /**
     * Рисует основной тултип справа и comparison-тултипы слева.
     * Comparison-тултипы раскладываются колонками по MAX_COMP_PER_COL в высоту.
     */
    private static final int MAX_COMP_PER_COL = 2;

    private void renderTooltipWithComparisons(SpriteBatch batch, LinkedHashMap item,
                                              java.util.List<LinkedHashMap> compList,
                                              float curX, float curY) {
        float gap    = 8f;
        float colGap = 8f;
        float screenW = store.uiWidthOriginal;
        float screenH = store.uiHeightOriginal;
        int   n       = compList.size();

        float[] id = tooltipDimensions(item, false);
        float[][] cd = new float[n][];
        for (int i = 0; i < n; i++) cd[i] = tooltipDimensions(compList.get(i), true);

        int numCols = (n + MAX_COMP_PER_COL - 1) / MAX_COMP_PER_COL;

        // Единая ширина всех comparison-тултипов (max)
        float colW = 0;
        for (float[] d : cd) colW = Math.max(colW, d[0]);

        // Максимальная высота среди всех колонок
        float maxColH = 0;
        for (int col = 0; col < numCols; col++) {
            int from = col * MAX_COMP_PER_COL;
            int to   = Math.min(from + MAX_COMP_PER_COL, n);
            float colH = -gap;
            for (int i = from; i < to; i++) colH += cd[i][1] + gap;
            maxColH = Math.max(maxColH, colH);
        }

        float compAreaW = numCols * colW + (numCols - 1) * colGap;
        float totalW    = compAreaW + id[0] + gap;
        float pairLeft  = Math.max(2f, Math.min(screenW - totalW - 2f, curX - totalW * 0.5f));
        float itemX     = pairLeft + compAreaW + gap;

        float sharedH = Math.max(id[1], maxColH);
        float sharedY = computeTooltipY(curY, sharedH);

        renderTooltipAt(batch, item, itemX, sharedY, id[0], id[1], false);

        for (int col = 0; col < numCols; col++) {
            int from = col * MAX_COMP_PER_COL;
            int to   = Math.min(from + MAX_COMP_PER_COL, n);

            float colH = -gap;
            for (int i = from; i < to; i++) colH += cd[i][1] + gap;

            float colX   = pairLeft + col * (colW + colGap);
            float colTop = sharedY + (sharedH - colH) * 0.5f;
            colTop = Math.max(2f, Math.min(screenH - colH - 2f, colTop));

            float cy = colTop;
            for (int i = from; i < to; i++) {
                renderTooltipAt(batch, compList.get(i), colX, cy, colW, cd[i][1], true);
                cy += cd[i][1] + gap;
            }
        }
    }

    /** Вычисляет Y-позицию тултипа: выше или ниже курсора. Зажимается в экранные границы. */
    private float computeTooltipY(float curY, float th) {
        float screenH = store.uiHeightOriginal;
        float spaceAbove = curY;
        float spaceBelow = screenH - curY;
        float ty = (spaceAbove >= th + 12f || (spaceAbove > spaceBelow && spaceAbove >= th + 4f))
            ? curY - th - 10f : curY + 10f;
        return Math.max(2f, Math.min(screenH - th - 2f, ty));
    }

    /** Вычисляет ширину и высоту тултипа без рендеринга (измеряет все строки). */
    private float[] tooltipDimensions(LinkedHashMap item, boolean equipped) {
        float TPAD = 14f, LINE_H = 22f;
        String typeKey  = (String) item.get("__type__");
        String classKey = (String) item.get("__itemClass__");
        String name     = item.containsKey("__name__") ? (String) item.get("__name__") : "Предмет";

        String subtypeLabel = null;
        if (typeKey != null && classKey != null && com.nicweiss.editor.utils.ItemModifierCatalog.TYPES.containsKey(typeKey))
            subtypeLabel = com.nicweiss.editor.utils.ItemModifierCatalog.TYPES.get(typeKey).labelForClass(classKey);

        String mainStatLine = null;
        if (item.containsKey("__mainStat__"))
            mainStatLine = mainStatLabel(typeKey) + ": " + item.get("__mainStat__");

        // Время горения факела — виден в тултипе (в отличие от цвета свечения, см. applyMod).
        String burnTimeLine = null;
        if ("torch".equals(typeKey) && item.containsKey("__torchBurnTime__"))
            burnTimeLine = "Время горения: " + formatBurnTime(toInt(item.get("__torchBurnTime__")));

        java.util.List<String> reqLines = new java.util.ArrayList<>();
        if (item.containsKey("__reqLevel__")    && toInt(item.get("__reqLevel__"))    > 1) reqLines.add("Требуемый уровень: "  + item.get("__reqLevel__"));
        if (item.containsKey("__reqStrength__") && toInt(item.get("__reqStrength__")) > 0) reqLines.add("Требуемая сила: "     + item.get("__reqStrength__"));
        if (item.containsKey("__reqMagic__")    && toInt(item.get("__reqMagic__"))    > 0) reqLines.add("Требуемая магия: "    + item.get("__reqMagic__"));

        java.util.List<String> modLines = new java.util.ArrayList<>();
        Object statsObj = item.get("__stats__");
        if (statsObj instanceof LinkedHashMap) {
            for (Object v : ((LinkedHashMap) statsObj).values()) {
                if (!(v instanceof LinkedHashMap)) continue;
                LinkedHashMap e = (LinkedHashMap) v;
                String modId = (String) e.get("__modId__");
                int val = toInt(e.get("__value__"));
                com.nicweiss.editor.utils.ItemModifierCatalog.ModifierDef def =
                    typeKey != null ? com.nicweiss.editor.utils.ItemModifierCatalog.findModifier(typeKey, modId) : null;
                modLines.add(def != null ? def.name + ": +" + val + (def.unit.isEmpty() ? "" : " " + def.unit) : modId + ": +" + val);
            }
        }

        int lines = 1 + (subtypeLabel != null ? 1 : 0) + (mainStatLine != null ? 2 : 0)
                  + (burnTimeLine != null ? 1 : 0)
                  + (!modLines.isEmpty() ? modLines.size() + 1 : 0)
                  + (!reqLines.isEmpty() ? reqLines.size() + 1 : 0)
                  + (equipped ? 2 : 0);

        float minW = 180f;
        layout.setText(font, name); minW = Math.max(minW, layout.width);
        if (subtypeLabel != null) { layout.setText(font, subtypeLabel); minW = Math.max(minW, layout.width); }
        if (mainStatLine != null) { layout.setText(font, mainStatLine); minW = Math.max(minW, layout.width); }
        if (burnTimeLine != null) { layout.setText(font, burnTimeLine); minW = Math.max(minW, layout.width); }
        for (String l : modLines) { layout.setText(font, l);            minW = Math.max(minW, layout.width); }
        for (String r : reqLines) { layout.setText(font, r);            minW = Math.max(minW, layout.width); }
        if (equipped)             { layout.setText(font, "Экипировано"); minW = Math.max(minW, layout.width); }

        return new float[]{ minW + TPAD * 2, lines * LINE_H + TPAD * 2 };
    }

    private void renderTooltip(SpriteBatch batch, LinkedHashMap item, float curX, float curY, boolean equipped) {
        float[] d = tooltipDimensions(item, equipped);
        float tw = d[0], th = d[1];
        float screenW = store.uiWidthOriginal;
        float ty = computeTooltipY(curY, th);
        float tx = Math.max(2f, Math.min(screenW - tw - 2f, curX - tw * 0.5f));
        renderTooltipAt(batch, item, tx, ty, tw, th, equipped);
    }

    /** Рисует тултип по явным координатам (tx,ty) с известными размерами. */
    private void renderTooltipAt(SpriteBatch batch, LinkedHashMap item,
                                  float tx, float ty, float tw, float th, boolean equipped) {
        String typeKey   = (String) item.get("__type__");
        String classKey  = (String) item.get("__itemClass__");
        String rarityKey = item.containsKey("__rarity__") ? (String) item.get("__rarity__") : "common";
        String name      = item.containsKey("__name__") ? (String) item.get("__name__") : "Предмет";

        // Подтип (класс предмета)
        String subtypeLabel = null;
        if (typeKey != null && classKey != null && com.nicweiss.editor.utils.ItemModifierCatalog.TYPES.containsKey(typeKey)) {
            subtypeLabel = com.nicweiss.editor.utils.ItemModifierCatalog.TYPES.get(typeKey).labelForClass(classKey);
        }

        // Основная характеристика (урон/защита/ёмкость/скорость/рейтинг атаки — см. mainStatLabel)
        String mainStatLine = null;
        if (item.containsKey("__mainStat__")) {
            mainStatLine = mainStatLabel(typeKey) + ": " + item.get("__mainStat__");
        }

        String burnTimeLine = null;
        if ("torch".equals(typeKey) && item.containsKey("__torchBurnTime__"))
            burnTimeLine = "Время горения: " + formatBurnTime(toInt(item.get("__torchBurnTime__")));

        // Требования
        java.util.List<String> reqLines = new java.util.ArrayList<>();
        if (item.containsKey("__reqLevel__")   && toInt(item.get("__reqLevel__"))   > 1)
            reqLines.add("Требуемый уровень: " + item.get("__reqLevel__"));
        if (item.containsKey("__reqStrength__") && toInt(item.get("__reqStrength__")) > 0)
            reqLines.add("Требуемая сила: " + item.get("__reqStrength__"));
        if (item.containsKey("__reqMagic__")   && toInt(item.get("__reqMagic__"))   > 0)
            reqLines.add("Требуемая магия: " + item.get("__reqMagic__"));

        // Модификаторы: {__modId__, __value__} → строка + цвет, отсортированные по кластеру
        java.util.List<String[]> rawMods = new java.util.ArrayList<>(); // {modId, displayText}
        Object statsObj = item.get("__stats__");
        if (statsObj instanceof LinkedHashMap) {
            for (Object v : ((LinkedHashMap) statsObj).values()) {
                if (!(v instanceof LinkedHashMap)) continue;
                LinkedHashMap statEntry = (LinkedHashMap) v;
                String modId = (String) statEntry.get("__modId__");
                int value = toInt(statEntry.get("__value__"));
                com.nicweiss.editor.utils.ItemModifierCatalog.ModifierDef def =
                    typeKey != null ? com.nicweiss.editor.utils.ItemModifierCatalog.findModifier(typeKey, modId) : null;
                String text = def != null
                    ? def.name + ": +" + value + (def.unit.isEmpty() ? "" : " " + def.unit)
                    : modId + ": +" + value;
                rawMods.add(new String[]{modId, text});
            }
        }
        // Сортировка по кластеру и sub-приоритету внутри кластера
        rawMods.sort((a, b) -> Integer.compare(modSortKey(a[0]), modSortKey(b[0])));

        java.util.List<String>  modLines  = new java.util.ArrayList<>();
        java.util.List<float[]> modColors = new java.util.ArrayList<>();
        for (String[] m : rawMods) {
            modLines.add(m[1]);
            modColors.add(modKeyColor(m[0]));
        }

        // ── Рендер ────────────────────────────────────────────────────────────
        float TPAD = 14f, LINE_H = 22f;
        col(batch, C_TOOLTIP_BG);
        batch.draw(pixel, tx, ty, tw, th);
        col(batch, C_BORDER);
        rect(batch, tx, ty, tw, th);

        float lineY = ty + th - TPAD - LINE_H * 0.72f;

        float[] rc = rarityColor(rarityKey);
        font.setColor(rc[0], rc[1], rc[2], 1f);
        drawCentered(batch, name, tx, lineY, tw); lineY -= LINE_H;

        if (subtypeLabel != null) {
            font.setColor(C_TEXT_DIM);
            drawCentered(batch, subtypeLabel, tx, lineY, tw); lineY -= LINE_H;
        }

        // Основная характеристика — белый
        if (mainStatLine != null) {
            col(batch, C_BORDER); batch.draw(pixel, tx + TPAD, lineY, tw - TPAD * 2, 1f); lineY -= LINE_H * 0.5f;
            font.setColor(C_TEXT_ACT);
            drawCentered(batch, mainStatLine, tx, lineY, tw); lineY -= LINE_H;
        }

        if (burnTimeLine != null) {
            font.setColor(C_TEXT_DIM);
            drawCentered(batch, burnTimeLine, tx, lineY, tw); lineY -= LINE_H;
        }

        // Требования — белый; красный если уровень выше текущего
        if (!reqLines.isEmpty()) {
            boolean playerReady = store.player != null && store.player.isInitialized();
            int pLevel    = playerReady ? store.player.level    : 0;
            int pStrength = playerReady ? store.player.strength : 0;
            int pMagic    = playerReady ? store.player.magic    : 0;
            for (String r : reqLines) {
                boolean failed = playerReady && (
                    (r.startsWith("Требуемый уровень:") && toInt(item.get("__reqLevel__"))    > pLevel)    ||
                    (r.startsWith("Требуемая сила:")    && toInt(item.get("__reqStrength__")) > pStrength) ||
                    (r.startsWith("Требуемая магия:")   && toInt(item.get("__reqMagic__"))    > pMagic)
                );
                font.setColor(failed ? 0.90f : 1f, failed ? 0.25f : 1f, failed ? 0.25f : 1f, 1f);
                drawCentered(batch, r, tx, lineY, tw); lineY -= LINE_H;
            }
        }

        // Модификаторы — каждый в цвете своего кластера
        if (!modLines.isEmpty()) {
            for (int i = 0; i < modLines.size(); i++) {
                float[] c = modColors.get(i);
                font.setColor(c[0], c[1], c[2], 1f);
                drawCentered(batch, modLines.get(i), tx, lineY, tw); lineY -= LINE_H;
            }
        }

        if (equipped) {
            col(batch, C_BORDER); batch.draw(pixel, tx + TPAD, lineY, tw - TPAD * 2, 1f); lineY -= LINE_H * 0.5f;
            font.setColor(0.40f, 0.80f, 0.40f, 1f);
            drawCentered(batch, "Экипировано", tx, lineY, tw);
        }

        batch.setColor(1, 1, 1, 1);
    }

    // ── Цвета модификаторов (мягкие, но читаемые на тёмном фоне тултипа) ──────
    private static final float[] MOD_RESIST  = {0.35f, 0.80f, 0.75f}; // бирюзовый  — резисты/защита
    private static final float[] MOD_SKILL   = {0.72f, 0.55f, 0.90f}; // лиловый    — навыки/FCR
    private static final float[] MOD_LEECH   = {0.88f, 0.42f, 0.55f}; // розовый    — личи
    private static final float[] MOD_POOL    = {0.42f, 0.83f, 0.50f}; // зелёный    — здоровье/мана
    private static final float[] MOD_STATS   = {0.42f, 0.68f, 0.92f}; // голубой    — статы
    private static final float[] MOD_UTILITY = {0.85f, 0.77f, 0.35f}; // золотой    — поиски/утилиты
    private static final float[] MOD_COMBAT  = {0.90f, 0.58f, 0.28f}; // оранжевый  — урон/атака/дебаффы

    /** Порядок кластера для сортировки (меньше = выше в списке). */
    private static int modClusterOrder(String k) {
        float[] c = modKeyColor(k);
        if (c == MOD_SKILL)   return 0;
        if (c == MOD_COMBAT)  return 1;
        if (c == MOD_LEECH)   return 2;
        if (c == MOD_STATS)   return 3;
        if (c == MOD_POOL)    return 4;
        if (c == MOD_RESIST)  return 5;
        return 6; // MOD_UTILITY
    }

    /**
     * Полный ключ сортировки: cluster * 100 + sub.
     * Sub-порядок определяется с учётом кластера — без конфликтов между ними.
     */
    private static int modSortKey(String k) {
        int cluster = modClusterOrder(k);
        int sub;
        switch (cluster) {
            case 0: // SKILL: все навыки → ветки → FCR
                if (k.contains("all_skills"))  sub = 0;
                else if (k.contains("_branch") || k.contains("elem_branch") || k.contains("single_skill")) sub = 1;
                else sub = 2; // _fcr
                break;
            case 1: // COMBAT: урон → скорость атаки → точность → стихийный урон → дебаффы
                if (k.contains("_ed") && !k.contains("_def")) sub = 0;
                else if (k.contains("damage") || k.contains("_maxdmg") || k.contains("flat_min")) sub = 1;
                else if (k.contains("_ias")) sub = 2;
                else if (k.contains("_ar"))  sub = 3;
                else if (k.contains("_fire_dmg") || k.contains("_lightning_dmg")
                      || k.contains("_cold_dmg")  || k.contains("_elem_dmg")) sub = 4;
                else sub = 5; // blind, freeze, itd, itr, thorns, prevent_heal
                break;
            case 2: // LEECH: жизнь → мана
                sub = k.contains("life") ? 0 : 1;
                break;
            case 3: // STATS: сила → энергия/магия → ловкость
                if (k.contains("strength"))  sub = 0;
                else if (k.contains("energy") || k.contains("_magic") || k.contains("_magick")) sub = 1;
                else sub = 2; // dexterity
                break;
            case 4: // POOL: здоровье → мана → восст. здоровья → восст. маны
                if (k.contains("_health"))            sub = 0;
                else if (k.contains("_mana"))         sub = 1;
                else if (k.contains("replenish_life")) sub = 2;
                else sub = 3; // replenish_mana
                break;
            case 5: // RESIST: все рез. → fire → cold → lightning → poison → физ.ред. → маг.ред. → блок → защита
                if (k.contains("allres"))                                       sub = 0;
                else if (k.contains("_res_fire"))                               sub = 1;
                else if (k.contains("_res_cold"))                               sub = 2;
                else if (k.contains("_res_lightning"))                          sub = 3;
                else if (k.contains("_res_poison"))                             sub = 4;
                else if (k.contains("phys_red") || k.contains("phys_reduction")) sub = 5;
                else if (k.contains("magic_red") || k.contains("magic_reduction")) sub = 6;
                else if (k.contains("_block"))                                  sub = 7;
                else sub = 8; // _def, ed_def, flat_def
                break;
            default: // UTILITY: поиск предметов → поиск золота → скорость → свет → контейнеры → опыт
                if (k.contains("_mf"))            sub = 0;
                else if (k.contains("_gf"))       sub = 1;
                else if (k.contains("_frw"))      sub = 2;
                else if (k.contains("light_radius")) sub = 3;
                else if (k.contains("containers")) sub = 4;
                else sub = 5; // experience
                break;
        }
        return cluster * 100 + sub;
    }

    private static float[] modKeyColor(String k) {
        if (k == null) return MOD_COMBAT;
        // Резисты и защита — первыми (специфичные паттерны)
        if (k.contains("allres") || k.contains("_res_") || k.contains("_resist")
         || k.contains("_block") || k.contains("phys_red") || k.contains("magic_red")
         || k.contains("phys_reduction") || k.contains("magic_reduction") || k.contains("_def"))
            return MOD_RESIST;
        // Личи
        if (k.contains("life_leech") || k.contains("mana_leech"))
            return MOD_LEECH;
        // Навыки и скорость каста
        if (k.contains("all_skills") || k.contains("single_skill") || k.contains("_branch")
         || k.contains("_fcr") || k.contains("elem_branch"))
            return MOD_SKILL;
        // Здоровье и мана (replenish тоже сюда)
        if (k.contains("_health") || k.contains("_mana") || k.contains("replenish"))
            return MOD_POOL;
        // Статы (_magic проверяем после magic_red — он уже пойман выше)
        if (k.contains("strength") || k.contains("_magic") || k.contains("_magick")
         || k.contains("energy") || k.contains("dexterity")
         || k.contains("all_attributes"))
            return MOD_STATS;
        // Утилиты — поиски, свет, скорость, контейнеры
        if (k.contains("_mf") || k.contains("_gf") || k.contains("light_radius")
         || k.contains("containers") || k.contains("_frw") || k.contains("experience"))
            return MOD_UTILITY;
        // По умолчанию — боевые
        return MOD_COMBAT;
    }

    /** Рисует строку текста по центру прямоугольника [rx, rx+rw]. */
    private void drawCentered(SpriteBatch batch, String text, float rx, float y, float rw) {
        layout.setText(font, text);
        font.draw(batch, text, rx + (rw - layout.width) * 0.5f, y);
    }

    // Синхронизировано с DropManager.rarityTextColor (лейблы дропа на земле) — тот же циан/малиновый.
    private static float[] rarityColor(String rarity) {
        if (rarity == null) return new float[]{0.85f, 0.88f, 0.95f};
        switch (rarity) {
            case "magic":  return new float[]{0.50f, 0.70f, 1.00f};
            case "rare":   return new float[]{0.15f, 0.95f, 0.90f};
            case "unique": return new float[]{1.00f, 0.10f, 0.75f};
            default:       return new float[]{0.85f, 0.88f, 0.95f};
        }
    }

    private static int toInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}
