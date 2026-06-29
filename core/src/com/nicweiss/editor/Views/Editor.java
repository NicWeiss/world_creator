package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;

import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;
import com.nicweiss.editor.components.UserInterface;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.objects.TextureObject;
import com.nicweiss.editor.utils.ArrayUtils;
import com.nicweiss.editor.utils.CameraSettings;
import com.nicweiss.editor.utils.Transform;
import com.nicweiss.editor.utils.Light;
import com.nicweiss.editor.utils.Perlin;

import java.util.Random;


public class Editor extends View{
    Texture hintUp, hintDown;
    TextureObject[] textures;

    Light light;
    UserInterface userInterface;
    Transform transform;

    int tileSizeX, tileSizeY;
    int selectedTileX, selectedTileY;
    int mouseX, mouseY;
    float cm = 0.01f;
    int[] lightObjectIds, surfacesIds;
    boolean isImmediatelyReleaseKey = false;
    boolean isUiTouched = false;

    // ── Дождь ─────────────────────────────────────────────────────────────────
    private static final int    N_DROPS         = 600;
    private static final float  LIGHT_RADIUS    = 160f;
    private static final float  SPLASH_DURATION = 0.22f;
    private static final int    N_SPLASHES      = 200;

    private final float[] dropWX    = new float[N_DROPS];
    private final float[] dropWY    = new float[N_DROPS];
    private final float[] dropAlt   = new float[N_DROPS];
    private final float[] dropSpd   = new float[N_DROPS];
    private final float[] splashX   = new float[N_SPLASHES];
    private final float[] splashY   = new float[N_SPLASHES];
    private final float[] splashAge = new float[N_SPLASHES]; // 1..0, 0 = inactive
    private int     splashNext = 0;
    private boolean dropsReady = false;
    private float   lastShiftX = Float.NaN;
    private float   lastShiftY = Float.NaN;
    private Texture dropTex;
    private Texture splashTex;
    private Texture flashTex;

    public Editor(){
        lightObjectIds = new int[] {11};
        surfacesIds = new int [] {1, 10};

        hintUp = new Texture("tile_hint_up.png");
        hintDown = new Texture("tile_hint_down.png");

        textures = new TextureObject[] {
            new TextureObject("gp_0.png",  0),
            new TextureObject("gp_1.png",  0),
            new TextureObject("gp_2.png",  50, true),   // дуб
            new TextureObject("gp_3.png",  50, true),   // ель
            new TextureObject("gp_4.png",  20, true),   // осеннее дерево
            new TextureObject("gp_5.png",  2),
            new TextureObject("gp_6.png",  4),
            new TextureObject("gp_7.png",  10),
            new TextureObject("gp_8.png",  1),
            new TextureObject("gp_9.png",  50),
            new TextureObject("gp_10.png", 0),
            new TextureObject("gp_11.png", 5),
            new TextureObject("gp_12.png", 0),
            new TextureObject("gp_13.png", 0),
            new TextureObject("gp_14.png", 0),
            new TextureObject("gp_15.png", 0),
            new TextureObject("gp_16.png", 0),
            new TextureObject("gp_17.png", 0),
            new TextureObject("gp_18.png", 0),
            new TextureObject("gp_19.png", 0),
            new TextureObject("gp_20.png", 0),
            new TextureObject("gp_21.png", 0),
            new TextureObject("gp_22.png", 0),
            new TextureObject("gp_23.png", 0)
        };

        store.tileSizeWidth = tileSizeX = 158 / store.tileDownScale;
        store.tileSizeHeight = tileSizeY = 158 / store.tileDownScale;
        store.shiftY = 0;
        store.shiftX = 12 * tileSizeX;

        light = new Light();
        userInterface = new UserInterface(textures, light, lightObjectIds);

        // Текстура капли дождя: 2×14, градиент прозрачно→плотно (альфа до 1.0)
        Pixmap dp = new Pixmap(2, 14, Pixmap.Format.RGBA8888);
        dp.setColor(0, 0, 0, 0); dp.fill();
        for (int py = 0; py < 14; py++) {
            float a = 0.35f + (py / 13f) * 0.65f; // 0.35 вверху → 1.0 внизу
            dp.setColor(1f, 1f, 1f, a);
            dp.drawPixel(0, py);
            dp.drawPixel(1, py);
        }
        dropTex = new Texture(dp);
        dp.dispose();

        // Текстура всплеска: изометрически плоский овальный контур 8×4
        Pixmap sp = new Pixmap(8, 4, Pixmap.Format.RGBA8888);
        sp.setColor(0, 0, 0, 0); sp.fill();
        sp.setColor(0.8f, 0.9f, 1f, 1f);
        sp.drawPixel(2,0); sp.drawPixel(3,0); sp.drawPixel(4,0); sp.drawPixel(5,0);
        sp.drawPixel(2,3); sp.drawPixel(3,3); sp.drawPixel(4,3); sp.drawPixel(5,3);
        sp.drawPixel(0,1); sp.drawPixel(0,2);
        sp.drawPixel(7,1); sp.drawPixel(7,2);
        splashTex = new Texture(sp);
        sp.dispose();

        // 1×1 белый пиксель для вспышки молнии
        Pixmap fp = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        fp.setColor(1f, 1f, 1f, 1f); fp.fill();
        flashTex = new Texture(fp);
        fp.dispose();
    }

