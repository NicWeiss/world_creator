package com.nicweiss.editor.components;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.components.windows.DialogEditorWindow;
import com.nicweiss.editor.components.windows.MapContextMenuWindow;
import com.nicweiss.editor.components.windows.TileSelectorWindow;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.objects.TextureObject;
import com.nicweiss.editor.utils.ArrayUtils;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.FileManager;
import com.nicweiss.editor.utils.Light;

public class UserInterface {
    FileManager fileManager;
    BOHelper bo_helper;
    public TileSelectorWindow tileSelectorWindow;
    public MapContextMenuWindow mapContextMenuWindow;
    public DialogEditorWindow dialogEditorWindow;

    Texture openTexture, saveTexture, white;

    public static Store store;
    BaseObject[] ui;
    BaseObject buttonBG;
    TextureObject[] tileTextures;
    Light lightClass;
    int[] lightObjectIds;

    int menuItemSize = 40, menuItemSpace = 50;

    public UserInterface(TextureObject[] tileTextures, Light lightClass, int[] lightObjectIds) {
        this.lightObjectIds = lightObjectIds;
        this.tileTextures = tileTextures;
        this.lightClass = lightClass;
        fileManager = new FileManager();
        bo_helper = new BOHelper();

        dialogEditorWindow = new DialogEditorWindow();

        openTexture = new Texture("open.png");
        saveTexture = new Texture("save.png");
        white = new Texture("white.png");

        tileSelectorWindow = new TileSelectorWindow(lightObjectIds);
        mapContextMenuWindow = new MapContextMenuWindow(dialogEditorWindow);
    }

    public void build() throws Exception {
        tileSelectorWindow.buildWindow(tileTextures);
        mapContextMenuWindow.buildWindow();
        dialogEditorWindow.buildWindow();

        ui = new BaseObject[2];

//        Open button
        ui[0] = bo_helper.constructObject(
                openTexture, 10, (int) (store.uiHeightOriginal - menuItemSize - 10), menuItemSize,
                menuItemSize, "open", 0
        );

//        Save button
        ui[1] = bo_helper.constructObject(
                saveTexture, menuItemSpace + 10, (int) (store.uiHeightOriginal - menuItemSize - 10),
                menuItemSize, menuItemSize, "save", 0
        );

        buttonBG = bo_helper.constructObject(
                white, 100, 150, 1, 1, "buttonBG", 0
        );
    }

    public void render(SpriteBatch uiBatch) {
        tileSelectorWindow.render(uiBatch);
        mapContextMenuWindow.render(uiBatch);
        dialogEditorWindow.render(uiBatch);

        for (BaseObject baseObject : ui) {
            if (baseObject.isTouched){
                bo_helper.draw(
                        uiBatch,buttonBG,
                        (int) baseObject.getX() - 5, (int) (store.uiHeightOriginal - menuItemSize - 15),
                        (int)baseObject.getWidth() + 10, (int)baseObject.getHeight() + 10
                );
            }
            bo_helper.draw(uiBatch, baseObject, (int) baseObject.getX(), (int) (store.uiHeightOriginal - menuItemSize - 10));
        }
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp, int button){
        if (dialogEditorWindow.checkTouch(isDragged, isTouchUp)){
            return true;
        }

        if (!tileSelectorWindow.isShowWindow && mapContextMenuWindow.checkTouch(isDragged, isTouchUp, button)){
            return true;
        }

        if (tileSelectorWindow.checkTouch(isDragged, isTouchUp)){
            return true;
        }

        if (!isTouchUp && !isDragged) {
//            Обработка событй в объектах интерфейса
            for (BaseObject baseObject : ui) {
                if (baseObject.isTouched) {
                    if (baseObject.getObjectId().equals("open")) {
                        openMap();
                        return true;
                    }
                    if (baseObject.getObjectId().equals("save")) {
                        saveMap();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean checkKey(int keyCode){
        if (mapContextMenuWindow.checkKey(keyCode)){
            return true;
        }

        if (tileSelectorWindow.checkKey(keyCode)){
            return true;
        }

        if (dialogEditorWindow.checkKey(keyCode)){
            return true;
        }

        return false;
    }

    public boolean keyTyped(char character){
        if (dialogEditorWindow.keyTyped(character)) {
            return true;
        }

        return false;
    }

    public void onMouseMoved(){
        mapContextMenuWindow.onMouseMoved();
    }

    private void openMap(){
        int textureId = 0;
        String uuid, type;
        String[][][] map =  fileManager.openMap();;

        if (map.length == 0) {
            return;
        }

        store.mapHeight = fileManager.mapHeight;
        store.mapWidth = fileManager.mapWidth;

        store.objectedMap = new MapObject[store.mapHeight][store.mapWidth];

        lightClass.clearAll();
        for (int i = 0; i < store.mapWidth; i++){
            for (int j = 0; j < store.mapHeight; j++){
                uuid = map[i][j][0];
                textureId = Integer.parseInt(map[i][j][1]);
                type = map[i][j][2];
                MapObject tmp = new MapObject();
                tmp.setTexture(tileTextures[textureId].texture);
                tmp.setObjectHeight(tileTextures[textureId].high);
                tmp.setTextureId(textureId);
                tmp.xPositionOnMap = i+1;
                tmp.yPositionOnMap = j+1;
                tmp.setUUID(uuid);

                if (type == "dialog") {
                    tmp.isDialogBind = true;
                }

                store.objectedMap[i][j] = tmp;

                if (ArrayUtils.checkIntInArray(textureId, lightObjectIds)){
                    lightClass.addPoint(i, j);
                }
            }
        }
        lightClass.recalcOnMap();
    }

    private void saveMap(){
        fileManager.saveMap(store.objectedMap, store.mapWidth, store.mapHeight);
    }

    public boolean getMouseMoveBlockStatus() {
        if (mapContextMenuWindow.isShow || tileSelectorWindow.isShowWindow || dialogEditorWindow.isShowWindow) {
            return true;
        }

        return false;
    }
}
