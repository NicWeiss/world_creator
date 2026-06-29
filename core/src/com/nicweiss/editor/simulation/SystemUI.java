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
    public enum Tab { QUESTS, INVENTORY, SKILLS, MENU }
    private static final String[] TAB_LABELS = {"ЗАДАНИЯ", "ИНВЕНТАРЬ", "НАВЫКИ", "МЕНЮ"};

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

    // ── Геймпад edge-detect ───────────────────────────────────────────────────
    private boolean prevLT = false, prevRT = false, prevStart = false, prevB = false;

    // ── GL-ресурсы ────────────────────────────────────────────────────────────
    private final Texture    pixel;
    private final BitmapFont font;
    private final GlyphLayout layout;

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
        float px = (sw - PANEL_W) / 2f, py = (sh - PANEL_H) / 2f;

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
                if (mx >= cx && mx <= cx+BTN_W && my >= by && my <= by+BTN_H) {
                    menuFocus = i;
                    activateMenuItem(i);
                    return true;
                }
            }
        }

        // Клик вне панели — закрыть
        if (mx < px || mx > px+PANEL_W || my < py || my > py+PANEL_H) isOpen = false;
        return isOpen;
    }

    /** Опрос кнопок геймпада (edge-detect внутри). */
    public void pollGamepad(boolean start, boolean lt, boolean rt, boolean b) {
        // Start: открывает ИНВЕНТАРЬ если закрыт, закрывает если открыт
        if (start && !prevStart) {
            if (isOpen) isOpen = false;
            else         toggle(Tab.INVENTORY);
        }
        // LT/RT: переключают вкладки ТОЛЬКО когда UI уже открыт
        if (lt && !prevLT && isOpen) switchTab(-1);
        if (rt && !prevRT && isOpen) switchTab(+1);
        // B: закрывает UI
        if (b  && !prevB  && isOpen) isOpen = false;

        prevStart = start; prevLT = lt; prevRT = rt; prevB = b;
    }

    /** Навигация по кнопкам меню (D-pad / стрелки). */
    public void gamepadNavigate(int dir) {
        if (!isMenuOpen()) return;
        menuFocus = (menuFocus + dir + MENU_ITEMS.length) % MENU_ITEMS.length;
    }

    /** Активация кнопки в фокусе (кнопка A). */
    public void gamepadActivate() {
        if (!isMenuOpen()) return;
        activateMenuItem(menuFocus);
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
    private void renderInventory(SpriteBatch batch, float px, float py, float cH) {
        // Центрируем оборудование и инвентарь по ширине панели
        float gridX = px + (PANEL_W - EQ_TOTAL_W) / 2f;
        float eqTop = py + cH - PAD; // верх области снаряжения (Y-up)

        // ── Слоты снаряжения ─────────────────────────────────────────────────
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            int[] s = EQ_SLOTS[i]; // {xPx, yPx, wCells, hCells}
            drawSlotPx(batch, gridX, eqTop, s[0], s[1], s[2], s[3], EQ_NAMES[i]);
        }

        float invGridX = px + (PANEL_W - (float)(INV_COLS * CELL)) / 2f;
        float invTop   = eqTop - EQ_TOTAL_H - INV_GAP;

        font.setColor(C_TEXT_DIM);
        layout.setText(font, "Инвентарь");
        font.draw(batch, "Инвентарь", invGridX, invTop + layout.height + 3);

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                drawSlotPx(batch, invGridX, invTop, col * CELL, row * CELL, 1, 1, null);
            }
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
        if (isOpen && activeTab == tab) isOpen = false;
        else { isOpen = true; activeTab = tab; }
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
}
