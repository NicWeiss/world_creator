package com.nicweiss.editor.objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.utils.Transform;


public class MapObject  extends BaseObject {
    Transform transform;

    public int xPositionOnMap = 0, yPositionOnMap = 0;
    public boolean isRenderLighAndNigth = true;
    public int objectHeight;
    public boolean isTree = false;
    public float additionalDarkCoeff = 1;
    private float nearestLightDist = 999999;
    public boolean isDialogBind = false;

    float[] point;

    public MapObject(){
        isEnableRenderLimits = true;
    }


    public void draw(Batch batch) {
        calcPosition();
        if (isRenderLighAndNigth) {
            batch.setColor(calcLitColor(opacity));
        }

        if (isDialogBind){
            batch.setColor(
                0.56f - store.dayCoefficient / 4,
                0.77f - store.dayCoefficient / 4,
                0.55f - store.dayCoefficient / 4,
                opacity
            );
        }

        if (store.isSimulationMode && isTree) {
            drawWindSwayed(batch);
        } else {
            super.draw(batch);
        }
    }

    /**
     * Рисует дерево с процедурным качанием кроны от ветра.
     * Два верхних вертекса сдвигаются по X — ствол стоит на месте, крона качается.
     * Фаза уникальна для каждого дерева через хэш позиции.
     */
    private void drawWindSwayed(Batch batch) {
        if (img == null) return;

        // ── Уникальные параметры дерева из хэша позиции ──────────────────────
        int h = xPositionOnMap * 374761393 + yPositionOnMap * 1073741827;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        float phase = (float)((h & 0xFFFF)        / (float)0xFFFF * Math.PI * 2.0);
        float phase2 = (float)(((h >> 8) & 0xFFFF) / (float)0xFFFF * Math.PI * 2.0);
        float phase3 = (float)(((h >> 4) & 0xFFFF) / (float)0xFFFF * Math.PI * 2.0);

        // Основная частота качания: 0.6..1.1 рад/с
        float freq1 = 0.6f + (Math.abs(h & 0xFF) / 255f) * 0.5f;
        // Вторичная — некратна первой, быстрее, меньше
        float freq2 = freq1 * (1.9f + (Math.abs((h >> 3) & 0xFF) / 255f) * 0.8f);
        // Третичная — рябь от листьев
        float freq3 = freq1 * (4.3f + (Math.abs((h >> 5) & 0xFF) / 255f) * 1.4f);

        float t = store.cloudTime;

        // ── Глобальный порыв ветра: сумма медленных некратных синусоид ────────
        // Три несоизмеримые частоты → результат никогда не повторяется
        float gust = 0.55f
            + 0.28f * (float)Math.sin(t * 0.23f + 0.0f)
            + 0.17f * (float)Math.sin(t * 0.11f + 1.73f)
            + 0.12f * (float)Math.sin(t * 0.41f + 3.07f)
            + 0.08f * (float)Math.sin(t * 0.07f + 5.11f);
        // Ограничиваем снизу: ветер не затихает полностью
        if (gust < 0.08f) gust = 0.08f;

        // ── Смещение кроны: три гармоники + глобальный порыв ─────────────────
        float primary   = (float)Math.sin(t * freq1 + phase);
        float secondary = 0.38f * (float)Math.sin(t * freq2 + phase2);
        float rustle    = 0.14f * (float)Math.sin(t * freq3 + phase3);

        float baseAmp = 3.5f + (Math.abs((h >> 12) & 0xFF) / 255f) * 2.5f; // 3.5..6 px
        float offsetX = baseAmp * gust * (primary + secondary + rustle);

        float w  = width  * x_scale;
        float h2 = height * y_scale;
        float c  = batch.getColor().toFloatBits();

        // Вертексы SpriteBatch: BL, TL, TR, BR (x, y, colorBits, u, v)
        // v=1 → низ изображения (корни), v=0 → верх (крона)
        windVerts[0]  = x;           windVerts[1]  = y;    windVerts[2]  = c; windVerts[3]  = 0f; windVerts[4]  = 1f;
        windVerts[5]  = x+offsetX;   windVerts[6]  = y+h2; windVerts[7]  = c; windVerts[8]  = 0f; windVerts[9]  = 0f;
        windVerts[10] = x+w+offsetX; windVerts[11] = y+h2; windVerts[12] = c; windVerts[13] = 1f; windVerts[14] = 0f;
        windVerts[15] = x+w;         windVerts[16] = y;    windVerts[17] = c; windVerts[18] = 1f; windVerts[19] = 1f;
        batch.draw(img, windVerts, 0, 20);
    }

