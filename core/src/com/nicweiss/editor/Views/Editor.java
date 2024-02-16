package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;
import com.nicweiss.editor.utils.FileManager;
import com.nicweiss.editor.utils.Perlin;

import java.util.Random;


public class Editor extends View{
    Texture dot, tile, tilePickerBG, tilePickerSelector, openTexture, saveTexture,
            dark, itemBgMenuTexture;
    Texture[] textures;

    FileManager fileManager;

    BaseObject[] picker, ui;
    BaseObject[][] objectedMap;

    int[][]  map;
    int tileSizeX, tileSizeY;
    int tileDownScale = 3;
    int selectedTileX, selectedTileY;
    int mouseX, mouseY;
    int mapHeight = 200, mapWidth = 200;
    int shiftX, shiftY;
    int widthUIPanel = 770, heightUIPanel = 90;
    int menuTileSpace = 77;
    int menuTileWidth = 70, menuTileHeight = 80;
    int selectedTailId = 1;
    int menuItemSize = 40, menuItemSpace = 50;
    float cm = (float)0.001;

    boolean isMenuReadyToTouch = false;

    public Editor(){
        fileManager = new FileManager();
        tile = new Texture("tile_selector.png");
        dot = new Texture("dot.png");
//        dark = new Texture("dark.png");
        openTexture = new Texture("open.png");
        saveTexture = new Texture("save.png");
        itemBgMenuTexture = new Texture("menu_item_bg.png");
        tilePickerBG = new Texture("tile_pick_bg.png");
        tilePickerSelector = new Texture("tile_pick_selector.png");

        textures = new Texture[] {
                new Texture("gp_0.png"),
                new Texture("gp_1.png"),
                new Texture("gp_2.png"),
                new Texture("gp_3.png"),
                new Texture("gp_4.png"),
                new Texture("gp_5.png"),
                new Texture("gp_6.png"),
                new Texture("gp_7.png"),
                new Texture("gp_8.png"),
                new Texture("gp_9.png"),
                new Texture("gp_10.png"),
                new Texture("gp_11.png")

        };

        defineMap();
        defineTileUI();

        tileSizeX = 158 / tileDownScale;
        tileSizeY = 158 / tileDownScale;
        shiftY = 0;
        shiftX = 12 * tileSizeX;
    }

    private void defineMap() {
        Random rand = new Random();
        Perlin perlin = new Perlin(rand.nextInt(9000));
        int[][] perlinMap = new int[mapHeight][mapWidth];

        for(int x = 0; x < mapHeight; x++) {
            for(int y = 0; y < mapWidth; y++) {
                float value = perlin.getNoise(x/10f,y/10f,2,0.6f);
                perlinMap[x][y] = (int)(value * 255) & 255;
            }
        }

        map = new int[mapHeight][mapWidth];
        objectedMap = new BaseObject[mapHeight][mapWidth];
        int rn = 0, ts = 0;

        for(int i = 0; i<mapHeight; i++) {
            for(int j = 0; j<mapWidth; j++) {
                rn = perlinMap[i][j];
                ts = 8;
                if (rn > 20){ts=1;}
//                if (rn > 50){ts=10;}
                if (rn > 140){ts=3;}
                if (rn > 244){ts=2;}

                if (rn == 246){ts=4;}
//                if (rn == 247){ts=5;}
//                if (rn == 248 ){ts=6;}
                if (rn == 249){
                    ts=rand.nextInt(3) + 5;
                }
                map[i][j] = ts;

                BaseObject tmp = new BaseObject();
                tmp.setTexture(textures[ts]);
                tmp.isRenderLighAndNigth = true;
//                if ( (x & 1) == 0 ) { even... } else { odd... }
                objectedMap[i][j] = tmp;
            }
        }
    }

