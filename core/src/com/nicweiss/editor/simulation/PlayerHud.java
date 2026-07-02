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
 * HUD игрока в режиме симуляции: здоровье/мана (сферы по бокам), уровень и полоса опыта,
 * плейсхолдеры ячеек навыков/расходников (по центру). Прижат к центру низа экрана,
 * рисуется только пока идёт симуляция (см. Editor.renderUI).
 *
 * Концепция взята из HTML/CSS-прототипа (сферы + центральная панель с ярусами) —
 * геометрия и цвета адаптированы под примитивы libGDX (без border-radius/градиентов):
 * "заливка" сфер сделана обрезкой круглой текстуры по вертикали (снизу), а не CSS-клипом.
 */
public class PlayerHud {
    public static Store store;

    private static final float ORB_SIZE    = 140f;
    private static final float PANEL_W     = 644f;
    private static final float PANEL_H     = 112f;
    private static final float GAP         = 8f;   // между сферами и центральной панелью
    private static final float BOTTOM_PAD  = 14f;

    private static final float SLOT_SIZE    = 53f;
    private static final float SLOT_GAP     = 8f;
    private static final float DIVIDER_GAP  = 20f;

    private static final Color C_ORB_BG        = new Color(0.04f, 0.03f, 0.02f, 1f);
    private static final Color C_ORB_BORDER    = new Color(0.28f, 0.21f, 0.17f, 1f);
    private static final Color C_HEALTH        = new Color(0.75f, 0.10f, 0.10f, 1f);
    private static final Color C_MANA          = new Color(0.10f, 0.40f, 0.95f, 1f);
    private static final Color C_PANEL_BG      = new Color(0.09f, 0.07f, 0.05f, 0.96f);
    private static final Color C_PANEL_LINE    = new Color(0.33f, 0.25f, 0.20f, 1f);
    private static final Color C_XP_BG         = new Color(0.04f, 0.03f, 0.02f, 1f);
    private static final Color C_XP_FILL       = new Color(0.72f, 0.55f, 0.20f, 1f);
    private static final Color C_SLOT_BG       = new Color(0.06f, 0.04f, 0.03f, 1f);
    private static final Color C_SLOT_BORDER   = new Color(0.26f, 0.20f, 0.16f, 1f);
    private static final Color C_SLOT_ITEM_BG      = new Color(0.08f, 0.05f, 0.04f, 1f);
    private static final Color C_SLOT_ITEM_BORDER  = new Color(0.22f, 0.17f, 0.14f, 1f);
    private static final Color C_HOTKEY_BG     = new Color(0.12f, 0.08f, 0.06f, 1f);
    private static final Color C_HOTKEY_TEXT   = new Color(0.64f, 0.54f, 0.46f, 1f);
    private static final Color C_TEXT          = new Color(0.92f, 0.86f, 0.75f, 1f);

    // Навыков пока нет — ячейки декоративные. Ячейки предметов привязаны к store.stacks (см.
    // StackManager) — те же 4 ячейки, что и кнопки 1-4/D-pad (см. SimulationInputThread).
    private static final String[] SKILL_KEYS = {"Q", "W", "E", "R", "ЛКМ", "ПКМ"};
    private static final String[] ITEM_KEYS  = {"1", "2", "3", "4"};

    private final Texture pixel;
    private final Texture circle; // белый круг на прозрачном фоне — основа сфер и ячеек
    private final BitmapFont fontSmall;
    private final GlyphLayout layout;

    // Иконки предметов из стеков — кешируются по пути картинки, чтобы не грузить Texture каждый кадр.
    private final Map<String, Texture> itemIconCache = new HashMap<>();

    public PlayerHud() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        circle = buildCircleTexture(128);
        circle.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("Fonts/Roboto-Medium.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter fp = new FreeTypeFontGenerator.FreeTypeFontParameter();
        fp.size = 17;
        fp.color = Color.WHITE;
        fp.characters = FreeTypeFontGenerator.DEFAULT_CHARS
            + "абвгдеёжзийклмнопрстуфхцчшщъьыэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЬЫЭЮЯ";
        fontSmall = gen.generateFont(fp);
        gen.dispose();

        layout = new GlyphLayout();
    }