    void defineUI() throws Exception {
        userInterface.build();
    }

    void defineMap() {
        Random rand = new Random();
        Perlin perlin = new Perlin(rand.nextInt(9000));
        int[][] perlinMap = new int[store.mapHeight][store.mapWidth];

        for(int x = 0; x < store.mapHeight; x++) {
            for(int y = 0; y < store.mapWidth; y++) {
                float value = perlin.getNoise(x/15f,y/15f,2,0.6f);
                perlinMap[x][y] = (int)(value * 255) & 255;
            }
        }

        store.objectedMap = new MapObject[store.mapHeight][store.mapWidth];
        int rn, ts;

        for(int i = 0; i<store.mapHeight; i++) {
            for(int j = 0; j<store.mapWidth; j++) {
                rn = perlinMap[i][j];
                ts = 8;
                if (rn > 30) {
                    ts = 1;
                }
                if (rn > 140) {
                    ts = 3;
                }
                if (rn > 253) {
                    ts = 2;
                }

//                if (rn > 254){ts=4;}
                if (rn == 249) {
                    ts = rand.nextInt(3) + 5;
                }

                MapObject tmp = new MapObject();

                tmp.setSurfaceTexture(textures[1].texture);
                tmp.setSurfaceId(1);

                tmp.setObjectHeight(textures[ts].high);
                tmp.isTree = textures[ts].isTree;
                tmp.setTexture(textures[ts].texture);
                tmp.setTextureId(ts);

                tmp.xPositionOnMap = i+1;
                tmp.yPositionOnMap = j+1;
                tmp.generateAndSetUUID();
                store.objectedMap[i][j] = tmp;

//                if (ArrayUtils.checkIntInArray(ts, lightObjectIds)){
//                    light.addPoint(i, j);
//                }
            }
        }

//        light.recalcOnMap();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        storeKey(button);
        lastTouchedButton = button == -1 ? lastTouchedButton : button;

        mouseMoved(screenX,screenY);
        int arrPointX = selectedTileX-1;
        int arrPointY = selectedTileY-1;


        if(!isDragged){
            isUiTouched = userInterface.checkTouch(false, false, button);
        }
        if (isUiTouched){
            return false;
        }

        if (lastTouchedButton == 2 && isDragged){
            int scaleX = 0, scaleY = 0;
            float cf=0;

            int scaleCoefficientX = (Math.abs(screenX - lastDraggedX));
            int scaleCoefficientY = (Math.abs(screenY - lastDraggedY));

            if (screenX>lastDraggedX) {scaleX = 0 - scaleCoefficientX;}
            if (screenX<lastDraggedX) {scaleX = scaleCoefficientX;}
            if (screenY>lastDraggedY) {scaleY = scaleCoefficientY;}
            if (screenY<lastDraggedY) {scaleY = 0 - scaleCoefficientY;}

            cf = store.scaleTotal / 1000*(float)0.55;

            store.shiftX = store.shiftX - (int)(scaleX+(scaleX*cf));
            store.shiftY = store.shiftY - (int)(scaleY+(scaleY*cf));
        }

        if (
            lastTouchedButton == 0 &&
            arrPointX >= 0 &&
            arrPointX < store.mapHeight &&
            arrPointY >= 0 &&
            arrPointY < store.mapWidth &&
            store.selectedTailId > 0
        ) {
//                Очистка света
            int previousTextureId = store.objectedMap[arrPointX][arrPointY].getTextureId();
            int newTextureId = store.selectedTailId;

            if (!ArrayUtils.checkIntInArray(newTextureId, lightObjectIds) && ArrayUtils.checkIntInArray(previousTextureId, lightObjectIds)) {
                light.removePoint(arrPointX, arrPointY);
                light.recalcOnMapFromPoint(arrPointX, arrPointY);
            }

//                Обновление элемента карты
            store.objectedMap[arrPointX][arrPointY].setTexture(textures[newTextureId].texture);
            store.objectedMap[arrPointX][arrPointY].setTextureId(newTextureId);

            if (ArrayUtils.checkIntInArray(newTextureId, surfacesIds)){
                store.objectedMap[arrPointX][arrPointY].setSurfaceTexture(textures[newTextureId].texture);
                store.objectedMap[arrPointX][arrPointY].setSurfaceId(newTextureId);
            }

            store.objectedMap[arrPointX][arrPointY].setObjectHeight(textures[newTextureId].high);
            store.objectedMap[arrPointX][arrPointY].isTree = textures[newTextureId].isTree;
            store.objectedMap[arrPointX][arrPointY].setWidth(textures[newTextureId].texture.getWidth() / store.tileDownScale);
            store.objectedMap[arrPointX][arrPointY].setHeight(textures[newTextureId].texture.getHeight() / store.tileDownScale);

//                Уствновка света
            if (ArrayUtils.checkIntInArray(newTextureId, lightObjectIds)) {
                light.addPoint(arrPointX, arrPointY);
            }

            light.recalcOnMapFromPoint(arrPointX, arrPointY);
        }

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        releaseKey(button);
        isUiTouched = userInterface.checkTouch(isDragged, true, button);
        return super.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        store.mouseX = mouseX = screenX;
        store.mouseY = mouseY = (int) store.uiHeightOriginal - screenY;

        if (!userInterface.getMouseMoveBlockStatus()){
            calcPositionCursor();
        }

        userInterface.onMouseMoved();

        return false;
    }

