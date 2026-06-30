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

public class NpcEditorWindow extends Window implements CallBack {
    public static Store store;

    TextInputWindow tiw = new TextInputWindow();
    ActionConfirnWindow acw = new ActionConfirnWindow();
    AssetPickerWindow assetPicker = new AssetPickerWindow();
    DialogEditorWindow dialogEditorWindow;

    Texture buttonBG, buttonBGHover, plusIcon, npcIcon, trashIcon, nameIcon, coordIcon;
    ButtonCommon[] items, npcDetails;
    ButtonCommon button;

    private String npcSearchQuery = "";

    public NpcEditorWindow(DialogEditorWindow dialogEditorWindow) {
        super();
        this.dialogEditorWindow = dialogEditorWindow;
        windowName = "Сущности (NPC)";

        buttonBG      = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");
        plusIcon      = new Texture("icons/quest_window/plus.png");
        npcIcon       = new Texture("icons/quest_window/quest_option.png");
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
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }

    @Override
    public void onShow() {
        prepareNpcListView();
    }

    // ── Список (левая панель) ──────────────────────────────────────────────────

    public void prepareNpcListView() {
        items = new ButtonCommon[1000];
        int i = 0;

        // Поиск (первым в списке)
        String searchLabel = npcSearchQuery.isEmpty() ? "Поиск по имени..." : "Поиск: " + npcSearchQuery;
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(nameIcon);
        button.setText(font, searchLabel);
        button.registerCallBack(this, "openNpcSearch");
        items[i++] = button;

        // Добавить NPC
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить NPC");
        button.registerCallBack(this, "addNpcCallback");
        items[i++] = button;

        // Список из Store.creations
        for (int idx = 0; idx <= store.creationCount; idx++) {
            Creation cr = store.creations[idx];
            if (cr == null) continue;

            String uuid = cr.getUUID();
            String name = getNpcName(uuid);

            if (!npcSearchQuery.isEmpty() &&
                !name.toLowerCase().contains(npcSearchQuery.toLowerCase())) {
                continue;
            }

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(npcIcon);
            button.setText(font, name);
            button.registerCallBack(this, "selectNpcCallback", new String[]{uuid});
            items[i++] = button;
        }

        items = Arrays.copyOfRange(items, 0, i);
    }

    // ── Поиск ─────────────────────────────────────────────────────────────────

    public void openNpcSearch() {
        if (tiw.isShowWindow || !isWindowActive) return;
        tiw.registerCallBack(this, "applyNpcSearch", new String[]{""});
        tiw.setText(npcSearchQuery);
        tiw.show();
    }

    public void applyNpcSearch(String query) {
        npcSearchQuery = query;
        prepareNpcListView();
    }

    // ── Создание и удаление ────────────────────────────────────────────────────

    public void addNpcCallback() {
        int[] center = getCenterTile();
        String uuid = Uuid.generate();

        float[] isoPos = Transform.cartesianToIsometric(
            (int)(center[0] * store.tileSizeWidth),
            (int)(center[1] * store.tileSizeHeight)
        );

        store.creationCount++;
        Creation cr = new Creation();
        cr.setUUID(uuid);
        cr.setTexture(new com.badlogic.gdx.graphics.Texture("creations/creation.png"));
        cr.setPosition(isoPos[0], isoPos[1]);
        cr.setCell(center[0], center[1]);
        store.creations[store.creationCount] = cr;
        store.npcs.put(uuid, "Новый NPC");

        prepareNpcListView();
        prepareNpcInteractionView(uuid);
    }

    public void selectNpcCallback(String uuid) {
        prepareNpcInteractionView(uuid);
    }

    public void deleteNpcConfirm(String uuid) {
        acw.setText("Удалить NPC ?");
        acw.registerCallBack(this, "deleteNpc", new String[]{uuid});
        acw.show();
    }

    public void deleteNpc(String uuid) {
        // Удаляем из массива creations, сдвигаем оставшиеся
        for (int i = 0; i <= store.creationCount; i++) {
            if (store.creations[i] != null && uuid.equals(store.creations[i].getUUID())) {
                for (int j = i; j < store.creationCount; j++) {
                    store.creations[j] = store.creations[j + 1];
                }
                store.creations[store.creationCount] = null;
                store.creationCount--;
                break;
            }
        }
        store.npcs.remove(uuid);
        npcDetails = new ButtonCommon[0];
        prepareNpcListView();
    }

    // ── Редактирование полей ───────────────────────────────────────────────────

    public void editNpcName(String uuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        tiw.registerCallBack(this, "editNpcNameDone", new String[]{uuid, ""});
        tiw.setText(getNpcName(uuid));
        tiw.show();
    }

    public void editNpcNameDone(String uuid, String value) {
        store.npcs.put(uuid, value.isEmpty() ? "NPC" : value);
        prepareNpcListView();
        prepareNpcInteractionView(uuid);
    }

    public void editNpcCoordX(String uuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        Creation cr = findCreation(uuid);
        if (cr == null) return;
        tiw.registerCallBack(this, "editNpcCoordXDone", new String[]{uuid, ""});
        tiw.setText(String.valueOf(cr.mapCellX));
        tiw.show();
    }

    public void editNpcCoordXDone(String uuid, String value) {
        value = value.replaceAll("[^0-9]", "");
        if (value.isEmpty()) value = "0";
        int newX = Math.max(0, Math.min(Integer.parseInt(value), store.mapWidth - 1));
        updateCreationCell(uuid, newX, -1);
        prepareNpcInteractionView(uuid);
    }

    public void editNpcCoordY(String uuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        Creation cr = findCreation(uuid);
        if (cr == null) return;
        tiw.registerCallBack(this, "editNpcCoordYDone", new String[]{uuid, ""});
        tiw.setText(String.valueOf(cr.mapCellY));
        tiw.show();
    }

