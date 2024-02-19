package com.nicweiss.editor.utils;

public class Transform {
    public static float[] cartesianToIsometric(int x, int y){
        float isometricX = x - y ;
        float isometricY = (float) ((x + y) / 2);

        return new float[] {isometricX, isometricY};
    }

    public static float[] isometricToCartesian(float x, float y){
        float decartX=(2*y+x)/2;
        float decartY=(2*y-x)/2;
        return new float[] {decartX, decartY} ;
    }
}