    public void calcPositionCursor(){
        Vector3 v = Main.viewport.getCamera().unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        float mouseInViewportX = v.x - store.shiftX - (float)(10 / store.tileDownScale) ;
        float mouseInViewportY = v.y - store.shiftY + (float)(60 / store.tileDownScale);
        float[] dotPoint = transform.isometricToCartesian(mouseInViewportX, mouseInViewportY);
        store.playerPositionX = v.x ;
        store.playerPositionY = v.y;

        light.setUserPoint(v.x, v.y);
        selectedTileX = (int) ((dotPoint[0]) / tileSizeX) - 1;
        selectedTileY = (int) ((dotPoint[1]) / tileSizeY);

        store.selectedTileX = (dotPoint[0] / tileSizeX) - 1;
        store.selectedTileY = dotPoint[1] / tileSizeY;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        isImmediatelyReleaseKey = true;
        if (amountY > 0) {
            keyDown(156);
        } else {
            keyDown(157);
        }
        store.isDragged = false;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        isUiTouched = userInterface.checkTouch(true, false, lastTouchedButton);

        return super.touchDragged(screenX, screenY, pointer);
    }

    @Override
    public boolean keyDown(int keyCode){
        // В симуляции — только обновляем флаги для SimulationInputThread
        if (store.isSimulationMode) {
            if (keyCode == 19 || keyCode == 51) store.simKeyUp    = true; // UP / W
            if (keyCode == 20 || keyCode == 47) store.simKeyDown  = true; // DOWN / S
            if (keyCode == 21 || keyCode == 29) store.simKeyLeft  = true; // LEFT / A
            if (keyCode == 22 || keyCode == 32) store.simKeyRight = true; // RIGHT / D
            return true;
        }

        if(userInterface.checkKey(keyCode)){
            return false;
        }

        super.keyDown(keyCode);

        boolean isNeedDownScale = false;
        boolean isNeedUpScale = false;

        if (keyCode == 157 && !store.isNeedToChangeScale) {
            isNeedUpScale = CameraSettings.upScale();
            calcPositionCursor();
        }

        if (keyCode == 156 && !store.isNeedToChangeScale) {
            isNeedDownScale = CameraSettings.downScale();
            calcPositionCursor();
        }

        if (keyCode == 19 || isNeedUpScale) {
            int scale = isNeedUpScale? tileSizeY/2 : tileSizeY;
            store.shiftY = store.shiftY - scale;
            calcPositionCursor();
        }

        if (keyCode == 20 || isNeedDownScale ) {
            int scale = isNeedDownScale? tileSizeY/2 : tileSizeY;
            store.shiftY = store.shiftY + scale;
            calcPositionCursor();
        }

        if (keyCode == 22 || isNeedUpScale) {
            int scale = isNeedUpScale ? tileSizeX : (tileSizeX*2);
            store.shiftX = store.shiftX - scale;
            calcPositionCursor();
        }

        if (keyCode == 21 || isNeedDownScale) {
            int scale = isNeedDownScale? tileSizeX : (tileSizeX*2);
            store.shiftX = store.shiftX + scale;
            calcPositionCursor();
        }

        if (isImmediatelyReleaseKey){
            isImmediatelyReleaseKey = false;
            releaseKey(keyCode);
        }

        return false;
    }

