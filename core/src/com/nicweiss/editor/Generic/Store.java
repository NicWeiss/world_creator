package com.nicweiss.editor.Generic;

import java.util.HashMap;


public class Store {

    public static HashMap<String, Float> display = new HashMap();
    public static boolean isNeedToChangeScale = true;
    public static int scale = 0;
    public static int scaleTotal = 0;
    public static float uiWidthOriginal = 0;
    public static float uiHeightOriginal = 0;

    public static float playerPositionX, playerPositionY;
    public static float dayCoefficient = (float)1;
    public static float lightShiftX, lightShiftY;

    public static int lightPointsCount = 10000;
    public static float[][] lightPoints;

    public static int scaledWidth, scaledHeight;
    public static int mouseX, mouseY;
    public static int selectedTailId = 1;
    public static boolean isSelectedLightObject = false;

    public static boolean isEditorLoadComplete = false;
    public static boolean isDragged = false;
    public static BaseObject[][] objectedMap;
    public static int mapHeight = 300, mapWidth = 300;

    public static float tileSizeWidth, tileSizeHeight;

    public static int shiftX, shiftY;
    public static int tileDownScale = 3;
}