    private void defineTileUI(){int widthUIPanel = 770;
        int renderUIFrom = (int) store.uiWidthOriginal /2 - widthUIPanel / 2;
        picker = new BaseObject[12];
        ui = new BaseObject[2];

        for (int i = 0; i<=11;  i++) {
            BaseObject tmp = new BaseObject();
            tmp.setTexture(textures[i]);
            tmp.setX(renderUIFrom + (i * menuTileSpace) + 3);
            tmp.setY(5);
            tmp.setWidth(menuTileWidth);
            tmp.setHeight(menuTileHeight);
            tmp.setObjectId(String.valueOf(i));
            picker[i] = tmp;
        }

//        Open button
        BaseObject tmp = new BaseObject();
        tmp.setTexture(openTexture);
        tmp.setX(10);
        tmp.setY(store.uiHeightOriginal - menuItemSize - 10);
        tmp.setWidth(menuItemSize);
        tmp.setHeight(menuItemSize);
        tmp.setObjectId("open");
        tmp.setBackgroundTexture(itemBgMenuTexture);
        ui[0] = tmp;

//        Save button
        tmp = new BaseObject();
        tmp.setTexture(saveTexture);
        tmp.setX(menuItemSpace + 10);
        tmp.setY(store.uiHeightOriginal - menuItemSize - 10);
        tmp.setWidth(menuItemSize);
        tmp.setHeight(menuItemSize);
        tmp.setObjectId("save");
        tmp.setBackgroundTexture(itemBgMenuTexture);
        ui[1] = tmp;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
//        Gdx.app.log("Debug", String.valueOf(selectedTileX) + " : " + String.valueOf(selectedTileY));
        mouseMoved(screenX,screenY);
        int arrPointX = selectedTileX-1;
        int arrPointY = selectedTileY-1;

        if(isMenuReadyToTouch && !isDragged){
//            Обработка собыкий в объектах пикера тайлов
            for (BaseObject object : picker) {
                object.checkTouch(mouseX, mouseY);
                if (object.isTouched) {
                    selectedTailId = Integer.parseInt(object.getObjectId());
                }
            }

//            Обработка событй в объектах интерфейса
            for (BaseObject baseObject : ui) {
                baseObject.checkTouch(mouseX, mouseY);
                if (baseObject.isTouched) {
                    if (baseObject.getObjectId().equals("open")) {
                        map = fileManager.openMap();
                        mapHeight = fileManager.mapHeight;
                        mapWidth = fileManager.mapWidth;
                    }
                    if (baseObject.getObjectId().equals("save")) {
                        fileManager.saveMap(map, mapWidth, mapHeight);
                    }
                }
            }
        } else {
            if (
                arrPointX >= 0 &&
                arrPointX < mapHeight &&
                arrPointY >= 0 &&
                arrPointY < mapWidth
            ) {
                map[arrPointX][arrPointY] = selectedTailId;
                objectedMap[arrPointX][arrPointY].setTexture(textures[selectedTailId]);

                if (selectedTailId == 11) {
                    float[] point = cartesianToIsometric(arrPointX*tileSizeX,arrPointY*tileSizeY);
                    store.addLightPoint(
                            point[0] + (float)(tile.getWidth()*2.15),
                            point[1] + (float)(tileSizeY*1.25)
                    );
                    recalcLightOnMap();
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        mouseX = screenX;
        mouseY = (int) store.uiHeightOriginal - screenY;

        Vector3 v = Main.viewport.getCamera().unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        float mouseInViewportX = v.x - shiftX - (10 / tileDownScale) ;
        float mouseInViewportY = v.y - shiftY + (60 / tileDownScale);
        float[] dotPoint = isometricToCartesian(mouseInViewportX, mouseInViewportY);
        store.playerPositionX = v.x ;
        store.playerPositionY = v.y;

        store.lightPoints[0][0] = 1;
        store.lightPoints[0][1] = v.x;
        store.lightPoints[0][2] = v.y;
        selectedTileX = (int) ((dotPoint[0]) / tileSizeX) - 1;
        selectedTileY = (int) ((dotPoint[1]) / tileSizeY);

        return false;
    }

    @Override
    public boolean keyDown(int keyCode){
        Gdx.app.log("Debug", String.valueOf(keyCode));
        super.keyDown(keyCode);
        if (keyCode == 19) {
            shiftY = shiftY - tileSizeY;
            store.lightShiftY = store.lightShiftY - tileSizeY;
        }

        if (keyCode == 20) {
            shiftY = shiftY + tileSizeY;
            store.lightShiftY = store.lightShiftY + tileSizeY;
        }

        if (keyCode == 21) {
            shiftX = shiftX + (tileSizeX*2);
            store.lightShiftX = store.lightShiftX + (tileSizeX*2);
        }

        if (keyCode == 22) {
            shiftX = shiftX - (tileSizeX*2);
            store.lightShiftX = store.lightShiftX - (tileSizeX*2);
        }

        if (keyCode == 157) {
            store.cameraUpScale();
        }

        if (keyCode == 156) {
            store.cameraDownScale();
        }

        return false;
    }

    public float[] cartesianToIsometric(int x, int y){
        float isometricX = x - y ;
        float isometricY = (float) ((x + y) / 2);

        return new float[] {isometricX, isometricY};
    }

    public float[] isometricToCartesian(float x, float y){
        float decartX=(2*y+x)/2;
        float decartY=(2*y-x)/2;
        return new float[] {decartX, decartY} ;
    }

    @Override
    public void render(SpriteBatch batch) {
//        recalcLightOnMap();
        super.render(batch);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glClearColor(1, 1, 1, 1);

        float[] cursorPoint = cartesianToIsometric(-1,-1);

//        Смена времени суток
        store.dayCoefficient = store.dayCoefficient - cm;
        if (store.dayCoefficient < -0.10) {
            store.dayCoefficient = (float)-0.10;
            cm = cm * -1;
        }
        if (store.dayCoefficient > 1) {
            store.dayCoefficient = 1;
            cm = cm * -1;
        }

//        Отрисовка карты

        int mapI, mapJ;
        for (int i=map.length; i > 0; i--)
        {
            mapI = i - 1;
            int[] subMap = map[mapI];
            for (int j=subMap.length; j > 0; j--){
                mapJ = j - 1;

                float[] point = cartesianToIsometric(i*tileSizeX,j*tileSizeY);

//                рисуем целевой элемент для установки
                if (i == selectedTileX && j == selectedTileY){
                    cursorPoint = cartesianToIsometric((selectedTileX)*tileSizeX,(selectedTileY)*tileSizeY);
                    batch.draw(textures[selectedTailId], cursorPoint[0] + shiftX, cursorPoint[1] + shiftY, tile.getWidth() / tileDownScale, tile.getHeight() / tileDownScale);
//                    batch.draw(textures[0], cursorPoint[0] + shiftX, cursorPoint[1] + shiftY, tile.getWidth() / tileDownScale, tile.getHeight() / tileDownScale);
                } else {
//                    batch.draw(textures[tileId], point[0] + shiftX, point[1] + shiftY, tile.getWidth() / tileDownScale, tile.getHeight() / tileDownScale );
//                Рисуем карту
                    objectedMap[mapI][mapJ].setX(point[0] + shiftX);
                    objectedMap[mapI][mapJ].setY(point[1] + shiftY);
                    objectedMap[mapI][mapJ].setWidth(tile.getWidth() / tileDownScale);
                    objectedMap[mapI][mapJ].setHeight(tile.getHeight() / tileDownScale);
                    objectedMap[mapI][mapJ].draw(batch);
                    objectedMap[mapI][mapJ].isPlayerInside = false;
                }

//                Рисуем курсор
                if (i == selectedTileX && j == selectedTileY){
                    cursorPoint = cartesianToIsometric((selectedTileX)*tileSizeX,(selectedTileY)*tileSizeY);
                    objectedMap[mapI][mapJ].isPlayerInside = true;
                }
            }
        }

//        Отрисовка курсора
        if (cursorPoint[0] != -1 && cursorPoint[1] != -1) {
            batch.draw(dot, cursorPoint[0] + shiftX + tileSizeX, cursorPoint[1] + shiftY + tileSizeY - (80 / tileDownScale));
        }
    }

    @Override
    public void renderUI(SpriteBatch uiBatch) {
        int renderUIFrom = (int) store.uiWidthOriginal /2 - widthUIPanel / 2;

        uiBatch.draw(tilePickerBG, renderUIFrom, 0, widthUIPanel, heightUIPanel);
        isMenuReadyToTouch = false;
        for (int i = 0; i<picker.length;  i++) {
            picker[i].setX(renderUIFrom + (i * menuTileSpace) + 3);
            picker[i].checkTouch(mouseX, mouseY);
            if (picker[i].isTouched){
                uiBatch.draw(tilePickerSelector, renderUIFrom + (menuTileSpace * i) + 3, 0, menuTileWidth, 15);
                isMenuReadyToTouch = true;
            }
            picker[i].draw(uiBatch);
        }

        for (BaseObject baseObject : ui) {
            baseObject.setY(store.uiHeightOriginal - menuItemSize - 10);
            baseObject.checkTouch(mouseX, mouseY);
            if (baseObject.isTouched) {
                isMenuReadyToTouch = true;
            }
            baseObject.draw(uiBatch);
        }
    }

    public void recalcLightOnMap(){
        for(int i = 0; i<mapHeight; i++) {
            for(int j = 0; j<mapWidth; j++) {
                objectedMap[i][j].calcLight("global");
            }
        }
    }
}