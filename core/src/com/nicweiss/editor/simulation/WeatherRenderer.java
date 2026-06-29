package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;

/**
 * Отвечает за визуальный рендер погоды в режиме симуляции.
 *
 * Создаётся на GL-потоке (нужен GL-контекст для текстур).
 * WeatherThread управляет состоянием (rainIntensity, windMultiplier и т.д.) через Store.
 * WeatherRenderer читает эти значения и рисует частицы.
 */
public class WeatherRenderer {
    public static Store store;

    // ── Константы ─────────────────────────────────────────────────────────────
    private static final int   N_DROPS         = 600;
    private static final float LIGHT_RADIUS    = 160f;
    private static final float SPLASH_DURATION = 0.22f;
    private static final int   N_SPLASHES      = 200;

    // ── Капли ─────────────────────────────────────────────────────────────────
    private final float[] dropWX  = new float[N_DROPS]; // мировой декарт. X точки приземления
    private final float[] dropWY  = new float[N_DROPS];
    private final float[] dropAlt = new float[N_DROPS]; // высота над поверхностью (px), 0 = земля
    private final float[] dropSpd = new float[N_DROPS]; // скорость падения (px/s)

    // ── Всплески ──────────────────────────────────────────────────────────────
    private final float[] splashX   = new float[N_SPLASHES];
    private final float[] splashY   = new float[N_SPLASHES];
    private final float[] splashAge = new float[N_SPLASHES]; // 1→0; 0 = неактивен
    private int splashNext = 0;

    private boolean dropsReady = false;
    private float   lastShiftX = Float.NaN;
    private float   lastShiftY = Float.NaN;

    // ── Текстуры ──────────────────────────────────────────────────────────────
    private final Texture dropTex;
    private final Texture splashTex;
    private final Texture flashTex;

    public WeatherRenderer() {
        // Текстура капли: 2×14, альфа 0.35→1.0
        Pixmap dp = new Pixmap(2, 14, Pixmap.Format.RGBA8888);
        dp.setColor(0, 0, 0, 0); dp.fill();
        for (int py = 0; py < 14; py++) {
            float a = 0.35f + (py / 13f) * 0.65f;
            dp.setColor(1f, 1f, 1f, a);
            dp.drawPixel(0, py);
            dp.drawPixel(1, py);
        }
        dropTex = new Texture(dp);
        dp.dispose();

        // Текстура всплеска: изометрически плоский овальный контур 8×4
        Pixmap sp = new Pixmap(8, 4, Pixmap.Format.RGBA8888);
        sp.setColor(0, 0, 0, 0); sp.fill();
        sp.setColor(0.8f, 0.9f, 1f, 1f);
        sp.drawPixel(2,0); sp.drawPixel(3,0); sp.drawPixel(4,0); sp.drawPixel(5,0);
        sp.drawPixel(2,3); sp.drawPixel(3,3); sp.drawPixel(4,3); sp.drawPixel(5,3);
        sp.drawPixel(0,1); sp.drawPixel(0,2);
        sp.drawPixel(7,1); sp.drawPixel(7,2);
        splashTex = new Texture(sp);
        sp.dispose();

        // 1×1 белый пиксель для вспышки молнии
        Pixmap fp = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        fp.setColor(1f, 1f, 1f, 1f); fp.fill();
        flashTex = new Texture(fp);
        fp.dispose();
    }

    /** Вызывается GL-потоком каждый кадр после рендера карты. */
    public void render(SpriteBatch batch) {
        renderRain(batch);
        renderSplash(batch);
        renderLightningFlash(batch);
    }

    // ── Дождь ─────────────────────────────────────────────────────────────────

    private void resetDrop(int k, float W, float H) {
        float sx   = (float)(Math.random() * (W + 400)) - 200;
        float sy   = (float)(Math.random() * H * 1.2f);
        float isoX = sx - store.shiftX;
        float isoY = sy - store.shiftY;
        dropWX[k]  = (isoX + 2f * isoY) / 2f;
        dropWY[k]  = (2f * isoY - isoX) / 2f;
        dropAlt[k] = (float)(Math.random() * H * 1.4f);
    }

