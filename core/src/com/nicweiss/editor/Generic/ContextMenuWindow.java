package com.nicweiss.editor.Generic;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.Font;

import java.lang.reflect.Method;

public class ContextMenuWindow implements CallBack {
    public static Store store;
    BOHelper bo_helper;
    Texture buttonBG, buttonBGHover, buttonSeparator, border;

    protected ButtonCommon[] buttons;
    protected Font font;
    public boolean isShow;
    protected int x,y;
    protected int menuHeight = -1, menuWidth = -1;

    public ContextMenuWindow(){
        bo_helper = new BOHelper();
        font = new Font(7, Color.BLACK);

        border = new Texture("Buttons/border.png");
        buttonSeparator = new Texture("Buttons/separator.png");
        buttonBG = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");
    }

    protected ButtonCommon createOptionButton(Class classObject, String callBackMethodName, String buttonText) {
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

    public void buildWindow() throws Exception {
        throw new Exception("Method must be overwrited!");
    }

    public void render(SpriteBatch batch) {
        if (!isShow) {return;}

        int yRenderPosition = y;

        for (int i=buttons.length-1; i > -1; i--){
            bo_helper.draw(batch, buttons[i], x, yRenderPosition, menuWidth, (menuHeight / buttons.length));

            if (i> 0 && i < buttons.length){
                batch.draw(buttonSeparator, x + 10, (yRenderPosition + buttons[i].getTextHeight()) - 1, menuWidth - 20, 1);
            }

            yRenderPosition = (int) (yRenderPosition + buttons[i].getTextHeight());
        }


        batch.draw(border, x, y, menuWidth, 1);
        batch.draw(border, x, y, 1, menuHeight);
        batch.draw(border, x + menuWidth, y + menuHeight - 1, menuWidth * -1, 1);
        batch.draw(border, x + menuWidth - 1, y + menuHeight, 1, menuHeight * -1);

    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp, int button){
        boolean touchResult = false;
        if (!isShow && button != 1) {return false;}

        if (menuHeight == -1  || menuWidth == -1) {
            calcMenuSize();
        }

//        Показать / Скрыть
        if (!isTouchUp && !isDragged && button == 1) {
            isShow = !isShow;

            x = store.mouseX + menuWidth > store.uiWidthOriginal? store.mouseX - menuWidth: store.mouseX;
            y = store.mouseY - menuHeight > 0 ? store.mouseY - menuHeight : store.mouseY;

            return true;
        }

//        Чекаем нажатие на элементы
        if (isTouchUp && !isDragged && button == 0) {
            for (ButtonCommon btn: buttons) {
                if (btn.checkTouchAndExec()) {
                    touchResult = true;
                    isShow = false;
                }
            }
        }

//        Если ничего не выбрано, то скрываем окно
        if (isTouchUp && button == 0 && isShow && !touchResult) {
            isShow = false;
            touchResult = true;
        }

        return touchResult;
    }

    public void onMouseMoved(){
        if (!isShow) {return;}

        for (ButtonCommon btn: buttons) {
            btn.checkTouch(store.mouseX, store.mouseY);
        }
    }

    public boolean checkKey(int keyCode){
        if (!isShow) {return false;}

        return true;
    }

    protected void calcMenuSize(){
        if (buttons.length > 0){
            menuHeight = (int) (buttons[0].getTextHeight() * (buttons.length));

            int btnWidth ;
            for (ButtonCommon btn: buttons){
                btnWidth = btn.getTextWidth();
                menuWidth =  btnWidth > menuWidth ? btnWidth : menuWidth;
            }
        } else {
            menuHeight = 0;
            menuWidth = 0;
        }
    }
}
