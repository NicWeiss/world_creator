package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.objects.TextureObject;
import com.nicweiss.editor.utils.ArrayUtils;
import com.nicweiss.editor.utils.BOHelper;

public class TileSelectorWindow {
    public static Store store;
    BOHelper bo_helper;

    Texture border, black, dark, dayNight, close, white, tileBox, tilePickerSelector,
            tilePickerBG, itemBgMenuTexture, circleWhite;
    BaseObject[] picker, allTiles;
    BaseObject dayNightSwitch, tileSelectWindow, tileSelectWindowBorder, tileSelectWindowHeader,
            tileMenuCloseButton, tileMenuCloseButtonBG, tileBoxButton, boundToMouse, circleButtonBG;

    int[] lightObjectIds;
    TextureObject[] textureObjects;

    int widthUIPanel = 770, heightUIPanel = 90;
    int menuTileSpace = 77, menuTileWidth = 70, menuTileHeight = 80;
    int menuShift = 0;
    int menuItemSize = 40;

    int boundToMouseId;
    boolean isMenuTileBoundToMouse = false;
    public boolean isShowMenuTile = false;


    public TileSelectorWindow(int[] lightObjectIds){
        bo_helper = new BOHelper();

        this.lightObjectIds = lightObjectIds;

        tilePickerSelector = new Texture("tile_pick_selector.png");
        tilePickerBG = new Texture("tile_pick_bg.png");
        itemBgMenuTexture = new Texture("menu_item_bg.png");
        dark = new Texture("dark.png");
        black = new Texture("black.png");
        close = new Texture("close.png");
        white = new Texture("white.png");
        circleWhite = new Texture("circle_white.png");
        tileBox = new Texture("tile_box.png");
        border = new Texture("border.png");
        dayNight = new Texture("day_night.png");
    }

    public void buildWindow(TextureObject[] textureObjects){
        this.textureObjects = textureObjects;
        int widthUIPanel = 770;
        int renderUIFrom = (int) store.uiWidthOriginal /2 - widthUIPanel / 2;

        tileBoxButton = bo_helper.constructObject(
                tileBox, 0,0, 70, 70, "", 0
        );
        dayNightSwitch = bo_helper.constructObject(
                dayNight, 0,0, 70, 70, "", 0
        );

        allTiles = new BaseObject[textureObjects.length];
        for (int i = 0; i<textureObjects.length;  i++) {
            allTiles[i] = bo_helper.constructObject(
                    textureObjects[i].texture, renderUIFrom + (i * menuTileSpace) + 3,5, menuTileWidth,
                    menuTileHeight, String.valueOf(i), i
            );
        }

        picker = new BaseObject[10];
        for (int i = 0; i<10;  i++) {
            picker[i] = bo_helper.constructObject(
                    textureObjects[i].texture, renderUIFrom + (i * menuTileSpace) + 3,5, menuTileWidth,
                    menuTileHeight, String.valueOf(i), i
            );
        }

        tileSelectWindow = bo_helper.constructObject(
                dark, 100, 150, 1, 1, "tileSelectWindow", 0
        );
        tileSelectWindowBorder = bo_helper.constructObject(
                border, 100, 150, 1, 1, "tileSelectWindowBorder", 0
        );
        tileSelectWindowHeader = bo_helper.constructObject(
                black, 0, 0, 1, 1, "tileSelectWindowHeader", 0
        );

        tileMenuCloseButton = bo_helper.constructObject(
                close, 0, 0, menuItemSize, menuItemSize, "", 0
        );

        tileMenuCloseButtonBG = bo_helper.constructObject(
                white, 100, 150, 1, 1, "tileMenuCloseButtonBG", 0
        );
        circleButtonBG = bo_helper.constructObject(
                circleWhite, 0, 0, 1, 1, "circleButtonBG", 0
        );
    }