    // Переиспользуемый буфер для ветровых вертексов — избегаем аллокацию на каждый тайл
    private final float[] windVerts = new float[20];

    public void drawSurface(Batch batch) {
        calcPosition();
        if (isRenderLighAndNigth) {
            batch.setColor(calcLitColor(opacity));
        }

        if (isDialogBind){
            batch.setColor(
                0.56f - store.dayCoefficient / 4,
                0.77f - store.dayCoefficient / 4,
                0.55f - store.dayCoefficient / 4,
                opacity
            );
        }

        super.drawSurface(batch);
    }

    public void calcLight(String environment){
        float dark;
        float distByX, distByY, dist;
        float start, end, lp;
        float rp, gp, bp;
        float localShiftX, localShiftY;
        int countFrom, countTo;
        boolean isLightSetted = false;

        float highestRp =  (float)0.2;
        float highestGp =  (float)0.2;
        float highestBp =  (float)0.2;


        if (environment.equals("player")){
            countFrom = 0;
            countTo = 1;
        } else {
            calcPosition();
            countFrom = 1;
            // Ограничиваем до наибольшего реально занятого слота — ключевая оптимизация:
            // вместо O(10000) проверок → O(activeCount) при каждом calcLight
            countTo = store.lightPointsHighWaterMark + 1;
        }

        for (int i = countFrom; i<countTo; i++) {
            float[] light = store.lightPoints[i];
            localShiftX = 0;
            localShiftY = 0;

            if (i > 0) {
                if (store.lightPoints[i][0] == 0){
                    continue;
                }

                localShiftX = store.shiftX;
                localShiftY = store.shiftY;
            }

            distByX = (float) (x - localShiftX + ((float) width / 2) - light[1]);
            distByY = (float) (y - localShiftY - (height * 0.1) - light[2]) * 1.45f;
            if (Math.abs(distByX)>400 || Math.abs(distByY)>400){
                continue;
            }

//            Вычисление дисстанции до источника света
            dist = (float) Math.sqrt(distByX * distByX + distByY * distByY);

//            Если источник статический, то дальние источники отбрасываются
            if (environment.equals("global") && Math.abs(dist) > nearestLightDist){
                continue;
            }

//            проверяем высоты для ограничения освещения перекрытых объектов
            float dx, dy, tmx, tmy, fx, fy;
            int heightOfLight, tx, ty;

//            Точка для которой ведётся вычисление
            tx = xPositionOnMap;
            ty = yPositionOnMap;

//            Источник света
            fx = i == 0 ? (int) (store.selectedTileX + 0.5f) : (int) light[3] + 2;
            fy = i == 0 ? (int) (store.selectedTileY + 0.5f) : (int) light[4] + 2;

            heightOfLight = i == 0 ? store.selectedTailObjectHigh : store.objectedMap[(int) light[3]][(int) light[4]].objectHeight;

//            шаги для вычисления
            dx = (tx - fx) / 1000f;
            dy = (ty - fy) / 1000f;

//            позиционный буфер
            tmx = fx+0.25f;
            tmy = fy+0.25f;
            boolean isStopLight = false, isCycleDone = false;

            /*
              Цикл вычисления препятствий на пути от источника света
            Вычислени идёт по трём линиям и если средняя высота препятствий не превышет высоту источника света,
            то объект считаетя освещаемым

            Сравнение идёт между высотой источника света и высотой препятствия. Если препятствие выше,
            значит всё что за ним - не осещённое
            */
            int distanceFromObstacle = 0;

            while (!isCycleDone){
                tmx = tmx + dx;
                tmy = tmy + dy;

                if ((int) tmx == tx && (int) tmy == ty){
                    isCycleDone = true;
                }

                if (tmx - 2 >= 0 && tmx - 2 < store.mapHeight && tmy - 2 >= 0 && tmy - 2 < store.mapWidth && !isCycleDone) {
                    int heightDown = 100;
                    int heightUp = 100;

                    int heightMiddle = store.objectedMap[(int) (tmx) - 2][(int) (tmy) - 2].objectHeight;

                    int xRounded = Math.round(tmx) - 2;
                    int yRounded = Math.round(tmy) - 2;
                    if (xRounded >= 0 && xRounded < store.mapHeight && yRounded >= 0 && yRounded < store.mapWidth) {
                        heightDown = store.objectedMap[xRounded][yRounded].objectHeight;
                    }

                    int xCeil = (int) (Math.ceil(tmx) - 3);
                    int yCeil = (int) (Math.ceil(tmy) - 3);
                    if (xCeil>= 0 && xCeil < store.mapHeight && yCeil >= 0 && yCeil < store.mapWidth) {
                        heightUp = store.objectedMap[xCeil][yCeil].objectHeight;
                    }

                    int mh =(heightMiddle + heightDown + heightUp) / 3;
                    if((int)tmx == (int)(fx+0.30f) && (int)tmy == (int)(fy+0.30f)) {
                        mh = heightOfLight;
                    }

                    if (mh > heightOfLight) {
                        isStopLight = true;
                        isCycleDone = true;

                        int distanceX = Math.max(tx, (int) (tmx)) - Math.min(tx, (int) (tmx));
                        int distanceY = Math.max(ty, (int) (tmy)) - Math.min(ty, (int) (tmy));
                        distanceFromObstacle = Math.max(distanceX, distanceY);
                    }
                } else {
                    isCycleDone = true;
                }
            }

            if (isStopLight) {
                if (distanceFromObstacle < 4) {

                    float coeffMultiplication;

                    if (objectHeight > heightOfLight){
                        coeffMultiplication = 10;
                    }
                    else {
                        coeffMultiplication = 2.5f;
                    }

                    additionalDarkCoeff = 1f + ((distanceFromObstacle * coeffMultiplication) / 10f);
                    additionalDarkCoeff = Math.max(additionalDarkCoeff, 1);
                } else {
                    additionalDarkCoeff = 100;
                }
            } else {
                additionalDarkCoeff = 1;

                isLightSetted = true;
                nearestLightDist = Math.abs(dist)+40;
            }

//            рассчёт освещённости клетки в зависимости от удалённости от источника света
            start = 0;
            end = 120;
            lp = (dist - start) / (end - start) * 100;
            dark = (float) 1.6 - (lp / 100 * 80) / 100;

            if (dark < 0.2) {
                dark = (float) 0.2;
            }

            rp = ((float) 1 - (lp / ((dark * 100) + 35) * 50) / 500) / additionalDarkCoeff;
            gp = ((float) 1 - (lp / ((dark * 100) + 15) * 50) / 500) / additionalDarkCoeff;
            bp = ((float) 1 - (lp / ((dark * 100) + 5) * 50) / 500) / additionalDarkCoeff;


            if (rp > highestRp) {
                highestRp = rp;
            }
            if (gp > highestGp) {
                highestGp = gp;
            }
            if (bp > highestBp) {
                highestBp = bp;
            }

            if (highestRp > 0.9f && highestBp> 0.9f && highestGp > 0.9f){
                break;
            }
        }

        if (highestRp < 0.2) {
            highestRp = (float) 0.2;
        }
        if (highestGp < 0.2) {
            highestGp = (float) 0.2;
        }
        if (highestBp < 0.2) {
            highestBp = (float) 0.2;
        }

        if (environment == "player") {
            dynamicLightRed = highestRp;
            dynamicLightGreen = highestGp;
            dynamicLightBlue = highestBp;
        } else {
            staticLightRed = highestRp;
            staticLightGreen = highestGp;
            staticLightBlue = highestBp;
        }


        if (!isLightSetted){
            nearestLightDist = 99999;
//            calcLight(environment);
        }
    }

