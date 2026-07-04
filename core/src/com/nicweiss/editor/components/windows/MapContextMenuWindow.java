package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.Gdx;
import com.nicweiss.editor.Generic.ContextMenuWindow;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Контекстное меню клика по карте (правая кнопка). Часть про NPC/объекты работает по клетке:
 *  - клетка свободна (нет ни NPC, ни объекта) → кнопки "Добавить NPC"/"Добавить объект";
 *  - на клетке есть NPC и/или объект → добавление скрыто, только "Редактировать <имя>" на каждого —
 *    открывает NpcEditorWindow/ObjectEditorWindow с этим NPC/объектом сразу в правой панели.
 * Взаимодействие тайла (диалог) — отдельная, не связанная с NPC/объектами часть, не тронута.
 */
public class MapContextMenuWindow extends ContextMenuWindow {
    public static Store store;
    public DialogEditorWindow dialogEditorWindow;
    public NpcEditorWindow npcEditorWindow;
    public ObjectEditorWindow objectEditorWindow;

    public MapContextMenuWindow(DialogEditorWindow dialogEditorWindow,
                                NpcEditorWindow npcEditorWindow,
                                ObjectEditorWindow objectEditorWindow) {
        this.dialogEditorWindow = dialogEditorWindow;
        this.npcEditorWindow = npcEditorWindow;
        this.objectEditorWindow = objectEditorWindow;
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

        List<Creation> npcsHere = new ArrayList<>();
        for (int i = 0; i <= store.creationCount; i++) {
            Creation cr = store.creations[i];
            if (cr != null && cr.mapCellX == tx && cr.mapCellY == ty) npcsHere.add(cr);
        }
        List<Creation> buildingsHere = new ArrayList<>();
        for (int i = 0; i <= store.buildingCount; i++) {
            Creation b = store.buildings[i];
            if (b != null && b.mapCellX == tx && b.mapCellY == ty) buildingsHere.add(b);
        }

        if (npcsHere.isEmpty() && buildingsHere.isEmpty()) {
            // Клетка свободна — можно добавить.
            list.add(createOptionButton(MapContextMenuWindow.class, "addNpcHere",    "Добавить NPC"));
            list.add(createOptionButton(MapContextMenuWindow.class, "addObjectHere", "Добавить объект"));
        } else {
            // Клетка занята — добавление скрыто, только редактирование того, что уже есть.
            for (Creation cr : npcsHere) {
                String uuid = cr.getUUID();
                String name = store.npcs.containsKey(uuid) ? store.npcs.get(uuid).toString() : "NPC";
                list.add(createDynamicButton("editNpcHere", "Редактировать NPC: " + name, uuid));
            }
            for (Creation b : buildingsHere) {
                String uuid = b.getUUID();
                String name = store.buildingNames.containsKey(uuid)
                    ? store.buildingNames.get(uuid).toString() : "Объект";
                list.add(createDynamicButton("editBuildingHere", "Редактировать объект: " + name, uuid));
            }
        }

        list.add(createOptionButton(MapContextMenuWindow.class, "openTileDialog",  "Ред. взаимодействие тайла"));
        list.add(createOptionButton(MapContextMenuWindow.class, "deleteDialog",    "Удалить взаимодействие тайла"));

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

    // ── NPC/объекты: добавить (клетка свободна) ────────────────────────────────

    public void addNpcHere() {
        npcEditorWindow.addNpcAt((int) store.selectedTileX, (int) store.selectedTileY);
    }

    public void addObjectHere() {
        objectEditorWindow.addObjectAt((int) store.selectedTileX, (int) store.selectedTileY);
    }

    // ── NPC/объекты: редактировать (клетка занята) ─────────────────────────────

    public void editNpcHere(String uuid) {
        npcEditorWindow.show();
        npcEditorWindow.selectNpcCallback(uuid);
    }

    public void editBuildingHere(String uuid) {
        objectEditorWindow.show();
        objectEditorWindow.selectObjectCallback(uuid);
    }

    // ── Взаимодействие тайла (не связано с NPC/объектами) ──────────────────────

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
}