    @Override
    public boolean keyUp(int keyCode) {
        if (store.isSimulationMode) {
            if (keyCode == 19 || keyCode == 51) store.simKeyUp    = false;
            if (keyCode == 20 || keyCode == 47) store.simKeyDown  = false;
            if (keyCode == 21 || keyCode == 29) store.simKeyLeft  = false;
            if (keyCode == 22 || keyCode == 32) store.simKeyRight = false;
            return true;
        }
        return super.keyUp(keyCode);
    }

    @Override
    public boolean keyTyped(char character){
        if (userInterface.keyTyped(character)){
            return true;
        }

        return false;
    }

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);

        if (store.isNeedToChangeScale) {
            calcPositionCursor();
            return;
        }

        // Карта строится чанками — пропускаем рендер тайлов до готовности
        if (store.isMapLoading || store.objectedMap == null) {
            return;
        }

        float[] cursorPoint = transform.cartesianToIsometric(-1,-1);
        int mapI, mapJ;
        float[] point;

//        Задаём задний фон
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glClearColor(store.dayCoefficient, store.dayCoefficient, store.dayCoefficient, 1);


//        Симуляция: PhysicThread двигает player; здесь только центрируем камеру
        if (store.isSimulationMode && store.player != null && store.player.isInitialized()) {
            float[] isoPos = Transform.cartesianToIsometric(
                (int) store.player.worldX, (int) store.player.worldY);
            store.shiftX = (int)(store.display.get("width")  / 2 - isoPos[0]);
            store.shiftY = (int)(store.display.get("height") / 2 - isoPos[1]);
        }

//        Геймпад: опрос левого стика на GL-потоке, результат в Store для SimulationInputThread
        if (store.isSimulationMode) {
            store.simStickX = 0f;
            store.simStickY = 0f;
            if (!Controllers.getControllers().isEmpty()) {
                Controller ctrl = Controllers.getControllers().first();
                float ax = ctrl.getAxis(ctrl.getMapping().axisLeftX);
                float ay = ctrl.getAxis(ctrl.getMapping().axisLeftY);
                float dead = 0.12f; // мёртвая зона
                store.simStickX = Math.abs(ax) > dead ? ax : 0f;
                store.simStickY = Math.abs(ay) > dead ? ay : 0f;
            }
        }