    private Texture buildCircleTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();
        pm.setColor(1f, 1f, 1f, 1f);
        pm.fillCircle(size / 2, size / 2, size / 2 - 1);
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    public void render(SpriteBatch batch) {
        if (store.player == null || !store.player.isInitialized()) return;
        // Статы актуальны даже когда инвентарь закрыт — иначе они пересчитываются только
        // во время рендера вкладок ИНВЕНТАРЬ/СТАТЫ (см. SystemUI.recomputeStats).
        if (store.systemUI != null) store.systemUI.recomputeStats();

        Player p = store.player;

        float totalW = ORB_SIZE * 2 + GAP * 2 + PANEL_W;
        float hudX = (store.uiWidthOriginal - totalW) / 2f;
        float hudY = BOTTOM_PAD;

        float healthX = hudX;
        float panelX  = healthX + ORB_SIZE + GAP;
        float manaX   = panelX + PANEL_W + GAP;

        float healthFrac = p.maxHealth > 0 ? clamp01(p.health / p.maxHealth) : 0f;
        float manaFrac   = p.maxMana   > 0 ? clamp01(p.mana   / p.maxMana)   : 0f;

        renderOrb(batch, healthX, hudY, healthFrac, C_HEALTH, (int) p.health + "/" + (int) p.maxHealth);
        renderPanel(batch, panelX, hudY, p);
        renderOrb(batch, manaX, hudY, manaFrac, C_MANA, (int) p.mana + "/" + (int) p.maxMana);

        batch.setColor(1, 1, 1, 1);
    }

    private float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ── Сферы (здоровье/мана) ───────────────────────────────────────────────────

    private void renderOrb(SpriteBatch batch, float x, float y, float fraction, Color fluidColor, String label) {
        col(batch, C_ORB_BORDER);
        batch.draw(circle, x, y, ORB_SIZE, ORB_SIZE);

        float inset = 6f;
        float innerSize = ORB_SIZE - inset * 2;
        col(batch, C_ORB_BG);
        batch.draw(circle, x + inset, y + inset, innerSize, innerSize);

        if (fraction > 0.001f) {
            // "Заливка" — берём только нижнюю полосу круглой текстуры по вертикали (Pixmap: Y растёт вниз),
            // это даёт натурально скруглённый край жидкости без gl-скиссора.
            int texSize = circle.getHeight();
            int srcH = Math.max(1, Math.round(texSize * fraction));
            int srcY = texSize - srcH;
            float fluidH = innerSize * fraction;
            col(batch, fluidColor);
            batch.draw(circle, x + inset, y + inset, innerSize, fluidH,
                0, srcY, texSize, srcH, false, false);
        }

        layout.setText(fontSmall, label);
        fontSmall.setColor(C_TEXT);
        fontSmall.draw(batch, label, x + (ORB_SIZE - layout.width) / 2f, y + (ORB_SIZE + layout.height) / 2f);

        batch.setColor(1, 1, 1, 1);
    }

    // ── Центральная панель: полоса опыта сверху, слоты снизу ───────────────────

    private void renderPanel(SpriteBatch batch, float x, float y, Player p) {
        col(batch, C_PANEL_BG);
        batch.draw(pixel, x, y, PANEL_W, PANEL_H);
        col(batch, C_PANEL_LINE);
        batch.draw(pixel, x, y + PANEL_H - 2, PANEL_W, 2);

        // ── Полоса опыта: вплотную к верхней рамке панели ───────────────────────
        float padX = 22f, padTop = 4f, xpH = 12f;
        float ribbonX = x + padX;
        float ribbonW = (x + PANEL_W - padX) - ribbonX;
        float ribbonY = y + PANEL_H - padTop - xpH;
        col(batch, C_XP_BG);
        batch.draw(pixel, ribbonX, ribbonY, ribbonW, xpH);

        int expToNext = Math.max(1, p.experienceToNextLevel());
        float xpFrac = clamp01(p.experience / (float) expToNext);
        if (xpFrac > 0.001f) {
            col(batch, C_XP_FILL);
            batch.draw(pixel, ribbonX, ribbonY, ribbonW * xpFrac, xpH);
        }

        // ── Нижний ярус: ячейки навыков и расходников (плейсхолдеры) ───────────
        float bottomPad = 11f;
        float slotY = y + bottomPad;

        float skillsW = SKILL_KEYS.length * SLOT_SIZE + (SKILL_KEYS.length - 1) * SLOT_GAP;
        float itemsW  = ITEM_KEYS.length  * SLOT_SIZE + (ITEM_KEYS.length  - 1) * SLOT_GAP;
        float rowW = skillsW + DIVIDER_GAP + itemsW;
        float rowX = x + (PANEL_W - rowW) / 2f;

        float sx = rowX;
        for (String key : SKILL_KEYS) {
            renderSlot(batch, sx, slotY, C_SLOT_BG, C_SLOT_BORDER, key);
            sx += SLOT_SIZE + SLOT_GAP;
        }

        float dividerX = sx + (DIVIDER_GAP - 1f) / 2f;
        col(batch, C_PANEL_LINE);
        batch.draw(pixel, dividerX, slotY + 6f, 1f, SLOT_SIZE - 12f);
        sx += DIVIDER_GAP;

        for (int i = 0; i < ITEM_KEYS.length; i++) {
            ItemStack stack = (store.stacks != null && i < store.stacks.length) ? store.stacks[i] : null;
            renderItemSlot(batch, sx, slotY, ITEM_KEYS[i], stack);
            sx += SLOT_SIZE + SLOT_GAP;
        }

        batch.setColor(1, 1, 1, 1);
    }

