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

    // Ячейки навыков — см. Player.mainSkillSlots/comboSkillSlots (com.nicweiss.editor.utils.SkillSlot),
    // привязка настраивается в окне пикера (SystemUI — вкладка Навыки). Ячейки предметов привязаны
    // к store.stacks (см. StackManager) — те же 4 ячейки, что и кнопки 1-4/D-pad (SimulationInputThread).
    private static final int SKILL_SLOT_COUNT = 6;
    private static final String[] ITEM_KEYS  = {"1", "2", "3", "4"};

    // Комбо-ряд (Shift/LT) показывается ВМЕСТО основного, а не рядом — считается каждый render().
    private boolean showComboRow = false;

    private final Texture pixel;
    private final Texture circle; // белый круг на прозрачном фоне — основа сфер и ячеек
    private final BitmapFont fontSmall;
    private final GlyphLayout layout;

    // Иконки предметов из стеков — кешируются по пути картинки, чтобы не грузить Texture каждый кадр.
    private final Map<String, Texture> itemIconCache = new HashMap<>();
    // Иконки умений — В ОТЛИЧИЕ от itemIconCache, кэшируем уже КРУГЛО ОБРЕЗАННУЮ версию (см.
    // circularSkillIcon) — исходники в assets/skills/ прямоугольные (128x128, непрозрачны до
    // самых углов), а ячейка круглая: без обрезки квадратные углы вылезали бы за пределы круга.
    private final Map<String, Texture> skillIconCache = new HashMap<>();

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

        // Комбо-ряд ячеек умений показывается вместо основного, пока зажат Shift (клавиатура)
        // или левый триггер геймпада (Store.leftTriggerHeld, пишет SimulationInputThread.pollFrame).
        boolean shiftHeld = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                         || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
        showComboRow = shiftHeld || store.leftTriggerHeld;

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

        float skillsW = SKILL_SLOT_COUNT * SLOT_SIZE + (SKILL_SLOT_COUNT - 1) * SLOT_GAP;
        float itemsW  = ITEM_KEYS.length  * SLOT_SIZE + (ITEM_KEYS.length  - 1) * SLOT_GAP;
        float rowW = skillsW + DIVIDER_GAP + itemsW;
        float rowX = x + (PANEL_W - rowW) / 2f;

        com.nicweiss.editor.utils.SkillSlot[] skillSlots = showComboRow ? p.comboSkillSlots : p.mainSkillSlots;
        float sx = rowX;
        for (int i = 0; i < SKILL_SLOT_COUNT; i++) {
            renderSkillSlot(batch, sx, slotY, skillSlots[i]);
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

    /** Ячейка умения: пустая — без подписи (ещё не привязана, см. SystemUI — окно пикера); занятая —
     *  иконка умения (круглый вырез, см. circularSkillIcon) + подпись фактически привязанной кнопки
     *  НАД ячейкой. */
    private void renderSkillSlot(SpriteBatch batch, float x, float y, com.nicweiss.editor.utils.SkillSlot slot) {
        boolean bound = slot != null && slot.skillId != null;
        col(batch, bound ? C_SLOT_ITEM_BORDER : C_SLOT_BORDER);
        batch.draw(circle, x, y, SLOT_SIZE, SLOT_SIZE);
        float inset = 3f;
        col(batch, bound ? C_SLOT_ITEM_BG : C_SLOT_BG);
        batch.draw(circle, x + inset, y + inset, SLOT_SIZE - inset * 2, SLOT_SIZE - inset * 2);

        if (bound) {
            // Иконка на ВЕСЬ внутренний круг (та же геометрия, что и фон-заливка выше) — уже
            // обрезана по кругу в самой текстуре (см. circularSkillIcon), поэтому можно рисовать
            // квадратом ровно по диаметру круга без риска "квадратных" углов.
            com.nicweiss.editor.utils.SkillCatalog.SkillDef def = com.nicweiss.editor.utils.SkillCatalog.SKILLS.get(slot.skillId);
            Texture icon = def != null ? circularSkillIcon(def.imageFile) : null;
            if (icon != null) {
                float iconSize = SLOT_SIZE - inset * 2;
                batch.setColor(1f, 1f, 1f, 1f);
                batch.draw(icon, x + inset, y + inset, iconSize, iconSize);
            }

            // Клавиатура/мышь и геймпад — РАЗДЕЛЬНЫЕ привязки (см. SkillSlot) — показываем ту,
            // что актуальна для текущего режима ввода (store.isGamepadMode, переключается сам по
            // последнему источнику ввода — нажатие на геймпаде переводит в режим геймпада со
            // своими привязками, клавиатура/мышь — обратно, каждая читает только своё поле).
            String activeCode = store.isGamepadMode ? slot.gamepadInputCode : slot.keyboardInputCode;
            if (activeCode != null) {
                String hotkey = displayLabelFor(activeCode);
                layout.setText(fontSmall, hotkey);
                float tagW = layout.width + 8f;
                float tagH = layout.height + 6f;
                float tagX = x + (SLOT_SIZE - tagW) / 2f;
                float tagY = y - tagH / 2f;
                col(batch, C_HOTKEY_BG);
                batch.draw(pixel, tagX, tagY, tagW, tagH);
                fontSmall.setColor(C_HOTKEY_TEXT);
                fontSmall.draw(batch, hotkey, tagX + 3f, tagY + tagH - 3f);
            }
        }

        batch.setColor(1, 1, 1, 1);
    }

    /** Стабильный inputCode ("KEY_Q", "MOUSE_LEFT", "PAD_X" и т.п., см. SkillSlot) → короткая
     *  подпись для HUD. */
    private static String displayLabelFor(String inputCode) {
        if (inputCode == null) return "?";
        switch (inputCode) {
            case "MOUSE_LEFT":  return "ЛКМ";
            case "MOUSE_RIGHT": return "ПКМ";
            case "PAD_X": return "X";
            case "PAD_Y": return "Y";
            case "PAD_B": return "B";
            case "PAD_R1": return "R1";
            case "PAD_R2": return "R2";
            case "PAD_R3": return "R3";
            default:
                // "KEY_Q" -> "Q"
                return inputCode.startsWith("KEY_") ? inputCode.substring(4) : inputCode;
        }
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

    /** Грузит иконку умения (assets/skills/&lt;imageFile&gt;, тот же приём разрешения пути, что
     *  ItemGenerator.applyDefaultImage) и вырезает круг по центру — исходники прямоугольные и
     *  непрозрачны до самых углов, без обрезки они торчали бы за пределы круглой ячейки. */
    private Texture circularSkillIcon(String imageFile) {
        if (imageFile == null) return null;
        Texture cached = skillIconCache.get(imageFile);
        if (cached != null) return cached;
        try {
            java.io.File file = Gdx.files.internal("assets/skills/" + imageFile).file();
            if (!file.exists()) return null;

            Pixmap raw = new Pixmap(Gdx.files.absolute(file.getAbsolutePath()));
            Pixmap rgba = raw;
            if (raw.getFormat() != Pixmap.Format.RGBA8888) {
                rgba = new Pixmap(raw.getWidth(), raw.getHeight(), Pixmap.Format.RGBA8888);
                rgba.setBlending(Pixmap.Blending.None);
                rgba.drawPixmap(raw, 0, 0);
                raw.dispose();
            }

            Pixmap masked = maskToCircle(rgba);
            if (rgba != raw) rgba.dispose(); else raw.dispose();

            Texture t = new Texture(masked);
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            masked.dispose();
            skillIconCache.put(imageFile, t);
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    /** Обнуляет альфу за пределами вписанного круга (с лёгким 1.5px сглаживанием на границе) —
     *  единственный надёжный способ гарантировать "ничего не вылезает за круг" независимо от
     *  того, что реально нарисовано в исходном прямоугольном файле. */
    private static Pixmap maskToCircle(Pixmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Pixmap out = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        out.setBlending(Pixmap.Blending.None);
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        float feather = 1.5f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                int rgba = src.getPixel(x, y);
                if (dist > r) {
                    rgba &= 0xFFFFFF00;
                } else if (dist > r - feather) {
                    int a = rgba & 0xFF;
                    int newA = Math.round(a * ((r - dist) / feather));
                    rgba = (rgba & 0xFFFFFF00) | (newA & 0xFF);
                }
                out.drawPixel(x, y, rgba);
            }
        }
        return out;
    }

    private void col(SpriteBatch b, Color c) { b.setColor(c.r, c.g, c.b, c.a); }
}
