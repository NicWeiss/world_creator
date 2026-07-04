package com.nicweiss.editor.utils;

import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.simulation.Drop;

/**
 * Единая точка расчёта освещения предметов на земле (Drop) и NPC/объектов (Creation) — раньше
 * эта логика была продублирована 1-в-1 в обоих классах, что плохо (правки/баги приходилось чинить
 * дважды). Тайлы карты считают освещение отдельно (см. MapObject.calcLitColor — там есть DDA-раскаст
 * тени от препятствий; здесь — упрощённая версия без него, приемлемая для мелких объектов на земле).
 */
public class Lighting {
    public static Store store;

    private Lighting() {}

    /**
     * Позиция источника света для якоря тайла (1-based mapCellX/Y, cellIndex-конвенция) — формат,
     * совместимый с Light.addColoredPoint/store.lightPoints[i][1]/[2]. ВАЖНО: это НЕ рендер-позиция
     * сущности — Light.addColoredPoint кладёт точки в системе координат, смещённой на один тайл по
     * X относительно обычного рендер-якоря (см. Store.TILE_X_ANCHOR_EXTRA_OFFSET) — квирк только
     * для сравнения расстояний со store.lightPoints, к позиции спрайта на экране не относится.
     * Пишет результат в out (переиспользуемый буфер — без аллокаций на кадр) и возвращает его же.
     */
    public static float[] tileLightAnchor(int mapCellX, int mapCellY, float[] out) {
        float[] iso = Transform.cartesianToIsometric(
            (mapCellX - store.TILE_INDEX_BASE - store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth,
            (mapCellY - store.TILE_INDEX_BASE - store.TILE_Y_ANCHOR_EXTRA_OFFSET) * store.tileSizeHeight);
        out[0] = iso[0] + store.tileSizeWidth;
        out[1] = iso[1] + store.tileSizeHeight;
        return out;
    }

    /**
     * Освещённость точки (selfIsoX/Y — в формате tileLightAnchor) — дневная температура цвета,
     * затемнение дождём, статичные источники света (store.lightPoints) и сферы опыта (свои
     * источники, см. MapObject.EXP_ORB_LIGHT_*). excludeDrop — сфера опыта, которую не нужно
     * подсвечивать самой собой (передать null, если вызывающий — не Drop). Пишет в out (буфер
     * вызывающего, без аллокаций) и возвращает его же.
     */
    public static float[] computeLitColor(float selfIsoX, float selfIsoY, Drop excludeDrop, float[] out) {
        double angle  = store.dayPhase * 2.0 * Math.PI;
        float  cosA   = (float) Math.cos(angle);
        float  warmth = cosA * cosA;
        float  cool   = Math.max(0f, (float) Math.sin(angle));
        float  dayBright = Math.max(0f, store.dayCoefficient);
        float tR = 1f + (warmth * 0.22f - cool * 0.06f) * dayBright;
        float tG = 1f + (-warmth * 0.06f + cool * 0.03f) * dayBright;
        float tB = 1f + (-warmth * 0.28f + cool * 0.22f) * dayBright;

        float raw = Math.max(0.05f, (0.2f + store.dayCoefficient) * (1f - store.rainIntensity * 0.45f));
        float dayR = raw * tR, dayG = raw * tG, dayB = raw * tB;

        float lr = 0f, lg = 0f, lb = 0f;

        if (store.lightPoints != null) {
            int countTo = Math.min(store.lightPointsHighWaterMark + 1, store.lightPoints.length);
            for (int i = 1; i < countTo; i++) {
                float[] lp = store.lightPoints[i];
                if (lp[0] == 0) continue;
                float ddx = selfIsoX - lp[1];
                float ddy = (selfIsoY - lp[2]) * 1.45f;
                if (Math.abs(ddx) > 130f || Math.abs(ddy) > 130f) continue;
                float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                if (dist >= 120f) continue;
                float lpv  = dist / 120f * 100f;
                float dark = Math.max(0.2f, 1.6f - lpv / 100f * 0.8f);
                lr = Math.max(lr, Math.max(0f, 1f - (lpv / (dark * 100f + 35f) * 50f) / 500f) * lp[5]);
                lg = Math.max(lg, Math.max(0f, 1f - (lpv / (dark * 100f + 15f) * 50f) / 500f) * lp[6]);
                lb = Math.max(lb, Math.max(0f, 1f - (lpv / (dark * 100f + 5f)  * 50f) / 500f) * lp[7]);
            }
        }

        if (store.drops != null) {
            for (Drop d : store.drops) {
                if (d == null || d == excludeDrop || d.expAmount <= 0) continue;
                float odx = selfIsoX - d.getLightSourceIsoX();
                float ody = (selfIsoY - d.getLightSourceIsoY()) * 1.45f;
                if (Math.abs(odx) > MapObject.EXP_ORB_LIGHT_RADIUS || Math.abs(ody) > MapObject.EXP_ORB_LIGHT_RADIUS) continue;
                float odist = (float) Math.sqrt(odx * odx + ody * ody);
                if (odist >= MapObject.EXP_ORB_LIGHT_RADIUS) continue;
                float t = 1f - odist / MapObject.EXP_ORB_LIGHT_RADIUS;
                lr = Math.max(lr, MapObject.EXP_ORB_LIGHT_R * t);
                lg = Math.max(lg, MapObject.EXP_ORB_LIGHT_G * t);
                lb = Math.max(lb, MapObject.EXP_ORB_LIGHT_B * t);
            }
        }

        out[0] = Math.min(1f, Math.max(dayR, lr));
        out[1] = Math.min(1f, Math.max(dayG, lg));
        out[2] = Math.min(1f, Math.max(dayB, lb));
        return out;
    }
}
