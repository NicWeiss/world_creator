package com.nicweiss.editor.utils;

public class Transform {
    public static float[] cartesianToIsometric(int x, int y){
        float isometricX = x - y;
        float isometricY = (float)((x + y) / 2);
        return new float[] {isometricX, isometricY};
    }

    /** Float-перегрузка: без целочисленного деления — исключает субпиксельный джиттер. */
    public static float[] cartesianToIsometric(float x, float y){
        return new float[] {x - y, (x + y) / 2f};
    }

    public static float[] isometricToCartesian(float x, float y){
        float decartX=(2*y+x)/2;
        float decartY=(2*y-x)/2;
        return new float[] {decartX, decartY} ;
    }
}
