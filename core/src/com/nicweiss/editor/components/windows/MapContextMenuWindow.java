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
        buttons[0] = createOptionButton(MapContextMenuWindow.class, "test1", "Редактировать взаимодействие");
        buttons[1] = createOptionButton(MapContextMenuWindow.class, "test2", "Опция 2 / Option 2");
        buttons[2] = createOptionButton(MapContextMenuWindow.class, "test3", "Опция 3 / Option 3");
        buttons[3] = createOptionButton(MapContextMenuWindow.class, "test4", "Опция 4 / Option 4");
        buttons[4] = createOptionButton(MapContextMenuWindow.class, "test5", "Опция 5 / Option 5");
    }

    public void test1(){
        Gdx.app.log("Debug", "Редактировать взаимодействие");
    }
    public void test2(){
        Gdx.app.log("Debug", "Callback 2");
    }
    public void test3(){
        Gdx.app.log("Debug", "Callback 3");
    }
    public void test4(){
        Gdx.app.log("Debug", "Callback 4");
    }
    public void test5(){
        Gdx.app.log("Debug", "Callback 5");
    }
}
