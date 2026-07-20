package com.nicweiss.editor.components;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.components.windows.DialogEditorWindow;
import com.nicweiss.editor.components.windows.ItemsEditorWindow;
import com.nicweiss.editor.components.windows.LoadingWindow;
import com.nicweiss.editor.simulation.CreationThread;
import com.nicweiss.editor.simulation.MagickThread;
import com.nicweiss.editor.simulation.PhysicThread;
import com.nicweiss.editor.simulation.SimulationInputThread;
import com.nicweiss.editor.simulation.WeatherThread;
import com.nicweiss.editor.utils.CameraSettings;
import com.nicweiss.editor.utils.Transform;
import com.nicweiss.editor.components.windows.MapContextMenuWindow;
import com.nicweiss.editor.components.windows.MapRedirectWindow;
import com.nicweiss.editor.components.windows.NpcEditorWindow;
import com.nicweiss.editor.components.windows.ObjectEditorWindow;
import com.nicweiss.editor.components.windows.QuestsEditorWindow;
import com.nicweiss.editor.components.windows.TileSelectorWindow;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.objects.TextureObject;
import com.nicweiss.editor.utils.ArrayUtils;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.FileManager;
import com.nicweiss.editor.utils.Light;

public class UserInterface {
    FileManager fileManager;
    BOHelper bo_helper;
    public TileSelectorWindow tileSelectorWindow;
    public MapContextMenuWindow mapContextMenuWindow;
    public DialogEditorWindow dialogEditorWindow;
    public QuestsEditorWindow questsEditorWindow;
    public ItemsEditorWindow itemsEditorWindow;
    public NpcEditorWindow npcEditorWindow;
    public ObjectEditorWindow objectEditorWindow;
    public MapRedirectWindow mapRedirectWindow;
    private LoadingWindow loadingWindow;

    // Состояние построения карты по чанкам
    private String[][][] pendingMap   = null;
    private int buildCursor           = 0;
    private int lightCursor           = 0;
    private static final int MAP_CHUNK   = 50;  // строк за кадр
    private static final int LIGHT_CHUNK = 30;  // строк за кадр

    Texture openTexture, saveTexture, questsTexture, itemsTexture, npcTexture, objectTexture, switchTexture, white;

    // Потоки симуляции
    private Thread creationThread;
    private Thread weatherThread;
    private Thread physicThread;
    private Thread magickThread;
    private Thread inputThread;

    // Фиксированный scaleTotal для игрового режима (сильное приближение)
    private static final int SIM_ZOOM_TARGET = -900;
    private int editorScaleTotal = 0;
    private int editorShiftX     = 0;
    private int editorShiftY     = 0;
    private volatile boolean isTransitioning = false; // зум-анимация в процессе

    public static Store store;
    BaseObject[] ui;
    BaseObject buttonBG;
    TextureObject[] tileTextures;
    Light lightClass;
    int[] lightObjectIds;

    int menuItemSize = 40, menuItemSpace = 50;

    // Окна, участвующие в z-сортировке по фокусу
    private Window[] focusWindows;

    public UserInterface(TextureObject[] tileTextures, Light lightClass, int[] lightObjectIds) {
        this.lightObjectIds = lightObjectIds;
        this.tileTextures = tileTextures;
        this.lightClass = lightClass;
        fileManager = new FileManager();
        bo_helper = new BOHelper();

        dialogEditorWindow = new DialogEditorWindow();
        questsEditorWindow = new QuestsEditorWindow();
        itemsEditorWindow = new ItemsEditorWindow();
        npcEditorWindow = new NpcEditorWindow(dialogEditorWindow);
        objectEditorWindow = new ObjectEditorWindow(dialogEditorWindow);
        mapRedirectWindow = new MapRedirectWindow();
        loadingWindow     = new LoadingWindow();

        openTexture = new Texture("open.png");
        saveTexture = new Texture("save.png");
        questsTexture = new Texture("quests_button.png");
        itemsTexture = new Texture("items_button.png");
        npcTexture = new Texture("npc_button.png");
        objectTexture = new Texture("object_button.png");
        switchTexture = new Texture("switch.png");
        white = new Texture("white.png");

        tileSelectorWindow = new TileSelectorWindow(lightObjectIds);
        mapContextMenuWindow = new MapContextMenuWindow(dialogEditorWindow, npcEditorWindow, objectEditorWindow);
    }