    /**
     * Дождь в мировом пространстве с компенсацией движения камеры.
     *
     * (dropWX, dropWY) — декартовая точка приземления на карте (фиксирована).
     * dropAlt — высота над поверхностью; 0 = приземление на тайл.
     *
     * Компенсация: при движении камеры на (dSX, dSY) корректируем мировые
     * координаты чтобы экранная позиция оставалась стабильной:
     *   compX = -dSX/2 - dSY,  compY = dSX/2 - dSY
     */
    private void renderRain(SpriteBatch batch) {
        float intensity = store.rainIntensity;
        float W = store.display.get("width");
        float H = store.display.get("height");

        if (!dropsReady || Float.isNaN(lastShiftX)) {
            for (int k = 0; k < N_DROPS; k++) {
                dropSpd[k] = 840f + (float)(Math.random() * 660f);
                resetDrop(k, W, H);
            }
            dropsReady = true;
            lastShiftX = store.shiftX;
            lastShiftY = store.shiftY;
        }

        float camDX = store.shiftX - lastShiftX;
        float camDY = store.shiftY - lastShiftY;
        lastShiftX  = store.shiftX;
        lastShiftY  = store.shiftY;

        if (intensity <= 0f) return;

        float dt         = Gdx.graphics.getDeltaTime();
        float compX      = -camDX / 2f - camDY;
        float compY      =  camDX / 2f - camDY;
        float isoXDrift  = (store.windMultiplier - 1f) * 55f * dt;
        float horizSpeed = (store.windMultiplier - 1f) * 55f;
        float lit        = Math.max(0.15f, store.dayCoefficient);
        float alpha      = intensity;
        float tileW      = store.tileSizeWidth;
        float tileH      = store.tileSizeHeight;

        for (int k = 0; k < N_DROPS; k++) {
            dropWX[k]  += compX + isoXDrift / 2f;
            dropWY[k]  += compY - isoXDrift / 2f;
            dropAlt[k] -= dropSpd[k] * dt;

            float scrX = dropWX[k] - dropWY[k] + store.shiftX;
            float scrY = (dropWX[k] + dropWY[k]) / 2f + dropAlt[k] + store.shiftY;

            if (dropAlt[k] <= 0f || scrX < -60f || scrX > W + 60f || scrY > H + 20f) {
                // Всплеск только на тайлах ниже деревьев (objectHeight < 20)
                float gsx = dropWX[k] - dropWY[k] + store.shiftX;
                float gsy = (dropWX[k] + dropWY[k]) / 2f + store.shiftY;
                if (gsx > 0 && gsx < W && gsy > 0 && gsy < H && store.objectedMap != null) {
                    int mi = (int)(dropWX[k] / tileW) - 1;
                    int mj = (int)(dropWY[k] / tileH) - 1;
                    if (mi >= 0 && mi < store.mapHeight && mj >= 0 && mj < store.mapWidth
                            && store.objectedMap[mi][mj].objectHeight < 20) {
                        splashX[splashNext]   = gsx;
                        splashY[splashNext]   = gsy;
                        splashAge[splashNext] = 1f;
                        splashNext = (splashNext + 1) % N_SPLASHES;
                    }
                }
                resetDrop(k, W, H);
                continue;
            }
            if (scrY < -14f) continue;

            // Освещение от источников света (костёр и т.п.)
            float dropIsoX   = dropWX[k] - dropWY[k];
            float dropIsoY   = (dropWX[k] + dropWY[k]) / 2f;
            float lightBoost = 0f;
            for (int li = 1; li <= store.lightPointsHighWaterMark; li++) {
                if (store.lightPoints[li][0] == 0) continue;
                float ldx = dropIsoX - store.lightPoints[li][1];
                float ldy = dropIsoY - store.lightPoints[li][2];
                if (Math.abs(ldx) > LIGHT_RADIUS || Math.abs(ldy) > LIGHT_RADIUS) continue;
                float dist = (float)Math.sqrt(ldx * ldx + ldy * ldy);
                if (dist < LIGHT_RADIUS) {
                    lightBoost = Math.max(lightBoost, (1f - dist / LIGHT_RADIUS) * 0.9f);
                }
            }

            float dropBright = (0.35f + lit * 0.20f + lightBoost * 0.7f) * 0.765f;
            dropBright = Math.min(1f, dropBright);
            float warmth = lightBoost * 0.6f;
            batch.setColor(
                Math.min(1f, dropBright * 0.72f + warmth * 0.30f),
                Math.min(1f, dropBright * 0.80f),
                Math.min(1f, dropBright * 1.15f - warmth * 0.15f),
                alpha);

            float leanAngle = (float)Math.toDegrees(Math.atan2(horizSpeed, dropSpd[k]));
            batch.draw(dropTex,
                scrX, scrY,
                1f, 7f, 2, 14, 1f, 1f,
                leanAngle,
                0, 0, 2, 14, false, false);
        }
        batch.setColor(1, 1, 1, 1);
    }

    // ── Всплески ──────────────────────────────────────────────────────────────

    private void renderSplash(SpriteBatch batch) {
        float intensity = store.rainIntensity;
        if (intensity <= 0f) return;
        float dt  = Gdx.graphics.getDeltaTime();
        float lit = Math.max(0.15f, store.dayCoefficient);

        for (int k = 0; k < N_SPLASHES; k++) {
            if (splashAge[k] <= 0f) continue;
            splashAge[k] -= dt / SPLASH_DURATION;
            if (splashAge[k] <= 0f) { splashAge[k] = 0f; continue; }

            float t     = 1f - splashAge[k];
            float scale = 1f + t * 1.8f;
            float a     = splashAge[k] * intensity * 0.25f;
            float w     = 5f * scale;
            float h     = 2.5f * scale;
            batch.setColor(0.82f * lit, 0.9f * lit, lit, a);
            batch.draw(splashTex,
                splashX[k] - w / 2f, splashY[k] - h / 2f,
                w, h);
        }
        batch.setColor(1, 1, 1, 1);
    }

    // ── Молния ────────────────────────────────────────────────────────────────

    /**
     * Экранный всполох молнии с двумя фазами:
     *   lf > 1.0  — предвспышечное затемнение
     *   lf 0..1.0 — фиолетовый + белый слои
     */
    private void renderLightningFlash(SpriteBatch batch) {
        float lf = store.lightningFlash;
        if (lf <= 0f) return;

        float dt = Gdx.graphics.getDeltaTime();
        float W  = store.display.get("width");
        float H  = store.display.get("height");

        if (lf > 1.0f) {
            float t = lf - 1.0f;
            batch.setColor(0f, 0f, 0.08f, t * 0.20f);
            batch.draw(flashTex, 0, 0, W, H);
            store.lightningFlash = lf - dt * 11f;
        } else {
            batch.setColor(0.45f, 0.05f, 0.85f, lf * 0.15f);
            batch.draw(flashTex, 0, 0, W, H);
            batch.setColor(1f, 0.97f, 1f, lf * 0.10f);
            batch.draw(flashTex, 0, 0, W, H);
            store.lightningFlash = Math.max(0f, lf - dt * 4.5f);
        }
        batch.setColor(1, 1, 1, 1);
    }
}
