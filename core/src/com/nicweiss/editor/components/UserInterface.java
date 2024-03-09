package com.nicweiss.editor.components;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.utils.ArrayUtils;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.FileManager;
import com.nicweiss.editor.utils.Light;
import com.nicweiss.editor.utils.Transform;

public class UserInterface {
    FileManager fileManager;
    Transform transform;
    BOHelper bo_helper;

    Texture dark, tilePickerBG, tilePickerSelector, openTexture, saveTexture, itemBgMenuTexture,
            close, white, tileBox;

    public static Store store;
    BaseObject[] picker, ui, allTiles;
    BaseObject tileSelectWindow, tileMenuCloseButton, tileMenuCloseButtonBG, tileBoxButton, boundToMouse;
    Texture[] tileTextures;
    Light lightClass;
    int[] lightObjectIds;

    int widthUIPanel = 770, heightUIPanel = 90;
    int menuTileSpace = 77;
    int menuTileWidth = 70, menuTileHeight = 80;
    int menuItemSize = 40, menuItemSpace = 50;

    int boundToMouseId;
    boolean isMenuTileBoundToMouse = false, isShowMenuTile = false;

    public UserInterface(Texture[] tileTextures, Light lightClass, int[] lightObjectIds) {
        this.lightObjectIds = lightObjectIds;
        this.tileTextures = tileTextures;
        this.lightClass = lightClass;
        fileManager = new FileManager();
        bo_helper = new BOHelper();

        openTexture = new Texture("open.png");
        saveTexture = new Texture("save.png");
        itemBgMenuTexture = new Texture("menu_item_bg.png");
        tilePickerBG = new Texture("tile_pick_bg.png");
        tilePickerSelector = new Texture("tile_pick_selector.png");
        dark = new Texture("dark.png");
        close = new Texture("close.png");
        white = new Texture("white.png");
        tileBox = new Texture("tile_box.png");
    }

    public void build(Texture[] textures){
        int widthUIPanel = 770;
        int renderUIFrom = (int) store.uiWidthOriginal /2 - widthUIPanel / 2;
        picker = new BaseObject[10];
        ui = new BaseObject[2];

        tileBoxButton = bo_helper.constructObject(
                tileBox, 0,0, 70, 70, "", 0
        );

        for (int i = 0; i<10;  i++) {
            picker[i] = bo_helper.constructObject(
                    textures[i], renderUIFrom + (i * menuTileSpace) + 3,5, menuTileWidth,
                    menuTileHeight, String.valueOf(i), i
            );
        }

        allTiles = new BaseObject[textures.length];
        for (int i = 0; i<textures.length;  i++) {
            allTiles[i] = bo_helper.constructObject(
                    textures[i], renderUIFrom + (i * menuTileSpace) + 3,5, menuTileWidth,
                    menuTileHeight, String.valueOf(i), i
            );
        }

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

        tileMenuCloseButton = bo_helper.constructObject(
                close, 0, 0, menuItemSize, menuItemSize, "", 0
        );

        tileSelectWindow = bo_helper.constructObject(
                dark, 100, 150, 1, 1, "tileSelectWindow", 0
        );

        tileMenuCloseButtonBG = bo_helper.constructObject(
                white, 100, 150, 1, 1, "tileMenuCloseButtonBG", 0
        );
    }

