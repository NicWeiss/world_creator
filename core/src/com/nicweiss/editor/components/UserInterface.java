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
    private int editorScaleTotal = 0; // сохранённый зум редактора

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
        mapContextMenuWindow = new MapContextMenuWindow(dialogEditorWindow, mapRedirectWindow);
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

                MapObject tmp = new MapObject();
                tmp.setSurfaceTexture(tileTextures[1].texture);
                tmp.setSurfaceId(1);
                tmp.setTexture(tileTextures[textureId].texture);
                tmp.setObjectHeight(tileTextures[textureId].high);
                tmp.setTextureId(textureId);
                tmp.xPositionOnMap = i + 1;
                tmp.yPositionOnMap = j + 1;
                tmp.setUUID(uuid);
                if (type == "dialog") tmp.isDialogBind = true;
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
            // Карта построена — пересчёт освещения
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

    /** Создаёт Creation-объекты с текстурами на GL-потоке */
    private void buildEntities() {
        com.badlogic.gdx.graphics.Texture npcTex      = null;
        com.badlogic.gdx.graphics.Texture buildingTex = null;

        store.creationCount = -1;
        for (java.util.Map<String, Object> d : fileManager.pendingNpcs) {
            String uuid = (String) d.get("uuid");
            int x = (int) d.get("x");
            int y = (int) d.get("y");

            if (npcTex == null) npcTex = new com.badlogic.gdx.graphics.Texture("creations/creation.png");

            store.creationCount++;
            com.nicweiss.editor.creations.Creation cr = new com.nicweiss.editor.creations.Creation();
            cr.setUUID(uuid);
            cr.setCell(x, y);
            cr.setTexture(npcTex);
            float[] iso = Transform.cartesianToIsometric(
                (int)(x * store.tileSizeWidth), (int)(y * store.tileSizeHeight)
            );
            cr.setPosition(iso[0], iso[1]);
            store.creations[store.creationCount] = cr;
        }

        store.buildingCount = -1;
        for (java.util.Map<String, Object> d : fileManager.pendingBuildings) {
            String uuid = (String) d.get("uuid");
            int x = (int) d.get("x");
            int y = (int) d.get("y");

            if (buildingTex == null) buildingTex = new com.badlogic.gdx.graphics.Texture("objects/default_object.png");

            store.buildingCount++;
            com.nicweiss.editor.creations.Creation b = new com.nicweiss.editor.creations.Creation();
            b.setUUID(uuid);
            b.setCell(x, y);
            b.setTexture(buildingTex);
            float[] iso = Transform.cartesianToIsometric(
                (int)(x * store.tileSizeWidth), (int)(y * store.tileSizeHeight)
            );
            b.setPosition(iso[0], iso[1]);
            store.buildings[store.buildingCount] = b;
        }
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
        if (store.isSimulationMode) {
            stopSimulation();
        } else {
            startSimulation();
        }
    }

    private void startSimulation() {
        editorScaleTotal = store.scaleTotal; // запоминаем зум редактора
        store.isSimulationMode = true;

        // Player создаётся на GL-потоке (Texture требует GL-контекста)
        com.badlogic.gdx.Gdx.app.postRunnable(() -> store.player = new com.nicweiss.editor.simulation.Player());

        creationThread = newDaemon(new CreationThread(),        "CreationThread");
        weatherThread  = newDaemon(new WeatherThread(),         "WeatherThread");
        physicThread   = newDaemon(new PhysicThread(),          "PhysicThread");
        magickThread   = newDaemon(new MagickThread(),          "MagickThread");
        inputThread    = newDaemon(new SimulationInputThread(), "SimulationInputThread");

        creationThread.start();
        weatherThread.start();
        physicThread.start();
        magickThread.start();
        inputThread.start();

        animateZoom(SIM_ZOOM_TARGET); // плавно до фиксированного игрового зума
    }

    private void stopSimulation() {
        store.isSimulationMode = false;
        store.player = null;

        if (creationThread != null) creationThread.interrupt();
        if (weatherThread  != null) weatherThread.interrupt();
        if (physicThread   != null) physicThread.interrupt();
        if (magickThread   != null) magickThread.interrupt();
        if (inputThread    != null) inputThread.interrupt();

        animateZoom(editorScaleTotal); // плавно возвращаем зум редактора
    }

    /** Плавно анимирует scaleTotal к targetScaleTotal через CameraSettings */
    private void animateZoom(int targetScaleTotal) {
        new Thread(() -> {
            boolean zoomIn = targetScaleTotal < store.scaleTotal;
            while (!Thread.currentThread().isInterrupted()) {
                int diff = targetScaleTotal - store.scaleTotal;
                if (Math.abs(diff) < 100) {
                    // Финальный snap до точного значения
                    store.scaleTotal = targetScaleTotal;
                    store.isNeedToChangeScale = true;
                    break;
                }
                if (zoomIn) CameraSettings.upScale();
                else        CameraSettings.downScale();
                try { Thread.sleep(35); } catch (InterruptedException e) { break; }
            }
        }, "ZoomAnim").start();
    }

    private Thread newDaemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