    public void render(SpriteBatch batch) {
        int renderUIFrom = (int) store.uiWidthOriginal / 2 - widthUIPanel / 2;

        batch.draw(tilePickerBG, renderUIFrom, 0, widthUIPanel, heightUIPanel);
        bo_helper.drawButton(batch, tileBoxButton, circleButtonBG, renderUIFrom - 75, 5);
        bo_helper.drawButton(batch, dayNightSwitch, circleButtonBG, renderUIFrom + widthUIPanel + 5, 5);

        for (int i = 0; i < picker.length; i++) {
            int xPos = renderUIFrom + (i * menuTileSpace) + 3;
            if (picker[i].isTouched) {
                batch.draw(tilePickerSelector, renderUIFrom + (menuTileSpace * i) + 3, 0, menuTileWidth, 15);
            }
            bo_helper.draw(batch, picker[i], xPos, 0);
        }

        if (isShowMenuTile) {
//            Кнопка закрытия окна
            int closeButtonX = (int) (tileSelectWindow.getX() + tileSelectWindow.getWidth() - tileMenuCloseButton.getWidth());
            int closeButtonY = (int) (tileSelectWindow.getY() + tileSelectWindow.getHeight() - tileMenuCloseButton.getHeight());

            bo_helper.drawWithSize(
                    batch, tileSelectWindow,
                    (int) (store.uiWidthOriginal - 200), (int) (store.uiHeightOriginal - 250)
            );
            bo_helper.drawWithSize(
                    batch, tileSelectWindowBorder,
                    (int) (store.uiWidthOriginal - 200), (int) (store.uiHeightOriginal - 250)
            );
            bo_helper.draw(
                    batch, tileSelectWindowHeader,
                    (int) (tileSelectWindow.getX()), (int) (tileSelectWindow.getHeight() + 110),
                    (int) (tileSelectWindow.getWidth()), (int) (tileSelectWindow.getY() - 110)
            );

            int xShift = 20, yShift = 100 + menuTileWidth + 60;
            int countPerLine = (int)((store.uiWidthOriginal - 200) / (menuTileWidth + 20));

            menuShift = Math.max(menuShift, 0);
            menuShift = menuShift * countPerLine > allTiles.length - countPerLine ? (allTiles.length / countPerLine) - 1 : menuShift;

            for (int i = Math.max(Math.min(menuShift * countPerLine, allTiles.length), 0); i < allTiles.length; i++) {
                int x, y;
                x = 100 + xShift;
                y = (int) (store.uiHeightOriginal - yShift);

                if (store.uiHeightOriginal - yShift > 170) {
                    bo_helper.draw(batch, allTiles[i], x, y);
                    if (allTiles[i].isTouched) {
                        batch.draw(tilePickerSelector, x, y, menuTileWidth, 15);
                    }
                    allTiles[i].draw(batch);
                }

                xShift = xShift + (menuTileSpace + 10);
                if ((xShift + menuTileHeight) > (store.uiWidthOriginal - 200)) {
                    xShift = 20;
                    yShift = yShift + (menuTileWidth + 20);
                }
            }

//            Выбранный элемент
            if (isMenuTileBoundToMouse) {
                bo_helper.draw(
                        batch, boundToMouse,
                        (int) (store.mouseX - boundToMouse.getWidth()/2),
                        (int) (store.mouseY - boundToMouse.getHeight()/2)
                );
            }

            if (tileMenuCloseButton.isTouched){
                bo_helper.draw(
                        batch,tileMenuCloseButtonBG, closeButtonX, closeButtonY,
                        (int) tileMenuCloseButton.getWidth(), (int) tileMenuCloseButton.getHeight()
                );
            }

            bo_helper.draw(batch,tileMenuCloseButton, closeButtonX, closeButtonY);
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

        if (!isTouchUp) {
//        Обработка событий в объектах пикера тайлов
            for (int i = 0; i < picker.length; i++) {
                if (picker[i].isTouched) {
                    if (!isDragged) {
                        int id = picker[i].getTextureId();
                        int high = textureObjects[id].high;
                        selectElement(id, high);
                    }
                    return true;
                }
            }

//        Обработка открытия и закрытия меню выбора тайлов
            if (isShowMenuTile && tileMenuCloseButton.isTouched) {
                if (!isDragged) {
                    isShowMenuTile = false;
                }
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
                        int id = tile.getTextureId();
                        int high = textureObjects[id].high;
                        selectElement(id, high);
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

        if (!isTouchUp && tileBoxButton.isTouched && !isDragged) {
            isShowMenuTile = !isShowMenuTile;
            return true;
        }

        if (!isTouchUp && dayNightSwitch.isTouched && !isDragged) {
            store.isDay = !store.isDay;
            return true;
        }

        if (isShowMenuTile) {
            return true;
        }

        return isUiTouched;
    }


    public boolean checkKey(int keyCode){
        if (isShowMenuTile){
            if (keyCode == 19 || keyCode == 156) {
                menuShift --;
                return true;
            }

            if (keyCode == 20 || keyCode == 157) {
                menuShift ++;
                return true;
            }

            if (keyCode == 21 || keyCode == 22){
                return true;
            }
        }

        return false;
    }

    private void selectElement(int id, int high){
        store.selectedTailId = id;
        store.selectedTailObjectHigh = high;
        if (ArrayUtils.checkIntInArray(store.selectedTailId, lightObjectIds)) {
            store.isSelectedLightObject = true;
        } else {
            store.isSelectedLightObject = false;
        }
    }
}
