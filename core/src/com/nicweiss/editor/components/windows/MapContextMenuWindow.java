package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.nicweiss.editor.Generic.ContextMenuWindow;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;

import java.util.ArrayList;
import java.util.List;

public class MapContextMenuWindow extends ContextMenuWindow {
    public static Store store;
    public DialogEditorWindow dialogEditorWindow;
    public MapRedirectWindow mapRedirectWindow;

    public MapContextMenuWindow(DialogEditorWindow dialogEditorWindow,
                                MapRedirectWindow mapRedirectWindow) {
        this.dialogEditorWindow = dialogEditorWindow;
        this.mapRedirectWindow = mapRedirectWindow;
    }

    @Override
    public void buildWindow() {
        rebuildButtons();
    }

    // Пересобирает меню при каждом показе — статика + динамика по текущей клетке
    private void rebuildButtons() {
        int tx = (int) store.selectedTileX;
        int ty = (int) store.selectedTileY;

        List<ButtonCommon> list = new ArrayList<>();

        list.add(createOptionButton(MapContextMenuWindow.class, "createCreation", "Создать сущность"));
        list.add(createOptionButton(MapContextMenuWindow.class, "createBuilding",  "Добавить недвижимость"));
        list.add(createOptionButton(MapContextMenuWindow.class, "openTileDialog",  "Ред. взаимодействие тайла"));
        list.add(createOptionButton(MapContextMenuWindow.class, "deleteDialog",    "Удалить взаимодействие тайла"));

        // Динамические кнопки для NPC на данной клетке
        for (int i = 0; i <= store.creationCount; i++) {
            Creation cr = store.creations[i];
            if (cr != null && cr.mapCellX == tx && cr.mapCellY == ty) {
                String uuid = cr.getUUID();
                String name = store.npcs.containsKey(uuid) ? store.npcs.get(uuid).toString() : "NPC";
                list.add(createDynamicButton("openNpcInteraction", "Ред. взаим. с " + name, uuid));
            }
        }

        // Динамические кнопки для зданий на данной клетке
        for (int i = 0; i <= store.buildingCount; i++) {
            Creation b = store.buildings[i];
            if (b != null && b.mapCellX == tx && b.mapCellY == ty) {
                String uuid = b.getUUID();
                String name = store.buildingNames.containsKey(uuid)
                    ? store.buildingNames.get(uuid).toString() : "Объект";
                list.add(createDynamicButton("openBuildingInteraction", "Ред. взаим. с " + name, uuid));
            }
        }

        buttons = list.toArray(new ButtonCommon[0]);
        menuHeight = -1;
        menuWidth  = -1;
    }

    // Создаёт кнопку с UUID-параметром. methodName должен быть строковым литералом
    // (чтобы ==  в registerCallBack работал с интернированными строками JVM).
    private ButtonCommon createDynamicButton(String methodName, String label, String uuid) {
        ButtonCommon btn = new ButtonCommon();
        btn.setBackgrounds(buttonBG, buttonBGHover);
        btn.setText(font, label);
        btn.setWidthByText();
        btn.registerCallBack(this, methodName, new String[]{uuid});
        return btn;
    }

    // Пересобираем меню при показе, чтобы динамика была актуальной
    @Override
    public boolean checkTouch(boolean isDragged, boolean isTouchUp, int button) {
        if (!isTouchUp && !isDragged && button == 1 && !isShow) {
            rebuildButtons();
        }
        return super.checkTouch(isDragged, isTouchUp, button);
    }

    // ── Статические действия ──────────────────────────────────────────────────

    public void createCreation() {
        store.creationCount++;
        Creation cr = new Creation();
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        cr.setUUID(uuid);
        cr.setPosition(store.playerPositionX - store.shiftX, store.playerPositionY - store.shiftY);
        cr.setCell((int) store.selectedTileX, (int) store.selectedTileY);
        cr.setTexture(new Texture("creations/creation.png"));
        store.creations[store.creationCount] = cr;
        store.npcs.put(uuid, "NPC");
    }

    public void createBuilding() {
        store.buildingCount++;
        Creation b = new Creation();
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        b.setUUID(uuid);
        b.setPosition(store.playerPositionX - store.shiftX, store.playerPositionY - store.shiftY);
        b.setCell((int) store.selectedTileX, (int) store.selectedTileY);
        b.setTexture(new Texture("objects/default_object.png"));
        store.buildings[store.buildingCount] = b;
        store.buildingNames.put(uuid, "Объект");
    }

    public void openTileDialog() {
        MapObject mapObject = store.objectedMap[(int) store.selectedTileX - 1][(int) store.selectedTileY - 1];
        String uuid = mapObject.getUUID();
        mapObject.isDialogBind = true;
        dialogEditorWindow.setRoot("Object_" + uuid);
        dialogEditorWindow.setUUID(uuid);
        dialogEditorWindow.show();
    }

    public void deleteDialog() {
        MapObject mapObject = store.objectedMap[(int) store.selectedTileX - 1][(int) store.selectedTileY - 1];
        String uuid = mapObject.getUUID();
        mapObject.isDialogBind = false;
        Gdx.app.log("Debug", "Удалить взаимодействие для " + uuid);
    }

    // ── Динамические обработчики ──────────────────────────────────────────────

    // Открывает редактор диалога для конкретного NPC
    public void openNpcInteraction(String uuid) {
        dialogEditorWindow.setRoot("NPC_" + uuid);
        dialogEditorWindow.setUUID(uuid);
        dialogEditorWindow.show();
    }

    // Открывает окно перенаправления для конкретного здания
    public void openBuildingInteraction(String uuid) {
        String name = store.buildingNames.containsKey(uuid)
            ? store.buildingNames.get(uuid).toString() : "Объект";
        mapRedirectWindow.configure(name, uuid);
        mapRedirectWindow.show();
    }
}
