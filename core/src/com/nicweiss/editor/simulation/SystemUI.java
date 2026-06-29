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
    private static final float PANEL_W  = 620f;
    private static final float PANEL_H  = 440f;
    private static final float TAB_H    = 44f;
    private static final float BTN_W    = 240f;
    private static final float BTN_H    = 44f;
    private static final float BTN_GAP  = 12f;

    // ── Цвета ─────────────────────────────────────────────────────────────────
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
            case INVENTORY: renderPlaceholder(batch, px, py, cH, "Инвентарь пуст.");       break;
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
