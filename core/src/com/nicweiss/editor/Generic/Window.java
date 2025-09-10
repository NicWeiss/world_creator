package com.nicweiss.editor.Generic;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Interfaces.BaseCallBack;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.Font;

import java.lang.reflect.Method;

public class Window extends BaseCallBack implements CallBack {
    public static Store store;
    BOHelper bo_helper;

    protected Texture black, windowBGColor, windowColor, close, tilePickerSelector, gray, sliderColor, sliderColorBG;
    Texture buttonBG, buttonBGHover;
    protected BaseObject windowHeader, window, windowBG;
    private BaseObject closeButton, closeButtonBG, slider, sliderBG;

    float headerTouchX, headerTouchY;
    public int x, y, windowOperationalHeight;
    public int width, height = 0;

    int controlButtonSize = 40;
    public boolean isShowWindow = false;
    public boolean isDualSectionMode = false;
    protected boolean isWindowActive = true;
    protected boolean isWindowIsDragged = false;
    protected boolean isHeaderIsDragged, isSliderIsDragged;
    public boolean isScrollHidden = false;

    protected Font font;
    protected String windowName = "";
    protected float textHeight;

    protected int windowWidth = 0;
    protected int windowHeight = 0;
    protected int additionalHeight = 0;
    protected int padding = 5;

    protected int menuObjectSpace = 77, itemWidth = 70, itemHeight = 80;

    public WindowSection leftSection;
    public WindowSection rightSection;

    protected ButtonCommon[] controlButtons;

    public int symbolWidth;

    public Window() {
        bo_helper = new BOHelper();

        windowColor = windowBGColor = new Texture("Buttons/btn_background.png");
        buttonBG = new Texture("Buttons/btn_window_background.png");
        gray = new Texture("Buttons/border.png");
        black = new Texture("black.png");
        close = new Texture("close.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");
        tilePickerSelector = new Texture("tile_pick_selector.png");

        font = new Font(7, Color.BLACK);
        symbolWidth = (int) font.getWidth("W");

        leftSection = new WindowSection();
        rightSection = new WindowSection();
    }

    public void buildWindow(){
        calcWindowSize();

        x = (int) ((store.uiWidthOriginal / 2) - (width / 2));
        y = (int) ((store.uiHeightOriginal / 2) - (height / 2)) + 50;

        window = bo_helper.constructObject(
                windowColor, x, y, 1, 1, "tileSelectWindow", 0
        );

        windowBG = bo_helper.constructObject(
            windowBGColor, x, y, 1, 1, "tileSelectWindow", 0
        );

        windowHeader = bo_helper.constructObject(
                gray, x, y, 1, 1, "tileSelectWindowHeader", 0
        );

        closeButton = bo_helper.constructObject(
                close, x, y, controlButtonSize, controlButtonSize, "", 0
        );

        closeButtonBG = bo_helper.constructObject(
                black, x, y, 1, 1, "tileMenuCloseButtonBG", 0
        );


        updateSectionSizes();
        leftSection.build();

        if (isDualSectionMode) {
            rightSection.build();
        }
    }

    private void updateSectionSizes(){
        int sectionWidth = width;
        int secondSectionWidth = sectionWidth;
        int sectionY = y + padding;
        int sectionHeight = windowOperationalHeight;
        leftSection.isScrollHidden = isScrollHidden;
        rightSection.isScrollHidden = isScrollHidden;

        if (isDualSectionMode) {
            sectionWidth = width / 3;
            secondSectionWidth = width -sectionWidth;
        }

        leftSection.setSize(
            x, sectionY,
            sectionWidth, sectionHeight
        );

        rightSection.setSize(
            x + leftSection.width, sectionY,
            secondSectionWidth, sectionHeight
        );
    }

    protected ButtonCommon createControlButton(Class classObject, String callBackMethodName, String buttonText) {
        ButtonCommon button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, buttonText);
        Method method = null;

        try {
            method = classObject.getDeclaredMethod(callBackMethodName);
        } catch (Exception e) {
            e.printStackTrace();
        }

        button.registerCallBack(this, method);

