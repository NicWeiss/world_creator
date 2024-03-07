package com.nicweiss.editor.utils;

import com.nicweiss.editor.Generic.Store;

public class Light {
    public static Store store;
    Transform transform;

    private float lightShiftCoefficientX = 1, lightShiftCoefficientY = 1;

    public Light() {
        lightShiftCoefficientX = store.tileSizeWidth;
        lightShiftCoefficientY = store.tileSizeHeight;

        store.lightPoints = new float[store.lightPointsCount][5];
    }

    public void setUserPoint(float x, float y){
        store.lightPoints[0][0] = 1;
        store.lightPoints[0][1] = x;
        store.lightPoints[0][2] = y - 30;
    }

    public void addPoint(float x, float y){
        float[] point = transform.cartesianToIsometric((int)(x * store.tileSizeWidth), (int)(y * store.tileSizeHeight));
        if (checkExist(x, y)){
            return;
        }

        for (int i = 1; i<store.lightPoints.length; i++){
            if (store.lightPoints[i][0] == 0){
                store.lightPoints[i][0] = 1;
                store.lightPoints[i][1] = point[0] + lightShiftCoefficientX;
                store.lightPoints[i][2] = point[1] + lightShiftCoefficientY;
                store.lightPoints[i][3] = x;
                store.lightPoints[i][4] = y;
                break;
            }
        }
    }

    public boolean checkExist(float x, float y){
        for (int i = 1; i<store.lightPoints.length; i++){
            if (store.lightPoints[i][3] == x && store.lightPoints[i][4] == y && store.lightPoints[i][0] == 1){
                return true;
            }
        }

        return false;
    }

    public void removePoint(float x, float y){
        for (int i = 1; i<store.lightPoints.length; i++){
            if (store.lightPoints[i][3] == x && store.lightPoints[i][4] == y){
                store.lightPoints[i][0] = 0;
                store.lightPoints[i][1] = -999;
                store.lightPoints[i][2] = -999;
            }
        }
    }

    public void recalcOnMapFromPoint(int x, int y){
        int xFrom, xTo, yFrom, yTo;
        int recalcDistance = 10;

        xFrom = x - recalcDistance;
        xTo = x + recalcDistance;
        yFrom = y - recalcDistance;
        yTo = y + recalcDistance;

        if (xTo > store.mapWidth ) {
            xTo = store.mapWidth;
        }

        if (xFrom < 0 ) {
            xFrom = 0;
        }

        if (yTo > store.mapHeight ) {
            yTo = store.mapHeight;
        }

        if (yFrom < 0 ) {
            yFrom = 0;
        }


        for(int i = xFrom; i<xTo; i++) {
            for(int j = yFrom; j<yTo; j++) {
                store.objectedMap[i][j].calcLight("global");
            }
        }
    }

    public void recalcOnMap(){
        for(int i = 0; i<store.mapWidth; i++) {
            for(int j = 0; j<store.mapHeight; j++) {
                store.objectedMap[i][j].calcLight("global");
            }
        }
    }

    public void clearAll(){
        store.lightPoints = new float[store.lightPointsCount][5];
    }
}