    public void calcPosition(){
        point = transform.cartesianToIsometric(
                (int)(xPositionOnMap * store.tileSizeWidth),
                (int)(yPositionOnMap * store.tileSizeHeight)
        );

        setX(point[0] + store.shiftX);
        setY(point[1] + store.shiftY);

        setWidth(img.getWidth() / store.tileDownScale);
        setHeight(img.getHeight() / store.tileDownScale);
    }

    /**
     * Вычисляет итоговый цвет тайла с учётом дневного света и источников.
     *
     * Принцип: берём max(дневной_ambient, вклад_источника).
     * Если клетка уже светлее от дня — источник не меняет её цвет (нет искажений днём).
     * Если источник ярче дня (ночь, у костра) — применяем свет источника.
     * Никакого переключателя по порогу — свет работает всегда.
     */
    // Переиспользуемый объект цвета — избегаем new Color() на каждый тайл
    private static final com.badlogic.gdx.graphics.Color TMP_COLOR =
        new com.badlogic.gdx.graphics.Color();

    private com.badlogic.gdx.graphics.Color calcLitColor(float a) {
        // Динамический свет (игрок с факелом)
        if (store.isSelectedLightObject) {
            calcLight("player");
        } else {
            dynamicLightRed   = 0.2f;
            dynamicLightGreen = 0.2f;
            dynamicLightBlue  = 0.2f;
        }

        // ── Температура цвета по фазе дня ────────────────────────────────────
        double angle  = store.dayPhase * 2.0 * Math.PI;
        float  cosA   = (float) Math.cos(angle);
        float  warmth = cosA * cosA;
        float  cool   = Math.max(0f, (float) Math.sin(angle));

        float dayBright = Math.max(0f, store.dayCoefficient);
        float tR = 1f + (warmth * 0.22f - cool * 0.06f) * dayBright;
        float tG = 1f + (-warmth * 0.06f + cool * 0.03f) * dayBright;
        float tB = 1f + (-warmth * 0.28f + cool * 0.22f) * dayBright;

        float raw  = 0.2f + store.dayCoefficient;
        float dayR = raw * tR;
        float dayG = raw * tG;
        float dayB = raw * tB;

        // Источники света — независимы от солнца (костёр светит под облаком)
        float lr = Math.max(staticLightRed,   dynamicLightRed);
        float lg = Math.max(staticLightGreen, dynamicLightGreen);
        float lb = Math.max(staticLightBlue,  dynamicLightBlue);

        // Облако затеняет ТОЛЬКО дневной свет, не источники
        float cloud = (store.isSimulationMode && store.dayCoefficient > 0f)
            ? computeCloudShadow()
            : 1f;

        float shadedR = dayR * cloud;
        float shadedG = dayG * cloud;
        float shadedB = dayB * cloud;

        // Итог: max(затенённый_день, источник_света)
        TMP_COLOR.set(
            Math.min(1f, Math.max(shadedR, lr)),
            Math.min(1f, Math.max(shadedG, lg)),
            Math.min(1f, Math.max(shadedB, lb)),
            a
        );
        return TMP_COLOR;
    }

