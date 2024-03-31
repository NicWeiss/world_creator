package com.nicweiss.editor.Generic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.utils.BOHelper;

public class Window {
    public static Store store;
    BOHelper bo_helper;

    Texture black, windowBGColor, close, tilePickerSelector, gray, sliderColor, sliderColorBG;
    protected BaseObject windowHeader, window;
    private BaseObject closeButton, closeButtonBG, slider, sliderBG;

    float headerTouchX, headerTouchY, sliderTouchY;
    int x, y, windowOperationalHeight, sliderGlobalY, sliderY = 0;

    int controlButtonSize = 40;
    public boolean isShowWindow = false;
    protected boolean isWindowIsDragged = false;
    protected boolean isHeaderIsDragged, isSliderIsDragged;

    int totalLines, menuShift = 0;

    public Window() {
        bo_helper = new BOHelper();

        windowBGColor = new Texture("Buttons/btn_background.png");
        gray = new Texture("Buttons/border.png");
        black = new Texture("black.png");
        close = new Texture("close.png");
        sliderColorBG = new Texture("Buttons/separator.png");
        sliderColor = new Texture("Buttons/btn_background_hover.png");
        tilePickerSelector = new Texture("tile_pick_selector.png");
    }

    public void buildWindow(){
        x = 100;
        y = sliderY = 150;

        window = bo_helper.constructObject(
                windowBGColor, x, y, 1, 1, "tileSelectWindow", 0
        );
        windowHeader = bo_helper.constructObject(
                gray, x, y, 1, 1, "tileSelectWindowHeader", 0
        );

        closeButton = bo_helper.constructObject(
                close, x, y, controlButtonSize, controlButtonSize, "", 0
        );

        slider = bo_helper.constructObject(
                sliderColor, x, y, controlButtonSize, controlButtonSize, "slider", 0
        );
        sliderBG = bo_helper.constructObject(
                sliderColorBG, x, y, controlButtonSize, controlButtonSize, "slider", 0
        );

        closeButtonBG = bo_helper.constructObject(
                black, x, y, 1, 1, "tileMenuCloseButtonBG", 0
        );
    }


    public void render(SpriteBatch batch) {
        if (isShowWindow) {
            int closeButtonX = (int) (window.getX() + window.getWidth() - closeButton.getWidth());
            int closeButtonY = (int) (window.getY() + window.getHeight() - closeButton.getHeight());

            int width = (int) (store.uiWidthOriginal - 200);
            int height = (int) (store.uiHeightOriginal - 250);
            int padding = 5;

            width = Math.max(width, 350);
            height = Math.max(height, 200);

            bo_helper.draw(batch, window, x, y,  width, height);


//            SLIDER
            windowOperationalHeight = (int)(height - closeButton.getHeight());
            int sliderHeight = totalLines > 0 ? windowOperationalHeight / totalLines : windowOperationalHeight;
            sliderGlobalY = sliderY + windowOperationalHeight;
            sliderGlobalY = Math.min(sliderGlobalY, y + windowOperationalHeight);
            sliderGlobalY = sliderGlobalY - sliderHeight < y ? y + sliderHeight : sliderGlobalY;
            bo_helper.draw(batch, sliderBG, x + width - padding, y, padding-20, windowOperationalHeight);
            bo_helper.draw(batch, slider, x + width - padding, sliderGlobalY, padding-20, sliderHeight * -1);

//            BORDERS
            batch.draw(gray, x, y, width, padding);
            batch.draw(gray, x, y, padding, height);
            batch.draw(gray, x + width, y + height - padding, width * -1, padding);
            batch.draw(gray, x + width - padding, y + height, padding, height * -1);

//            HEADER
            bo_helper.draw(
                    batch, windowHeader,
                    x, (int)(window.getY() + window.getHeight() - closeButton.getHeight()),
                    width, (int)closeButton.getHeight()
            );

//            CONTROL BUTTONS
            if (closeButton.isTouched){
                bo_helper.draw(
                        batch,closeButtonBG, closeButtonX, closeButtonY,
                        (int) closeButton.getWidth(), (int) closeButton.getHeight()
                );
            }

            bo_helper.draw(batch, closeButton, closeButtonX, closeButtonY);
        }
    }