//        Смена времени суток (только в dev-режиме — в симуляции управляют треды)
        if (!store.isSimulationMode) {
            if (!store.isDay) {
                store.dayCoefficient = store.dayCoefficient - cm;
                if (store.dayCoefficient < -0.10) store.dayCoefficient = (float)-0.10;
            }
            if (store.isDay) {
                store.dayCoefficient = store.dayCoefficient + cm;
                if (store.dayCoefficient > 1) store.dayCoefficient = 1;
            }
        }

//        Расчёт области карты для отрисовки
        int d = (int) (
                (store.display.get("width") / tileSizeX) + (store.display.get("height") / tileSizeY)
                        + (Math.abs(store.shiftX) / (tileSizeX*2))
                        +(Math.abs(store.shiftY) / (tileSizeY)));

        float fc = Math.min(Math.max(store.scaleTotal / 10*(float)5, 20), 150);

        int e1 = (-(store.shiftX / (tileSizeX*2)) - (store.shiftY / (tileSizeY)) - 10);
        int e2 = ((store.shiftX / (tileSizeX*2)) - (store.shiftY / (tileSizeY)))-(int)fc;

//        Отрисовка карты
//        1 Поверхности
        for (int i=Math.min(d, store.mapHeight); i > Math.max(e1,0); i--) {
            mapI = i - 1;

            for (int j = Math.min(e2 + (int) (fc * 2), store.mapWidth); j > Math.max(e2, 0); j--) {
                mapJ = j - 1;

//                Ограничение отрисовки на основе отрисовывемого элемента
                point = transform.cartesianToIsometric(i * tileSizeX, j * tileSizeY);

                if (point[0] + store.shiftX - (tileSizeX * 2) > store.display.get("width")) {
                    break;
                }
                if (point[1] + store.shiftY + (tileSizeX * 2) < 0) {
                    break;
                }

                if (point[0] + store.shiftX + (tileSizeX * 2) < 0) {
                    continue;
                }
                if (point[1] + store.shiftY - (tileSizeY * 2) > store.display.get("height")) {
                    continue;
                }

                store.objectedMap[mapI][mapJ].drawSurface(batch);
                store.objectedMap[mapI][mapJ].isPlayerInside = false;
                store.objectedMap[mapI][mapJ].isRenderLighAndNigth = true;
            }
        }

