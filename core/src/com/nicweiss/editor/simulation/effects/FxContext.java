package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.Transform;

/**
 * Общие утилиты рендера визуальных эффектов умений (см. пакет effects/) — координаты (мир↔экран),
 * примитивы отрисовки (растянутый пиксель для линий, пунктирное кольцо), общая 1×1
 * текстура-пиксель, константы/запись динамического света (см. Store.skillLightPoints). Вынесено
 * из SkillEffectRenderer, чтобы каждый эффект мог рисовать/освещать себя сам, не таская родителя.
 */
public final class FxContext {
    public static Store store;

    private static Texture pixel;

    private FxContext() {}

    public static Texture pixel() {
        if (pixel == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            pm.fill();
            pixel = new Texture(pm);
            pm.dispose();
        }
        return pixel;
    }

    /** Игрок всегда рисуется в центре экрана (камера следит за ним, см. Player.draw). */
    public static float[] playerScreenPos() {
        return new float[]{store.display.get("width") / 2f, store.display.get("height") / 2f};
    }

    public static float[] aimScreenPos() {
        return worldToScreen(store.cursorWorldX, store.cursorWorldY);
    }

    public static float[] worldToScreen(float wx, float wy) {
        float[] iso = Transform.cartesianToIsometric(wx, wy);
        return new float[]{iso[0] + store.shiftX, iso[1] + store.shiftY};
    }

    public static float[] offset(float[] p, float dx, float dy) {
        return new float[]{p[0] + dx, p[1] + dy};
    }

    /** Повёрнутый растянутый пиксель — линия/росчерк произвольной толщины. */
    public static void drawSeg(SpriteBatch batch, float x1, float y1, float x2, float y2,
                                float thick, float r, float g, float b, float alpha) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
        batch.setColor(r, g, b, alpha);
        batch.draw(pixel(), x1 - thick / 2f, y1, thick / 2f, 0f, thick, len, 1f, 1f, angle, 0, 0, 1, 1, false, false);
        batch.setColor(1, 1, 1, 1);
    }

    /** Кольцо из точек, сплюснутое по Y (тот же изометрический трюк, что тень/лужа под ногами) —
     *  визуально читается как область на земле, а не как окружность в экранной плоскости. */
    public static void drawDottedRing(SpriteBatch batch, float cx, float cy, float radius,
                                       float r, float g, float b, float alpha) {
        int count = Math.max(8, (int) (radius / 6f));
        float dotSize = 5f;
        batch.setColor(r, g, b, alpha);
        for (int i = 0; i < count; i++) {
            float ang = (float) (i * 2 * Math.PI / count);
            float px = cx + (float) Math.cos(ang) * radius;
            float py = cy + (float) Math.sin(ang) * radius * 0.5f;
            batch.draw(pixel(), px - dotSize / 2f, py - dotSize / 2f, dotSize, dotSize);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** Грузит текстуру по пути относительно assets/skills/ (тот же паттерн, что
     *  SystemUI.loadSkillIcon) — без кэша, кэширование каждый эффект делает сам (частота/время
     *  жизни кэша разное — что-то грузится один раз на весь рантайм, что-то на сессию). */
    public static Texture loadSkillTexture(String relPath) {
        java.io.File f = Gdx.files.internal("assets/skills/" + relPath).file();
        if (!f.exists()) return null;
        try {
            return new Texture(Gdx.files.absolute(f.getAbsolutePath()));
        } catch (Exception e) {
            Gdx.app.error("FxContext", "Не удалось загрузить текстуру: " + relPath, e);
            return null;
        }
    }

    // ── Динамический свет эффектов умений (см. Store.skillLightPoints) ─────────────────────────
    // "2-3 клетки вокруг" (по требованию пользователя) — считается напрямую в тайлах карты
    // (store.tileSizeWidth), а не через торч-калибровку MapObject.torchRadius(lightPower) — та
    // калибрована отдельно (px/1 очко силы света) и не даёт ровно N тайлов радиуса.
    public static final float LIGHT_RADIUS_TILES = 4f;
    public static final float LIGHT_INTENSITY = 1.7f; // в 2 раза ярче прежнего (0.85)
    // Температура света: огонь тёплый оранжевый, лёд холодный синий, молния — фиолетовая (тот же
    // цвет, что у разряда WeatherRenderer) — у каждого источника свой цвет.
    public static final float[] LIGHT_COLOR_FIRE = {1.00f, 0.55f, 0.20f};
    public static final float[] LIGHT_COLOR_ICE = {0.35f, 0.65f, 1.00f};
    public static final float[] LIGHT_COLOR_LIGHTNING = {0.60f, 0.35f, 1.00f};

    public static float lightRadiusPx() {
        return LIGHT_RADIUS_TILES * store.tileSizeWidth;
    }

    /** Пишет одну точку света в Store.skillLightPoints, если ещё есть место (см. idxRef[0] —
     *  общий курсор записи на все эффекты сразу, см. SkillEffectRenderer.updateLightSnapshot). */
    public static void writeLight(float[][] pts, int[] idxRef, float wx, float wy, float[] color) {
        if (idxRef[0] >= pts.length) return;
        int idx = idxRef[0]++;
        pts[idx][0] = 1f;
        pts[idx][1] = wx;
        pts[idx][2] = wy;
        pts[idx][3] = lightRadiusPx();
        pts[idx][4] = LIGHT_INTENSITY;
        pts[idx][5] = color[0];
        pts[idx][6] = color[1];
        pts[idx][7] = color[2];
    }
}
