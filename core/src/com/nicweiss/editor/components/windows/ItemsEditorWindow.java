package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.Uuid;

import java.util.Arrays;
import java.util.LinkedHashMap;

public class ItemsEditorWindow extends Window implements CallBack {
    public static Store store;

    TextInputWindow tiw = new TextInputWindow();
    ActionConfirnWindow acw = new ActionConfirnWindow();
    LinkedHashMap itemTemplates;

    Texture buttonBG, buttonBGHover, plusIcon, itemIcon, trashIcon, nameIcon, descriptionIcon, statIcon;
    ButtonCommon[] items, itemDetails;
    ButtonCommon button;

    // Хранит имя редактируемого поля стата между открытием TextInputWindow и коллбэком
    private String pendingStatField;

    public ItemsEditorWindow() {
        super();
        itemTemplates = store.itemTemplates;
        windowName = "Предметы";

        buttonBG = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");
        plusIcon = new Texture("icons/quest_window/plus.png");
        itemIcon = new Texture("icons/quest_window/quest.png");
        trashIcon = new Texture("icons/quest_window/trash.png");
        nameIcon = new Texture("icons/quest_window/name.png");
        descriptionIcon = new Texture("icons/quest_window/description.png");
        statIcon = new Texture("icons/quest_window/experience.png");
    }

    public void buildWindow() {
        setDualSectionMode(true);
        super.buildWindow();
        tiw.buildWindow();
        acw.buildWindow();
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }

    @Override
    public void onShow() {
        prepareItemsView();
    }

    // ---- Шаблоны предметов ----

    public void addItemCallback() {
        LinkedHashMap template = new LinkedHashMap();
        String uuid = Uuid.generate();
        template.put("__uuid__", uuid);
        template.put("__name__", "Новый предмет #" + uuid);
        template.put("__stats__", new LinkedHashMap());
        itemTemplates.put(uuid, template);
        prepareItemsView();
        prepareSelectedItemView(uuid);
    }

    public void selectItemCallback(String uuid) {
        prepareSelectedItemView(uuid);
    }

    public void deleteItemConfirm(String uuid) {
        acw.setText("Удалить предмет ?");
        acw.registerCallBack(this, "deleteItem", new String[]{uuid});
        acw.show();
    }

    public void deleteItem(String uuid) {
        itemTemplates.remove(uuid);
        itemDetails = new ButtonCommon[0];
        prepareItemsView();
    }

    // ---- Редактирование полей предмета ----

    public void editItemTextField(String uuid, String fieldName) {
        if (tiw.isShowWindow || !isWindowActive) return;
        LinkedHashMap obj = (LinkedHashMap) itemTemplates.get(uuid);
        Object raw = obj.get(fieldName);
        tiw.registerCallBack(this, "textEditDoneCallback", new String[]{uuid, fieldName, ""});
        tiw.setText(raw != null ? raw.toString() : "");
        tiw.show();
    }

    public void textEditDoneCallback(String uuid, String fieldName, String value) {
        LinkedHashMap obj = (LinkedHashMap) itemTemplates.get(uuid);
        obj.put(fieldName, value);
        prepareItemsView();
        prepareSelectedItemView(uuid);
    }

    // ---- Характеристики (статы) ----

    public void addStatCallback(String itemUuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        tiw.registerCallBack(this, "addStatNameDone", new String[]{itemUuid, ""});
        tiw.setText("");
        tiw.show();
    }

    // params[1] = имя стата, введённое пользователем
    public void addStatNameDone(String itemUuid, String statName) {
        if (statName.isEmpty()) return;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");

        String statUuid = "stat_" + Uuid.generate();
        LinkedHashMap stat = new LinkedHashMap();
        stat.put("__uuid__", statUuid);
        stat.put("__name__", statName);
        stat.put("__value__", 0);
        stat.put("__min__", 0);
        stat.put("__max__", 0);
        stats.put(statUuid, stat);

        prepareStatView(itemUuid, statUuid);
    }

    // fieldName передаётся через кнопку, сохраняется в pendingStatField,
    // затем используется в editStatFieldDone — обход ограничения TextInputWindow (params[2] = текст)
    public void editStatField(String itemUuid, String statUuid, String fieldName) {
        if (tiw.isShowWindow || !isWindowActive) return;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        LinkedHashMap stat = (LinkedHashMap) stats.get(statUuid);

        pendingStatField = fieldName;
        tiw.registerCallBack(this, "editStatFieldDone", new String[]{itemUuid, statUuid, ""});
        tiw.setText(stat.get(fieldName).toString());
        tiw.show();
    }

