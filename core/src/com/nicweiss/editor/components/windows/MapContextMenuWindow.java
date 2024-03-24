package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.Gdx;
import com.nicweiss.editor.Generic.ContextMenuWindow;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.components.ButtonCommon;

public class MapContextMenuWindow extends ContextMenuWindow {
    public static Store store;

    @Override
    public void buildWindow(){
        buttons = new ButtonCommon[5];
        buttons[0] = createOptionButton(MapContextMenuWindow.class, "test", "Опция 1 / Option 1");
        buttons[1] = createOptionButton(MapContextMenuWindow.class, "test", "Огшывгшапыгаынрг ыругшап ы олаыпвагшыве гашывпаг");
        buttons[2] = createOptionButton(MapContextMenuWindow.class, "test", "1234567 пsddfs ывпаг");
        buttons[3] = createOptionButton(MapContextMenuWindow.class, "test", "SBNDF<GJLU");
        buttons[4] = createOptionButton(MapContextMenuWindow.class, "test", "12@##RQF#г");
    }

    public void test(){
        Gdx.app.log("Debug", "Callback is wooooork!!!!");
    }
}