    // ── Процедурные облака на основе градиентного шума ──────────────────────
    // Шум сэмплируется в мировых координатах + смещение ветром → облака
    // привязаны к миру (не к камере) и плавно движутся.

    private static final float WIND_X      = 0.9f;   // тайлов/сек
    private static final float WIND_Y      = 0.12f;
    private static final float CLOUD_FREQ  = 0.090f; // масштаб облаков (~11 тайлов)
    private static final float CLOUD_THOLD = 0.45f;
    private static final float SHADOW_MAX  = 0.70f;

    private float computeCloudShadow() {
        float t  = store.cloudTime;

        // Мировая позиция тайла + смещение ветром
        float wx = (xPositionOnMap + WIND_X * t) * CLOUD_FREQ;
        float wy = (yPositionOnMap + WIND_Y * t) * CLOUD_FREQ;

        // Многооктавный шум (4 октавы) для облакоподобных форм
        float noise = cloudFbm(wx, wy, 4);

        // Только значения выше порога дают тень
        float density = Math.max(0f, noise - CLOUD_THOLD) / (1f - CLOUD_THOLD);
        // Сглаживаем края облаков
        density = density * density * (3f - 2f * density);

        float dayScale = Math.min(1f, store.dayCoefficient * 2f);
        return 1f - density * SHADOW_MAX * dayScale;
    }

    /** Фрактальный броуновый шум: несколько октав градиентного шума */
    private static float cloudFbm(float x, float y, int octaves) {
        float value = 0f, amp = 1f, freq = 1f, max = 0f;
        for (int o = 0; o < octaves; o++) {
            value += cloudValueNoise(x * freq, y * freq) * amp;
            max   += amp;
            amp   *= 0.5f;
            freq  *= 2.1f; // чуть > 2 — убирает периодичность
        }
        return value / max;
    }

    /** Градиентный шум на целочисленной сетке */
    private static float cloudValueNoise(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        // Smoothstep сглаживание
        float ux = fx * fx * (3f - 2f * fx);
        float uy = fy * fy * (3f - 2f * fy);

        float v00 = cloudHash(ix,   iy);
        float v10 = cloudHash(ix+1, iy);
        float v01 = cloudHash(ix,   iy+1);
        float v11 = cloudHash(ix+1, iy+1);

        return v00 + ux*(v10-v00) + uy*(v01-v00) + ux*uy*(v00-v10-v01+v11);
    }

    /** Детерминированный хэш пары целых → [0..1] */
    private static float cloudHash(int x, int y) {
        int h = x * 374761393 + y * 1073741827;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h & 0xFF) / 255f;
    }

    /** Быстрый сброс до ambient-освещения без DDA — вызывается из Light.recalcOnMap() */
    public void resetToAmbient() {
        staticLightRed   = 0.2f;
        staticLightGreen = 0.2f;
        staticLightBlue  = 0.2f;
        nearestLightDist = 99999;
    }

    public void setObjectHeight(int high) {
        objectHeight = high;
    }
}
