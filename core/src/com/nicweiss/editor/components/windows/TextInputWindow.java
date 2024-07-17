package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;

public class TextInputWindow extends Window {
    public static Store store;

    public TextInputWindow() {
        super();
        windowName = "Меню выбора и настройки тайлов";
    }

    public void buildWindow(){
        super.buildWindow();
    }


    public void render(SpriteBatch batch) {
        super.render(batch);
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        super.checkTouch(isDragged, isTouchUp);

        if (isShowWindow) {
            return true;
        }

        return false;
    }

    @Override
    public boolean checkKey(int keyCode){
        return super.checkKey(keyCode);
    }
}