        return button;
    }

    private void calcWindowSize(){
        additionalHeight = 0;

        if (controlButtons != null && controlButtons.length > 0){
            additionalHeight = controlButtons[0].height + 20;
        }

        width = (int) (store.uiWidthOriginal - 200);
        height = (int) (store.uiHeightOriginal - 250);

        if (windowWidth > 0) {
            width = windowWidth;
        }

        if (windowHeight > 0) {
            height = windowHeight ;
        }

        width = Math.max(width, 350);
        height = Math.max((height), 100);

        windowOperationalHeight = height - controlButtonSize - padding;
        updateSectionSizes();
    }


    public void render(SpriteBatch batch) {
        if (isShowWindow) {
            calcWindowSize();

            bo_helper.draw(batch, windowBG, x, y - additionalHeight,  width, height);
            bo_helper.draw(batch, window, x, y,  width, height);

//            BORDERS
            batch.draw(gray, x, y - additionalHeight, width, padding);
            batch.draw(gray, x, y - additionalHeight, padding, height + additionalHeight);
            batch.draw(gray, x + width, y + height - padding, width * -1, padding);
            batch.draw(gray, x + width - padding, y + height, padding, (height + additionalHeight) * -1);

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
            int closeButtonX = (int) (window.getX() + window.getWidth() - closeButton.getWidth());
            int closeButtonY = (int) (window.getY() + window.getHeight() - closeButton.getHeight());

            if (closeButton.isTouched){
                bo_helper.draw(
                        batch,closeButtonBG, closeButtonX, closeButtonY,
                        (int) closeButton.getWidth(), (int) closeButton.getHeight()
                );
            }

            int btnPosX = 0;
            if (controlButtons != null){
                for (BaseObject bo : controlButtons){
                    btnPosX = btnPosX + (int) (bo.getWidth() + 20);
                    bo_helper.draw(
                        batch,
                        bo,
                        (int) (windowHeader.getX() + window.width - btnPosX),
                        (int) (windowHeader.getY() - window.getHeight())
                    );
                }
            }

            bo_helper.draw(batch, closeButton, closeButtonX, closeButtonY);

            leftSection.render(batch);
            if (isDualSectionMode) {
                rightSection.render(batch);
            }
        }
    }

    public void renderItemsList(SpriteBatch batch, BaseObject[] objects, boolean isTiled) {
        leftSection.renderItems(batch, objects, isTiled, false);
    }

    public void renderItemsList(SpriteBatch batch, ButtonCommon[] objects, boolean isTiled) {
        leftSection.renderItems(batch, objects, isTiled, false);
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        float diffx, diffy;
        if (!isWindowActive || !isShowWindow){
            return false;
        }
        if (isTouchUp && !isDragged) {
//        Обработка открытия и закрытия
            if (isShowWindow && closeButton.isTouched) {
                isShowWindow = false;
                return true;
            }

            if (controlButtons != null && controlButtons.length > 0){
                for (ButtonCommon bo : controlButtons){
                    boolean isButtonTouched = bo.checkTouchAndExec();
                    if (isButtonTouched) { return true; }
                }
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
        }

        if (!isTouchUp && isDragged){
            if (isWindowIsDragged && isHeaderIsDragged){
                diffx = headerTouchX - store.mouseX;
                diffy = headerTouchY - store.mouseY;
                x = (int) (window.getX() - diffx);
                y = (int) (window.getY() - diffy);
                headerTouchX = store.mouseX;
                headerTouchY = store.mouseY;
                return true;
            }
        } else {
            isWindowIsDragged = false;
            isHeaderIsDragged = false;
        }

        if (leftSection.checkTouch(store.mouseX, store.mouseY, isDragged, isTouchUp)) {
            return true;
        }
        if (isDualSectionMode) {
            if (rightSection.checkTouch(store.mouseX, store.mouseY, isDragged, isTouchUp)) {
                return true;
            }
        }

        if (isShowWindow) {
            return true;
        }

        return false;
    }


    public void resetWindow(){
        x = 100;
        y = 150;
    }

    public boolean checkKey(int keyCode){
        if (!isWindowActive || !isShowWindow){
            return false;
        }

        if (isShowWindow){
            if (leftSection.checkKey(keyCode)) {
                return true;
            }
            if (isDualSectionMode) {
                if (rightSection.checkKey(keyCode)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void scrollDown(){
        if (isWindowActive && isShowWindow){
            leftSection.scrollDown();
            if (isDualSectionMode) {
                rightSection.scrollDown();
            }
        }
    }

    public void show(){
        isWindowActive = true;
        isShowWindow = true;
        onShow();
    }

    public void hide(){
        isWindowActive = false;
        isShowWindow = false;
        onHide();
    }

    public void onShow() {}

    public void onHide() {}

    public void setDualSectionMode(boolean isDual) {
        this.isDualSectionMode = isDual;
    }
}
