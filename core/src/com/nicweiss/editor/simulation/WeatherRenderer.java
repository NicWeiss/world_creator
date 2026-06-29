package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.Light;

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
    private static final int   N_DROPS         = 300; // максимум; активно 50-300 динамически
    private static final float LIGHT_RADIUS    = 160f;
    private static final float SPLASH_DURATION = 0.22f;
    private static final int   N_SPLASHES      = 200;
    private static final int   N_LEAVES        = 80;
    private static final float LEAF_LIFE       = 4.5f; // секунды жизни листа

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

    // ── Листья ────────────────────────────────────────────────────────────────
    // Хранятся в изометрических мировых координатах (без shiftX/Y).
    // Спавнятся с isTree-тайлов в видимой области при усилении ветра.
    private final float[] leafIsoX = new float[N_LEAVES]; // мировой iso X
    private final float[] leafIsoY = new float[N_LEAVES]; // мировой iso Y
    private final float[] leafVX   = new float[N_LEAVES]; // скорость iso X (px/s)
    private final float[] leafVY   = new float[N_LEAVES]; // скорость iso Y (px/s)
    private final float[] leafAge  = new float[N_LEAVES]; // 1→0; 0 = неактивен
    private final float[] leafPhase= new float[N_LEAVES]; // фаза флаттера (трепетания)
    private float leafSpawnTimer = 0f;

    // ── Молния ────────────────────────────────────────────────────────────────
    private static final int   MAX_SEGS   = 180;  // сегменты молнии (ствол + ветки)
    private static final float BOLT_LIFE  = 0.40f; // секунды видимости разряда (×2)

    private final float[] segRX1  = new float[MAX_SEGS]; // X1 относительно цели (iso)
    private final float[] segRY1  = new float[MAX_SEGS]; // Y1
    private final float[] segRX2  = new float[MAX_SEGS]; // X2
    private final float[] segRY2  = new float[MAX_SEGS]; // Y2
    private final float[] segAlph = new float[MAX_SEGS]; // непрозрачность (ветки тусклее)
    private int     segCount  = 0;
    private float   boltAge   = 0f;      // убывает до 0 → разряд исчезает
    private float   boltTWX   = 0f;      // целевой декарт. X в мире
    private float   boltTWY   = 0f;

    private final java.util.Random boltRng = new java.util.Random();

    private boolean flashFade_wasActive = false;

    private final Light light; // ссылка на систему освещения

    // ── Текстуры ──────────────────────────────────────────────────────────────
    private final Texture dropTex;
    private final Texture splashTex;
    private final Texture flashTex;
    private final Texture leafTex; // маленький овальный листок 4×3

    public WeatherRenderer(Light light) {
        this.light = light;
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

        // Текстура листа: маленький овал 4×3
        Pixmap lp = new Pixmap(4, 3, Pixmap.Format.RGBA8888);
        lp.setColor(0, 0, 0, 0); lp.fill();
        lp.setColor(1f, 1f, 1f, 1f);
        lp.drawPixel(1,0); lp.drawPixel(2,0);
        lp.drawPixel(0,1); lp.drawPixel(1,1); lp.drawPixel(2,1); lp.drawPixel(3,1);
        lp.drawPixel(1,2); lp.drawPixel(2,2);
        leafTex = new Texture(lp);
        lp.dispose();
    }

    /** Вызывается GL-потоком каждый кадр после рендера карты. */
    public void render(SpriteBatch batch) {
        // Генерируем новый разряд молнии если пришёл сигнал от WeatherThread
        if (store.lightningBoltNew) {
            store.lightningBoltNew = false;
            generateBolt(store.lightningTargetWX, store.lightningTargetWY);
        }
        renderRain(batch);
        renderSplash(batch);
        renderLeaves(batch);
        renderBolt(batch);
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
        float windSpeed  = (store.windMultiplier - 1f) * 55f;
        // Дрейф в направлении ветра: iso-скорость → декартовые дельты
        float isoXDrift  = windSpeed * store.windDirX * dt;
        float isoYDrift  = windSpeed * store.windDirY * dt;
        float dCartXDrift = (isoXDrift + 2f * isoYDrift) / 2f;
        float dCartYDrift = (2f * isoYDrift - isoXDrift) / 2f;
        float horizSpeed = windSpeed * store.windDirX; // экранный X для расчёта наклона
        float lit        = Math.max(0.15f, store.dayCoefficient);
        float alpha      = intensity;
        float tileW      = store.tileSizeWidth;
        float tileH      = store.tileSizeHeight;

        // Динамическое количество капель: 50-300, медленно нарастает и спадает
        float t0 = store.cloudTime;
        float dropFrac = 0.5f + 0.3f * (float)Math.sin(t0 * 0.09f)
                               + 0.2f * (float)Math.sin(t0 * 0.14f); // 0..1
        int activeDrops = (int)(50 + dropFrac * 250); // 50..300
        activeDrops = Math.max(50, Math.min(N_DROPS, activeDrops));

        for (int k = 0; k < activeDrops; k++) {
            dropWX[k]  += compX + dCartXDrift;
            dropWY[k]  += compY + dCartYDrift;
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
                        // Храним в мировых iso координатах (без shift) → не едут с камерой
                        splashX[splashNext]   = gsx - store.shiftX;
                        splashY[splashNext]   = gsy - store.shiftY;
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

            // ×4 — усиливаем наклон для видимости; физически скорость дождя >>ветра
            float leanAngle = (float)Math.toDegrees(Math.atan2(horizSpeed * 4f, dropSpd[k]));
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
            // Переводим мировые iso координаты в экранные
            float scrX  = splashX[k] + store.shiftX;
            float scrY  = splashY[k] + store.shiftY;
            batch.setColor(0.82f * lit, 0.9f * lit, lit, a);
            batch.draw(splashTex,
                scrX - w / 2f, scrY - h / 2f,
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
            batch.setColor(0.45f, 0.05f, 0.85f, lf * 0.075f); // ÷2
            batch.draw(flashTex, 0, 0, W, H);
            batch.setColor(1f, 0.97f, 1f,    lf * 0.050f); // ÷2
            batch.draw(flashTex, 0, 0, W, H);
            store.lightningFlash = Math.max(0f, lf - dt * 4.5f);
        }
        batch.setColor(1, 1, 1, 1);
    }

    // ── Листья ────────────────────────────────────────────────────────────────

    /**
     * Спавним и рисуем листья, срываемые ветром с деревьев во время дождя.
     *
     * Листья хранятся в мировых iso-координатах (без shift) — следуют за камерой.
     * Спавн: случайная позиция в видимой области → проверяем isTree тайла.
     * Скорость усиливается с windMultiplier; жизнь листа = LEAF_LIFE секунд.
     */
    private void renderLeaves(SpriteBatch batch) {
        float wind = store.windMultiplier;
        if (wind < 1.4f) return; // листья срываются только при ощутимом ветре

        float dt = Gdx.graphics.getDeltaTime();
        float W  = store.display.get("width");
        float H  = store.display.get("height");

        // ── Спавн новых листьев ───────────────────────────────────────────────
        // Частота зависит от силы ветра; интервал уменьшается при сильном ветре
        float spawnInterval = 0.18f / (wind - 1f); // при wind=2: 0.18s, wind=3: 0.09s
        leafSpawnTimer += dt;
        if (leafSpawnTimer >= spawnInterval) {
            leafSpawnTimer = 0f;
            trySpawnLeaf(W, H, wind);
        }

        // ── Обновление и рендер ───────────────────────────────────────────────
        float windIsoX = (wind - 1f) * 55f; // скорость ветра в iso X px/s

        for (int k = 0; k < N_LEAVES; k++) {
            if (leafAge[k] <= 0f) continue;

            leafAge[k] -= dt / LEAF_LIFE;
            if (leafAge[k] <= 0f) { leafAge[k] = 0f; continue; }

            // Плавно тянем скорость листа к текущему направлению ветра
            float windTarget = (store.windMultiplier - 1f) * 32f * store.windDirX;
            leafVX[k] += (windTarget - leafVX[k]) * dt * 1.8f;

            // Физика: ветер + трепетание + гравитация
            float flutter   = (float)Math.sin(leafPhase[k] + store.cloudTime * (5f + k * 0.07f)) * 18f;
            float vxEff     = leafVX[k] + flutter;
            leafIsoX[k]    += vxEff * dt;
            leafIsoY[k]    += leafVY[k] * dt;
            leafVY[k]      -= 28f * dt; // гравитация (iso Y вниз)

            // Экранная позиция
            float scrX = leafIsoX[k] + store.shiftX;
            float scrY = leafIsoY[k] + store.shiftY;
            if (scrX < -20 || scrX > W + 20 || scrY < -20 || scrY > H + 20) continue;

            float t     = leafAge[k]; // 1→0
            float alpha = t * Math.min(1f, t * 3f); // быстро появляется, плавно гаснет

            // Свежие — тёмно-зелёные как хвоя; старые — коричневые как опавшая хвоя
            float lit = Math.max(0.15f, store.dayCoefficient); // затенение по освещению мира
            float r = (0.14f + (1f - t) * 0.46f) * lit;
            float g = (0.38f - (1f - t) * 0.20f) * lit;
            float b = (0.10f - (1f - t) * 0.07f) * lit;

            float angle = (float)Math.toDegrees(Math.atan2(vxEff, -leafVY[k]));
            batch.setColor(r, g, b, alpha);
            batch.draw(leafTex,
                scrX, scrY,
                2f, 1.5f,             // origin (центр листа)
                4, 3, 1f, 1f,
                angle,
                0, 0, 4, 3, false, false);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /**
     * Пытается заспавнить лист с isTree-тайла в случайной позиции видимой области.
     * Не гарантирует спавн — если попали не в дерево, пропускаем.
     */
    private void trySpawnLeaf(float W, float H, float wind) {
        if (store.objectedMap == null) return;

        // Находим свободный слот
        int slot = -1;
        for (int k = 0; k < N_LEAVES; k++) {
            if (leafAge[k] <= 0f) { slot = k; break; }
        }
        if (slot < 0) return; // все слоты заняты

        // Случайная позиция на экране → тайловые координаты
        float scrX = boltRng.nextFloat() * W;
        float scrY = boltRng.nextFloat() * H;
        float isoX = scrX - store.shiftX;
        float isoY = scrY - store.shiftY;
        float cartX = (isoX + 2f * isoY) / 2f;
        float cartY = (2f * isoY - isoX) / 2f;
        int mi = (int)(cartX / store.tileSizeWidth) - 1;
        int mj = (int)(cartY / store.tileSizeHeight) - 1;

        if (mi < 0 || mi >= store.mapHeight || mj < 0 || mj >= store.mapWidth) return;
        if (!store.objectedMap[mi][mj].isTree) return;

        // Спавним лист в верхней части спрайта дерева
        float tileH   = store.tileSizeHeight;
        float spawnOY = tileH * (0.5f + boltRng.nextFloat() * 0.4f); // верхняя половина кроны
        leafIsoX[slot]  = isoX + (boltRng.nextFloat() - 0.5f) * store.tileSizeWidth * 0.6f;
        leafIsoY[slot]  = isoY + spawnOY;
        float leafSpd   = (wind - 1f) * 40f * (0.6f + boltRng.nextFloat() * 0.8f);
        leafVX[slot]    = leafSpd * store.windDirX;
        leafVY[slot]    = 15f + boltRng.nextFloat() * 25f + leafSpd * store.windDirY;
        leafAge[slot]   = 1f;
        leafPhase[slot] = boltRng.nextFloat() * (float)(Math.PI * 2);
    }

    // ── Разряд молнии ─────────────────────────────────────────────────────────

    /**
     * Генерирует разряд молнии к целевому тайлу.
     * Точки хранятся в изометрических экранных координатах ОТНОСИТЕЛЬНО цели,
     * поэтому разряд движется вместе с картой при движении игрока.
     *
     * (0, 0) = цель. Старт выше экрана: (±rand, +H+extra).
     * Алгоритм: итеративное смещение средней точки с убывающей амплитудой.
     * Ветки: от промежуточных точек уходят в сторону и не достигают земли.
     */
    private void generateBolt(float tWX, float tWY) {
        boltTWX  = tWX;
        boltTWY  = tWY;
        segCount = 0;
        boltAge  = BOLT_LIFE;
        flashFade_wasActive = false;

        float H = store.display.get("height");

        // Стартовая точка: случайно выше экрана
        float startX = (boltRng.nextFloat() - 0.5f) * 180f; // ±90 px от цели по X
        float startY = H + 60f + boltRng.nextFloat() * 120f;

        // Опорные точки основного ствола (zig-zag через смещение средних точек)
        float[] ptX = new float[256];
        float[] ptY = new float[256];
        ptX[0] = startX; ptY[0] = startY;
        ptX[1] = 0f;     ptY[1] = 0f;
        int ptCount = 2;

        // 5 итераций деления — каждая вдвое уменьшает амплитуду отклонения
        float amplitude = 55f;
        for (int iter = 0; iter < 5 && ptCount < 200; iter++) {
            int newCount = ptCount * 2 - 1;
            float[] nx = new float[newCount];
            float[] ny = new float[newCount];
            for (int i = 0; i < ptCount - 1; i++) {
                nx[i * 2]     = ptX[i];
                ny[i * 2]     = ptY[i];
                float mx = (ptX[i] + ptX[i+1]) / 2f;
                float my = (ptY[i] + ptY[i+1]) / 2f;
                // Горизонтальный зигзаг — молния типично ломается по X, не по Y
                nx[i * 2 + 1] = mx + (boltRng.nextFloat() - 0.5f) * amplitude;
                ny[i * 2 + 1] = my;
            }
            nx[newCount - 1] = ptX[ptCount - 1];
            ny[newCount - 1] = ptY[ptCount - 1];
            System.arraycopy(nx, 0, ptX, 0, newCount);
            System.arraycopy(ny, 0, ptY, 0, newCount);
            ptCount   = newCount;
            amplitude *= 0.52f; // быстро убываем для острых углов
        }

        // Сохраняем сегменты ствола
        for (int i = 0; i < ptCount - 1 && segCount < MAX_SEGS - 1; i++) {
            segRX1[segCount] = ptX[i];   segRY1[segCount] = ptY[i];
            segRX2[segCount] = ptX[i+1]; segRY2[segCount] = ptY[i+1];
            segAlph[segCount] = 1f;
            segCount++;
        }

        // Ветки: от случайных промежуточных точек, уходят в сторону и НЕ достигают земли
        for (int i = 2; i < ptCount - 3 && segCount < MAX_SEGS - 8; i++) {
            if (boltRng.nextFloat() > 0.22f) continue; // 22% вероятность ветки
            float bx = ptX[i], by = ptY[i];
            // Ветка идёт в сторону, немного вниз, и быстро затухает
            float dirX = (boltRng.nextFloat() - 0.5f) * 80f;
            float dirY = -(boltRng.nextFloat() * 50f + 20f); // вверх от цели (к небу)
            float brAlpha = 0.75f;
            for (int s = 0; s < 4 && segCount < MAX_SEGS - 1; s++) {
                float ex = bx + dirX;
                float ey = by + dirY;
                segRX1[segCount] = bx; segRY1[segCount] = by;
                segRX2[segCount] = ex; segRY2[segCount] = ey;
                segAlph[segCount] = brAlpha;
                segCount++;
                bx = ex; by = ey;
                // Ветка сужается и загибается
                dirX *= 0.55f;
                dirY *= 0.65f;
                brAlpha *= 0.6f;
            }
        }
    }

    /**
     * Рисует активный разряд молнии: толстая фиолетовая обводка + тонкое белое ядро.
     * Координаты вычисляются каждый кадр из мировой позиции цели + shiftX/Y.
     */
    private void renderBolt(SpriteBatch batch) {
        if (boltAge <= 0f || segCount == 0) return;

        float dt   = Gdx.graphics.getDeltaTime();
        // fade вычисляем ДО уменьшения → первый кадр всегда fade=1.0 (резкое появление)
        float fade = boltAge / BOLT_LIFE;
        boltAge    = Math.max(0f, boltAge - dt);

        // Экранная позиция цели (движется с картой)
        float tScrX = boltTWX - boltTWY + store.shiftX;
        float tScrY = (boltTWX + boltTWY) / 2f + store.shiftY;

        // Рисуем сегменты молнии
        for (int i = 0; i < segCount; i++) {
            float x1 = tScrX + segRX1[i];
            float y1 = tScrY + segRY1[i];
            float x2 = tScrX + segRX2[i];
            float y2 = tScrY + segRY2[i];
            float a  = segAlph[i] * fade;

            // Яркая фиолетовая обводка
            batch.setColor(0.65f, 0.05f, 1.0f, a * 0.85f);
            drawSeg(batch, flashTex, x1, y1, x2, y2, 5f);
            // Тонкое белое ядро
            batch.setColor(1f, 1f, 1f, a);
            drawSeg(batch, flashTex, x1, y1, x2, y2, 0.8f);
        }

        // Вспышка на тайле: исчезает за первые 30% жизни разряда
        float flashFade = Math.max(0f, fade * 3.3f - 2.3f); // активна при fade > 0.697
        flashFade = Math.min(1f, flashFade);

        // Динамический свет: просто выставляем значения в Store → calcLitColor читает каждый кадр.
        // Не нужно addPoint/removePoint — освещение исчезает само когда bright=0.
        if (flashFade > 0f) {
            // Формат совпадает с lightPoints[i][1/2] из addPoint: isoX/Y + tileSizeWidth/Height
            store.lightningFlashIsoX   = boltTWX - boltTWY + store.tileSizeWidth;
            store.lightningFlashIsoY   = (boltTWX + boltTWY) / 2f + store.tileSizeHeight;
            store.lightningFlashBright = flashFade;
        } else {
            store.lightningFlashBright = 0f;
        }

        if (flashFade > 0f) {
            // Внутренний яркий центр (исходный размер)
            float fw = 28f * flashFade;
            float fh = 12f * flashFade;
            batch.setColor(1f, 0.8f, 1f, flashFade);
            batch.draw(flashTex, tScrX - fw / 2f, tScrY - fh / 2f, fw, fh);

            // Фиолетовый ореол (исходный размер)
            float gw = 70f * flashFade;
            float gh = 30f * flashFade;
            batch.setColor(0.55f, 0.05f, 0.9f, flashFade * 0.55f);
            batch.draw(flashTex, tScrX - gw / 2f, tScrY - gh / 2f, gw, gh);

            // Мягкое широкое свечение (исходный размер)
            float gw2 = 130f * flashFade;
            float gh2 = 55f  * flashFade;
            batch.setColor(0.4f, 0f, 0.7f, flashFade * 0.22f);
            batch.draw(flashTex, tScrX - gw2 / 2f, tScrY - gh2 / 2f, gw2, gh2);
        }

        batch.setColor(1, 1, 1, 1);
    }

    /**
     * Рисует отрезок как повёрнутый прямоугольник через SpriteBatch.
     * angle = atan2(-dx, dy) — поворот CCW от оси +Y.
     */
    private void drawSeg(SpriteBatch batch, Texture tex,
                         float x1, float y1, float x2, float y2, float thick) {
        float dx  = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        float angle   = (float) Math.toDegrees(Math.atan2(-dx, dy));
        float halfT   = thick / 2f;
        batch.draw(tex,
            x1 - halfT, y1,          // позиция
            halfT, 0f,                // ось вращения — начало отрезка по центру толщины
            thick, len,               // размер
            1f, 1f,                   // масштаб
            angle,                    // поворот (CCW)
            0, 0, 1, 1,               // текстурный регион (1×1)
            false, false);
    }
}
