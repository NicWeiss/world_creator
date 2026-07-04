package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.utils.ObjectCatalog;
import com.nicweiss.editor.utils.Transform;
import com.nicweiss.editor.utils.Uuid;

import java.util.Arrays;
import java.util.LinkedHashMap;

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
    // Хранит имя редактируемого поля настроек объекта между открытием TextInputWindow и коллбэком
    // (см. editSettingsNumericField/editSettingsTextField).
    private String pendingSettingsField;

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
        String uuid = createObjectAt(center[0], center[1]);
        prepareObjectListView();
        prepareObjectInteractionView(uuid);
    }

    /**
     * Создаёт объект на конкретном тайле и сразу открывает окно с ним, выбранным в правой панели —
     * используется контекстным меню карты (см. MapContextMenuWindow.addObjectHere), где объект
     * должен появиться ровно на выбранной клетке, а не в центре камеры (как addObjectCallback).
     */
    public void addObjectAt(int tileX, int tileY) {
        String uuid = createObjectAt(tileX, tileY);
        show();
        prepareObjectListView();
        prepareObjectInteractionView(uuid);
    }

    private String createObjectAt(int tileX, int tileY) {
        String uuid = Uuid.generate();

        // Рендер-позиция — подтверждено эмпирически (см. Store.TILE_X_ANCHOR_EXTRA_OFFSET):
        // tileX/Y из store.selectedTileX/Y (см. Editor.calcPositionCursor — для X там своя доп.
        // компенсация, для Y её нет) — нужна симметричная компенсация на X при рендере.
        float[] isoPos = Transform.cartesianToIsometric(
            (int)((tileX + store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth),
            (int)(tileY * store.tileSizeHeight)
        );

        store.buildingCount++;
        Creation b = new Creation();
        b.setUUID(uuid);
        b.setTexture(new Texture("objects/default_object.png"));
        b.setPosition(isoPos[0], isoPos[1]);
        b.setCell(tileX, tileY);
        store.buildings[store.buildingCount] = b;
        store.buildingNames.put(uuid, "Новый объект");
        return uuid;
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
        store.buildingSettings.remove(uuid);
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

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(objectIcon);
        button.setText(font, "Тип: " + getObjectTypeLabel(uuid));
        button.registerCallBack(this, "prepareObjectTypePickerView", new String[]{uuid});
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

        // ── ПЕРСОНАЛЬНЫЕ НАСТРОЙКИ (зависят от типа, см. ObjectCatalog) ──
        String objType = getObjectType(uuid);
        if (objType != null) {
            LinkedHashMap settings = getOrCreateSettings(uuid, objType);
            objectDetails[i++] = makeSectionHeader("── НАСТРОЙКИ ──");

            if (ObjectCatalog.isSpawner(objType)) {
                i = addNumericSetting(uuid, settings, i, "Уровень спавна", "__level__", 1);
                i = addNumericSetting(uuid, settings, i, "Макс. количество", "__maxCount__", 1);
            } else if ("chest".equals(objType)) {
                boolean useLevelFromPlayer = toBool(settings.get("__useRewardLevelFromPlayer__"), true);
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(coordIcon);
                button.setText(font, "Уровень наград: " + (useLevelFromPlayer ? "Соответствует уровню игрока" : "кастомный"));
                button.registerCallBack(this, "toggleSettingsBool", new String[]{uuid, "__useRewardLevelFromPlayer__"});
                objectDetails[i++] = button;
                if (!useLevelFromPlayer) i = addNumericSetting(uuid, settings, i, "Кастомный уровень наград", "__rewardLevel__", 1);

                boolean useMfFromPlayer = toBool(settings.get("__useMagicFindFromPlayer__"), true);
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(coordIcon);
                button.setText(font, "Поиск предметов: " + (useMfFromPlayer ? "Соответствует уровню игрока" : "кастомный"));
                button.registerCallBack(this, "toggleSettingsBool", new String[]{uuid, "__useMagicFindFromPlayer__"});
                objectDetails[i++] = button;
                if (!useMfFromPlayer) i = addNumericSetting(uuid, settings, i, "Кастомный поиск предметов, %", "__magicFindLevel__", 1);

                boolean randomCount = toBool(settings.get("__useDropCountRandom__"), true);
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(coordIcon);
                button.setText(font, "Количество дропа: " + (randomCount ? "Случайное" : "заданный диапазон"));
                button.registerCallBack(this, "toggleSettingsBool", new String[]{uuid, "__useDropCountRandom__"});
                objectDetails[i++] = button;
                if (!randomCount) {
                    i = addNumericSetting(uuid, settings, i, "Минимум дропа", "__dropCountMin__", 1);
                    i = addNumericSetting(uuid, settings, i, "Максимум дропа", "__dropCountMax__", 1);
                }
            } else if ("source".equals(objType)) {
                boolean isMana = "mana".equals(settings.get("__resourceType__"));
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(coordIcon);
                button.setText(font, "Ресурс: " + (isMana ? "Мана" : "Здоровье"));
                button.registerCallBack(this, "toggleResourceType", new String[]{uuid});
                objectDetails[i++] = button;

                boolean regenerates = toBool(settings.get("__regenerates__"), true);
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(coordIcon);
                button.setText(font, "Восстанавливается: " + (regenerates ? "Да" : "Нет"));
                button.registerCallBack(this, "toggleSettingsBool", new String[]{uuid, "__regenerates__"});
                objectDetails[i++] = button;
                if (regenerates) i = addNumericSetting(uuid, settings, i, "Скорость восстановления, сек", "__regenSpeed__", 1);

                i = addNumericSetting(uuid, settings, i, "Количество использований", "__uses__", 1);
                i = addNumericSetting(uuid, settings, i, "Восстановление за использование", "__amountPerUse__", 1);
            } else if ("portal".equals(objType)) {
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(coordIcon);
                Object targetMap = settings.get("__targetMap__");
                String targetMapStr = targetMap != null && !targetMap.toString().isEmpty() ? targetMap.toString() : "(не задана)";
                button.setText(font, "Целевая карта: " + targetMapStr);
                button.registerCallBack(this, "editSettingsTextField", new String[]{uuid, "__targetMap__"});
                objectDetails[i++] = button;

                i = addNumericSetting(uuid, settings, i, "Целевая точка X", "__targetX__", 1);
                i = addNumericSetting(uuid, settings, i, "Целевая точка Y", "__targetY__", 1);

                // Мульти-карт системы пока нет — переход настраивается, но ведёт в заглушку.
                button = new ButtonCommon();
                button.setBackgrounds(buttonBGHover, buttonBGHover);
                button.setText(font, "(Переход пока не активируется — нет системы мульти-карт)");
                objectDetails[i++] = button;
            }
        }

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить объект");
        button.registerCallBack(this, "deleteObjectConfirm", new String[]{uuid});
        objectDetails[i++] = button;

        objectDetails = Arrays.copyOfRange(objectDetails, 0, i);
    }

    // ── Тип объекта (ObjectCatalog) ──────────────────────────────────────────

    public void prepareObjectTypePickerView(String uuid) {
        objectDetails = new ButtonCommon[1000];
        int i = 0;
        String selType = getObjectType(uuid);

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "selectObjectCallback", new String[]{uuid});
        objectDetails[i++] = button;

        for (String typeKey : ObjectCatalog.TYPES.keySet()) {
            ObjectCatalog.TypeDef type = ObjectCatalog.TYPES.get(typeKey);
            boolean selected = typeKey.equals(selType);
            button = new ButtonCommon();
            button.setBackgrounds(selected ? buttonBGHover : buttonBG, buttonBGHover);
            button.setIcon(objectIcon);
            button.setText(font, type.label + (selected ? "  <" : ""));
            button.registerCallBack(this, "setObjectType", new String[]{uuid, typeKey});
            objectDetails[i++] = button;
        }

        objectDetails = Arrays.copyOfRange(objectDetails, 0, i);
    }

    // Смена типа: ставит дефолтную текстуру типа (см. ObjectCatalog.TypeDef.defaultImage) и
    // дефолтные значения его персональных настроек (только если их ещё нет — переключение туда-
    // обратно между типами не теряет уже введённые значения).
    public void setObjectType(String uuid, String typeKey) {
        LinkedHashMap settings = getOrCreateSettings(uuid, typeKey);
        settings.put("__objectType__", typeKey);
        applyTypeDefaults(settings, typeKey);

        ObjectCatalog.TypeDef type = ObjectCatalog.get(typeKey);
        Creation b = findBuilding(uuid);
        if (type != null && b != null) {
            if (type.defaultImage != null) b.setTexture(new Texture(type.defaultImage));
            // Спавнер/сундук/источник — размером с тайл, переход — размером с дерево
            // (см. ObjectCatalog.targetSizeTileMult, Creation.targetMaxScreenSize).
            b.targetMaxScreenSize = store.tileSizeWidth * ObjectCatalog.targetSizeTileMult(typeKey);
        }

        prepareObjectInteractionView(uuid);
    }

    private String getObjectType(String uuid) {
        LinkedHashMap settings = store.buildingSettings.get(uuid);
        return settings != null ? (String) settings.get("__objectType__") : null;
    }

    private String getObjectTypeLabel(String uuid) {
        ObjectCatalog.TypeDef type = ObjectCatalog.get(getObjectType(uuid));
        return type != null ? type.label : "Не выбран";
    }

    private LinkedHashMap getOrCreateSettings(String uuid, String typeKeyForDefaults) {
        LinkedHashMap settings = store.buildingSettings.get(uuid);
        if (settings == null) {
            settings = new LinkedHashMap();
            store.buildingSettings.put(uuid, settings);
        }
        if (typeKeyForDefaults != null) applyTypeDefaults(settings, typeKeyForDefaults);
        return settings;
    }

    // Дефолтные значения персональных настроек по типу — выставляются один раз (putIfAbsent),
    // не перетирают уже введённые пользователем значения при повторных вызовах.
    @SuppressWarnings("unchecked")
    private void applyTypeDefaults(LinkedHashMap settings, String typeKey) {
        if (ObjectCatalog.isSpawner(typeKey)) {
            settings.putIfAbsent("__level__", 1);
            settings.putIfAbsent("__maxCount__", 3);
        } else if ("chest".equals(typeKey)) {
            settings.putIfAbsent("__useRewardLevelFromPlayer__", true);
            settings.putIfAbsent("__rewardLevel__", 1);
            settings.putIfAbsent("__useMagicFindFromPlayer__", true);
            settings.putIfAbsent("__magicFindLevel__", 0);
            settings.putIfAbsent("__useDropCountRandom__", true);
            settings.putIfAbsent("__dropCountMin__", 1);
            settings.putIfAbsent("__dropCountMax__", 3);
        } else if ("source".equals(typeKey)) {
            settings.putIfAbsent("__resourceType__", "health");
            settings.putIfAbsent("__regenerates__", true);
            settings.putIfAbsent("__regenSpeed__", 5);
            settings.putIfAbsent("__uses__", 10);
            settings.putIfAbsent("__amountPerUse__", 20);
        } else if ("portal".equals(typeKey)) {
            settings.putIfAbsent("__targetMap__", "");
            settings.putIfAbsent("__targetX__", 0);
            settings.putIfAbsent("__targetY__", 0);
        }
    }

    // ── Персональные настройки: числовые/текстовые/булевы поля ─────────────────

    @SuppressWarnings("unchecked")
    private int addNumericSetting(String uuid, LinkedHashMap settings, int i, String label, String fieldKey, int minValue) {
        int value = toInt(settings.get(fieldKey), minValue);
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(coordIcon);
        button.setText(font, label + ": " + value);
        button.registerCallBack(this, "editSettingsNumericField", new String[]{uuid, fieldKey});
        objectDetails[i++] = button;
        return i;
    }

    public void editSettingsNumericField(String uuid, String fieldName) {
        if (tiw.isShowWindow || !isWindowActive) return;
        LinkedHashMap settings = getOrCreateSettings(uuid, getObjectType(uuid));
        pendingSettingsField = fieldName;
        Object raw = settings.get(fieldName);
        tiw.registerCallBack(this, "editSettingsNumericFieldDone", new String[]{uuid, ""});
        tiw.setText(raw != null ? raw.toString() : "0");
        tiw.show();
    }

    @SuppressWarnings("unchecked")
    public void editSettingsNumericFieldDone(String uuid, String value) {
        LinkedHashMap settings = getOrCreateSettings(uuid, getObjectType(uuid));
        value = value.replaceAll("[^0-9\\-]", "");
        if (value.isEmpty()) value = "0";
        settings.put(pendingSettingsField, Integer.parseInt(value));
        prepareObjectInteractionView(uuid);
    }

    public void editSettingsTextField(String uuid, String fieldName) {
        if (tiw.isShowWindow || !isWindowActive) return;
        LinkedHashMap settings = getOrCreateSettings(uuid, getObjectType(uuid));
        pendingSettingsField = fieldName;
        Object raw = settings.get(fieldName);
        tiw.registerCallBack(this, "editSettingsTextFieldDone", new String[]{uuid, ""});
        tiw.setText(raw != null ? raw.toString() : "");
        tiw.show();
    }

    @SuppressWarnings("unchecked")
    public void editSettingsTextFieldDone(String uuid, String value) {
        LinkedHashMap settings = getOrCreateSettings(uuid, getObjectType(uuid));
        settings.put(pendingSettingsField, value);
        prepareObjectInteractionView(uuid);
    }

    @SuppressWarnings("unchecked")
    public void toggleSettingsBool(String uuid, String fieldName) {
        LinkedHashMap settings = getOrCreateSettings(uuid, getObjectType(uuid));
        boolean cur = toBool(settings.get(fieldName), false);
        settings.put(fieldName, !cur);
        prepareObjectInteractionView(uuid);
    }

    @SuppressWarnings("unchecked")
    public void toggleResourceType(String uuid) {
        LinkedHashMap settings = getOrCreateSettings(uuid, getObjectType(uuid));
        boolean isMana = "mana".equals(settings.get("__resourceType__"));
        settings.put("__resourceType__", isMana ? "health" : "mana");
        prepareObjectInteractionView(uuid);
    }

    private int toInt(Object o, int fallback) {
        return o instanceof Number ? ((Number) o).intValue() : fallback;
    }

    private boolean toBool(Object o, boolean fallback) {
        return o instanceof Boolean ? (Boolean) o : fallback;
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
        // См. createObjectAt — рендер-позиция, +TILE_X_ANCHOR_EXTRA_OFFSET на X (подтверждено эмпирически).
        float[] isoPos = Transform.cartesianToIsometric(
            (int)((finalX + store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth),
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
        return super.checkTouch(isDragged, isTouchUp);
    }

    @Override
    public boolean checkKey(int keyCode) {
        // Модальные попапы (см. checkTouch выше) — глушим скролл/клавиши карты, пока открыты.
        if (mapRedirectWindow.isShowWindow) {
            mapRedirectWindow.checkKey(keyCode);
            return true;
        }
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
