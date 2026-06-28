package com.nicweiss.editor.utils;

import com.nicweiss.editor.Generic.Store;

public class Light {
    public static Store store;
    Transform transform;

    // Радиус действия источника в тайлах — используется в bounding box recalc.
    // Подобран консервативно: экранный радиус 120px ÷ ~21px/тайл ≈ 6, берём 12 с запасом.
    private static final int TILE_RADIUS = 12;

    private float lightShiftCoefficientX = 1, lightShiftCoefficientY = 1;

    public Light() {
        lightShiftCoefficientX = store.tileSizeWidth;
        lightShiftCoefficientY = store.tileSizeHeight;
        store.lightPoints = new float[store.lightPointsCount][5];
        store.lightPointsHighWaterMark = 0;
    }

    public void setUserPoint(float x, float y){
        store.lightPoints[0][0] = 1;
        store.lightPoints[0][1] = x;
        store.lightPoints[0][2] = y - 30;
    }

    public void addPoint(float x, float y){
        float[] point = transform.cartesianToIsometric(
            (int)(x * store.tileSizeWidth), (int)(y * store.tileSizeHeight)
        );
        if (checkExist(x, y)) return;

        for (int i = 1; i < store.lightPoints.length; i++){
            if (store.lightPoints[i][0] == 0){
                store.lightPoints[i][0] = 1;
                store.lightPoints[i][1] = point[0] + lightShiftCoefficientX;
                store.lightPoints[i][2] = point[1] + lightShiftCoefficientY;
                store.lightPoints[i][3] = x;
                store.lightPoints[i][4] = y;
                // Фиксируем наибольший занятый индекс
                if (i > store.lightPointsHighWaterMark) store.lightPointsHighWaterMark = i;
                break;
            }
        }
    }

    public boolean checkExist(float x, float y){
        for (int i = 1; i <= store.lightPointsHighWaterMark; i++){
            if (store.lightPoints[i][3] == x && store.lightPoints[i][4] == y
             && store.lightPoints[i][0] == 1){
                return true;
            }
        }
        return false;
    }

    public void removePoint(float x, float y){
        for (int i = 1; i <= store.lightPointsHighWaterMark; i++){
            if (store.lightPoints[i][3] == x && store.lightPoints[i][4] == y){
                store.lightPoints[i][0] = 0;
                store.lightPoints[i][1] = -999;
                store.lightPoints[i][2] = -999;
            }
        }
        // Пересчитываем highWaterMark (убираем пустые хвосты)
        while (store.lightPointsHighWaterMark > 0
            && store.lightPoints[store.lightPointsHighWaterMark][0] == 0) {
            store.lightPointsHighWaterMark--;
        }
    }

    /**
     * Пересчёт освещения для всей карты.
     *
     * Оптимизация: вместо вызова calcLight() для всех 1M тайлов (каждый проверяет 10K слотов)
     * используется light-centric подход:
     *   1. Быстрый сброс всех тайлов до ambient (4 присваивания на тайл, без DDA)
     *   2. Для каждого активного источника пересчитываем только тайлы в TILE_RADIUS вокруг него
     *
     * Сложность: вместо O(tiles × 10000) → O(tiles + lights × radius²)
     */
    public void recalcOnMap(){
        // Шаг 1: быстро сбросить всё в ambient
        for (int i = 0; i < store.mapWidth; i++) {
            for (int j = 0; j < store.mapHeight; j++) {
                store.objectedMap[i][j].resetToAmbient();
            }
        }

        // Шаг 2: пересчитать только зоны вокруг активных источников
        for (int l = 1; l <= store.lightPointsHighWaterMark; l++) {
            if (store.lightPoints[l][0] == 0) continue;

            int lx = (int) store.lightPoints[l][3];
            int ly = (int) store.lightPoints[l][4];

            int xFrom = Math.max(0, lx - TILE_RADIUS);
            int xTo   = Math.min(store.mapWidth,  lx + TILE_RADIUS + 1);
            int yFrom = Math.max(0, ly - TILE_RADIUS);
            int yTo   = Math.min(store.mapHeight, ly + TILE_RADIUS + 1);

            for (int i = xFrom; i < xTo; i++) {
                for (int j = yFrom; j < yTo; j++) {
                    store.objectedMap[i][j].calcLight("global");
                }
            }
        }
    }

    /** Локальный пересчёт при размещении тайла — уже ограничен по радиусу, ускоряем inner-loop */
    public void recalcOnMapFromPoint(int x, int y){
        int xFrom = Math.max(0, x - TILE_RADIUS);
        int xTo   = Math.min(store.mapWidth,  x + TILE_RADIUS);
        int yFrom = Math.max(0, y - TILE_RADIUS);
        int yTo   = Math.min(store.mapHeight, y + TILE_RADIUS);

        for (int i = xFrom; i < xTo; i++) {
            for (int j = yFrom; j < yTo; j++) {
                store.objectedMap[i][j].calcLight("global");
            }
        }
    }

    public void clearAll(){
        store.lightPoints = new float[store.lightPointsCount][5];
        store.lightPointsHighWaterMark = 0;
    }
}
