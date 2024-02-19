package com.nicweiss.editor.components;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.ArrayUtils;
import com.nicweiss.editor.utils.FileManager;
import com.nicweiss.editor.utils.Light;
import com.nicweiss.editor.utils.Transform;

import java.util.stream.IntStream;

public class UserInterface {
    FileManager fileManager;
    Transform transform;

    Texture tilePickerBG, tilePickerSelector, openTexture, saveTexture, itemBgMenuTexture;

    public static Store store;
    BaseObject[] picker, ui;
    Texture[] tileTextures;
    Light lightClass;
    int[] lightObjectIds;

    int widthUIPanel = 770, heightUIPanel = 90;
    int menuTileSpace = 77;
    int menuTileWidth = 70, menuTileHeight = 80;
    int menuItemSize = 40, menuItemSpace = 50;

    public UserInterface(Texture[] tileTextures, Light lightClass, int[] lightObjectIds) {
        this.lightObjectIds = lightObjectIds;
        this.tileTextures = tileTextures;
        this.lightClass = lightClass;
        fileManager = new FileManager();

        openTexture = new Texture("open.png");
        saveTexture = new Texture("save.png");
        itemBgMenuTexture = new Texture("menu_item_bg.png");
        tilePickerBG = new Texture("tile_pick_bg.png");
        tilePickerSelector = new Texture("tile_pick_selector.png");
    }

    public void build(Texture[] textures){
        int widthUIPanel = 770;
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

    public void render(SpriteBatch uiBatch) {
        int renderUIFrom = (int) store.uiWidthOriginal / 2 - widthUIPanel / 2;

        uiBatch.draw(tilePickerBG, renderUIFrom, 0, widthUIPanel, heightUIPanel);
        for (int i = 0; i < picker.length; i++) {
            picker[i].setX(renderUIFrom + (i * menuTileSpace) + 3);
            picker[i].checkTouch(store.mouseX, store.mouseY);
            if (picker[i].isTouched) {
                uiBatch.draw(tilePickerSelector, renderUIFrom + (menuTileSpace * i) + 3, 0, menuTileWidth, 15);
            }
            picker[i].draw(uiBatch);
        }

        for (BaseObject baseObject : ui) {
            baseObject.setY(store.uiHeightOriginal - menuItemSize - 10);
            baseObject.checkTouch(store.mouseX, store.mouseY);
            baseObject.draw(uiBatch);
        }
    }

    public boolean checkTouch(){
        if (store.isDragged){
            return false;
        }

//        Обработка собыкий в объектах пикера тайлов
        for (BaseObject object : picker) {
            object.checkTouch(store.mouseX, store.mouseY);
            if (object.isTouched) {
                store.selectedTailId = Integer.parseInt(object.getObjectId());
                if (ArrayUtils.checkIntInArray(store.selectedTailId, lightObjectIds)){
                    store.isSelectedLightObject = true;
                } else {
                    store.isSelectedLightObject = false;
                }
                return true;
            }
        }

//            Обработка событй в объектах интерфейса
        for (BaseObject baseObject : ui) {
            baseObject.checkTouch(store.mouseX, store.mouseY);
            if (baseObject.isTouched) {
                if (baseObject.getObjectId().equals("open")) {
                    int textureId = 0;
                    int[][] map =  fileManager.openMap();
                    float[] point;
                    if (map.length == 0) {
                        return true;
                    }

                    store.mapHeight = fileManager.mapHeight;
                    store.mapWidth = fileManager.mapWidth;

                    store.objectedMap = new BaseObject[store.mapHeight][store.mapWidth];

                    lightClass.clearAll();
                    for (int i = 0; i < store.mapWidth; i++){
                        for (int j = 0; j < store.mapHeight; j++){
                            point = transform.cartesianToIsometric(
                                    (int)((i+1)*store.tileSizeWidth),
                                    (int)((j+1)*store.tileSizeHeight)
                            );

                            textureId = map[i][j];
                            BaseObject tmp = new BaseObject();
                            tmp.setTexture(tileTextures[textureId]);
                            tmp.setTextureId(textureId);
                            tmp.setX(point[0] + store.shiftX);
                            tmp.setY(point[1] + store.shiftY);
                            tmp.setWidth(tileTextures[textureId].getWidth() / store.tileDownScale);
                            tmp.setHeight(tileTextures[textureId].getHeight() / store.tileDownScale);
                            tmp.isRenderLighAndNigth = true;
                            tmp.isEnableRenderLimits = true;
                            tmp.isPlayerInside = false;
                            store.objectedMap[i][j] = tmp;


                            if (ArrayUtils.checkIntInArray(textureId, lightObjectIds)){
                                lightClass.addPoint(i, j);
                            }
                        }
                    }
                    lightClass.recalcOnMap();
                    return true;
                }
                if (baseObject.getObjectId().equals("save")) {
                    int[][] map = new int[store.mapWidth][store.mapHeight];

                    for (int i = 0; i < store.mapWidth; i++) {
                        for (int j = 0; j < store.mapHeight; j++) {
                            map[i][j] = store.objectedMap[i][j].getTextureId();
                        }
                    }

                  fileManager.saveMap(map, store.mapWidth, store.mapHeight);
                    return true;
                }
            }
        }

        return false;
    }
}
