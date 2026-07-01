package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.nicweiss.editor.Generic.Store;

import java.util.HashMap;
import java.util.LinkedHashMap;
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
    public enum Tab { QUESTS, INVENTORY, STATS, SKILLS, MENU }
    private static final String[] TAB_LABELS = {"ЗАДАНИЯ", "ИНВЕНТАРЬ", "СТАТЫ", "НАВЫКИ", "МЕНЮ"};

    private boolean isOpen    = false;
    private Tab     activeTab = Tab.INVENTORY;

    // ── Меню-кнопки ───────────────────────────────────────────────────────────
    private static final String[] MENU_ITEMS = {"Сохранить", "Загрузить", "Настройки", "Выход"};
    private int menuFocus = 0;

    // ── Размеры ───────────────────────────────────────────────────────────────
    private static final float PANEL_W  = 560f;
    private static final float PANEL_H  = 800f;
    private static final float TAB_H    = 44f;
    private static final float BTN_W    = 240f;
    private static final float BTN_H    = 44f;
    private static final float BTN_GAP  = 12f;

    // ── Инвентарь ────────────────────────────────────────────────────────────
    private static final int CELL     = 47;   // пикселей на ячейку (+30% от 36)
    private static final int GAP      = 16;   // зазор между группами слотов
    private static final int INV_COLS = 10;
    private static final int INV_ROWS = 5;
    private static final int INV_GAP  = 40;  // отступ от снаряжения до инвентаря
    private static final int PAD      = 18;  // внутренний отступ панели

    /**
     * Слоты снаряжения в пиксельных координатах: {xPx, yPx, wCells, hCells}.
     * xPx, yPx — позиция от верхнего-левого угла области снаряжения (↓, →).
     * Все зазоры = GAP=16px.
     *
     * Раскладка (CELL=36, GAP=16):
     *  Левая сторона  (x=0):
     *    Оружие    y=0,   h=4 → низ=144
     *    Перчатки  y=160  (144+16)
     *
     *  Центр (x=88 = 72+16):
     *    Шлем      x=88,  y=0,   w=2,h=2 → низ=72
     *    Амулет    x=176  (88+72+16), y=0
     *    Броня     x=88,  y=88   (72+16)
     *    Пояс      x=88,  y=248  (88+144+16)
     *    Артефакты x=88,140,192,244,296  y=300 (248+36+16), шаг=52(36+16)
     *
     *  Правая сторона (x=348 = 296+36+16):
     *    Щит       y=0,   h=4 → низ=144
     *    Сапоги    y=160
     *
     * Итог: ширина=420px (348+72), высота=336px (300+36)
     */
    /**
     * Размеры области снаряжения (CELL=47, GAP=16, SIDE_Y_OFFSET=70=1.5*CELL):
     *
     * Горизонталь:
     *   Weapon(2*47=94) + GAP(16) + artifacts_span(5*47+4*16=299) + GAP(16) + Shield(94) = 519 → 520
     *   Центр = 260; Helm x=260-47=213; Amulet x=213+94+16=323; Artifacts start=260-149=111
     *
     * Вертикаль (центр-группа):
     *   Helm(94) + GAP(16) = Armor y=110; Armor(188) + GAP(16) = Belt y=314; Belt(47) + GAP(16) = Art y=377
     *   EQ_TOTAL_H = 377+47 = 424
     *
     * Вертикаль (боковые, +70px сдвиг):
     *   Weapon y=70; Gloves y=70+188+16=274; Gloves bottom=274+94=368 < 424 ✓
     */
    private static final int EQ_TOTAL_W = 520;
    private static final int EQ_TOTAL_H = 424;

    private static final int[][] EQ_SLOTS = {
        // {xPx, yPx, wCells, hCells}   CELL=47, GAP=16

        // Левая сторона (сдвиг вниз 70px; x=50: gap_панель≈gap_до_брони ≈70px)
        { 50,  70, 2, 4},  //  0  Оружие       x=50, y=70
        { 50, 274, 2, 2},  //  1  Перчатки      y=274

        // Центр (x=213=260-47; y без сдвига)
        {213,   0, 2, 2},  //  2  Шлем           x=213, y=0
        {323,   0, 1, 1},  //  3  Амулет          x=213+94+16=323
        {213, 110, 2, 4},  //  4  Броня           y=94+16=110
        {213, 314, 2, 1},  //  5  Пояс            y=110+188+16=314
        {111, 377, 1, 1},  //  6  Арт. 1          y=314+47+16=377; x=260-149=111
        {174, 377, 1, 1},  //  7  Арт. 2          x=111+63
        {237, 377, 1, 1},  //  8  Арт. 3
        {300, 377, 1, 1},  //  9  Арт. 4
        {363, 377, 1, 1},  // 10  Арт. 5          x=363, right=410; shield=426 gap=16 ✓

        // Правая сторона (сдвиг вниз 70px; x=376=520-94-50: симметрично оружию)
        {376,  70, 2, 4},  // 11  Щит             x=376
        {376, 274, 2, 2},  // 12  Сапоги
    };
    private static final String[] EQ_NAMES = {
        "Оружие","Перчатки","Шлем","Амулет","Броня",
        "Пояс","","","","","",
        "Щит","Сапоги"
    };

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
    private static final float HOLD_A_SWAP = 1.0f; // секунд до быстрой замены

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

    /** Клавиша поглощена → возвращает true */
    public boolean handleKeyDown(int keyCode) {
        if (keyCode == 37)  { toggle(Tab.QUESTS);    return true; } // I
        if (keyCode == 39)  { toggle(Tab.SKILLS);    return true; } // K
        if (keyCode == 61)  { toggle(Tab.INVENTORY); return true; } // Tab
        if (keyCode == 111) { toggle(Tab.MENU);      return true; } // ESC
        return false;
    }

    /** Клик мышью. @return true — поглощён */
    public boolean handleClick(float mx, float my, float sw, float sh) {
        if (!isOpen) return false;
        store.isGamepadMode = false;
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

        // Клик по кнопкам меню
        if (activeTab == Tab.MENU) {
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

        // Клик в зоне инвентаря
        if (activeTab == Tab.INVENTORY) {
            boolean shift = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                         || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
            if (shift && draggedItem == null) quickSwapWithEquipped(mx, my);
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

        // X — выбросить перетаскиваемый предмет на землю
        if (bx && !prevBX && isOpen && draggedItem != null) dropDraggedToGround();

        // Y — переключить режим сравнения (инвентарь) / скролл вниз (статы)
        if (y && !prevY && isOpen && activeTab == Tab.INVENTORY) compareMode = !compareMode;

        // Скролл вкладки статов левым стиком
        if (isOpen && activeTab == Tab.STATS && Math.abs(stickY) > 0.12f) {
            statsScroll = Math.max(0, statsScroll - stickY * STATS_SCROLL_SPEED * dt);
        }

        if (isOpen && activeTab == Tab.INVENTORY) {
            float contentH = PANEL_H - TAB_H - 1;
            if (Math.abs(stickX) > 0.12f) gpX += stickX * GP_SPEED * dt;
            if (Math.abs(stickY) > 0.12f) gpY -= stickY * GP_SPEED * dt;
            gpX = Math.max(_px, Math.min(_px + PANEL_W - CELL, gpX));
            gpY = Math.max(_py, Math.min(_py + contentH - CELL, gpY));

            // A: долгое удержание (≥1с) — быстрый своп со сравниваемым; короткое — обычный подбор
            if (a) {
                holdATimer += dt;
                if (!holdAFired && holdATimer >= HOLD_A_SWAP) {
                    quickSwapWithEquipped(gpX + CELL * 0.5f, gpY + CELL * 0.5f);
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

        prevStart = start; prevLT = lt; prevRT = rt; prevB = b; prevBX = bx;
        prevY = y; prevAGp = a;
    }

    /** Скролл колёсиком мыши (amountY > 0 = вниз). */
    public boolean handleScroll(float amountY) {
        if (!isOpen || activeTab != Tab.STATS) return false;
        statsScroll = Math.max(0, statsScroll + amountY * STATS_LINE_H * 3);
        return true;
    }

    /** Навигация по кнопкам меню (D-pad / стрелки). */
    public void gamepadNavigate(int dir) {
        if (!isMenuOpen()) return;
        menuFocus = (menuFocus + dir + MENU_ITEMS.length) % MENU_ITEMS.length;
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
            case SKILLS:    renderPlaceholder(batch, px, py, cH, "Навыки не изучены.");    break;
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

    // ── Инвентарь ─────────────────────────────────────────────────────────────

    /**
     * Рисует вкладку инвентаря: сверху сетка снаряжения, снизу главный инвентарь.
     * В Y-up libGDX: строка 0 снаряжения находится ВВЕРХУ (большой Y).
     */
    private void renderStats(SpriteBatch batch, float px, float py, float cH) {
        if (store.player == null) return;
        Player p = store.player;
        recomputePlayerStats();

        // Собираем строки
        java.util.List<String[]> lines = new java.util.ArrayList<>(); // {label, value, color_r, color_g, color_b}

        // Вспомогательные лямбды для формата
        // Базовый стат: "X (B + D)" если D != 0, иначе просто "X"
        java.util.function.BiConsumer<String, int[]> addBase = (name, vals) -> {
            int base = vals[0], bonus = vals[1], total = base + bonus;
            String val = bonus != 0 ? total + " (" + base + " + " + bonus + ")" : String.valueOf(total);
            lines.add(new String[]{name, val, "1.0", "1.0", "1.0"});
        };
        // Простой стат: показываем только если != 0
        java.util.function.BiConsumer<String, int[]> addBonus = (name, vals) -> {
            int v = vals[0]; String suffix = vals.length > 1 ? vals[1] + "" : "";
            if (v == 0) return;
            lines.add(new String[]{name, v + (vals.length > 1 ? "%" : ""), "0.85", "0.88", "0.95"});
        };

        // ── Базовые атрибуты ──────────────────────────────────────────────────
        lines.add(new String[]{"Уровень", String.valueOf(p.level), "0.85", "0.88", "0.95"});
        addBase.accept("Сила",     new int[]{p.baseStrength,  p.strength  - p.baseStrength});
        addBase.accept("Магия",    new int[]{p.baseMagic,     p.magic     - p.baseMagic});
        addBase.accept("Ловкость", new int[]{p.baseDexterity, p.dexterity - p.baseDexterity});
        if (p.stamina   != 0) lines.add(new String[]{"Выносливость", String.valueOf(p.stamina),    "0.85","0.88","0.95"});
        if (p.maxMana   != 0) lines.add(new String[]{"Мана",     "+" + p.maxMana,              "0.42","0.68","0.92"});

        // ── Боевые ────────────────────────────────────────────────────────────
        if (p.physDamage   != 0) lines.add(new String[]{"Физический урон",   "+" + p.physDamage,   "0.91","0.58","0.28"});
        if (p.magicDamage  != 0) lines.add(new String[]{"Магический урон", "+" + p.magicDamage,  "0.72","0.55","0.90"});
        if (p.attackRating != 0) lines.add(new String[]{"Рейтинг атаки",   String.valueOf(p.attackRating), "0.91","0.58","0.28"});
        if (p.attackSpeed  != 0) lines.add(new String[]{"Скорость атаки",  p.attackSpeed  + "%", "0.91","0.58","0.28"});
        if (p.castSpeed    != 0) lines.add(new String[]{"Скорость каста",  p.castSpeed    + "%", "0.72","0.55","0.90"});
        if (p.runSpeed     != 0) lines.add(new String[]{"Скорость бега",   p.runSpeed     + "%", "0.85","0.77","0.35"});

        // ── Защита ────────────────────────────────────────────────────────────
        if (p.defence          != 0) lines.add(new String[]{"Защита",             String.valueOf(p.defence),          "0.35","0.80","0.75"});
        if (p.defenceRating    != 0) lines.add(new String[]{"Повышение защита",      p.defenceRating    + "%",           "0.35","0.80","0.75"});
        if (p.physDamageReduce != 0) lines.add(new String[]{"Снижение физического урона",String.valueOf(p.physDamageReduce), "0.35","0.80","0.75"});
        if (p.magicDamageReduce!= 0) lines.add(new String[]{"Снижение магического урона",String.valueOf(p.magicDamageReduce),"0.35","0.80","0.75"});

        // ── Резисты ───────────────────────────────────────────────────────────
        if (p.fireRes      != 0) lines.add(new String[]{"Сопротивление к огню",   p.fireRes      + "%", "0.35","0.80","0.75"});
        if (p.coldRes      != 0) lines.add(new String[]{"Сопротивление к холоду", p.coldRes      + "%", "0.35","0.80","0.75"});
        if (p.lightningRes != 0) lines.add(new String[]{"Сопротивление к молнии", p.lightningRes + "%", "0.35","0.80","0.75"});

        // ── Личи ─────────────────────────────────────────────────────────────
        if (p.lifeLeech != 0) lines.add(new String[]{"Похищение жизни", p.lifeLeech + "%", "0.88","0.42","0.55"});
        if (p.manaLeech != 0) lines.add(new String[]{"Похищение маны",  p.manaLeech + "%", "0.88","0.42","0.55"});

        // ── Поиски ────────────────────────────────────────────────────────────
        if (p.magicFind  != 0) lines.add(new String[]{"Поиск предметов",  (int)p.magicFind + "%", "0.85","0.77","0.35"});
        if (p.goldFind   != 0) lines.add(new String[]{"Поиск золота",     (int)p.goldFind  + "%", "0.85","0.77","0.35"});
        if (p.containers != 0) lines.add(new String[]{"Контейнеры",       String.valueOf(p.containers), "0.85","0.88","0.95"});

        // ── Рендер ────────────────────────────────────────────────────────────
        float totalH    = lines.size() * STATS_LINE_H + STATS_PAD * 2;
        float maxScroll = Math.max(0, totalH - cH);
        statsScroll     = Math.min(statsScroll, maxScroll);

        float contentTop = py + cH - STATS_PAD + statsScroll;
        float colValX    = px + PANEL_W - STATS_PAD;

        for (String[] entry : lines) {
            float lineY = contentTop - (lines.indexOf(entry) + 1) * STATS_LINE_H;
            if (lineY + STATS_LINE_H < py) break;           // ниже видимой области
            if (lineY > py + cH)           continue;         // выше видимой области

            float r = Float.parseFloat(entry[2]), g = Float.parseFloat(entry[3]), b = Float.parseFloat(entry[4]);
            font.setColor(r, g, b, 1f);
            font.draw(batch, entry[0], px + STATS_PAD, lineY);

            layout.setText(font, entry[1]);
            font.draw(batch, entry[1], colValX - layout.width, lineY);
        }

        // Полоса скролла
        if (maxScroll > 0) {
            float trackH  = cH - STATS_PAD * 2;
            float thumbH  = Math.max(20f, trackH * (cH / totalH));
            float thumbY  = py + STATS_PAD + (trackH - thumbH) * (1f - statsScroll / maxScroll);
            col(batch, C_BORDER);
            batch.draw(pixel, px + PANEL_W - 6f, py + STATS_PAD, 4f, trackH);
            col(batch, C_FOCUS_OUT);
            batch.draw(pixel, px + PANEL_W - 6f, thumbY, 4f, thumbH);
        }

        batch.setColor(1, 1, 1, 1);
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

        // ── Серые заблокированные слоты артефактов ───────────────────────────
        int availContainers = store.player != null ? store.player.containers : 0;
        for (int i = 6; i <= 10; i++) {
            if ((i - 6) < availContainers) continue;
            int[] s = EQ_SLOTS[i];
            col(batch, new com.badlogic.gdx.graphics.Color(0.12f, 0.12f, 0.15f, 0.88f));
            batch.draw(pixel, gridX + s[0] + 1, eqTop - s[1] - s[3] * CELL + 1,
                       s[2] * CELL - 2, s[3] * CELL - 2);
        }
        batch.setColor(1, 1, 1, 1);

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
        if (eq >= 0 && canPlaceInEqSlot(eq, draggedItem)) {
            // Проверяем требования: нельзя надеть если не хватает статов
            if (store.player != null && !itemMeetsRequirements(draggedItem, store.player)) return;
            LinkedHashMap existing = store.equipmentSlots[eq];
            store.equipmentSlots[eq] = draggedItem;
            draggedItem = existing;
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

        // Сброс к базовым значениям
        p.strength = p.baseStrength; p.magic = p.baseMagic; p.dexterity = p.baseDexterity;
        p.energy = 0; p.stamina = 0; p.maxMana = 0;
        p.fireRes = 0; p.coldRes = 0; p.lightningRes = 0;
        p.attackSpeed = 0; p.castSpeed = 0; p.runSpeed = 0;
        p.attackRating = 0; p.physDamage = 0; p.magicDamage = 0;
        p.defence = 0; p.defenceRating = 0;
        p.physDamageReduce = 0; p.magicDamageReduce = 0;
        p.containers = 0;
        p.lifeLeech = 0; p.manaLeech = 0;
        p.magicFind = 0f; p.goldFind = 0f;

        // Чармы — активны если уровень игрока >= требуемого
        for (Object v : store.inventory.values()) {
            if (!(v instanceof LinkedHashMap)) continue;
            LinkedHashMap item = (LinkedHashMap) v;
            if (item == draggedItem) continue;
            if ("charm".equals(item.get("__type__")) && itemMeetsRequirements(item, p))
                applyItemStats(item, p);
        }

        // Фаза 1: всё снаряжение кроме артефактов (слоты 6-10), итеративно
        // Нужно сначала получить containers от пояса, чтобы затем решить что делать с артефактами
        boolean[] active = new boolean[store.equipmentSlots.length];
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < store.equipmentSlots.length; i++) {
                if (i >= 6 && i <= 10) continue; // артефакты — в фазе 2
                LinkedHashMap item = store.equipmentSlots[i];
                if (item == null || item == draggedItem || active[i]) continue;
                if (itemMeetsRequirements(item, p)) {
                    applyItemStats(item, p);
                    active[i] = true;
                    changed = true;
                }
            }
        }

        // Фаза 2: артефакты — только в пределах containers
        for (int i = 6; i <= 10; i++) {
            LinkedHashMap item = store.equipmentSlots[i];
            if (item == null || item == draggedItem) continue;
            boolean slotUnlocked = (i - 6) < p.containers;
            if (slotUnlocked && itemMeetsRequirements(item, p)) {
                applyItemStats(item, p);
                active[i] = true;
            }
        }

        // Помечаем неактивные слоты
        store.inactiveEquipment.clear();
        for (int i = 0; i < store.equipmentSlots.length; i++) {
            LinkedHashMap item = store.equipmentSlots[i];
            if (item != null && item != draggedItem && !active[i])
                store.inactiveEquipment.add(item);
        }
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
        if (!(statsObj instanceof LinkedHashMap)) return;
        for (Object v : ((LinkedHashMap) statsObj).values()) {
            if (!(v instanceof LinkedHashMap)) continue;
            LinkedHashMap stat = (LinkedHashMap) v;
            applyMod((String) stat.get("__modId__"), toInt(stat.get("__value__")), p);
        }
    }

    private static void applyMod(String k, int v, Player p) {
        if (k == null) return;
        // Снижение урона проверяем ДО _magic/_mana чтобы не поймать magic_red в _magic
        if      (k.contains("phys_red") || k.contains("phys_reduction"))  p.physDamageReduce  += v;
        else if (k.contains("magic_red") || k.contains("magic_reduction")) p.magicDamageReduce += v;
        else if (k.contains("strength"))     p.strength      += v;
        else if (k.contains("_magic") || k.contains("_magick")) p.magic += v;
        else if (k.contains("energy"))       p.energy        += v;
        else if (k.contains("dexterity"))    p.dexterity     += v;
        else if (k.contains("stamina"))      p.stamina       += v;
        else if (k.contains("_health"))      p.maxHealth     += v;  // float field
        else if (k.contains("_mana") && !k.contains("leech") && !k.contains("replenish")) p.maxMana += v;
        else if (k.contains("allres"))       { p.fireRes += v; p.coldRes += v; p.lightningRes += v; }
        else if (k.contains("_res_fire"))    p.fireRes       += v;
        else if (k.contains("_res_cold"))    p.coldRes       += v;
        else if (k.contains("_res_lightning")) p.lightningRes += v;
        else if (k.contains("life_leech"))   p.lifeLeech     += v;
        else if (k.contains("mana_leech"))   p.manaLeech     += v;
        else if (k.contains("_mf"))          p.magicFind     += v;
        else if (k.contains("_gf"))          p.goldFind      += v;
        else if (k.contains("_ias"))         p.attackSpeed   += v;
        else if (k.contains("_fcr"))         p.castSpeed     += v;
        else if (k.contains("_frw"))         p.runSpeed      += v;
        else if (k.contains("_ar"))          p.attackRating  += v;
        else if (k.contains("_fire_dmg") || k.contains("_lightning_dmg")
              || k.contains("_cold_dmg")  || k.contains("_elem_dmg")) p.magicDamage += v;
        else if (k.contains("flat_def"))     p.defence       += v;
        else if (k.contains("_def"))         p.defenceRating += v;  // ed_def, def_plate, def_robe
        else if (k.contains("_maxdmg") || k.contains("flat_min"))          p.physDamage += v;
        else if (k.contains("_ed") && !k.contains("_def"))                 p.physDamage += v;
        else if (k.contains("damage") && !k.contains("magic") && !k.contains("_red")) p.physDamage += v;
        else if (k.contains("all_attributes")) { p.strength += v; p.magic += v; p.dexterity += v; }
        else if (k.contains("containers"))   p.containers    += v;
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
        return eq >= 0 ? store.equipmentSlots[eq] : null;
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
            // Артефактные слоты 6-10: доступны только если containers достаточно
            if (s >= 6 && s <= 10) {
                int containers = store.player != null ? store.player.containers : 0;
                return (s - 6) < containers;
            }
            return true;
        }
        return false;
    }

    private static int[] allowedEqSlotsForType(String type) {
        if (type == null) return new int[0];
        switch (type) {
            case "weapon":   return new int[]{0};
            case "shield":   return new int[]{11};
            case "armor":    return new int[]{4};
            case "helmet":   return new int[]{2};
            case "belt":     return new int[]{5};
            case "gloves":   return new int[]{1};
            case "boots":    return new int[]{12};
            case "amulet":   return new int[]{3};
            case "artifact": return new int[]{6, 7, 8, 9, 10};
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
     * Для артефактов — все занятые из слотов 6-10 (до 5 штук).
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
            mainStatLine = ("weapon".equals(typeKey) ? "Урон" : "Защита") + ": " + item.get("__mainStat__");

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
                  + (!modLines.isEmpty() ? modLines.size() + 1 : 0)
                  + (!reqLines.isEmpty() ? reqLines.size() + 1 : 0)
                  + (equipped ? 2 : 0);

        float minW = 180f;
        layout.setText(font, name); minW = Math.max(minW, layout.width);
        if (subtypeLabel != null) { layout.setText(font, subtypeLabel); minW = Math.max(minW, layout.width); }
        if (mainStatLine != null) { layout.setText(font, mainStatLine); minW = Math.max(minW, layout.width); }
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

        // Основная характеристика (урон/защита)
        String mainStatLine = null;
        if (item.containsKey("__mainStat__")) {
            String statLabel = "weapon".equals(typeKey) ? "Урон" : "Защита";
            mainStatLine = statLabel + ": " + item.get("__mainStat__");
        }

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
            case 3: // STATS: сила → энергия/магия → ловкость → стамина
                if (k.contains("strength"))  sub = 0;
                else if (k.contains("energy") || k.contains("_magic") || k.contains("_magick")) sub = 1;
                else if (k.contains("dexterity")) sub = 2;
                else sub = 3; // stamina
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
                else sub = 5; // experience, stamina_regen
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
         || k.contains("energy") || k.contains("dexterity") || k.contains("stamina")
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

    private static float[] rarityColor(String rarity) {
        if (rarity == null) return new float[]{0.85f, 0.88f, 0.95f};
        switch (rarity) {
            case "magic":  return new float[]{0.50f, 0.70f, 1.00f};
            case "rare":   return new float[]{1.00f, 0.97f, 0.10f};
            case "unique": return new float[]{1.00f, 0.55f, 0.08f};
            default:       return new float[]{0.85f, 0.88f, 0.95f};
        }
    }

    private static int toInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}
