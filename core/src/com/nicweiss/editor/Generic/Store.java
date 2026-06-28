package com.nicweiss.editor.Generic;

import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;

import java.util.HashMap;
import java.util.LinkedHashMap;


public class Store {

    public static HashMap<String, Float> display = new HashMap();
    public static boolean isNeedToChangeScale = true;
    public static int scale = 0;
    public static int scaleTotal = 0;
    public static float uiWidthOriginal = 0;
    public static float uiHeightOriginal = 0;

    public static float playerPositionX, playerPositionY;
    public static float dayCoefficient = 1f;

    public static int lightPointsCount = 10000;
    public static float[][] lightPoints;
    // Наибольший реально занятый индекс в lightPoints — ограничивает inner-loop в calcLight
    public static int lightPointsHighWaterMark = 0;

    public static int scaledWidth, scaledHeight;
    public static int mouseX, mouseY;
    public static int selectedTailId = 0;
    public static boolean isSelectedLightObject = false;

    public static boolean isEditorLoadComplete = false;
    public static boolean isMapLoading     = false;
    public static boolean isSimulationMode = false;

    // Состояние клавиш движения в симуляции (volatile — читает SimulationInputThread)
    public static volatile boolean simKeyUp    = false;
    public static volatile boolean simKeyDown  = false;
    public static volatile boolean simKeyLeft  = false;
    public static volatile boolean simKeyRight = false;
    public static boolean isDragged = false;
    public static boolean isTouchUp = false;

    public static boolean isDay = true;

    public static MapObject[][] objectedMap;
    public static int mapHeight = 1000, mapWidth = 1000;

    public static float tileSizeWidth, tileSizeHeight;

    public static int shiftX, shiftY;
    public static int tileDownScale = 3;

    public static float selectedTileX = 0, selectedTileY = 0;
    public static int selectedTailObjectHigh = 0;

    public static int[][] pressedKeys = new int[100][2];

    public static LinkedHashMap<String, Object> dialogs = new LinkedHashMap<>();
    public static LinkedHashMap<String, Object> quests = new LinkedHashMap<>();
    public static LinkedHashMap<String, Object> itemTemplates = new LinkedHashMap<>();
    public static LinkedHashMap<String, Object> inventory = new LinkedHashMap<>();
    public static LinkedHashMap<String, Object> npcs = new LinkedHashMap<>();
    public static Creation[] buildings = new Creation[100];
    public static int buildingCount = -1;
    public static LinkedHashMap<String, Object> buildingNames = new LinkedHashMap<>();

    public static Creation[] creations = new Creation[100];
    public static int creationCount = -1;
}
