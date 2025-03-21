package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.ContextMenuWindow;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;

public class MapContextMenuWindow extends ContextMenuWindow {
    public static Store store;
    public DialogEditorWindow dialogEditorWindow;

    public MapContextMenuWindow(DialogEditorWindow dialogEditorWindow) {
        this.dialogEditorWindow = dialogEditorWindow;
    }

    @Override
    public void buildWindow(){
        buttons = new ButtonCommon[6];
        buttons[0] = createOptionButton(MapContextMenuWindow.class, "createCreation", "Создать сущность");
        buttons[1] = createOptionButton(MapContextMenuWindow.class, "openDialogEditor", "Редактировать взаимодействие");
        buttons[2] = createOptionButton(MapContextMenuWindow.class, "deleteDialog", "Удалить взаимодействие");
        buttons[3] = createOptionButton(MapContextMenuWindow.class, "test3", "Опция 3 / Option 3");
        buttons[4] = createOptionButton(MapContextMenuWindow.class, "test4", "Опция 4 / Option 4");
        buttons[5] = createOptionButton(MapContextMenuWindow.class, "test5", "Опция 5 / Option 5");
    }


    public void createCreation(){
        Gdx.app.log("Debug", "Callback createCreation");
        store.creationCount ++;
        store.creations[store.creationCount] = new Creation();
        store.creations[store.creationCount].setPosition(store.playerPositionX-store.shiftX, store.playerPositionY - store.shiftY);
        store.creations[store.creationCount].setCell((int) store.selectedTileX, (int) store.selectedTileY);
        store.creations[store.creationCount].setTexture(new Texture("creations/creation.png"));
    }

    public void openDialogEditor(){
        MapObject mapObject = store.objectedMap[(int) store.selectedTileX - 1][(int) store.selectedTileY - 1];
        String uuid = mapObject.getUUID();
        mapObject.isDialogBind = true;
        dialogEditorWindow.setUUID(uuid);
        dialogEditorWindow.show();
        Gdx.app.log("Debug", "Редактировать взаимодействие для " + uuid);
    }

    public void deleteDialog(){
        MapObject mapObject = store.objectedMap[(int) store.selectedTileX - 1][(int) store.selectedTileY - 1];
        String uuid = mapObject.getUUID();
        mapObject.isDialogBind = false;
        Gdx.app.log("Debug", "Удалить взаимодействие для " + uuid);
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