    public void editNpcCoordYDone(String uuid, String value) {
        value = value.replaceAll("[^0-9]", "");
        if (value.isEmpty()) value = "0";
        int newY = Math.max(0, Math.min(Integer.parseInt(value), store.mapHeight - 1));
        updateCreationCell(uuid, -1, newY);
        prepareNpcInteractionView(uuid);
    }

    // Перемещает NPC к текущей выделенной клетке
    public void placeOnMap(String uuid) {
        updateCreationCell(uuid, (int) store.selectedTileX, (int) store.selectedTileY);
        prepareNpcInteractionView(uuid);
    }

    public void openAssetPicker(String uuid) {
        if (!isWindowActive) return;
        assetPicker.populate("creations", this, "setCreationTexture", uuid);
        assetPicker.setX(x + width + 10);
        assetPicker.setY(y);
        assetPicker.show();
    }

    public void setCreationTexture(String uuid, String filePath) {
        Creation cr = findCreation(uuid);
        if (cr != null) cr.setTexture(new com.badlogic.gdx.graphics.Texture(
            com.badlogic.gdx.Gdx.files.absolute(filePath)
        ));
        prepareNpcInteractionView(uuid);
    }

    // Открывает редактор диалога для этого NPC
    public void openInteractionEditor(String uuid) {
        dialogEditorWindow.setRoot("NPC_" + uuid);
        dialogEditorWindow.setUUID(uuid);
        dialogEditorWindow.show();
    }

    // ── Редактор взаимодействия (правая панель) ───────────────────────────────

    public void prepareNpcInteractionView(String uuid) {
        npcDetails = new ButtonCommon[1000];
        int i = 0;
        Creation cr = findCreation(uuid);

        // ── СУЩНОСТЬ ──
        npcDetails[i++] = makeSectionHeader("── СУЩНОСТЬ ──");

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(nameIcon);
        button.setText(font, "Имя: " + getNpcName(uuid));
        button.registerCallBack(this, "editNpcName", new String[]{uuid});
        npcDetails[i++] = button;

        if (cr != null) {
            // Выбор тайла
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            if (cr.getTexture() != null) button.setIcon(cr.getTexture());
            button.setText(font, "Выбрать тайл");
            button.registerCallBack(this, "openAssetPicker", new String[]{uuid});
            npcDetails[i++] = button;

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(coordIcon);
            button.setText(font, "Координата X: " + cr.mapCellX);
            button.registerCallBack(this, "editNpcCoordX", new String[]{uuid});
            npcDetails[i++] = button;

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(coordIcon);
            button.setText(font, "Координата Y: " + cr.mapCellY);
            button.registerCallBack(this, "editNpcCoordY", new String[]{uuid});
            npcDetails[i++] = button;

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(plusIcon);
            button.setText(font, "Установить на карте (с выделенной клетки)");
            button.registerCallBack(this, "placeOnMap", new String[]{uuid});
            npcDetails[i++] = button;
        }

        // ── ВЗАИМОДЕЙСТВИЕ ──
        npcDetails[i++] = makeSectionHeader("── ВЗАИМОДЕЙСТВИЕ ──");

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(npcIcon);
        button.setText(font, "Редактировать диалог");
        button.registerCallBack(this, "openInteractionEditor", new String[]{uuid});
        npcDetails[i++] = button;

        // Заглушка перехода на карту
        button = new ButtonCommon();
        button.setBackgrounds(buttonBGHover, buttonBGHover);
        button.setText(font, "Переход на карту: (не реализовано)");
        npcDetails[i++] = button;

        // Удалить
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить NPC");
        button.registerCallBack(this, "deleteNpcConfirm", new String[]{uuid});
        npcDetails[i++] = button;

        npcDetails = Arrays.copyOfRange(npcDetails, 0, i);
    }

    // ── Вспомогательные методы ────────────────────────────────────────────────

    private Creation findCreation(String uuid) {
        if (uuid == null) return null;
        for (int i = 0; i <= store.creationCount; i++) {
            Creation cr = store.creations[i];
            if (cr != null && uuid.equals(cr.getUUID())) return cr;
        }
        return null;
    }

    private String getNpcName(String uuid) {
        Object name = store.npcs.get(uuid);
        return name != null ? name.toString() : "NPC";
    }

    private void updateCreationCell(String uuid, int newX, int newY) {
        Creation cr = findCreation(uuid);
        if (cr == null) return;
        int finalX = newX >= 0 ? newX : cr.mapCellX;
        int finalY = newY >= 0 ? newY : cr.mapCellY;
        cr.setCell(finalX, finalY);
        float[] isoPos = Transform.cartesianToIsometric(
            (int)(finalX * store.tileSizeWidth),
            (int)(finalY * store.tileSizeHeight)
        );
        cr.setPosition(isoPos[0], isoPos[1]);
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
            rightSection.renderItemsList(batch, npcDetails, false);
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
    }

    @Override
    public boolean checkTouch(boolean isDragged, boolean isTouchUp) {
        if (!isShowWindow) return false;

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
        return super.checkTouch(isDragged, isTouchUp);
    }

    @Override
    public boolean checkKey(int keyCode) {
        // Пикер ассетов модален (см. checkTouch выше) — глушим скролл/клавиши карты, пока открыт.
        if (assetPicker.isShowWindow) {
            assetPicker.checkKey(keyCode);
            return true;
        }
        if (tiw.checkKey(keyCode)) return true;
        return super.checkKey(keyCode);
    }

    public boolean keyTyped(char character) {
        return tiw.keyTyped(character);
    }
}