    public void render(SpriteBatch uiBatch) {
        int renderUIFrom = (int) store.uiWidthOriginal / 2 - widthUIPanel / 2;

        bo_helper.draw(uiBatch, tileBoxButton, renderUIFrom - 75, 0);
        uiBatch.draw(tilePickerBG, renderUIFrom, 0, widthUIPanel, heightUIPanel);

        for (int i = 0; i < picker.length; i++) {
            int xPos = renderUIFrom + (i * menuTileSpace) + 3;
            if (picker[i].isTouched) {
                uiBatch.draw(tilePickerSelector, renderUIFrom + (menuTileSpace * i) + 3, 0, menuTileWidth, 15);
            }
            bo_helper.draw(uiBatch, picker[i], xPos, 0);
        }

        for (BaseObject baseObject : ui) {
            if (baseObject.isTouched){
                bo_helper.draw(
                        uiBatch,tileMenuCloseButtonBG,
                        (int) baseObject.getX() - 5, (int) (store.uiHeightOriginal - menuItemSize - 15),
                        (int)baseObject.getWidth() + 10, (int)baseObject.getHeight() + 10
                );
            }
            bo_helper.draw(uiBatch, baseObject, (int) baseObject.getX(), (int) (store.uiHeightOriginal - menuItemSize - 10));
        }

//        Рисуем меню настройки пикера
        if (isShowMenuTile) {
            bo_helper.drawWithSize(
                    uiBatch, tileSelectWindow,
                    (int) (store.uiWidthOriginal - 200), (int) (store.uiHeightOriginal - 250)
            );

            int xShift = 10, yShift = 100 + menuTileWidth + 20;
            for (int i = 0; i < allTiles.length; i++) {
                int x, y;
                x = 100 + xShift;
                y = (int) (store.uiHeightOriginal - yShift);
                bo_helper.draw(uiBatch, allTiles[i], x, y);

                if (store.uiHeightOriginal - yShift > 200) {
                    if (allTiles[i].isTouched) {
                        uiBatch.draw(tilePickerSelector, x, y, menuTileWidth, 15);
                    }
                    allTiles[i].draw(uiBatch);
                }

                xShift = xShift + (menuTileSpace + 10);
                if ((xShift + menuTileHeight) > (store.uiWidthOriginal - 200)) {
                    xShift = 10;
                    yShift = yShift + (menuTileWidth + 20);
                }
            }

//            Выбранный элемент
            if (isMenuTileBoundToMouse) {
                bo_helper.draw(
                        uiBatch, boundToMouse,
                        (int) (store.mouseX - boundToMouse.getWidth()/2),
                        (int) (store.mouseY - boundToMouse.getHeight()/2)
                );
            }

//            Кнопка закрытия окна
            int closeButtonX = (int) (tileSelectWindow.getX() + tileSelectWindow.getWidth() - tileMenuCloseButton.getWidth());
            int closeButtonY = (int) (tileSelectWindow.getY() + tileSelectWindow.getHeight() - tileMenuCloseButton.getHeight());

            if (tileMenuCloseButton.isTouched){
                bo_helper.draw(
                        uiBatch,tileMenuCloseButtonBG, closeButtonX, closeButtonY,
                        (int) tileMenuCloseButton.getWidth(), (int) tileMenuCloseButton.getHeight()
                );
            }

            bo_helper.draw(uiBatch,tileMenuCloseButton, closeButtonX, closeButtonY);
        }
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        boolean isUiTouched = false, isDraggedElementDropped = false;

//        Проверка на перетягивание элемента
        if (isTouchUp && isDragged) {
            for (int i = 0; i < picker.length; i++) {
                if (picker[i].isTouched) {
                    if (isMenuTileBoundToMouse) {
                        try {
                            picker[i] = (BaseObject) allTiles[boundToMouseId].clone();
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                        isMenuTileBoundToMouse = false;
                        return true;
                    }
                }
            }
            isMenuTileBoundToMouse = false;
            isDraggedElementDropped = true;
        }

        if (!isTouchUp && !isDragged) {
//        Обработка собыкий в объектах пикера тайлов
            for (int i = 0; i < picker.length; i++) {
                if (picker[i].isTouched) {
                    selectElement(picker[i].getTextureId());
                    return true;
                }
            }

//        Обработка открытия и закрытия меню выбора тайлов
            if (isShowMenuTile && tileMenuCloseButton.isTouched) {
                isShowMenuTile = false;
                return true;
            }

//            Обработка событй в объектах интерфейса
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
                }
            }

            if (tileBoxButton.isTouched && !isDragged) {
                isShowMenuTile = !isShowMenuTile;
                return true;
            }
        }

//        Обработка собыкий в объектах меню тайлов
        if (isShowMenuTile && !isDraggedElementDropped) {
            if (tileSelectWindow.isTouched) {
                isUiTouched = true;
            }

            for (BaseObject tile : allTiles) {
                if (tile.isTouched) {
                    isUiTouched = true;
                    if (isTouchUp) {
                        selectElement(tile.getTextureId());
                        isShowMenuTile = false;
                    }
                    if (isDragged && !isMenuTileBoundToMouse) {
                        isMenuTileBoundToMouse = true;
                        boundToMouseId = tile.getTextureId();
                        try {
                            boundToMouse = (BaseObject) tile.clone();
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

//        Gdx.app.log("Debug", String.valueOf(isUiTouched));

        if (isShowMenuTile){
            return true;
        }

        return isUiTouched;
    }

    private void openMap(){
        int textureId = 0;
        int[][] map =  fileManager.openMap();
        float[] point;
        if (map.length == 0) {
            return;
        }

        store.mapHeight = fileManager.mapHeight;
        store.mapWidth = fileManager.mapWidth;

        store.objectedMap = new MapObject[store.mapHeight][store.mapWidth];

        lightClass.clearAll();
        for (int i = 0; i < store.mapWidth; i++){
            for (int j = 0; j < store.mapHeight; j++){
                point = transform.cartesianToIsometric(
                        (int)((i+1)*store.tileSizeWidth),
                        (int)((j+1)*store.tileSizeHeight)
                );

                textureId = map[i][j];
                MapObject tmp = new MapObject();
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
    }

    private void saveMap(){
        int[][] map = new int[store.mapWidth][store.mapHeight];

        for (int i = 0; i < store.mapWidth; i++) {
            for (int j = 0; j < store.mapHeight; j++) {
                map[i][j] = store.objectedMap[i][j].getTextureId();
            }
        }

        fileManager.saveMap(map, store.mapWidth, store.mapHeight);
    }

    private void selectElement(int id){
        store.selectedTailId = id;
        if (ArrayUtils.checkIntInArray(store.selectedTailId, lightObjectIds)) {
            store.isSelectedLightObject = true;
        } else {
            store.isSelectedLightObject = false;
        }
    }
}
