package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.utils.Transform;
import com.nicweiss.editor.utils.Uuid;

import java.util.Arrays;

public class ObjectEditorWindow extends Window implements CallBack {
    public static Store store;

    TextInputWindow tiw = new TextInputWindow();
    ActionConfirnWindow acw = new ActionConfirnWindow();
    AssetPickerWindow assetPicker = new AssetPickerWindow();
    MapRedirectWindow mapRedirectWindow = new MapRedirectWindow();
    DialogEditorWindow dialogEditorWindow; // kept for potential future use

    Texture buttonBG, buttonBGHover, plusIcon, objectIcon, trashIcon, nameIcon, coordIcon;
    ButtonCommon[] items, objectDetails;
    ButtonCommon button;

    private String objectSearchQuery = "";

    public ObjectEditorWindow(DialogEditorWindow dialogEditorWindow) {
        super();
        this.dialogEditorWindow = dialogEditorWindow;
        windowName = "Недвижимость";

        buttonBG      = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");
        plusIcon      = new Texture("icons/quest_window/plus.png");
        objectIcon    = new Texture("icons/quest_window/quest_option.png");
        trashIcon     = new Texture("icons/quest_window/trash.png");
        nameIcon      = new Texture("icons/quest_window/name.png");
        coordIcon     = new Texture("icons/quest_window/experience.png");
    }

    public void buildWindow() {
        setDualSectionMode(true);
        super.buildWindow();
        tiw.buildWindow();
        acw.buildWindow();
        assetPicker.buildWindow();
        mapRedirectWindow.buildWindow();
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }

    @Override
    public void onShow() {
        prepareObjectListView();
    }

    // ── Список (левая панель) ──────────────────────────────────────────────────

