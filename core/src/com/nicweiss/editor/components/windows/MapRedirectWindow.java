package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;

import java.util.Arrays;

public class MapRedirectWindow extends Window implements CallBack {
    public static Store store;

    Texture buttonBG, buttonBGHover, nameIcon;
    ButtonCommon[] items;

    public MapRedirectWindow() {
        super();
        windowName = "Взаимодействие объекта";
        windowWidth = 500;
        windowHeight = 300;

        buttonBG      = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");
        nameIcon      = new Texture("icons/quest_window/name.png");
    }

    public void buildWindow() {
        super.buildWindow();
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }

    // Настраивает окно для конкретного объекта перед show()
    public void configure(String name, String uuid) {
        items = new ButtonCommon[10];
        int i = 0;

        ButtonCommon btn;

        btn = new ButtonCommon();
        btn.setBackgrounds(buttonBG, buttonBGHover);
        btn.setIcon(nameIcon);
        btn.setText(font, "Объект: " + name);
        items[i++] = btn;

        // Заглушка секции перенаправления
        btn = new ButtonCommon();
        btn.setBackgrounds(buttonBGHover, buttonBGHover);
        btn.setText(font, "── ПЕРЕХОД НА КАРТУ ──");
        items[i++] = btn;

        btn = new ButtonCommon();
        btn.setBackgrounds(buttonBGHover, buttonBGHover);
        btn.setText(font, "Целевая карта: (не задана)");
        items[i++] = btn;

        btn = new ButtonCommon();
        btn.setBackgrounds(buttonBGHover, buttonBGHover);
        btn.setText(font, "Точка входа: (не задана)");
        items[i++] = btn;

        btn = new ButtonCommon();
        btn.setBackgrounds(buttonBGHover, buttonBGHover);
        btn.setText(font, "(Функция будет реализована в следующих версиях)");
        items[i++] = btn;

        items = Arrays.copyOfRange(items, 0, i);
    }

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);
        if (isShowWindow) {
            renderItemsList(batch, items, false);
        }
    }

    @Override
    public boolean checkTouch(boolean isDragged, boolean isTouchUp) {
        if (!isShowWindow) return false;
        super.checkTouch(isDragged, isTouchUp);
        return isShowWindow;
    }

    @Override
    public boolean checkKey(int keyCode) {
        return super.checkKey(keyCode);
    }
}