    public void build() throws Exception {
        tileSelectorWindow.buildWindow(tileTextures);
        mapContextMenuWindow.buildWindow();
        dialogEditorWindow.buildWindow();
        questsEditorWindow.buildWindow();
        itemsEditorWindow.buildWindow();
        npcEditorWindow.buildWindow();
        objectEditorWindow.buildWindow();
        mapRedirectWindow.buildWindow();
        loadingWindow.buildWindow();

        ui = new BaseObject[7];

//        Open button
        ui[0] = bo_helper.constructObject(
                openTexture, 10, (int) (store.uiHeightOriginal - menuItemSize - 10), menuItemSize,
                menuItemSize, "open", 0
        );

//        Save button
        ui[1] = bo_helper.constructObject(
                saveTexture, menuItemSpace + 10, (int) (store.uiHeightOriginal - menuItemSize - 10),
                menuItemSize, menuItemSize, "save", 0
        );

        // Группа иконок редактора — 10px между кнопками (как между open и save)
        int editorIconStep = menuItemSize + 10;
        int editorStartX = (int)(ui[1].getX() + menuItemSize + menuItemSpace);

//        Quest window button
        ui[2] = bo_helper.constructObject(
            questsTexture,
            editorStartX,
            (int) (store.uiHeightOriginal - menuItemSize - 10),
            menuItemSize, menuItemSize, "quests", 0
        );

//        Items window button
        ui[3] = bo_helper.constructObject(
            itemsTexture,
            editorStartX + editorIconStep,
            (int) (store.uiHeightOriginal - menuItemSize - 10),
            menuItemSize, menuItemSize, "items", 0
        );

//        NPC window button
        ui[4] = bo_helper.constructObject(
            npcTexture,
            editorStartX + editorIconStep * 2,
            (int) (store.uiHeightOriginal - menuItemSize - 10),
            menuItemSize, menuItemSize, "npc", 0
        );

//        Object/Building window button
        ui[5] = bo_helper.constructObject(
            objectTexture,
            editorStartX + editorIconStep * 3,
            (int) (store.uiHeightOriginal - menuItemSize - 10),
            menuItemSize, menuItemSize, "object", 0
        );

//        Switch (режим симуляции) — всегда у правого края экрана
        ui[6] = bo_helper.constructObject(
            switchTexture,
            (int) (store.uiWidthOriginal - menuItemSize - 10),
            (int) (store.uiHeightOriginal - menuItemSize - 10),
            menuItemSize, menuItemSize, "switch", 0
        );

        buttonBG = bo_helper.constructObject(
                white, 100, 150, 1, 1, "buttonBG", 0
        );

        focusWindows = new Window[]{
            dialogEditorWindow, questsEditorWindow, itemsEditorWindow,
            npcEditorWindow, objectEditorWindow, mapRedirectWindow
        };
    }

    // Сортирует focusWindows по focusOrder (ascending) — результат: первый рендерится позади, последний сверху
    private void sortWindowsByFocus() {
        for (int i = 0; i < focusWindows.length - 1; i++) {
            for (int j = 0; j < focusWindows.length - 1 - i; j++) {
                if (focusWindows[j].focusOrder > focusWindows[j + 1].focusOrder) {
                    Window t = focusWindows[j];
                    focusWindows[j] = focusWindows[j + 1];
                    focusWindows[j + 1] = t;
                }
            }
        }
    }