    public void editStatFieldDone(String itemUuid, String statUuid, String value) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        LinkedHashMap stat = (LinkedHashMap) stats.get(statUuid);

        if (pendingStatField.equals("__name__")) {
            stat.put(pendingStatField, value);
        } else {
            value = value.replaceAll("[^0-9\\-]", "");
            if (value.isEmpty()) value = "0";
            stat.put(pendingStatField, Integer.parseInt(value));
        }
        prepareStatView(itemUuid, statUuid);
    }

    public void deleteStatConfirm(String itemUuid, String statUuid) {
        acw.setText("Удалить характеристику ?");
        acw.registerCallBack(this, "deleteStat", new String[]{itemUuid, statUuid});
        acw.show();
    }

    public void deleteStat(String itemUuid, String statUuid) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        stats.remove(statUuid);
        prepareSelectedItemView(itemUuid);
    }

    // ---- Построение UI ----

    public void prepareItemsView() {
        items = new ButtonCommon[1000];
        int i = 0;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить предмет");
        button.registerCallBack(this, "addItemCallback");
        items[i++] = button;

        for (Object keyEl : itemTemplates.keySet()) {
            String uuid = keyEl.toString();
            LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(itemIcon);
            button.setText(font, (String) template.get("__name__"));
            button.registerCallBack(this, "selectItemCallback", new String[]{uuid});
            items[i++] = button;
        }

        items = Arrays.copyOfRange(items, 0, i);
    }

    public void prepareSelectedItemView(String uuid) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        boolean hasDescription = template.containsKey("__description__");

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(nameIcon);
        button.setText(font, "Название: " + template.get("__name__"));
        button.registerCallBack(this, "editItemTextField", new String[]{uuid, "__name__"});
        itemDetails[i++] = button;

        if (hasDescription) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(descriptionIcon);
            button.setText(font, "Описание: " + template.get("__description__"));
            button.registerCallBack(this, "editItemTextField", new String[]{uuid, "__description__"});
            itemDetails[i++] = button;
        } else {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(plusIcon);
            button.setText(font, "Добавить описание");
            button.registerCallBack(this, "editItemTextField", new String[]{uuid, "__description__"});
            itemDetails[i++] = button;
        }

        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        for (Object statKey : stats.keySet()) {
            String statUuid = statKey.toString();
            LinkedHashMap stat = (LinkedHashMap) stats.get(statUuid);
            int val = (int) stat.get("__value__");
            int min = (int) stat.get("__min__");
            int max = (int) stat.get("__max__");
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(statIcon);
            button.setText(font, stat.get("__name__") + ": " + val + "  [" + min + " .. " + max + "]");
            button.registerCallBack(this, "prepareStatView", new String[]{uuid, statUuid});
            itemDetails[i++] = button;
        }

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить характеристику");
        button.registerCallBack(this, "addStatCallback", new String[]{uuid});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить предмет");
        button.registerCallBack(this, "deleteItemConfirm", new String[]{uuid});
        itemDetails[i++] = button;

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    public void prepareStatView(String itemUuid, String statUuid) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        LinkedHashMap stat = (LinkedHashMap) stats.get(statUuid);

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedItemView", new String[]{itemUuid});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(nameIcon);
        button.setText(font, "Название: " + stat.get("__name__"));
        button.registerCallBack(this, "editStatField", new String[]{itemUuid, statUuid, "__name__"});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Базовое значение: " + stat.get("__value__"));
        button.registerCallBack(this, "editStatField", new String[]{itemUuid, statUuid, "__value__"});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Мин. модификатор: " + stat.get("__min__"));
        button.registerCallBack(this, "editStatField", new String[]{itemUuid, statUuid, "__min__"});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Макс. модификатор: " + stat.get("__max__"));
        button.registerCallBack(this, "editStatField", new String[]{itemUuid, statUuid, "__max__"});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить характеристику");
        button.registerCallBack(this, "deleteStatConfirm", new String[]{itemUuid, statUuid});
        itemDetails[i++] = button;

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    // ---- Рендер и ввод ----

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);

        if (isShowWindow) {
            renderItemsList(batch, items, false);
            rightSection.renderItemsList(batch, itemDetails, false);
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
    }

    @Override
    public boolean checkTouch(boolean isDragged, boolean isTouchUp) {
        if (!isShowWindow) return false;
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