//        Объекты на поверхностях
        for (int i=Math.min(d, store.mapHeight); i > Math.max(e1,0); i--)
        {
            mapI = i - 1;

            for (int j=Math.min(e2+(int)(fc*2), store.mapWidth); j > Math.max(e2,0); j--){
                mapJ = j - 1;

//                Ограничение отрисовки на основе отрисовывемого элемента
                point = transform.cartesianToIsometric(i * tileSizeX, j * tileSizeY);

                if (point[0] + store.shiftX - (tileSizeX*2)>store.display.get("width")){
                    break;
                }
                if (point[1] + store.shiftY + (tileSizeX*2)< 0){
                    break;
                }

                if (point[0] + store.shiftX + (tileSizeX*2)< 0){
                    continue;
                }
                if (point[1] + store.shiftY - (tileSizeY*2)>store.display.get("height")){
                    continue;
                }

//                рисуем целевой элемент для установки
                if (i == selectedTileX && j == selectedTileY && store.selectedTailId > 0){

                    batch.setColor(0.75f,0.62f,0.12f,1);
                    cursorPoint = transform.cartesianToIsometric((selectedTileX)*tileSizeX,(selectedTileY)*tileSizeY);

                    int sx, sy;
                    float sw, sh;
                    Texture t = textures[store.selectedTailId].texture;

                    sx = (int) (cursorPoint[0] + store.shiftX);
                    sy = (int) (cursorPoint[1] + store.shiftY);
                    sw = (float) t.getWidth() / store.tileDownScale;
                    sh = (float) t.getHeight() / store.tileDownScale;

//                    Рисуем рамку и выбранный элемент внутри
                    batch.draw(hintUp,sx + 2, sy + 4, sw, sh);
                    batch.draw(textures[store.selectedTailId].texture, sx, sy, sw, sh);
                    batch.draw(hintDown,sx + 3, sy + 3, sw, sh);
                } else {
//                    Делаем подсветку для элемента под курсором
                    if (i == selectedTileX && j == selectedTileY){
                        batch.setColor(0.56f,0.57f,0.75f,1);
                        store.objectedMap[mapI][mapJ].isRenderLighAndNigth = false;
                    }

//                Рисуем карту
                    store.objectedMap[mapI][mapJ].draw(batch);
                    store.objectedMap[mapI][mapJ].isPlayerInside = false;
                    store.objectedMap[mapI][mapJ].isRenderLighAndNigth = true;
                }

//                Рисуем существ и здания на карте
                renderCreations(batch, mapI, mapJ, false);
                renderBuildings(batch, mapI, mapJ, false);

//                Рисуем игрока в его тайловой позиции (как creations)
                if (store.isSimulationMode && store.player != null && store.player.isInitialized()) {
                    int pi = (int)(store.player.worldX / tileSizeX);
                    int pj = (int)(store.player.worldY / tileSizeY);
                    if (i == pi && j == pj) {
                        store.player.draw(batch);
                    }
                }

//                на смежных, если они на уровне земли
                renderCreations(batch, mapI, mapJ+1, true);
                renderCreations(batch, mapI+1, mapJ, true);
                renderBuildings(batch, mapI, mapJ+1, true);
                renderBuildings(batch, mapI+1, mapJ, true);
            }
        }

        // ── Дождь и молния ────────────────────────────────────────────────────
        if (store.isSimulationMode) {
            renderRain(batch);
            renderSplash(batch, store.rainIntensity);
            renderLightningFlash(batch);
        }
    }

    /**
     * Помещает каплю в случайную точку приземления на видимой области.
     * scatter=true: начальная высота случайна (для первичной инициализации).
     * scatter=false: капля стартует сверху (после приземления или сброса).
     */
    private void resetDrop(int k, float W, float H) {
        float sx  = (float)(Math.random() * (W + 400)) - 200;
        float sy  = (float)(Math.random() * H * 1.2f);
        float isoX = sx - store.shiftX;
        float isoY = sy - store.shiftY;
        dropWX[k]  = (isoX + 2f * isoY) / 2f;
        dropWY[k]  = (2f * isoY - isoX) / 2f;
        // Всегда равномерное распределение высот — убирает эффект "волн/струй"
        dropAlt[k] = (float)(Math.random() * H * 1.4f);
    }

    /**
     * Дождь в мировом пространстве с компенсацией движения камеры.
     *
     * Каждая капля хранит (dropWX, dropWY) — декартовую точку приземления —
     * и dropAlt — высоту в экранных пикселях. Экранная позиция:
     *   scrX = dropWX - dropWY + shiftX
     *   scrY = (dropWX + dropWY)/2 + dropAlt + shiftY
     *
     * Компенсация камеры: при изменении shiftX/Y на (dSX, dSY) корректируем
     * мировые координаты так, чтобы экранная позиция не менялась:
     *   compX = -dSX/2 - dSY,  compY = dSX/2 - dSY
     * Это гарантирует стабильный угол/скорость дождя при движении игрока.
     *
     * Приземление = altZ <= 0 (капля достигла поверхности тайла).
     */
    private void renderRain(SpriteBatch batch) {
        float intensity = store.rainIntensity;
        float W = store.display.get("width");
        float H = store.display.get("height");

        if (!dropsReady || Float.isNaN(lastShiftX)) {
            for (int k = 0; k < N_DROPS; k++) {
                dropSpd[k] = 840f + (float)(Math.random() * 660f); // 840..1500 px/s
                resetDrop(k, W, H);
            }
            dropsReady  = true;
            lastShiftX  = store.shiftX;
            lastShiftY  = store.shiftY;
        }

        // Компенсация движения камеры — вычисляем до early-return
        float camDX = store.shiftX - lastShiftX;
        float camDY = store.shiftY - lastShiftY;
        lastShiftX  = store.shiftX;
        lastShiftY  = store.shiftY;

        // Компенсацию камеры обновляем всегда, даже при нулевой интенсивности
        if (intensity <= 0f) return;

        float dt        = Gdx.graphics.getDeltaTime();
        float compX     = -camDX / 2f - camDY;
        float compY     =  camDX / 2f - camDY;
        float isoXDrift  = (store.windMultiplier - 1f) * 55f * dt;
        float horizSpeed = (store.windMultiplier - 1f) * 55f; // px/s горизонтальный снос
        float lit   = Math.max(0.15f, store.dayCoefficient);
        float alpha = intensity;

        for (int k = 0; k < N_DROPS; k++) {
            dropWX[k]  += compX + isoXDrift / 2f;
            dropWY[k]  += compY - isoXDrift / 2f;
            dropAlt[k] -= dropSpd[k] * dt;

            float scrX = dropWX[k] - dropWY[k] + store.shiftX;
            float scrY = (dropWX[k] + dropWY[k]) / 2f + dropAlt[k] + store.shiftY;

            if (dropAlt[k] <= 0f || scrX < -60f || scrX > W + 60f || scrY > H + 20f) {
                // Всплеск только на surface-тайлах (objectHeight == 0)
                float gsx = dropWX[k] - dropWY[k] + store.shiftX;
                float gsy = (dropWX[k] + dropWY[k]) / 2f + store.shiftY;
                if (gsx > 0 && gsx < W && gsy > 0 && gsy < H && store.objectedMap != null) {
                    int mi = (int)(dropWX[k] / tileSizeX) - 1;
                    int mj = (int)(dropWY[k] / tileSizeY) - 1;
                    if (mi >= 0 && mi < store.mapHeight && mj >= 0 && mj < store.mapWidth
                            && store.objectedMap[mi][mj].objectHeight < 20) { // ниже деревьев
                        splashX[splashNext]   = gsx;
                        splashY[splashNext]   = gsy;
                        splashAge[splashNext] = 1f;
                        splashNext = (splashNext + 1) % N_SPLASHES;
                    }
                }
                resetDrop(k, W, H);
                continue;
            }
            if (scrY < -14f) continue;

            // Освещение от ближайшего источника света (костёр и т.п.)
            float dropIsoX  = dropWX[k] - dropWY[k];
            float dropIsoY  = (dropWX[k] + dropWY[k]) / 2f;
            float lightBoost = 0f;
            for (int li = 1; li <= store.lightPointsHighWaterMark; li++) {
                if (store.lightPoints[li][0] == 0) continue;
                float ldx = dropIsoX - store.lightPoints[li][1];
                float ldy = dropIsoY - store.lightPoints[li][2];
                if (Math.abs(ldx) > LIGHT_RADIUS || Math.abs(ldy) > LIGHT_RADIUS) continue;
                float dist = (float)Math.sqrt(ldx*ldx + ldy*ldy);
                if (dist < LIGHT_RADIUS) {
                    lightBoost = Math.max(lightBoost, (1f - dist / LIGHT_RADIUS) * 0.9f);
                }
            }

            float dropBright = (0.35f + lit * 0.20f + lightBoost * 0.7f) * 0.765f; // -25% итого
            dropBright = Math.min(1f, dropBright);
            float warmth = lightBoost * 0.6f;
            batch.setColor(
                Math.min(1f, dropBright * 0.72f + warmth * 0.30f),
                Math.min(1f, dropBright * 0.80f),
                Math.min(1f, dropBright * 1.15f - warmth * 0.15f),
                alpha);
            // Угол из реальных скоростей: горизонтальный снос vs вертикальное падение
            float leanAngle = (float)Math.toDegrees(Math.atan2(horizSpeed, dropSpd[k]));
            batch.draw(dropTex,
                scrX, scrY,
                1f, 7f, 2, 14, 1f, 1f,
                leanAngle,
                0, 0, 2, 14, false, false);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** Рисует активные всплески от приземлившихся капель. */
    private void renderSplash(SpriteBatch batch, float intensity) {
        if (intensity <= 0f) return;
        float dt  = Gdx.graphics.getDeltaTime();
        float lit = Math.max(0.15f, store.dayCoefficient);

        for (int k = 0; k < N_SPLASHES; k++) {
            if (splashAge[k] <= 0f) continue;
            splashAge[k] -= dt / SPLASH_DURATION;
            if (splashAge[k] <= 0f) { splashAge[k] = 0f; continue; }

            float t = 1f - splashAge[k];
            float scale = 1f + t * 1.8f;              // меньше разрастается
            float a  = splashAge[k] * intensity * 0.25f; // менее заметны
            float w  = 5f * scale;
            float h  = 2.5f * scale;
            batch.setColor(0.82f * lit, 0.9f * lit, lit, a);
            batch.draw(splashTex,
                splashX[k] - w / 2f, splashY[k] - h / 2f,
                w, h);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /**
     * Рисует всполох молнии с тремя фазами:
     *   lf > 1.0  — предвспышечное затемнение (резкое потемнение перед ударом)
     *   lf 0..1.0 — главная вспышка: глубокий фиолет + яркий белый поверх
     *
     * WeatherThread устанавливает lightningFlash = 1.5f для запуска цикла.
     */
    private void renderLightningFlash(SpriteBatch batch) {
        float lf = store.lightningFlash;
        if (lf <= 0f) return;

        float dt = Gdx.graphics.getDeltaTime();
        float W  = store.display.get("width");
        float H  = store.display.get("height");

        if (lf > 1.0f) {
            // Фаза 1: предвспышечное затемнение
            float t = lf - 1.0f;
            batch.setColor(0f, 0f, 0.08f, t * 0.20f);     // ÷4 от 0.75
            batch.draw(flashTex, 0, 0, W, H);
            store.lightningFlash = lf - dt * 11f;
        } else {
            // Фаза 2: фиолетовый + белый, ослаблены в 4 раза
            batch.setColor(0.45f, 0.05f, 0.85f, lf * 0.15f); // ÷4 от 0.60
            batch.draw(flashTex, 0, 0, W, H);
            batch.setColor(1f, 0.97f, 1f,    lf * 0.10f);    // ÷4 от 0.42
            batch.draw(flashTex, 0, 0, W, H);
            store.lightningFlash = Math.max(0f, lf - dt * 4.5f);
        }
        batch.setColor(1, 1, 1, 1);
    }

    @Override
    public void renderUI(SpriteBatch uiBatch) {
        userInterface.render(uiBatch);
    }

    public void renderCreations(SpriteBatch batch, int mapI, int mapJ, boolean filterByHeight) {
        for (Creation creation: store.creations) {
            if (creation != null){
                if (creation.mapCellX != (mapI+1) || creation.mapCellY != (mapJ+1) ){
                    continue;
                }
                if (filterByHeight) {
                    MapObject el = store.objectedMap[mapI][mapJ];
                    if (el.getHeight() != 0) { continue; }
                }
                creation.draw(batch);
            }
        }
    }

    public void renderBuildings(SpriteBatch batch, int mapI, int mapJ, boolean filterByHeight) {
        for (int i = 0; i <= store.buildingCount; i++) {
            Creation b = store.buildings[i];
            if (b != null) {
                if (b.mapCellX != (mapI+1) || b.mapCellY != (mapJ+1)) { continue; }
                if (filterByHeight) {
                    MapObject el = store.objectedMap[mapI][mapJ];
                    if (el.getHeight() != 0) { continue; }
                }
                b.draw(batch);
            }
        }
    }
}