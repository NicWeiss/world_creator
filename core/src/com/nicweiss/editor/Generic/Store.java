package com.nicweiss.editor.Generic;

import java.util.HashMap;


public class Store {

    public static HashMap<String, Float> display = new HashMap();
    public static boolean isNeedToChangeScale = false;
    public static int scale = 0;
    public static int scaleTotal = 0;
    public static float uiWidthOriginal = 0;
    public static float uiHeightOriginal = 0;

    public static float playerPositionX, playerPositionY;
    public static float dayCoefficient = 1;
    public static float lightShiftX, lightShiftY;

    public static int ligtPointsCount = 1000;
    public static float[][] lightPoints = new float[ligtPointsCount][3];


    public static void cameraUpScale(){
        isNeedToChangeScale = true;
        scale = -100;
        scaleTotal = scaleTotal - 100;
    }

    public static void cameraDownScale(){
        isNeedToChangeScale = true;
        scale = 100;
        scaleTotal = scaleTotal + 100;
    }

    public static void addLightPoint(float x, float y){
        for (int i = 1; i<lightPoints.length; i++){
            if (lightPoints[i][0] == 0){
                lightPoints[i][0] = 1;
                lightPoints[i][1] = x;
                lightPoints[i][2] = y;
                break;
            }
        }
    }

}