    public void render(SpriteBatch uiBatch) {
        for (int idx = 0; idx < ui.length; idx++) {
            BaseObject baseObject = ui[idx];
            boolean isSwitchBtn = "switch".equals(baseObject.getObjectId());

            // В режиме симуляции рисуем только кнопку switch (остальное скрыто)
            if (store.isSimulationMode && !isSwitchBtn) continue;

            if (baseObject.isTouched) {
                bo_helper.draw(
                    uiBatch, buttonBG,
                    (int) baseObject.getX() - 5, (int) (store.uiHeightOriginal - menuItemSize - 15),
                    (int) baseObject.getWidth() + 10, (int) baseObject.getHeight() + 10
                );
            }

            // Switch-кнопка в режиме симуляции подсвечивается зелёным
            if (isSwitchBtn && store.isSimulationMode) {
                uiBatch.setColor(0.3f, 1f, 0.4f, 1f);
            }
            bo_helper.draw(uiBatch, baseObject, (int) baseObject.getX(), (int) (store.uiHeightOriginal - menuItemSize - 10));
            uiBatch.setColor(1f, 1f, 1f, 1f);
        }

        if (!store.isSimulationMode) {
            tileSelectorWindow.render(uiBatch);
            mapContextMenuWindow.render(uiBatch);
            sortWindowsByFocus();
            for (Window w : focusWindows) w.render(uiBatch);
        }
        if (loadingWindow.isShowWindow) loadingWindow.render(uiBatch);
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp, int button){
        // Окно загрузки имеет абсолютный приоритет
        if (loadingWindow.isShowWindow) {
            loadingWindow.checkTouch(isDragged, isTouchUp);
            return true;
        }

        // Обрабатываем в обратном порядке фокуса — верхнее окно получает ввод первым
        sortWindowsByFocus();
        for (int i = focusWindows.length - 1; i >= 0; i--) {
            if (focusWindows[i].checkTouch(isDragged, isTouchUp)) {
                return true;
            }
        }

        if (!tileSelectorWindow.isShowWindow && mapContextMenuWindow.checkTouch(isDragged, isTouchUp, button)){
            return true;
        }

        if (tileSelectorWindow.checkTouch(isDragged, isTouchUp)){
            return true;
        }

        if (!isTouchUp && !isDragged) {
            // Switch-кнопка доступна в любом режиме
            for (BaseObject baseObject : ui) {
                if (baseObject.isTouched && baseObject.getObjectId().equals("switch")) {
                    toggleSimulation();
                    return true;
                }
            }
//            Остальные кнопки — только в режиме редактора
            if (store.isSimulationMode) return false;
            for (BaseObject baseObject : ui) {
                if (baseObject.isTouched) {
                    if (baseObject.getObjectId().equals("open")) {
                        openMap();
                        return true;
                    }
                    if (baseObject.getObjectId().equals("save")) {
                        saveMap();
                        return true;
                    }
                    if (baseObject.getObjectId().equals("quests")) {
                        openQuestsWindow();
                        return true;
                    }
                    if (baseObject.getObjectId().equals("items")) {
                        itemsEditorWindow.show();
                        return true;
                    }
                    if (baseObject.getObjectId().equals("npc")) {
                        npcEditorWindow.show();
                        return true;
                    }
                    if (baseObject.getObjectId().equals("object")) {
                        objectEditorWindow.show();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean checkKey(int keyCode){
        if (mapContextMenuWindow.checkKey(keyCode)){
            return true;
        }

        if (tileSelectorWindow.checkKey(keyCode)){
            return true;
        }

        for (int i = focusWindows.length - 1; i >= 0; i--) {
            if (focusWindows[i].checkKey(keyCode)) return true;
        }

        return false;
    }

    public boolean keyTyped(char character){
        sortWindowsByFocus();
        for (int i = focusWindows.length - 1; i >= 0; i--) {
            Window w = focusWindows[i];
            if (w == dialogEditorWindow  && dialogEditorWindow.keyTyped(character))  return true;
            if (w == questsEditorWindow  && questsEditorWindow.keyTyped(character))  return true;
            if (w == itemsEditorWindow   && itemsEditorWindow.keyTyped(character))   return true;
            if (w == npcEditorWindow     && npcEditorWindow.keyTyped(character))     return true;
            if (w == objectEditorWindow  && objectEditorWindow.keyTyped(character))  return true;
        }
        return false;
    }

    public void onMouseMoved(){
        mapContextMenuWindow.onMouseMoved();
    }

    private void openMap() {
        // Шаг 1: выбираем файл на GL-потоке (FileDialog блокирует поток, но без GL)
        String filename = fileManager.pickFile();
        if (filename == null) return;

        // Показываем окно загрузки
        loadingWindow.startLoading(new java.io.File(filename).getName());
        loadingWindow.show();

        // Шаг 2: читаем ZIP в фоновом потоке
        final String finalFilename = filename;
        new Thread(() -> {
            String[][][] map = fileManager.loadFile(finalFilename, loadingWindow);

            // Шаг 3: возвращаемся на GL-поток для построения карты
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                if (map.length == 0) {
                    loadingWindow.hide();
                    return;
                }

                store.mapHeight = fileManager.mapHeight;
                store.mapWidth  = fileManager.mapWidth;
                store.objectedMap = new MapObject[store.mapHeight][store.mapWidth];
                store.isMapLoading = true;  // блокируем рендер до окончания построения
                lightClass.clearAll();

                pendingMap  = map;
                buildCursor = 0;
                loadingWindow.setStep(8, 9, "Построение карты", 0);
                buildMapChunk();
            });
        }).start();
    }

    /** Строит MAP_CHUNK строк за кадр, затем планирует следующий чанк */
    private void buildMapChunk() {
        int end = Math.min(buildCursor + MAP_CHUNK, store.mapWidth);
        for (int i = buildCursor; i < end; i++) {
            for (int j = 0; j < store.mapHeight; j++) {
                String uuid      = pendingMap[i][j][0];
                int textureId    = Integer.parseInt(pendingMap[i][j][1]);
                String type      = pendingMap[i][j][2];
                boolean isTree   = "tree".equals(pendingMap[i][j][3]);
                // objectHeight — с v3 (см. FileManager) сохраняется ПЕР-ТАЙЛОВО (рекам нужна своя
                // глубина на тайл, не единая высота текстуры) — null в старых сейвах, тогда как раньше.
                String savedHeight = pendingMap[i][j][4];
                // isRiverWater — с v4, чисто для water-шейдера (течение у реки vs волны у озера).
                boolean isRiverWater = "river".equals(pendingMap[i][j][5]);
                // surfaceId/surfaceDepth — с v5: земля/вода строго ПОВЕРХНОСТЬ, независимая от
                // объектного слоя (см. класс-комментарий в MapObject). null в старых сейвах — их
                // формат хранил воду В ОБЪЕКТНОМ слое (textureId==10, старая архитектура), поэтому
                // для миграции: если textureId==10 (вода) и surfaceId не сохранён, переносим воду
                // на поверхность сами (см. WATER_TEXTURE_ID в Editor — тот же id=10 здесь).
                String savedSurfaceId    = pendingMap[i][j][6];
                String savedSurfaceDepth = pendingMap[i][j][7];
                boolean isOldFormatWater = savedSurfaceId == null && textureId == 10;
                int surfaceId    = savedSurfaceId != null ? Integer.parseInt(savedSurfaceId) : (isOldFormatWater ? 10 : 1);
                int surfaceDepth = savedSurfaceDepth != null ? Integer.parseInt(savedSurfaceDepth)
                    : (isOldFormatWater && savedHeight != null ? Integer.parseInt(savedHeight) : 0);

                MapObject tmp = new MapObject();
                tmp.setSurfaceTexture(tileTextures[surfaceId].texture);
                tmp.setSurfaceId(surfaceId);
                tmp.surfaceDepth = surfaceDepth;
                tmp.setTexture(tileTextures[textureId].texture);
                tmp.setObjectHeight(savedHeight != null && !isOldFormatWater ? Integer.parseInt(savedHeight) : tileTextures[textureId].high);
                tmp.setTextureId(textureId);
                tmp.isTree = isTree;
                tmp.isRiverWater = isRiverWater;
                tmp.xPositionOnMap = i + store.TILE_INDEX_BASE;
                tmp.yPositionOnMap = j + store.TILE_INDEX_BASE;
                tmp.setUUID(uuid);
                if (type.equals("dialog")) tmp.isDialogBind = true;
                store.objectedMap[i][j] = tmp;

                if (ArrayUtils.checkIntInArray(textureId, lightObjectIds)) {
                    lightClass.addPoint(i, j);
                }
            }
        }
        buildCursor = end;
        int pct = buildCursor * 100 / store.mapWidth;
        loadingWindow.setStep(8, 9, "Построение карты", pct);

        if (buildCursor < store.mapWidth) {
            com.badlogic.gdx.Gdx.app.postRunnable(this::buildMapChunk);
        } else {
            // Карта построена — берег (см. Editor.markShores) не персистится, дёшево пересчитать
            // сразу после загрузки. Дорожки (см. Editor.recalcAllPaths) — тоже: flipX/flipY не
            // персистятся, полностью выводятся из соседей, как и isShore.
            com.nicweiss.editor.Views.Editor.markShores();
            com.nicweiss.editor.Views.Editor.recalcAllPaths();
            lightCursor = 0;
            loadingWindow.setStep(9, 9, "Расчёт освещения", 0);
            com.badlogic.gdx.Gdx.app.postRunnable(this::calcLightChunk);
        }
    }

    /** Пересчитывает освещение LIGHT_CHUNK строк за кадр */
    private void calcLightChunk() {
        int end = Math.min(lightCursor + LIGHT_CHUNK, store.mapWidth);
        for (int i = lightCursor; i < end; i++) {
            for (int j = 0; j < store.mapHeight; j++) {
                store.objectedMap[i][j].calcLight("global");
            }
        }
        lightCursor = end;
        int pct = lightCursor * 100 / store.mapWidth;
        loadingWindow.setStep(9, 9, "Расчёт освещения", pct);

        if (lightCursor < store.mapWidth) {
            com.badlogic.gdx.Gdx.app.postRunnable(this::calcLightChunk);
        } else {
            // Освещение готово — создаём сущности и объекты (GL-поток, нужны Texture)
            buildEntities();
            store.isMapLoading = false;  // разблокируем рендер
            pendingMap = null;
            loadingWindow.complete();
        }
    }

    /** Создаёт Creation-объекты с текстурами на GL-потоке. Текстура берётся по типу
     *  (NpcCatalog/ObjectCatalog), с кэшем на тип — раньше все NPC/здания в файле делили ОДНУ
     *  текстуру независимо от типа, теперь разные типы получают свои картинки. */
    private void buildEntities() {
        java.util.Map<String, com.badlogic.gdx.graphics.Texture> npcTexCache      = new java.util.HashMap<>();
        java.util.Map<String, com.badlogic.gdx.graphics.Texture> buildingTexCache = new java.util.HashMap<>();

        store.creationCount = -1;
        for (java.util.Map<String, Object> d : fileManager.pendingNpcs) {
            String uuid = (String) d.get("uuid");
            int x = (int) d.get("x");
            int y = (int) d.get("y");
            String typeKey = (String) d.get("type"); // может отсутствовать в старых сохранениях

            store.creationCount++;
            com.nicweiss.editor.creations.Creation cr = new com.nicweiss.editor.creations.Creation();
            cr.setUUID(uuid);
            cr.setCell(x, y);
            cr.setTexture(resolveNpcTexture(typeKey, npcTexCache));
            if (com.nicweiss.editor.utils.NpcCatalog.get(typeKey) != null) {
                cr.targetMaxScreenSize = store.tileSizeWidth * com.nicweiss.editor.utils.NpcCatalog.NPC_SIZE_TILE_MULT;
            }
            // Рендер-позиция — +TILE_X_ANCHOR_EXTRA_OFFSET на X (подтверждено эмпирически, см.
            // NpcEditorWindow.createNpcAt) — x/y пришли из сохранённого mapCellX/Y, конвенция та же.
            float[] iso = Transform.cartesianToIsometric(
                (int)((x + store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth), (int)(y * store.tileSizeHeight)
            );
            cr.setPosition(iso[0], iso[1]);
            store.creations[store.creationCount] = cr;
        }

        store.buildingCount = -1;
        for (java.util.Map<String, Object> d : fileManager.pendingBuildings) {
            String uuid = (String) d.get("uuid");
            int x = (int) d.get("x");
            int y = (int) d.get("y");
            java.util.LinkedHashMap settings = store.buildingSettings.get(uuid);

            store.buildingCount++;
            com.nicweiss.editor.creations.Creation b = new com.nicweiss.editor.creations.Creation();
            b.setUUID(uuid);
            b.setCell(x, y);
            b.setTexture(resolveBuildingTexture(settings, buildingTexCache));
            String objType = settings != null ? (String) settings.get("__objectType__") : null;
            if (com.nicweiss.editor.utils.ObjectCatalog.get(objType) != null) {
                b.targetMaxScreenSize = store.tileSizeWidth * com.nicweiss.editor.utils.ObjectCatalog.targetSizeTileMult(objType);
            }
            // См. выше (NPC) — рендер-позиция, +TILE_X_ANCHOR_EXTRA_OFFSET на X.
            float[] iso = Transform.cartesianToIsometric(
                (int)((x + store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth), (int)(y * store.tileSizeHeight)
            );
            b.setPosition(iso[0], iso[1]);
            store.buildings[store.buildingCount] = b;
        }
    }

    private com.badlogic.gdx.graphics.Texture resolveNpcTexture(
            String typeKey, java.util.Map<String, com.badlogic.gdx.graphics.Texture> cache) {
        com.nicweiss.editor.utils.NpcCatalog.TypeDef type = com.nicweiss.editor.utils.NpcCatalog.get(typeKey);
        String folder = type != null ? type.imageFolder : null;
        String cacheKey = folder != null ? folder : "__default__";

        com.badlogic.gdx.graphics.Texture cached = cache.get(cacheKey);
        if (cached != null) return cached;

        com.badlogic.gdx.graphics.Texture tex = folder != null
            ? new com.badlogic.gdx.graphics.Texture("creations/" + folder + "/default.png")
            : new com.badlogic.gdx.graphics.Texture("creations/creation.png");
        cache.put(cacheKey, tex);
        return tex;
    }

    private com.badlogic.gdx.graphics.Texture resolveBuildingTexture(
            java.util.LinkedHashMap settings, java.util.Map<String, com.badlogic.gdx.graphics.Texture> cache) {
        String typeKey = settings != null ? (String) settings.get("__objectType__") : null;
        com.nicweiss.editor.utils.ObjectCatalog.TypeDef type = com.nicweiss.editor.utils.ObjectCatalog.get(typeKey);
        String image = type != null ? type.defaultImage : null;
        String cacheKey = image != null ? image : "__default__";

        com.badlogic.gdx.graphics.Texture cached = cache.get(cacheKey);
        if (cached != null) return cached;

        com.badlogic.gdx.graphics.Texture tex = image != null
            ? new com.badlogic.gdx.graphics.Texture(image)
            : new com.badlogic.gdx.graphics.Texture("objects/default_object.png");
        cache.put(cacheKey, tex);
        return tex;
    }

    private void saveMap(){
        fileManager.saveMap(store.objectedMap, store.mapWidth, store.mapHeight);
    }

    public boolean getMouseMoveBlockStatus() {
        if (mapContextMenuWindow.isShow || tileSelectorWindow.isShowWindow || dialogEditorWindow.isShowWindow || questsEditorWindow.isShowWindow || itemsEditorWindow.isShowWindow || npcEditorWindow.isShowWindow || objectEditorWindow.isShowWindow || mapRedirectWindow.isShowWindow) {
            return true;
        }

        return false;
    }

    public void openQuestsWindow() {
        questsEditorWindow.show();
    }

    // ── Управление симуляцией ─────────────────────────────────────────────────

    public void toggleSimulation() {
        if (isTransitioning) return; // блокируем на время зум-анимации
        if (store.isSimulationMode) {
            stopSimulation();
        } else {
            startSimulation();
        }
    }

    private void startSimulation() {
        editorScaleTotal = store.scaleTotal;
        editorShiftX     = store.shiftX;   // запоминаем позицию камеры редактора
        editorShiftY     = store.shiftY;
        store.isSimulationMode      = true;
        store.stopSimulationAction  = () -> com.badlogic.gdx.Gdx.app.postRunnable(this::toggleSimulation);
        // Enter/R3 (см. SimulationInputThread) — отладочный выброс лута+опыта у ног игрока.
        store.debugDropTrigger      = () -> newDaemon(this::debugSpawnDrops, "DropDebugThread").start();
        // Сброс runtime-списка боевых NPC спавнеров (см. SpawnManager, CreationThread.update).
        com.nicweiss.editor.simulation.SpawnManager.init();

        // Player, PlayerUI, SystemUI и WeatherRenderer создаются на GL-потоке
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            store.player = new com.nicweiss.editor.simulation.Player();
            // Восстанавливаем прогресс (уровень/опыт/распределённые статы) с прошлой сессии
            // симуляции, если она уже была — иначе прокачка сбрасывалась бы при каждом выходе/
            // повторном входе (см. stopSimulation, где прогресс сохраняется перед обнулением player).
            if (store.savedProgress != null) {
                com.nicweiss.editor.simulation.Player p = store.player;
                com.nicweiss.editor.Generic.Store.PlayerProgress prog = store.savedProgress;
                p.level             = prog.level;
                p.experience        = prog.experience;
                p.baseStrength      = prog.baseStrength;
                p.baseMagic         = prog.baseMagic;
                p.baseDexterity     = prog.baseDexterity;
                p.unspentStatPoints = prog.unspentStatPoints;
                p.unspentSkillPoints = prog.unspentSkillPoints;
            }
            store.playerUI        = new com.nicweiss.editor.simulation.PlayerUI();
            store.playerHud       = new com.nicweiss.editor.simulation.PlayerHud();
            store.systemUI        = new com.nicweiss.editor.simulation.SystemUI();
            store.weatherRenderer = new com.nicweiss.editor.simulation.WeatherRenderer(lightClass);
        });

        creationThread = newDaemon(new CreationThread(), "CreationThread");
        weatherThread  = newDaemon(new WeatherThread(),  "WeatherThread");
        physicThread   = newDaemon(new PhysicThread(),   "PhysicThread");
        magickThread   = newDaemon(new MagickThread(),   "MagickThread");

        // SimulationInputThread — и фоновый поток движения, и обработчик ввода
        store.simulationInput = new SimulationInputThread();
        inputThread           = newDaemon(store.simulationInput, "SimulationInputThread");

        creationThread.start();
        weatherThread.start();
        physicThread.start();
        magickThread.start();
        inputThread.start();

        animateZoom(SIM_ZOOM_TARGET); // плавно до фиксированного игрового зума
    }

    // ── Отладка дропа (по кнопке Enter/R3, см. Store.debugDropTrigger) ─────────
    // TODO: убрать, когда появятся реальные точки вызова (смерть врага / открытие сундука) —
    // DropManager.dropLoot/dropExperience должны вызываться оттуда, а не отсюда.
    private void debugSpawnDrops() {
        if (store.player == null || !store.player.isInitialized()) return;

        int playerTileX = (int) (store.player.worldX / store.tileSizeWidth)  - 1;
        int playerTileY = (int) (store.player.worldY / store.tileSizeHeight) - 1;

        // Уровень врага = уровень игрока (пока нет реальной точки вызова — смерть врага того же
        // уровня даёт multiplier=1, элита/босс — больше). Раньше был зафиксирован на 10 — не
        // отражал реальные статы игрока (magicFind и т.д. в dropLoot зависят от store.player,
        // но "уровень источника дропа" был constant, из-за чего отладка на высоких уровнях
        // выглядела как дроп с врага уровня 10, а не как игрок ожидает).
        int enemyLevel = Math.max(1, store.player.level);
        com.nicweiss.editor.simulation.DropManager.dropExperience(enemyLevel, 1f, playerTileX, playerTileY);

        // Факелы больше НЕ форсируются здесь — они уже роллятся сами по вероятности внутри
        // dropLoot() -> rollTorchDrop() на каждой из итераций ниже (см. DropManager), как и весь
        // остальной лут. Принудительный гарантированный дроп по одному факелу каждой редкости
        // обходил систему вероятностей — убран.
        for (int i = 0; i < 10; i++) {
            if (!store.isSimulationMode) return;
            com.nicweiss.editor.simulation.DropManager.dropLoot(enemyLevel, playerTileX, playerTileY);
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    private void stopSimulation() {
        // Сохраняем прогресс прокачки (уровень/опыт/распределённые статы) ПЕРЕД тем, как player
        // будет обнулён — см. восстановление в startSimulation.
        if (store.player != null) {
            com.nicweiss.editor.simulation.Player p = store.player;
            com.nicweiss.editor.Generic.Store.PlayerProgress prog = new com.nicweiss.editor.Generic.Store.PlayerProgress();
            prog.level             = p.level;
            prog.experience        = p.experience;
            prog.baseStrength      = p.baseStrength;
            prog.baseMagic         = p.baseMagic;
            prog.baseDexterity     = p.baseDexterity;
            prog.unspentStatPoints = p.unspentStatPoints;
            prog.unspentSkillPoints = p.unspentSkillPoints;
            store.savedProgress = prog;
        }

        store.isSimulationMode  = false;
        store.player            = null;
        store.playerUI          = null;
        store.playerHud         = null;
        store.systemUI          = null;
        store.simulationInput      = null;
        store.stopSimulationAction = null;
        store.debugDropTrigger     = null;
        store.weatherRenderer      = null;
        store.rainIntensity  = 0f;
        store.windMultiplier = 1f;
        store.windGustSpeed  = 1f;
        store.lightningFlash = 0f;
        com.nicweiss.editor.simulation.SpawnManager.clear();

        if (creationThread != null) creationThread.interrupt();
        if (weatherThread  != null) weatherThread.interrupt();
        if (physicThread   != null) physicThread.interrupt();
        if (magickThread   != null) magickThread.interrupt();
        if (inputThread    != null) inputThread.interrupt();

        // При выходе — просто отдаляем камеру от текущего положения, без возврата к точке входа
        animateZoom(editorScaleTotal);
    }

    /**
     * Плавно анимирует scaleTotal к targetScaleTotal.
     * Если переданы finalShiftX/Y — параллельно интерполирует shiftX/Y к ним,
     * снэппит их точно в конце. Используется при выходе из симуляции.
     */
    /**
     * Zoom без восстановления позиции, но с коррекцией shiftX/Y на каждом шаге:
     * при zoom-out каждый шаг увеличивает вьюпорт на 100px по X и 100*ar по Y,
     * поэтому сдвигаем shift на +50 / +50*ar чтобы центр экрана остался на том же тайле.
     */
    private void animateZoom(int targetScaleTotal) {
        isTransitioning = true;
        float ar = store.uiWidthOriginal > 0
            ? (float) store.uiHeightOriginal / store.uiWidthOriginal : 1f;

        new Thread(() -> {
            boolean zoomIn = targetScaleTotal < store.scaleTotal;
            while (!Thread.currentThread().isInterrupted()) {
                int diff = targetScaleTotal - store.scaleTotal;
                if (Math.abs(diff) < 100) {
                    store.scaleTotal = targetScaleTotal;
                    store.isNeedToChangeScale = true;
                    isTransitioning = false;
                    break;
                }
                if (zoomIn) {
                    CameraSettings.upScale();
                } else {
                    CameraSettings.downScale();
                    // Вьюпорт вырос на 100×(100*ar) → центрируем смещая shift на +50/(+50*ar)
                    store.shiftX += 50;
                    store.shiftY += (int)(50 * ar);
                }
                try { Thread.sleep(35); } catch (InterruptedException e) {
                    isTransitioning = false;
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ZoomAnim").start();
    }

    private void animateZoom(int targetScaleTotal, int finalShiftX, int finalShiftY) {
        boolean restoreShift = finalShiftX != Integer.MIN_VALUE;
        int startShiftX = store.shiftX;
        int startShiftY = store.shiftY;
        int startScale  = store.scaleTotal;
        int totalDelta  = Math.abs(targetScaleTotal - startScale);

        isTransitioning = true;

        new Thread(() -> {
            boolean zoomIn = targetScaleTotal < store.scaleTotal;
            while (!Thread.currentThread().isInterrupted()) {
                int diff = targetScaleTotal - store.scaleTotal;
                if (Math.abs(diff) < 100) {
                    store.scaleTotal = targetScaleTotal;
                    store.isNeedToChangeScale = true;
                    if (restoreShift) {
                        store.shiftX = finalShiftX;
                        store.shiftY = finalShiftY;
                    }
                    isTransitioning = false; // анимация завершена — кнопка разблокирована
                    break;
                }
                if (zoomIn) CameraSettings.upScale();
                else        CameraSettings.downScale();

                if (restoreShift && totalDelta > 0) {
                    int remaining = Math.abs(targetScaleTotal - store.scaleTotal);
                    float progress = 1f - (float) remaining / totalDelta;
                    store.shiftX = startShiftX + (int)(progress * (finalShiftX - startShiftX));
                    store.shiftY = startShiftY + (int)(progress * (finalShiftY - startShiftY));
                }

                try { Thread.sleep(35); } catch (InterruptedException e) {
                    isTransitioning = false; // снимаем блокировку и при прерывании
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ZoomAnim").start();
    }

    private Thread newDaemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