    public void prepareObjectListView() {
        items = new ButtonCommon[1000];
        int i = 0;

        String searchLabel = objectSearchQuery.isEmpty() ? "Поиск по имени..." : "Поиск: " + objectSearchQuery;
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(nameIcon);
        button.setText(font, searchLabel);
        button.registerCallBack(this, "openObjectSearch");
        items[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить объект");
        button.registerCallBack(this, "addObjectCallback");
        items[i++] = button;

        for (int idx = 0; idx <= store.buildingCount; idx++) {
            Creation b = store.buildings[idx];
            if (b == null) continue;
            String uuid = b.getUUID();
            String name = getObjectName(uuid);
            if (!objectSearchQuery.isEmpty() &&
                !name.toLowerCase().contains(objectSearchQuery.toLowerCase())) continue;

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(objectIcon);
            button.setText(font, name);
            button.registerCallBack(this, "selectObjectCallback", new String[]{uuid});
            items[i++] = button;
        }

        items = Arrays.copyOfRange(items, 0, i);
    }

    // ── Поиск ─────────────────────────────────────────────────────────────────

    public void openObjectSearch() {
        if (tiw.isShowWindow || !isWindowActive) return;
        tiw.registerCallBack(this, "applyObjectSearch", new String[]{""});
        tiw.setText(objectSearchQuery);
        tiw.show();
    }

    public void applyObjectSearch(String query) {
        objectSearchQuery = query;
        prepareObjectListView();
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public void addObjectCallback() {
        int[] center = getCenterTile();
        String uuid = Uuid.generate();

        float[] isoPos = Transform.cartesianToIsometric(
            (int)(center[0] * store.tileSizeWidth),
            (int)(center[1] * store.tileSizeHeight)
        );

        store.buildingCount++;
        Creation b = new Creation();
        b.setUUID(uuid);
        b.setTexture(new Texture("objects/default_object.png"));
        b.setPosition(isoPos[0], isoPos[1]);
        b.setCell(center[0], center[1]);
        store.buildings[store.buildingCount] = b;
        store.buildingNames.put(uuid, "Новый объект");

        prepareObjectListView();
        prepareObjectInteractionView(uuid);
    }

    public void selectObjectCallback(String uuid) {
        prepareObjectInteractionView(uuid);
    }

    public void deleteObjectConfirm(String uuid) {
        acw.setText("Удалить объект ?");
        acw.registerCallBack(this, "deleteObject", new String[]{uuid});
        acw.show();
    }

    public void deleteObject(String uuid) {
        for (int i = 0; i <= store.buildingCount; i++) {
            if (store.buildings[i] != null && uuid.equals(store.buildings[i].getUUID())) {
                for (int j = i; j < store.buildingCount; j++) {
                    store.buildings[j] = store.buildings[j + 1];
                }
                store.buildings[store.buildingCount] = null;
                store.buildingCount--;
                break;
            }
        }
        store.buildingNames.remove(uuid);
        objectDetails = new ButtonCommon[0];
        prepareObjectListView();
    }

    // ── Редактирование полей ───────────────────────────────────────────────────

    public void editObjectName(String uuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        tiw.registerCallBack(this, "editObjectNameDone", new String[]{uuid, ""});
        tiw.setText(getObjectName(uuid));
        tiw.show();
    }

    public void editObjectNameDone(String uuid, String value) {
        store.buildingNames.put(uuid, value.isEmpty() ? "Объект" : value);
        prepareObjectListView();
        prepareObjectInteractionView(uuid);
    }

    public void editObjectCoordX(String uuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        Creation b = findBuilding(uuid);
        if (b == null) return;
        tiw.registerCallBack(this, "editObjectCoordXDone", new String[]{uuid, ""});
        tiw.setText(String.valueOf(b.mapCellX));
        tiw.show();
    }

    public void editObjectCoordXDone(String uuid, String value) {
        value = value.replaceAll("[^0-9]", "");
        if (value.isEmpty()) value = "0";
        int newX = Math.max(0, Math.min(Integer.parseInt(value), store.mapWidth - 1));
        updateBuildingCell(uuid, newX, -1);
        prepareObjectInteractionView(uuid);
    }

    public void editObjectCoordY(String uuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        Creation b = findBuilding(uuid);
        if (b == null) return;
        tiw.registerCallBack(this, "editObjectCoordYDone", new String[]{uuid, ""});
        tiw.setText(String.valueOf(b.mapCellY));
        tiw.show();
    }

    public void editObjectCoordYDone(String uuid, String value) {
        value = value.replaceAll("[^0-9]", "");
        if (value.isEmpty()) value = "0";
        int newY = Math.max(0, Math.min(Integer.parseInt(value), store.mapHeight - 1));
        updateBuildingCell(uuid, -1, newY);
        prepareObjectInteractionView(uuid);
    }

    public void placeOnMap(String uuid) {
        updateBuildingCell(uuid, (int) store.selectedTileX, (int) store.selectedTileY);
        prepareObjectInteractionView(uuid);
    }

    // ── Выбор тайла ───────────────────────────────────────────────────────────

    public void openAssetPicker(String uuid) {
        if (!isWindowActive) return;
        assetPicker.populate("objects", this, "setBuildingTexture", uuid);
        assetPicker.setX(x + width + 10);
        assetPicker.setY(y);
        assetPicker.show();
    }

    public void setBuildingTexture(String uuid, String filePath) {
        Creation b = findBuilding(uuid);
        if (b != null) b.setTexture(new Texture(com.badlogic.gdx.Gdx.files.absolute(filePath)));
        prepareObjectInteractionView(uuid);
    }

    // ── Редактор взаимодействия: открывает окно перенаправления (не диалог) ──

    public void openInteractionEditor(String uuid) {
        String name = getObjectName(uuid);
        mapRedirectWindow.configure(name, uuid);
        mapRedirectWindow.setX(x + width + 10);
        mapRedirectWindow.setY(y);
        mapRedirectWindow.show();
    }

    public void prepareObjectInteractionView(String uuid) {
        objectDetails = new ButtonCommon[1000];
        int i = 0;
        Creation b = findBuilding(uuid);

        // ── ОБЪЕКТ ──
        objectDetails[i++] = makeSectionHeader("── ОБЪЕКТ ──");

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(nameIcon);
        button.setText(font, "Имя: " + getObjectName(uuid));
        button.registerCallBack(this, "editObjectName", new String[]{uuid});
        objectDetails[i++] = button;

        if (b != null) {
            // Выбрать тайл
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            if (b.getTexture() != null) button.setIcon(b.getTexture());
            button.setText(font, "Выбрать тайл");
            button.registerCallBack(this, "openAssetPicker", new String[]{uuid});
            objectDetails[i++] = button;

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(coordIcon);
            button.setText(font, "Координата X: " + b.mapCellX);
            button.registerCallBack(this, "editObjectCoordX", new String[]{uuid});
            objectDetails[i++] = button;

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(coordIcon);
            button.setText(font, "Координата Y: " + b.mapCellY);
            button.registerCallBack(this, "editObjectCoordY", new String[]{uuid});
            objectDetails[i++] = button;

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(plusIcon);
            button.setText(font, "Установить на карте (с выделенной клетки)");
            button.registerCallBack(this, "placeOnMap", new String[]{uuid});
            objectDetails[i++] = button;
        }

        // ── ВЗАИМОДЕЙСТВИЕ ──
        objectDetails[i++] = makeSectionHeader("── ВЗАИМОДЕЙСТВИЕ ──");

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(objectIcon);
        button.setText(font, "Редактировать переход");
        button.registerCallBack(this, "openInteractionEditor", new String[]{uuid});
        objectDetails[i++] = button;

        // Заглушка перехода на карту
        button = new ButtonCommon();
        button.setBackgrounds(buttonBGHover, buttonBGHover);
        button.setText(font, "Переход на карту: (не реализовано)");
        objectDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить объект");
        button.registerCallBack(this, "deleteObjectConfirm", new String[]{uuid});
        objectDetails[i++] = button;

        objectDetails = Arrays.copyOfRange(objectDetails, 0, i);
    }

    // ── Вспомогательные методы ────────────────────────────────────────────────

    private Creation findBuilding(String uuid) {
        if (uuid == null) return null;
        for (int i = 0; i <= store.buildingCount; i++) {
            Creation b = store.buildings[i];
            if (b != null && uuid.equals(b.getUUID())) return b;
        }
        return null;
    }

    private String getObjectName(String uuid) {
        Object name = store.buildingNames.get(uuid);
        return name != null ? name.toString() : "Объект";
    }

    private void updateBuildingCell(String uuid, int newX, int newY) {
        Creation b = findBuilding(uuid);
        if (b == null) return;
        int finalX = newX >= 0 ? newX : b.mapCellX;
        int finalY = newY >= 0 ? newY : b.mapCellY;
        b.setCell(finalX, finalY);
        float[] isoPos = Transform.cartesianToIsometric(
            (int)(finalX * store.tileSizeWidth),
            (int)(finalY * store.tileSizeHeight)
        );
        b.setPosition(isoPos[0], isoPos[1]);
    }

    private int[] getCenterTile() {
        float isoX = store.scaledWidth / 2f - store.shiftX;
        float isoY = store.scaledHeight / 2f - store.shiftY;
        float[] cart = Transform.isometricToCartesian(isoX, isoY);
        int tileX = Math.max(0, Math.min(store.mapWidth - 1, (int)(cart[0] / store.tileSizeWidth)));
        int tileY = Math.max(0, Math.min(store.mapHeight - 1, (int)(cart[1] / store.tileSizeHeight)));
        return new int[]{tileX, tileY};
    }

    private ButtonCommon makeSectionHeader(String text) {
        ButtonCommon header = new ButtonCommon();
        header.setBackgrounds(buttonBGHover, buttonBGHover);
        header.setText(font, text);
        return header;
    }

    // ── Рендер и ввод ─────────────────────────────────────────────────────────

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);

        if (isShowWindow) {
            renderItemsList(batch, items, false);
            rightSection.renderItemsList(batch, objectDetails, false);
        }

        if (tiw.isShowWindow) {
            tiw.render(batch);
            isWindowActive = false;
        }
        if (acw.isShowWindow) {
            acw.render(batch);
            isWindowActive = false;
        } else {
            isWindowActive = true;
        }

        if (assetPicker.isShowWindow) {
            assetPicker.render(batch);
            isWindowActive = false;
        }

        if (mapRedirectWindow.isShowWindow) {
            mapRedirectWindow.render(batch);
            isWindowActive = false;
        }
    }

    @Override
    public boolean checkTouch(boolean isDragged, boolean isTouchUp) {
        if (!isShowWindow) return false;

        if (mapRedirectWindow.isShowWindow) {
            mapRedirectWindow.checkTouch(isDragged, isTouchUp);
            return true;
        }

        if (assetPicker.isShowWindow) {
            if (assetPicker.isTouchInsideBounds()) {
                assetPicker.checkTouch(isDragged, isTouchUp);
            } else if (!isDragged && isTouchUp) {
                assetPicker.hide();
            }
            return true;
        }

        if (tiw.isShowWindow && tiw.checkTouch(isDragged, isTouchUp)) return true;
        if (acw.isShowWindow && acw.checkTouch(isDragged, isTouchUp)) return true;
        super.checkTouch(isDragged, isTouchUp);
        return isShowWindow;
    }

    @Override
    public boolean checkKey(int keyCode) {
        if (tiw.checkKey(keyCode)) return true;
        return super.checkKey(keyCode);
    }

    public boolean keyTyped(char character) {
        return tiw.keyTyped(character);
    }
}
