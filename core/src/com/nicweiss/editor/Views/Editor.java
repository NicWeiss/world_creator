package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;
import com.nicweiss.editor.components.UserInterface;
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
    int[] lightObjectIds;
    boolean isImmediatelyReleaseKey = false;
    boolean isUiTouched = false;


    public Editor(){
        lightObjectIds = new int[] {11};

        hintUp = new Texture("tile_hint_up.png");
        hintDown = new Texture("tile_hint_down.png");

        textures = new TextureObject[] {
                new TextureObject("gp_0.png", 0),
                new TextureObject("gp_1.png", 0),
                new TextureObject("gp_2.png", 50),
                new TextureObject("gp_3.png", 50),
                new TextureObject("gp_4.png", 20),
                new TextureObject("gp_5.png", 2),
                new TextureObject("gp_6.png", 4),
                new TextureObject("gp_7.png", 10),
                new TextureObject("gp_8.png", 1),
                new TextureObject("gp_9.png", 50),
                new TextureObject("gp_10.png", 0),
                new TextureObject("gp_11.png", 5)

        };

        store.tileSizeWidth = tileSizeX = 158 / store.tileDownScale;
        store.tileSizeHeight = tileSizeY = 158 / store.tileDownScale;
        store.shiftY = 0;
        store.shiftX = 12 * tileSizeX;

        light = new Light();
        userInterface = new UserInterface(textures, light, lightObjectIds);
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
                if (rn > 30){ts=1;}
                if (rn > 140){ts=3;}
                if (rn > 253){ts=2;}

//                if (rn > 254){ts=4;}
                if (rn == 249){
                    ts=rand.nextInt(3) + 5;
                }

                MapObject tmp = new MapObject();
                tmp.setTexture(textures[ts].texture);
                tmp.setObjectHeight(textures[ts].high);
                tmp.setTextureId(ts);
                tmp.xPositionOnMap = i+1;
                tmp.yPositionOnMap = j+1;
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
            store.objectedMap[arrPointX][arrPointY].setObjectHeight(textures[newTextureId].high);
            store.objectedMap[arrPointX][arrPointY].setTextureId(newTextureId);
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
        isUiTouched = userInterface.checkTouch(isDragged, true, button);
        return super.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        store.mouseX = mouseX = screenX;
        store.mouseY = mouseY = (int) store.uiHeightOriginal - screenY;

        if (!userInterface.mapContextMenuWindow.isShow && !userInterface.tileSelectorWindow.isShowWindow) {
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
//        Gdx.app.log("Debug", String.valueOf(amountY));
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
//        Gdx.app.log("Debug", String.valueOf(keyCode));

        if(userInterface.checkKey(keyCode)){
            return true;
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
    public void render(SpriteBatch batch) {
        super.render(batch);

        if (store.isNeedToChangeScale) {
            calcPositionCursor();
            return;
        }

        float[] cursorPoint = transform.cartesianToIsometric(-1,-1);
        int mapI, mapJ;
        float[] point;

//        Задаём задний фон
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glClearColor(store.dayCoefficient, store.dayCoefficient, store.dayCoefficient, 1);


//        Смена времени суток
        if (!store.isDay) {
            store.dayCoefficient = store.dayCoefficient - cm;

            if (store.dayCoefficient < -0.10){
                store.dayCoefficient = (float)-0.10;
            }
        }
        if (store.isDay) {
            store.dayCoefficient = store.dayCoefficient + cm;

            if (store.dayCoefficient > 1) {
                store.dayCoefficient = 1;
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
            }
        }
    }

    @Override
    public void renderUI(SpriteBatch uiBatch) {
        userInterface.render(uiBatch);
    }
}