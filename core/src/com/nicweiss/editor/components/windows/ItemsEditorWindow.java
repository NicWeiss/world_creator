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

    // Хранит имя редактируемого числового поля предмета между открытием TextInputWindow и коллбэком
    private String pendingItemNumericField;

    // Ключи типов предметов — используются в __type__ и в игровой логике.
    // Названия для отображения хранятся отдельно (ITEM_TYPE_LABELS), чтобы переименование
    // в UI не ломало сохранённые данные и зависящую от типа логику.
    private static final String[] ITEM_TYPE_KEYS = {
        "weapon", "shield", "helmet", "armor", "gloves", "boots", "belt", "amulet", "artifact"
    };

    private static final LinkedHashMap<String, String> ITEM_TYPE_LABELS = new LinkedHashMap<>();
    static {
        ITEM_TYPE_LABELS.put("weapon", "Оружие");
        ITEM_TYPE_LABELS.put("shield", "Щит");
        ITEM_TYPE_LABELS.put("helmet", "Шлем");
        ITEM_TYPE_LABELS.put("armor", "Броня");
        ITEM_TYPE_LABELS.put("gloves", "Перчатки");
        ITEM_TYPE_LABELS.put("boots", "Сапоги");
        ITEM_TYPE_LABELS.put("belt", "Пояс");
        ITEM_TYPE_LABELS.put("amulet", "Амулет");
        ITEM_TYPE_LABELS.put("artifact", "Артефакт");
    }

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
        template.put("__width__", 1);
        template.put("__height__", 1);
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

        // ──────── ОСНОВНЫЕ ПАРАМЕТРЫ ────────
        itemDetails[i++] = makeSectionHeader("── ОСНОВНЫЕ ПАРАМЕТРЫ ──");

        int selW = template.containsKey("__width__")  ? (int) template.get("__width__")  : 1;
        int selH = template.containsKey("__height__") ? (int) template.get("__height__") : 1;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Размер: " + selW + "x" + selH + "\n" + makeSizeGrid(selW, selH));
        button.registerCallBack(this, "prepareSizePickerView", new String[]{uuid});
        itemDetails[i++] = button;

        String selTypeKey = template.containsKey("__type__") ? (String) template.get("__type__") : null;
        String selTypeLabel = selTypeKey != null ? ITEM_TYPE_LABELS.get(selTypeKey) : "Не выбран";

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(itemIcon);
        button.setText(font, "Тип: " + selTypeLabel);
        button.registerCallBack(this, "prepareTypePickerView", new String[]{uuid});
        itemDetails[i++] = button;

        // ──────── ТРЕБОВАНИЯ И ОСНОВНАЯ ХАРАКТЕРИСТИКА ────────
        int mainStat = template.containsKey("__mainStat__") ? (int) template.get("__mainStat__") : 0;
        int reqLevel = template.containsKey("__reqLevel__") ? (int) template.get("__reqLevel__") : 0;
        int reqStrength = template.containsKey("__reqStrength__") ? (int) template.get("__reqStrength__") : 0;
        int reqMagic = template.containsKey("__reqMagic__") ? (int) template.get("__reqMagic__") : 0;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Основная характеристика: " + mainStat);
        button.registerCallBack(this, "editItemNumericField", new String[]{uuid, "__mainStat__"});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Требуемый уровень: " + reqLevel);
        button.registerCallBack(this, "editItemNumericField", new String[]{uuid, "__reqLevel__"});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Требуемая сила: " + reqStrength);
        button.registerCallBack(this, "editItemNumericField", new String[]{uuid, "__reqStrength__"});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Требуемая магия: " + reqMagic);
        button.registerCallBack(this, "editItemNumericField", new String[]{uuid, "__reqMagic__"});
        itemDetails[i++] = button;

        // ──────── ХАРАКТЕРИСТИКИ ────────
        itemDetails[i++] = makeSectionHeader("── ХАРАКТЕРИСТИКИ ──");

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

    public void prepareSizePickerView(String uuid) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        int selW = template.containsKey("__width__")  ? (int) template.get("__width__")  : 1;
        int selH = template.containsKey("__height__") ? (int) template.get("__height__") : 1;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedItemView", new String[]{uuid});
        itemDetails[i++] = button;

        int[][] sizes = {{1,1},{1,2},{1,3},{1,4},{2,1},{2,2},{2,3},{2,4}};
        for (int[] sz : sizes) {
            int sw = sz[0], sh = sz[1];
            boolean selected = (sw == selW && sh == selH);
            button = new ButtonCommon();
            button.setBackgrounds(selected ? buttonBGHover : buttonBG, buttonBGHover);
            // Метка + визуальная сетка из # (символ блока поддерживается шрифтом)
            button.setText(font, sw + "x" + sh + (selected ? "  <" : "") + "\n" + makeSizeGrid(sw, sh));
            button.registerCallBack(this, "setItemSize", new String[]{uuid, String.valueOf(sw), String.valueOf(sh)});
            itemDetails[i++] = button;
        }

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    public void setItemSize(String uuid, String widthStr, String heightStr) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        template.put("__width__", Integer.parseInt(widthStr));
        template.put("__height__", Integer.parseInt(heightStr));
        prepareSelectedItemView(uuid);
    }

    // ---- Тип предмета ----

    public void prepareTypePickerView(String uuid) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        String selTypeKey = template.containsKey("__type__") ? (String) template.get("__type__") : "";

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedItemView", new String[]{uuid});
        itemDetails[i++] = button;

        for (String typeKey : ITEM_TYPE_KEYS) {
            boolean selected = typeKey.equals(selTypeKey);
            button = new ButtonCommon();
            button.setBackgrounds(selected ? buttonBGHover : buttonBG, buttonBGHover);
            button.setIcon(itemIcon);
            button.setText(font, ITEM_TYPE_LABELS.get(typeKey) + (selected ? "  <" : ""));
            button.registerCallBack(this, "setItemType", new String[]{uuid, typeKey});
            itemDetails[i++] = button;
        }

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    public void setItemType(String uuid, String typeKey) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        template.put("__type__", typeKey);
        prepareSelectedItemView(uuid);
    }

    // ---- Числовые поля предмета (характеристика, требования) ----

    public void editItemNumericField(String uuid, String fieldName) {
        if (tiw.isShowWindow || !isWindowActive) return;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        pendingItemNumericField = fieldName;
        Object raw = template.get(fieldName);
        tiw.registerCallBack(this, "editItemNumericFieldDone", new String[]{uuid, ""});
        tiw.setText(raw != null ? raw.toString() : "0");
        tiw.show();
    }

    public void editItemNumericFieldDone(String uuid, String value) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        value = value.replaceAll("[^0-9\\-]", "");
        if (value.isEmpty()) value = "0";
        template.put(pendingItemNumericField, Integer.parseInt(value));
        prepareSelectedItemView(uuid);
    }

    private String makeSizeGrid(int w, int h) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < h; row++) {
            if (row > 0) sb.append("\n");
            for (int col = 0; col < w; col++) {
                if (col > 0) sb.append("  ");
                sb.append("#");
            }
        }
        return sb.toString();
    }

    private ButtonCommon makeSectionHeader(String text) {
        ButtonCommon header = new ButtonCommon();
        header.setBackgrounds(buttonBGHover, buttonBGHover);
        header.setText(font, text);
        return header;
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
