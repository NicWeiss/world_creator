package com.nicweiss.editor.Generic;

import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.simulation.Player;
import com.nicweiss.editor.simulation.PlayerUI;
import com.nicweiss.editor.simulation.SimulationInputThread;
import com.nicweiss.editor.simulation.SystemUI;
import com.nicweiss.editor.simulation.WeatherRenderer;

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

    // Игрок — единственный экземпляр, создаётся при запуске симуляции
    public static Player   player   = null;
    // Интерфейс игрока (HUD)
    public static PlayerUI  playerUI  = null;
    // Системный интерфейс (меню / инвентарь / навыки / задания)
    public static SystemUI       systemUI       = null;
    // Обработчик ввода в симуляции (он же фоновый поток движения)
    public static SimulationInputThread simulationInput = null;
    // Действие выхода из симуляции — устанавливает UserInterface, вызывает SystemUI
    public static Runnable stopSimulationAction = null;

    // Рендер погоды — создаётся на GL-потоке при запуске симуляции
    public static WeatherRenderer weatherRenderer = null;

    // ── Погода ────────────────────────────────────────────────────────────────
    // Интенсивность дождя [0..1] — плавно меняется WeatherThread
    public static volatile float rainIntensity   = 0f;
    // Множитель амплитуды ветра для деревьев [1..3]
    public static volatile float windMultiplier = 1f;
    // Множитель частоты порывов ветра [1..2.5]
    public static volatile float windGustSpeed  = 1f;
    // Направление ветра в изометрических экранных координатах (единичный вектор).
    // windDirX=1,windDirY=0 = вправо по экрану; меняется WeatherThread плавно.
    public static volatile float windDirX = 1f;
    public static volatile float windDirY = 0f;
    // Яркость вспышки молнии [0..1+], затухает GL-потоком (Editor)
    public static volatile float lightningFlash = 0f;
    // Точка удара молнии в декартовых мировых координатах
    public static volatile float lightningTargetWX = 0f;
    public static volatile float lightningTargetWY = 0f;
    // Сигнал для WeatherRenderer сгенерировать новый разряд
    public static volatile boolean lightningBoltNew = false;

    // Динамический свет вспышки молнии (пересчитывается каждый кадр как факел, без addPoint/removePoint).
    // isoX/Y — позиция источника в изометрических мировых координатах (без shift).
    // bright = 0 означает «вспышки нет», тайлы пересчитывать не нужно.
    public static volatile float lightningFlashIsoX  = 0f;
    public static volatile float lightningFlashIsoY  = 0f;
    public static volatile float lightningFlashBright = 0f;

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
    // Нормализованная фаза цикла дня [0..1]: 0=рассвет, 0.25=полдень, 0.5=закат, 0.75=полночь
    public static volatile float dayPhase   = 0.25f;
    // Секунды для смещения облаков (обновляется WeatherThread)
    public static volatile float cloudTime  = 0f;

    // Состояние клавиш движения в симуляции (volatile — читает SimulationInputThread)
    public static volatile boolean simKeyUp    = false;
    public static volatile boolean simKeyDown  = false;
    public static volatile boolean simKeyLeft  = false;
    public static volatile boolean simKeyRight = false;
    // Левый стик геймпада [-1..1], опрашивается GL-потоком
    public static volatile float simStickX = 0f;
    public static volatile float simStickY = 0f;
    // Атомарный дельта камеры: старшие 32 бита = dX, младшие 32 = dY.
    // Пишется фоновым потоком, читается и сбрасывается GL-потоком.
    // volatile long гарантирует атомарность — нет частичных обновлений.
    public static volatile long simCamDelta = 0L;
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
