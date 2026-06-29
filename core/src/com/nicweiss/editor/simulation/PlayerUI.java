package com.nicweiss.editor.simulation;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;

/**
 * Интерфейс игрока в режиме симуляции.
 *
 * Миникарта (левый верхний угол):
 *   - Схематичный изометрический вид — тайлы расположены в той же проекции что и мир
 *   - Охват: 3× видимая зона камеры (dispW × dispH)
 *   - Плавный скролл через float-позицию игрока (нет целочисленного джиттера)
 */
public class PlayerUI {
    public static Store store;

    private static final int   MINI_PX      = 220;   // размер миникарты на экране (px)
    private static final int   PADDING      = 12;
    private static final float BG_ALPHA     = 0.72f;
    private static final float BORDER_ALPHA = 0.85f;

    private final Texture pixel;

    public PlayerUI() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1f, 1f, 1f, 1f); pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
    }

    public void render(SpriteBatch batch) {
        if (store.player == null || !store.player.isInitialized()) return;
        renderMinimap(batch);
    }

    // ── Миникарта ─────────────────────────────────────────────────────────────

    private void renderMinimap(SpriteBatch batch) {
        if (store.objectedMap == null) return;

        // Позиция в пространстве UI-батча (использует uiHeightOriginal, а не display.height)
        float screenH = store.uiHeightOriginal;
        float mx = PADDING;
        float my = screenH - MINI_PX - PADDING;  // топ-лефт в Y-up координатах
        float cx = mx + MINI_PX / 2f;            // центр миникарты X
        float cy = my + MINI_PX / 2f;            // центр миникарты Y

        // Масштаб: MINI_PX экранных пикселей = 3× ширина камеры
        float dispW   = store.display.get("width");
        float dispH   = store.display.get("height");
        float scale   = (float) MINI_PX / (3f * dispW);

        float tileW   = store.tileSizeWidth;
        float tileH   = store.tileSizeHeight;

        // Float-позиция игрока в тайловых координатах → нет джиттера
        float playerTX = store.player.worldX / tileW;
        float playerTY = store.player.worldY / tileH;

        // Сколько тайлов проверяем в каждую сторону
        // ── Фон ────────────────────────────────────────────────────────────────
        batch.setColor(0.05f, 0.06f, 0.08f, BG_ALPHA);
        batch.draw(pixel, mx, my, MINI_PX, MINI_PX);

        // ── Тайлы в изометрической проекции ───────────────────────────────────
        // Отрисовка back-to-front: сначала тайлы с большим di+dj (дальние)
        float tileMW = tileW * scale;         // ширина тайла на миникарте
        float tileMH = tileH * scale / 2f;    // высота (изо Y = декарт Y / 2)

        // halfRange: нужно заполнить углы квадрата миникарты.
        // Дальняя точка — угол (±MINI_PX/2, ±MINI_PX/2) от центра.
        // В изо isoY=MINI_PX/2 → нужно (MINI_PX/2)/tileMH тайлов по каждой оси.
        int halfRange = (int)(MINI_PX / (2f * tileMH)) + 6;

        int miMin = Math.max(0, (int)(playerTX - halfRange));
        int miMax = Math.min(store.mapHeight - 1, (int)(playerTX + halfRange));
        int mjMin = Math.max(0, (int)(playerTY - halfRange));
        int mjMax = Math.min(store.mapWidth  - 1, (int)(playerTY + halfRange));

        // Ambient освещение + минимальный порог видимости на миникарте
        float rawLight = Math.max(0.27f, 0.2f + store.dayCoefficient);
        rawLight *= (1f - store.rainIntensity * 0.35f);
        rawLight  = Math.min(1f, rawLight);

        // Радиус света источника в тайлах (screen radius 120px / tileSize)
        float lightR = 120f / tileW;
        int   lightCount = store.lightPointsHighWaterMark;

        for (int mi = miMin; mi <= miMax; mi++) {
            for (int mj = mjMax; mj >= mjMin; mj--) {
                float di = mi - playerTX;
                float dj = mj - playerTY;

                float px = cx + (di - dj) * tileW  * scale;
                float py = cy + (di + dj) * tileH  * scale / 2f;

                float dx1 = Math.max(px, mx);
                float dy1 = Math.max(py, my);
                float dx2 = Math.min(px + tileMW, mx + MINI_PX);
                float dy2 = Math.min(py + tileMH, my + MINI_PX);
                if (dx2 <= dx1 || dy2 <= dy1) continue;

                // Вклад источников света (упрощённая формула из calcLight)
                float lightBoost = 0f;
                if (store.lightPoints != null) {
                    for (int li = 1; li <= lightCount; li++) {
                        float[] lp = store.lightPoints[li];
                        if (lp[0] == 0) continue;
                        float dlx = mi - lp[3];
                        float dly = mj - lp[4];
                        // Manhattan quick-reject
                        if (Math.abs(dlx) > lightR || Math.abs(dly) > lightR) continue;
                        float dist = (float)Math.sqrt(dlx*dlx + dly*dly);
                        if (dist < lightR) {
                            float t    = dist / lightR * 100f;
                            float dark = Math.max(0.2f, 1.6f - t * 0.008f);
                            float contrib = Math.max(0f, 1f - (t / (dark*100f + 25f) * 50f) / 500f);
                            // Учитываем цвет источника (индексы 5,6,7 = r,g,b)
                            lightBoost = Math.max(lightBoost, contrib);
                        }
                    }
                }

                float lit = Math.max(rawLight, lightBoost);
                float[] c = tileColor(store.objectedMap[mi][mj].getTextureId());
                batch.setColor(c[0] * lit, c[1] * lit, c[2] * lit, 1f);
                batch.draw(pixel, dx1, dy1, dx2 - dx1, dy2 - dy1);
            }
        }

        // ── Рамка камеры: прямоугольник = текущая зона видимости ───────────────
        float camW = dispW * scale;
        float camH = dispH * scale;
        drawRect(batch,
            cx - camW / 2f, cy - camH / 2f, camW, camH,
            0.85f, 0.85f, 0.85f, 0.60f);

        // ── Точка игрока в центре ──────────────────────────────────────────────
        float dot = Math.max(4f, tileMW * 0.8f);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(pixel, cx - dot / 2f, cy - dot / 2f, dot, dot);

        // ── Внешняя рамка миникарты ────────────────────────────────────────────
        drawRect(batch, mx, my, MINI_PX, MINI_PX, 0.40f, 0.45f, 0.52f, BORDER_ALPHA);

        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawRect(SpriteBatch batch, float x, float y, float w, float h,
                          float r, float g, float b, float a) {
        batch.setColor(r, g, b, a);
        batch.draw(pixel, x,         y,         w, 1);
        batch.draw(pixel, x,         y + h - 1, w, 1);
        batch.draw(pixel, x,         y,         1, h);
        batch.draw(pixel, x + w - 1, y,         1, h);
    }

    // ── Цветовая палитра тайлов ───────────────────────────────────────────────

    private static final float[][] TILE_COLORS = {
        {0.22f, 0.20f, 0.13f},  //  0  голая земля
        {0.20f, 0.35f, 0.10f},  //  1  трава
        {0.07f, 0.24f, 0.04f},  //  2  дуб
        {0.03f, 0.16f, 0.03f},  //  3  ель
        {0.48f, 0.20f, 0.05f},  //  4  осеннее дерево
        {0.42f, 0.42f, 0.38f},  //  5  маленький камень
        {0.44f, 0.44f, 0.40f},  //  6  средний камень
        {0.48f, 0.46f, 0.44f},  //  7  большой камень
        {0.16f, 0.30f, 0.09f},  //  8  сорняк
        {0.55f, 0.36f, 0.16f},  //  9  здание
        {0.20f, 0.35f, 0.10f},  // 10  трава 2
        {0.18f, 0.28f, 0.08f},  // 11  кустарник
    };

    private float[] tileColor(int id) {
        if (id >= 0 && id < TILE_COLORS.length) return TILE_COLORS[id];
        return new float[]{0.28f, 0.28f, 0.28f};
    }
}
