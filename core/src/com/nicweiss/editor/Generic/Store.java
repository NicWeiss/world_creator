package com.nicweiss.editor.Generic;

import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.simulation.Drop;
import com.nicweiss.editor.simulation.ItemStack;
import com.nicweiss.editor.simulation.Player;
import com.nicweiss.editor.simulation.PlayerHud;
import com.nicweiss.editor.simulation.PlayerUI;
import com.nicweiss.editor.simulation.SimCreature;
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
    // Интерфейс игрока (миникарта)
    public static PlayerUI  playerUI  = null;
    // HUD игрока (здоровье/мана/опыт, низ-центр экрана)
    public static PlayerHud playerHud = null;
    // Системный интерфейс (меню / инвентарь / навыки / задания)
    public static SystemUI       systemUI       = null;
    // Обработчик ввода в симуляции (он же фоновый поток движения)
    public static SimulationInputThread simulationInput = null;
    // Действие выхода из симуляции — устанавливает UserInterface, вызывает SystemUI
    public static Runnable stopSimulationAction = null;
    // Отладочный выброс лута+опыта по кнопке (Enter/R3) — устанавливает UserInterface,
    // вызывает SimulationInputThread. TODO: убрать, когда появится реальная точка вызова.
    public static Runnable debugDropTrigger = null;

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

    // Тайловые индексы в этом проекте хранятся 1-based ("cellIndex_1based" — mapCellX/Y у Drop и
    // Creation, xPositionOnMap/yPositionOnMap у MapObject.calcPosition): индекс массива objectedMap
    // при переводе в декартовый якорь тайла умножается на (idx + TILE_INDEX_BASE), а не на idx.
    // Это отдельная, "чистая" конвенция — не путать с TILE_X/Y_ANCHOR_EXTRA_OFFSET ниже (тот —
    // дополнительный квирк геометрии поверх этой базовой конвенции, только на X).
    public static final int TILE_INDEX_BASE = 1;

    // ИСТОРИЯ ПРАВОК (важно не наступать на те же грабли третий раз):
    // 1) Изначально константа применялась к рендер-позиции NPC/объектов — оказалось лишним (+1 не туда).
    // 2) Убрали компенсацию совсем — стало НЕ ХВАТАТЬ ровно тайла по X (проверено на скриншоте:
    //    спавнер, созданный через контекстное меню строго по центру между 4 кострами, рендерился
    //    внутри одного из костров — т.е. смещён на целый тайл по X относительно сохранённых координат).
    // 3) Корень найден: Editor.calcPositionCursor присваивает store.selectedTileX/Y асимметрично —
    //    `selectedTileX = floor(dotX/tileSizeX) - 1`, а `selectedTileY = floor(dotY/tileSizeY)` БЕЗ
    //    "-1". Через store.selectedTileX/Y создаются NPC/объекты по контекстному меню (и всё, что от
    //    них наследуется — mapCellX/Y хранится как есть). Поэтому РЕНДЕР позиции этих сущностей
    //    (Drop/Creation.setPosition, MapObject.calcPosition-подобная формула) ТРЕБУЕТ такую же
    //    компенсацию на X, что и сравнение со store.lightPoints — обе цепочки корнями упираются в
    //    один и тот же асимметричный "-1" на X в Editor.calcPositionCursor.
    // Итог: TILE_X_ANCHOR_EXTRA_OFFSET нужен И для store.lightPoints (Lighting.tileLightAnchor,
    // Player.isCollidingAt), И для рендер-позиции сущностей, чей mapCellX/Y пришёл из
    // store.selectedTileX/Y (NpcEditorWindow/ObjectEditorWindow/UserInterface.buildEntities/
    // SpawnManager — там ДОБАВЛЯЕТСЯ +TILE_X_ANCHOR_EXTRA_OFFSET к X при рендере, mapCellX/Y при
    // этом хранится БЕЗ сдвига, как и раньше). К MapObject.calcPosition (сами тайлы, xPositionOnMap
    // выставляется напрямую как arrayIndex+1 при генерации карты, БЕЗ похода через selectedTileX)
    // отношения не имеет — там компенсация не нужна и не применяется. Не применять и к раскастам
    // препятствий (MapObject.calcLight — offsets по X/Y одинаковые, не тот же квирк). Y-константа
    // ниже равна 0 (компенсация на Y не нужна), заведена намеренно, чтобы не потерять ось при правках.
    public static final int TILE_X_ANCHOR_EXTRA_OFFSET = 1;
    public static final int TILE_Y_ANCHOR_EXTRA_OFFSET = 0;

    public static int shiftX, shiftY;
    public static int tileDownScale = 3;

    public static float selectedTileX = 0, selectedTileY = 0;
    public static int selectedTailObjectHigh = 0;

    public static int[][] pressedKeys = new int[100][2];

    public static LinkedHashMap<String, Object> dialogs = new LinkedHashMap<>();
    public static LinkedHashMap<String, Object> quests = new LinkedHashMap<>();
    public static LinkedHashMap<String, Object> itemTemplates = new LinkedHashMap<>();
    public static LinkedHashMap<String, Object> inventory = new LinkedHashMap<>();
    // Занятость ячеек инвентаря (10 в ширину, 5 в высоту) — см. SystemUI.INV_COLS/INV_ROWS.
    public static boolean[][] inventoryGrid = new boolean[12][4];
    public static LinkedHashMap<String, Object> npcs = new LinkedHashMap<>();
    public static Creation[] buildings = new Creation[100];
    public static int buildingCount = -1;
    public static LinkedHashMap<String, Object> buildingNames = new LinkedHashMap<>();
    // Тип объекта (ключ ObjectCatalog) + персональные настройки конкретного здания-объекта
    // (спавнер: уровень/макс.кол-во, сундук: награды, источник: восстановление, портал: цель) —
    // uuid → LinkedHashMap с dunder-полями (__objectType__ обязателен, остальное по типу).
    // См. ObjectEditorWindow, сериализуется в buildings.json (см. FileManager).
    public static LinkedHashMap<String, LinkedHashMap> buildingSettings = new LinkedHashMap<>();

    public static Creation[] creations = new Creation[100];
    public static int creationCount = -1;
    // Тип статично расставленного NPC (ключ NpcCatalog.TYPES) — uuid → ключ. Заполняется в
    // NpcEditorWindow, сериализуется в npcs.json. Отсутствие записи = тип не задан (легаси-сейвы).
    public static LinkedHashMap<String, String> npcTypes = new LinkedHashMap<>();

    // Runtime боевые NPC, порождённые спавнерами в симуляции (см. SpawnManager) — ОТДЕЛЬНО от
    // Store.creations: существуют только в симуляции, не сохраняются, не в списке NpcEditorWindow,
    // не редактируемы. Аналог Store.drops по духу (runtime-only список игровых объектов).
    public static SimCreature[] simCreatures = new SimCreature[600];
    public static int simCreatureCount = -1;

    // Слоты снаряжения, требования которых не выполнены — рисуются тёмно-красными (см. SystemUI).
    public static java.util.Set<LinkedHashMap> inactiveEquipment = new java.util.HashSet<>();

    // Предметы/золото, лежащие на земле в симуляции (см. DropManager) — отдельный список
    // объектов карты, с которыми можно взаимодействовать (подобрать).
    public static Drop[] drops = new Drop[5000];
    public static int dropCount = -1;
    // Alt (клавиатура) или левый бампер (геймпад) — показывает подписи всех дропов в камере.
    public static volatile boolean revealAllDropLabels = false;

    // Режим ввода: true = геймпад, false = клавиатура/мышь (переключается автоматически).
    public static boolean isGamepadMode = false;

    // Предметы в слотах снаряжения (индексы совпадают с EQ_SLOTS в SystemUI). 14 слотов:
    // оружие/перчатки/шлем/амулет/броня/пояс/6 артефактов(3+3)/щит/сапоги.
    public static LinkedHashMap[] equipmentSlots = new LinkedHashMap[14];

    // 4 стека зелий/свитков (см. StackManager) — ёмкость каждой ячейки зависит от __mainStat__
    // надетого пояса (Player.beltCapacity). Индексы совпадают с кнопками 1-4 / D-pad (см.
    // SimulationInputThread) и с 4 ячейками быстрого применения в PlayerHud.
    public static ItemStack[] stacks = new ItemStack[4];
    static {
        for (int i = 0; i < stacks.length; i++) stacks[i] = new ItemStack();
    }
}