    public void renderItemsList(SpriteBatch batch, BaseObject[] objects){
        if (objects.length == 0){
            return;
        }

        int xShift = 20, yShift = (int)objects[0].getHeight() + 20;
        int _x, _y;
        int menuObjectSpace = 77, itemWidth = 70, itemHeight = 80;
        int countPerLine = getMenuWidth() / (itemWidth + 20);

//        Перерасчёт позиции слайдера
        int cpl = (int) Math.ceil((float)objects.length / countPerLine);
        if (cpl != totalLines ) {
            totalLines = cpl;
            sliderY = (int) (y + (slider.getHeight() * (menuShift)));
        }
        menuShift = menuShift < totalLines ? menuShift : menuShift - 1;

        for (int i = Math.max(Math.min(menuShift * countPerLine, objects.length), 0); i < objects.length; i++) {
            _x = getMenuX() + xShift;
            _y = getMenuHeight() + getMenuY() - yShift - (int) windowHeader.getHeight();

            if (_y > getMenuY()) {
                bo_helper.draw(batch, objects[i], _x, _y);
                if (objects[i].isTouched) {
                    batch.draw(tilePickerSelector, _x, _y, itemWidth, 15);
                }
                objects[i].draw(batch);
            }

            xShift = xShift + (menuObjectSpace + 10);
            if ((xShift + itemHeight) > getMenuWidth()) {
                xShift = 20;
                yShift = yShift + (itemWidth + 20);
            }
        }
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        float diffx, diffy;

        if (!isTouchUp) {
//        Обработка открытия и закрытия
            if (isShowWindow && closeButton.isTouched) {
                if (!isDragged) {
                    isShowWindow = false;
                }
                return true;
            }
        }

        if (!isTouchUp && !isDragged){
            if (windowHeader.isTouched && !closeButton.isTouched){
                headerTouchX = store.mouseX;
                headerTouchY = store.mouseY;
                isWindowIsDragged = true;
                isHeaderIsDragged = true;
                return true;
            }
            if (slider.isTouched && !closeButton.isTouched){
                sliderTouchY = store.mouseY;
                isWindowIsDragged = true;
                isSliderIsDragged = true;
                return true;
            }
        }
        if (!isTouchUp && isDragged){
            if (isWindowIsDragged && isHeaderIsDragged){
                diffx = headerTouchX - store.mouseX;
                diffy = headerTouchY - store.mouseY;

                x = (int) (window.getX() - diffx);
                y = (int) (window.getY() - diffy);

                sliderY = (int) (sliderY - diffy);
                headerTouchX = store.mouseX;
                headerTouchY = store.mouseY;
                return true;
            }
            if (isWindowIsDragged && isSliderIsDragged){
                diffy = sliderTouchY - store.mouseY;
                sliderY = (int)(sliderY - diffy);
                sliderTouchY = store.mouseY;

                int newMenuShift = (int) (Math.abs((sliderY-y) / slider.getHeight()));
                if (menuShift != newMenuShift){
                    menuShift = newMenuShift;
                }

                return true;
            }
        } else {
            isWindowIsDragged = false;
            isSliderIsDragged = false;
            isHeaderIsDragged = false;
        }

        if (isShowWindow) {
            return true;
        }

        return false;
    }

    public int getMenuX(){
        return (int)window.getX();
    }

    public int getMenuY(){
        return (int)window.getY();
    }

    public int getMenuWidth(){
        return (int)window.getWidth();
    }

    public int getMenuHeight(){
        return (int)window.getHeight();
    }

    public void resetWindow(){
        x = 100;
        y = 150;
    }

    public boolean checkKey(int keyCode){
        if (isShowWindow){
            if (keyCode == 19 || keyCode == 156) {
                menuShift --;
                menuShift = Math.max(menuShift, 0);
                sliderY = (int) (y + (slider.getHeight() * (menuShift)));

                return true;
            }

            if (keyCode == 20 || keyCode == 157) {
                menuShift ++;
                menuShift = menuShift < totalLines ? menuShift: menuShift - 1;
                sliderY = (int) (y + (slider.getHeight() * (menuShift)));

                return true;
            }

            if (keyCode == 21 || keyCode == 22){
                return true;
            }
        }

        return false;
    }
}
