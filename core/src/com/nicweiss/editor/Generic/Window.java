package com.nicweiss.editor.Generic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.objects.TextObject;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.Font;
import com.nicweiss.editor.utils.Text;

public class Window {
    public static Store store;
    BOHelper bo_helper;
    TextObject[] textObjects;

    Texture black, windowBGColor, close, tilePickerSelector, gray, sliderColor, sliderColorBG;
    protected BaseObject windowHeader, window;
    private BaseObject closeButton, closeButtonBG, slider, sliderBG;

    float headerTouchX, headerTouchY, sliderTouchY;
    int x, y, windowOperationalHeight, sliderGlobalY, sliderY = 0;
    int width, height = 0;

    int controlButtonSize = 40;
    public boolean isShowWindow = false;
    protected boolean isWindowActive = true;
    protected boolean isWindowIsDragged = false;
    protected boolean isHeaderIsDragged, isSliderIsDragged;

    int totalLines, menuShift = 0;
    protected Font font;
    protected String windowName = "";
    protected float textHeight;

    protected int windowWidth = 0;
    protected int windowHeight = 0;

    protected int menuObjectSpace = 77, itemWidth = 70, itemHeight = 80;
    protected String textForRender = "";

    public Window() {
        bo_helper = new BOHelper();

        windowBGColor = new Texture("Buttons/btn_background.png");
        gray = new Texture("Buttons/border.png");
        black = new Texture("black.png");
        close = new Texture("close.png");
        sliderColorBG = new Texture("Buttons/separator.png");
        sliderColor = new Texture("Buttons/btn_background_hover.png");
        tilePickerSelector = new Texture("tile_pick_selector.png");

        font = new Font(7, Color.BLACK);
    }

    public void buildWindow(){
        calcWindowSize();

        x = (int) ((store.uiWidthOriginal / 2) - (width / 2));
        y = sliderY = (int) ((store.uiHeightOriginal / 2) - (height / 2)) + 50;

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

    private void calcWindowSize(){
        width = (int) (store.uiWidthOriginal - 200);
        height = (int) (store.uiHeightOriginal - 250);

        if (windowWidth > 0) {
            width = windowWidth;
        }

        if (windowHeight > 0) {
            height = windowHeight;
        }

        width = Math.max(width, 350);
        height = Math.max(height, 100);
    }


    public void render(SpriteBatch batch) {
        if (isShowWindow) {
            int padding = 5;
            int closeButtonX = (int) (window.getX() + window.getWidth() - closeButton.getWidth());
            int closeButtonY = (int) (window.getY() + window.getHeight() - closeButton.getHeight());

            calcWindowSize();

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


            textHeight = font.getHeight(windowName);
            font.draw(
                    batch, windowName ,
                    windowHeader.getX() + ((windowHeader.getHeight() - textHeight) / 2),
                    windowHeader.getY() + (windowHeader.getHeight() / 2) - (textHeight / 2)
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

    public void renderItemsList(SpriteBatch batch, BaseObject[] objects, boolean isTiled) {
        renderItems(batch, objects, isTiled);
    }

    public void renderItemsList(SpriteBatch batch, ButtonCommon[] objects, boolean isTiled) {
        renderItems(batch, objects, isTiled);
    }

    protected void addTextObject(String text, int id) {
        TextObject to = new TextObject(font, text);
        textObjects[id] = to;
    }

    public void renderText(SpriteBatch batch, String text) {
        if (text != textForRender) {
            String[] textLines = text.split("\n");
            int i = 0;
            int symbolWidth = (int) font.getWidth("W");
            int maxWindowSymbolCounts = ((width - 50) / symbolWidth);
            textObjects = new TextObject[1000];

            for(String textLine : textLines ) {
                String[] subStrings = textLine.split(" ");
                String part = "";

                for(String subString : subStrings ) {
                    if ((int)font.getWidth(subString) >= width - 50) {
                        if (part != "") {
                            addTextObject(part, i);
                            i = i+1;
                        }

                        part = "";
                        String[] spl = Text.split(subString , maxWindowSymbolCounts);

                        for (String s: spl) {
                            addTextObject(s, i);
                            i = i+1;
                        }
                        continue;
                    }

                    if ((int) font.getWidth(part + " " + subString) < width - 50) {
                        part = part + " " + subString;
                    } else {
                        addTextObject(part, i);
                        part = subString;
                        i = i + 1;
                    }
                }
                if (part != "") {
                    addTextObject(part, i);
                    i = i+1;
                }
            }
        }

        if (textObjects != null) {
            renderItems(batch, textObjects, false);
            itemWidth = (int) font.getWidth(".");
        }
    }

    private void renderItems(SpriteBatch batch, BaseObject[] objects, boolean isTiled) {
        int objectsCount = 0;

        if (objects != null){
            for (BaseObject bo : objects){
                if (bo != null) {
                    objectsCount ++;
                }
            }
        }
//        int objectsCount = objects.length;
        if (objectsCount == 0) {
            return;
        }

        int xShift = 20, yShift = (int)objects[0].getHeight() + 20;
        int _x, _y;
        int countPerLine = isTiled ? getMenuWidth() / (itemWidth + 20) : 1;

//        Перерасчёт позиции слайдера
        int cpl = (int) Math.ceil((float)objectsCount / countPerLine);
        if (cpl != totalLines ) {
            totalLines = cpl;
            sliderY = (int) (y + (slider.getHeight() * (menuShift)));
        }
        menuShift = menuShift < totalLines ? menuShift : menuShift - 1;

        for (int i = Math.max(Math.min(menuShift * countPerLine, objectsCount), 0); i < objectsCount; i++) {
            _x = getMenuX() + xShift;
            _y = getMenuHeight() + getMenuY() - yShift - (int) windowHeader.getHeight();

            if (_y > getMenuY()) {
                if (!isTiled) {
                    objects[i].setWidth(getMenuWidth() - 60);
                }
                bo_helper.draw(batch, objects[i], _x, _y, isWindowActive);

                if (objects[i].isTouched) {
                    batch.draw(tilePickerSelector, _x, _y, itemWidth, 15);
                }
                objects[i].draw(batch);
            }

            xShift = xShift + (menuObjectSpace + 10);
            if ((xShift + itemHeight) > Math.min(getMenuWidth()+slider.getWidth(), (itemHeight + 20) * countPerLine)) {
                xShift = 20;
                yShift = yShift + (itemWidth + 20);
            }
        }
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        float diffx, diffy;

        if (!isWindowActive){
            return true;
        }

        if (isTouchUp && !isDragged) {
//        Обработка открытия и закрытия
            if (isShowWindow && closeButton.isTouched) {
                isShowWindow = false;
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
        if (!isWindowActive){
            return true;
        }

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

    public void scrollDown(){
        int maxItems = (int)(height/2 / (itemWidth + 20));
        menuShift = Math.max(totalLines - maxItems, 0);
        sliderY = (int) (y + (slider.getHeight() * (menuShift)));
    }

    public void show(){
        isWindowActive = true;
        isShowWindow = true;
    }

    public void hide(){
        isWindowActive = false;
        isShowWindow = false;
    }
}