    private void renderSlot(SpriteBatch batch, float x, float y, Color bg, Color border, String hotkey) {
        col(batch, border);
        batch.draw(circle, x, y, SLOT_SIZE, SLOT_SIZE);
        float inset = 3f;
        col(batch, bg);
        batch.draw(circle, x + inset, y + inset, SLOT_SIZE - inset * 2, SLOT_SIZE - inset * 2);

        layout.setText(fontSmall, hotkey);
        float tagW = layout.width + 8f;
        float tagH = layout.height + 6f;
        float tagX = x + (SLOT_SIZE - tagW) / 2f;
        float tagY = y - tagH / 2f;
        col(batch, C_HOTKEY_BG);
        batch.draw(pixel, tagX, tagY, tagW, tagH);
        fontSmall.setColor(C_HOTKEY_TEXT);
        fontSmall.draw(batch, hotkey, tagX + 3f, tagY + tagH - 3f);

        batch.setColor(1, 1, 1, 1);
    }

    /** Ячейка стека: иконка первого предмета в очереди + бейдж "N/ёмкость" в нижнем правом углу. */
    private void renderItemSlot(SpriteBatch batch, float x, float y, String hotkey, ItemStack stack) {
        col(batch, C_SLOT_ITEM_BORDER);
        batch.draw(circle, x, y, SLOT_SIZE, SLOT_SIZE);
        float inset = 3f;
        col(batch, C_SLOT_ITEM_BG);
        batch.draw(circle, x + inset, y + inset, SLOT_SIZE - inset * 2, SLOT_SIZE - inset * 2);

        if (stack != null && !stack.items.isEmpty()) {
            LinkedHashMap first = stack.items.get(0);
            Texture icon = iconFor((String) first.get("__image__"));
            if (icon != null) {
                float iconSize = SLOT_SIZE - inset * 2 - 10f;
                batch.setColor(1f, 1f, 1f, 1f);
                batch.draw(icon, x + (SLOT_SIZE - iconSize) / 2f, y + (SLOT_SIZE - iconSize) / 2f, iconSize, iconSize);
            }

            String countText = stack.items.size() + "";
            layout.setText(fontSmall, countText);
            float badgeW = layout.width + 6f, badgeH = layout.height + 4f;
            float badgeX = x + SLOT_SIZE - badgeW - 2f;
            float badgeY = y + 2f;
            col(batch, C_HOTKEY_BG);
            batch.draw(pixel, badgeX, badgeY, badgeW, badgeH);
            fontSmall.setColor(C_TEXT);
            fontSmall.draw(batch, countText, badgeX + 3f, badgeY + badgeH - 2f);
        }

        layout.setText(fontSmall, hotkey);
        float tagW = layout.width + 8f;
        float tagH = layout.height + 6f;
        float tagX = x + (SLOT_SIZE - tagW) / 2f;
        float tagY = y - tagH / 2f;
        col(batch, C_HOTKEY_BG);
        batch.draw(pixel, tagX, tagY, tagW, tagH);
        fontSmall.setColor(C_HOTKEY_TEXT);
        fontSmall.draw(batch, hotkey, tagX + 3f, tagY + tagH - 3f);

        batch.setColor(1, 1, 1, 1);
    }

    private Texture iconFor(String imagePath) {
        if (imagePath == null) return null;
        Texture t = itemIconCache.get(imagePath);
        if (t == null) {
            try {
                t = new Texture(Gdx.files.absolute(imagePath));
                itemIconCache.put(imagePath, t);
            } catch (Exception e) {
                return null;
            }
        }
        return t;
    }

    private void col(SpriteBatch b, Color c) { b.setColor(c.r, c.g, c.b, c.a); }
}
