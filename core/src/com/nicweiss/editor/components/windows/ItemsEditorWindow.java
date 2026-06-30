package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.ItemGenerator;
import com.nicweiss.editor.utils.ItemModifierCatalog;
import com.nicweiss.editor.utils.ItemModifierCatalog.ModifierDef;
import com.nicweiss.editor.utils.ItemModifierCatalog.RarityDef;
import com.nicweiss.editor.utils.ItemModifierCatalog.Subtype;
import com.nicweiss.editor.utils.ItemModifierCatalog.TypeDef;
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

    // Хранит имя редактируемого числового поля предмета между открытием TextInputWindow и коллбэком
    private String pendingItemNumericField;

    // Размеры по умолчанию для предметов без выбранного типа
    private static final int[][] DEFAULT_SIZES = {{1,1},{1,2},{1,3},{1,4},{2,1},{2,2},{2,3},{2,4}};

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
        int parsed = Integer.parseInt(value);
        if (pendingItemNumericField.equals("__itemLevel__")) {
            parsed = ItemGenerator.clamp(parsed, 1, 99);
        }
        template.put(pendingItemNumericField, parsed);

        // Уровень и основная характеристика влияют на требования — пересчитываем сразу.
        if (pendingItemNumericField.equals("__mainStat__") || pendingItemNumericField.equals("__itemLevel__")) {
            ItemGenerator.recomputeRequirements(template);
        }
        prepareSelectedItemView(uuid);
    }

    // ---- Тип и класс предмета ----

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

        for (String typeKey : ItemModifierCatalog.TYPES.keySet()) {
            TypeDef type = ItemModifierCatalog.TYPES.get(typeKey);
            boolean selected = typeKey.equals(selTypeKey);
            button = new ButtonCommon();
            button.setBackgrounds(selected ? buttonBGHover : buttonBG, buttonBGHover);
            button.setIcon(itemIcon);
            button.setText(font, type.label + (selected ? "  <" : ""));
            button.registerCallBack(this, "setItemType", new String[]{uuid, typeKey});
            itemDetails[i++] = button;
        }

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    // Смена типа: ставится размер по умолчанию для типа, класс роллится (или выводится из размера
    // для чармов), уровень/редкость роллятся заново, модификаторы перегенерируются с нуля.
    public void setItemType(String uuid, String typeKey) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        ItemGenerator.applyType(template, typeKey);
        prepareSelectedItemView(uuid);
    }

    public void prepareRarityPickerView(String uuid) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        String selRarityKey = template.containsKey("__rarity__") ? (String) template.get("__rarity__") : "";

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedItemView", new String[]{uuid});
        itemDetails[i++] = button;

        for (String rarityKey : ItemModifierCatalog.RARITIES.keySet()) {
            RarityDef rarity = ItemModifierCatalog.RARITIES.get(rarityKey);
            boolean selected = rarityKey.equals(selRarityKey);
            button = new ButtonCommon();
            button.setBackgrounds(selected ? buttonBGHover : buttonBG, buttonBGHover);
            button.setIcon(statIcon);
            button.setText(font, rarity.label + (selected ? "  <" : ""));
            button.registerCallBack(this, "setItemRarity", new String[]{uuid, rarityKey});
            itemDetails[i++] = button;
        }

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    // Ручная смена редкости не рероллит модификаторы — это делает отдельная кнопка реролла,
    // но требования пересчитываются сразу, т.к. редкость даёт прямую добавку.
    public void setItemRarity(String uuid, String rarityKey) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        ItemGenerator.setRarity(template, rarityKey);
        ItemGenerator.recomputeRequirements(template);
        prepareSelectedItemView(uuid);
    }

    public void prepareClassPickerView(String uuid) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        TypeDef type = currentType(template);
        String selClassKey = template.containsKey("__itemClass__") ? (String) template.get("__itemClass__") : "";

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedItemView", new String[]{uuid});
        itemDetails[i++] = button;

        if (type != null) {
            for (Subtype s : type.subtypes) {
                boolean selected = s.key.equals(selClassKey);
                button = new ButtonCommon();
                button.setBackgrounds(selected ? buttonBGHover : buttonBG, buttonBGHover);
                button.setIcon(itemIcon);
                button.setText(font, s.label + (selected ? "  <" : ""));
                button.registerCallBack(this, "setItemClass", new String[]{uuid, s.key});
                itemDetails[i++] = button;
            }
        }

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    // Ручная смена класса не трогает уже накатанные модификаторы — реролл делается отдельной кнопкой.
    public void setItemClass(String uuid, String classKey) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        ItemGenerator.setClass(template, classKey);
        prepareSelectedItemView(uuid);
    }

    // ---- Размер ----

    public void prepareSizePickerView(String uuid) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        int selW = template.containsKey("__width__")  ? (int) template.get("__width__")  : 1;
        int selH = template.containsKey("__height__") ? (int) template.get("__height__") : 1;
        TypeDef type = currentType(template);
        int[][] sizes = type != null ? type.sizes.toArray(new int[0][]) : DEFAULT_SIZES;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedItemView", new String[]{uuid});
        itemDetails[i++] = button;

        for (int[] sz : sizes) {
            int sw = sz[0], sh = sz[1];
            boolean selected = (sw == selW && sh == selH);
            button = new ButtonCommon();
            button.setBackgrounds(selected ? buttonBGHover : buttonBG, buttonBGHover);
            button.setText(font, sw + "x" + sh + (selected ? "  <" : "") + "\n" + makeSizeGrid(sw, sh));
            button.registerCallBack(this, "setItemSize", new String[]{uuid, String.valueOf(sw), String.valueOf(sh)});
            itemDetails[i++] = button;
        }

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    public void setItemSize(String uuid, String widthStr, String heightStr) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        ItemGenerator.setSize(template, Integer.parseInt(widthStr), Integer.parseInt(heightStr));
        prepareSelectedItemView(uuid);
    }

    // ---- Модификаторы (характеристики) ----

    // Перегенерирует набор модификаторов с нуля под текущий тип/класс/уровень/редкость предмета.
    // Вся логика ролла живёт в ItemGenerator — она же будет использоваться рантайм-симуляцией лута.
    public void rollModifiers(String uuid) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        ItemGenerator.rollModifiers(template);
    }

    public void rerollModifiersCallback(String uuid) {
        rollModifiers(uuid);
        prepareSelectedItemView(uuid);
    }

    // Ручное добавление характеристики НЕ подчиняется правилам генератора (гейтинг по
    // редкости/уровню, эксклюзивность резистов/пулов, skiller-лок, лимиты количества) — это
    // инструмент дизайнера для сборки предметов, которые рандом в принципе никогда не выдаст.
    // Все эти правила остаются только в ItemGenerator и применяются исключительно к авто-роллу.
    public void prepareAddStatPickerView(String uuid) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        TypeDef type = currentType(template);

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedItemView", new String[]{uuid});
        itemDetails[i++] = button;

        if (type == null) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, "Сначала выберите тип предмета");
            itemDetails[i++] = button;
        } else {
            String classKey = (String) template.get("__itemClass__");
            String rarityKey = ItemGenerator.currentRarity(template).key;
            LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
            for (ModifierDef def : type.modifiersFor(classKey)) {
                if (stats.containsKey(def.key)) continue;
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(statIcon);
                button.setText(font, def.name + "  [" + def.min + ".." + def.effectiveMax(rarityKey) + def.unit + "]");
                button.registerCallBack(this, "addSpecificStat", new String[]{uuid, def.key});
                itemDetails[i++] = button;
            }
        }

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    public void addSpecificStat(String uuid, String modKey) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(uuid);
        ItemGenerator.addModifier(template, modKey);
        prepareSelectedItemView(uuid);
    }

    public void editStatValue(String itemUuid, String statKey) {
        if (tiw.isShowWindow || !isWindowActive) return;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        LinkedHashMap stat = (LinkedHashMap) stats.get(statKey);

        tiw.registerCallBack(this, "editStatValueDone", new String[]{itemUuid, statKey, ""});
        tiw.setText(stat.get("__value__").toString());
        tiw.show();
    }

    public void editStatValueDone(String itemUuid, String statKey, String value) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        LinkedHashMap stat = (LinkedHashMap) stats.get(statKey);

        value = value.replaceAll("[^0-9\\-]", "");
        if (value.isEmpty()) value = "0";
        stat.put("__value__", Integer.parseInt(value));
        ItemGenerator.recomputeRequirements(template);
        prepareStatView(itemUuid, statKey);
    }

    public void deleteStatConfirm(String itemUuid, String statKey) {
        acw.setText("Удалить характеристику ?");
        acw.registerCallBack(this, "deleteStat", new String[]{itemUuid, statKey});
        acw.show();
    }

    public void deleteStat(String itemUuid, String statKey) {
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        stats.remove(statKey);
        ItemGenerator.recomputeRequirements(template);
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

        TypeDef type = currentType(template);
        String selTypeKey = template.containsKey("__type__") ? (String) template.get("__type__") : null;
        String selTypeLabel = type != null ? type.label : "Не выбран";

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(itemIcon);
        button.setText(font, "Тип: " + selTypeLabel);
        button.registerCallBack(this, "prepareTypePickerView", new String[]{uuid});
        itemDetails[i++] = button;

        if (type != null && !type.subtypes.isEmpty()) {
            String classKey = template.containsKey("__itemClass__") ? (String) template.get("__itemClass__") : null;
            String classLabel = classKey != null ? type.labelForClass(classKey) : "Не выбран";

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(statIcon);
            if (type.classDerivedFromSize) {
                button.setText(font, "Класс: " + classLabel + " (определяется размером)");
                // Без коллбэка — класс чарма меняется только сменой размера.
            } else {
                button.setText(font, "Класс: " + classLabel);
                button.registerCallBack(this, "prepareClassPickerView", new String[]{uuid});
            }
            itemDetails[i++] = button;
        }

        if (type != null) {
            int itemLevel = template.containsKey("__itemLevel__") ? (int) template.get("__itemLevel__") : 1;
            RarityDef rarity = ItemGenerator.currentRarity(template);

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(statIcon);
            button.setText(font, "Уровень предмета: " + itemLevel);
            button.registerCallBack(this, "editItemNumericField", new String[]{uuid, "__itemLevel__"});
            itemDetails[i++] = button;

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(statIcon);
            button.setText(font, "Редкость: " + rarity.label);
            button.registerCallBack(this, "prepareRarityPickerView", new String[]{uuid});
            itemDetails[i++] = button;
        }

        int selW = template.containsKey("__width__")  ? (int) template.get("__width__")  : 1;
        int selH = template.containsKey("__height__") ? (int) template.get("__height__") : 1;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Размер: " + selW + "x" + selH + "\n" + makeSizeGrid(selW, selH));
        button.registerCallBack(this, "prepareSizePickerView", new String[]{uuid});
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

        if (type != null) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(plusIcon);
            button.setText(font, "Реролл модификаторов");
            button.registerCallBack(this, "rerollModifiersCallback", new String[]{uuid});
            itemDetails[i++] = button;
        }

        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        for (Object statKeyObj : stats.keySet()) {
            String statKey = statKeyObj.toString();
            LinkedHashMap stat = (LinkedHashMap) stats.get(statKey);
            int val = (int) stat.get("__value__");
            ModifierDef def = selTypeKey != null ? ItemModifierCatalog.findModifier(selTypeKey, statKey) : null;
            String label = def != null
                ? def.name + ": " + val + def.unit + "  [" + def.min + ".." + def.effectiveMax(ItemGenerator.currentRarity(template).key) + "]"
                : statKey + ": " + val;
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(statIcon);
            button.setText(font, label);
            button.registerCallBack(this, "prepareStatView", new String[]{uuid, statKey});
            itemDetails[i++] = button;
        }

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить характеристику");
        button.registerCallBack(this, "prepareAddStatPickerView", new String[]{uuid});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить предмет");
        button.registerCallBack(this, "deleteItemConfirm", new String[]{uuid});
        itemDetails[i++] = button;

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    public void prepareStatView(String itemUuid, String statKey) {
        itemDetails = new ButtonCommon[1000];
        int i = 0;
        LinkedHashMap template = (LinkedHashMap) itemTemplates.get(itemUuid);
        LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
        LinkedHashMap stat = (LinkedHashMap) stats.get(statKey);
        String typeKey = (String) template.get("__type__");
        ModifierDef def = typeKey != null ? ItemModifierCatalog.findModifier(typeKey, statKey) : null;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedItemView", new String[]{itemUuid});
        itemDetails[i++] = button;

        itemDetails[i++] = makeSectionHeader(def != null
            ? def.name + "  (диапазон каталога: " + def.min + ".." + def.effectiveMax(ItemGenerator.currentRarity(template).key) + def.unit + ")"
            : statKey);

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(statIcon);
        button.setText(font, "Значение: " + stat.get("__value__"));
        button.registerCallBack(this, "editStatValue", new String[]{itemUuid, statKey});
        itemDetails[i++] = button;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить характеристику");
        button.registerCallBack(this, "deleteStatConfirm", new String[]{itemUuid, statKey});
        itemDetails[i++] = button;

        itemDetails = Arrays.copyOfRange(itemDetails, 0, i);
    }

    private TypeDef currentType(LinkedHashMap template) {
        if (!template.containsKey("__type__")) return null;
        return ItemModifierCatalog.TYPES.get(template.get("__type__"));
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
        return super.checkTouch(isDragged, isTouchUp);
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
