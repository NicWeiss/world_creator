package com.nicweiss.editor.utils;

import com.nicweiss.editor.Generic.Store;

/**
 * Управляет источниками света на карте.
 *
 * Формат lightPoints[i]: [active, isoX, isoY, tileX, tileY, r, g, b]
 * По умолчанию r=g=b=1 (белый, как костёр).
 * Цветные источники (напр. фиолетовая молния) задаются через addColoredPoint().
 */
public class Light {
    public static Store store;
    Transform transform;

    private static final int TILE_RADIUS = 12;

    private float lightShiftCoefficientX = 1, lightShiftCoefficientY = 1;

    public Light() {
        lightShiftCoefficientX = store.tileSizeWidth;
        lightShiftCoefficientY = store.tileSizeHeight;
        store.lightPoints = new float[store.lightPointsCount][8]; // +3 для RGB
        store.lightPointsHighWaterMark = 0;
    }

    public void setUserPoint(float x, float y) {
        store.lightPoints[0][0] = 1;
        store.lightPoints[0][1] = x;
        store.lightPoints[0][2] = y - 30;
        store.lightPoints[0][5] = 1f;
        store.lightPoints[0][6] = 1f;
        store.lightPoints[0][7] = 1f;
    }

    /** Добавляет белый источник света (как костёр). */
    public void addPoint(float x, float y) {
        addColoredPoint(x, y, 1f, 1f, 1f);
    }

    /**
     * Добавляет цветной источник света.
     * r, g, b — множители яркости канала [0..1].
     * Работает по тем же правилам, что и обычный источник.
     */
    public void addColoredPoint(float x, float y, float r, float g, float b) {
        float[] point = transform.cartesianToIsometric(
            (int)(x * store.tileSizeWidth), (int)(y * store.tileSizeHeight)
        );
        if (checkExist(x, y)) return;

        for (int i = 1; i < store.lightPoints.length; i++) {
            if (store.lightPoints[i][0] == 0) {
                store.lightPoints[i][0] = 1;
                store.lightPoints[i][1] = point[0] + lightShiftCoefficientX;
                store.lightPoints[i][2] = point[1] + lightShiftCoefficientY;
                store.lightPoints[i][3] = x;
                store.lightPoints[i][4] = y;
                store.lightPoints[i][5] = r;
                store.lightPoints[i][6] = g;
                store.lightPoints[i][7] = b;
                if (i > store.lightPointsHighWaterMark) store.lightPointsHighWaterMark = i;
                break;
            }
        }
    }

    public boolean checkExist(float x, float y) {
        for (int i = 1; i <= store.lightPointsHighWaterMark; i++) {
            if (store.lightPoints[i][3] == x && store.lightPoints[i][4] == y
             && store.lightPoints[i][0] == 1) {
                return true;
            }
        }
        return false;
    }

    public void removePoint(float x, float y) {
        for (int i = 1; i <= store.lightPointsHighWaterMark; i++) {
            if (store.lightPoints[i][3] == x && store.lightPoints[i][4] == y) {
                store.lightPoints[i][0] = 0;
                store.lightPoints[i][1] = -999;
                store.lightPoints[i][2] = -999;
            }
        }
        while (store.lightPointsHighWaterMark > 0
            && store.lightPoints[store.lightPointsHighWaterMark][0] == 0) {
            store.lightPointsHighWaterMark--;
        }
    }

    public void recalcOnMap() {
        for (int i = 0; i < store.mapWidth; i++)
            for (int j = 0; j < store.mapHeight; j++)
                store.objectedMap[i][j].resetToAmbient();

        for (int l = 1; l <= store.lightPointsHighWaterMark; l++) {
            if (store.lightPoints[l][0] == 0) continue;
            int lx = (int) store.lightPoints[l][3];
            int ly = (int) store.lightPoints[l][4];
            int xFrom = Math.max(0, lx - TILE_RADIUS);
            int xTo   = Math.min(store.mapWidth,  lx + TILE_RADIUS + 1);
            int yFrom = Math.max(0, ly - TILE_RADIUS);
            int yTo   = Math.min(store.mapHeight, ly + TILE_RADIUS + 1);
            for (int i = xFrom; i < xTo; i++)
                for (int j = yFrom; j < yTo; j++)
                    store.objectedMap[i][j].calcLight("global");
        }
    }

    public void recalcOnMapFromPoint(int x, int y) {
        int xFrom = Math.max(0, x - TILE_RADIUS);
        int xTo   = Math.min(store.mapWidth,  x + TILE_RADIUS);
        int yFrom = Math.max(0, y - TILE_RADIUS);
        int yTo   = Math.min(store.mapHeight, y + TILE_RADIUS);
        for (int i = xFrom; i < xTo; i++)
            for (int j = yFrom; j < yTo; j++)
                store.objectedMap[i][j].calcLight("global");
    }

    public void clearAll() {
        store.lightPoints = new float[store.lightPointsCount][8];
        store.lightPointsHighWaterMark = 0;
    }
}
