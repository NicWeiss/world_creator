package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.objects.TextureObject;
import com.nicweiss.editor.utils.ArrayUtils;
import com.nicweiss.editor.utils.BOHelper;

public class TileSelectorWindow extends Window {
    public static Store store;
    BOHelper bo_helper;

    Texture dayNight, tileBox, tilePickerSelector,
            tilePickerBG, itemBgMenuTexture, circleWhite;
    BaseObject[] picker, allTiles;
    BaseObject dayNightSwitch, tileBoxButton, boundToMouse, circleButtonBG;

    int[] lightObjectIds;
    TextureObject[] textureObjects;

    int widthUIPanel = 770, heightUIPanel = 90;
    int menuTileSpace = 77, menuTileWidth = 70, menuTileHeight = 80;
    int menuShift = 0;

    int boundToMouseId;
    boolean isMenuTileBoundToMouse = false;


    public TileSelectorWindow(int[] lightObjectIds) {
        super();
        bo_helper = new BOHelper();

        this.lightObjectIds = lightObjectIds;

        tilePickerSelector = new Texture("tile_pick_selector.png");
        tilePickerBG = new Texture("tile_pick_bg.png");
        itemBgMenuTexture = new Texture("menu_item_bg.png");
        circleWhite = new Texture("circle_white.png");
        tileBox = new Texture("tile_box.png");
        dayNight = new Texture("day_night.png");
        windowName = "Меню выбора и настройки тайлов";
    }

    public void buildWindow(TextureObject[] textureObjects){
        super.buildWindow();
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

        circleButtonBG = bo_helper.constructObject(
                circleWhite, 0, 0, 1, 1, "circleButtonBG", 0
        );
    }


    public void render(SpriteBatch batch) {
        super.render(batch);
        int renderUIFrom = (int) store.uiWidthOriginal / 2 - widthUIPanel / 2;

        if (isShowWindow) {
            renderItemsList(batch, allTiles, true);
        }

//        Основная панель пикера
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

        //            Выбранный элемент
        if (isShowWindow && isMenuTileBoundToMouse) {
            bo_helper.draw(
                    batch, boundToMouse,
                    (int) (store.mouseX - boundToMouse.getWidth()/2),
                    (int) (store.mouseY - boundToMouse.getHeight()/2)
            );
        }
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        boolean isUiTouched, isDraggedElementDropped = false;
        isUiTouched = super.checkTouch(isDragged, isTouchUp);

        if (isWindowIsDragged){
            return isUiTouched;
        }

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
        }

//        Обработка собыкий в объектах меню тайлов
        if (isShowWindow && !isDraggedElementDropped) {
            if (super.window.isTouched) {
                isUiTouched = true;
            }

            for (BaseObject tile : allTiles) {
                if (tile.isTouched) {
                    isUiTouched = true;
                    if (isTouchUp) {
                        int id = tile.getTextureId();
                        int high = textureObjects[id].high;
                        selectElement(id, high);
                        isShowWindow = false;
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
            resetWindow();
            isShowWindow = !isShowWindow;
            return true;
        }

        if (!isTouchUp && dayNightSwitch.isTouched && !isDragged) {
            store.isDay = !store.isDay;
            return true;
        }

        if (isShowWindow) {
            return true;
        }

        return isUiTouched;
    }

    @Override
    public boolean checkKey(int keyCode){
        return super.checkKey(keyCode);
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
