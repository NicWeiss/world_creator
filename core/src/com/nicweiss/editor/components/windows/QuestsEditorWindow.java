package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.Uuid;

import java.util.Arrays;
import java.util.LinkedHashMap;


public class QuestsEditorWindow extends Window implements CallBack {
    public static Store store;

    BOHelper bo_helper;
    TextInputWindow tiw = new TextInputWindow();
    ActionConfirnWindow acw = new ActionConfirnWindow();
    ItemCardWindow itemCard = new ItemCardWindow();
    LinkedHashMap questsList, selectedQuest;
    private String itemSearchQuery = "";

    Texture buttonBG, buttonBGHover, plusIcon, questIcon, trashIcon, questOptionIcon, nameIcon, descriptionIcon;
    Texture checkboxOn, checkboxOff, experienceIcon;
    ButtonCommon[] items, questItems;
    ButtonCommon button;

    public QuestsEditorWindow() {
        super();
        bo_helper = new BOHelper();
        questsList = store.quests;
        windowName = "Редактирование квестов";

        buttonBG = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");

        plusIcon = new Texture("icons/quest_window/plus.png");
        questIcon = new Texture("icons/quest_window/quest.png");
        questOptionIcon = new Texture("icons/quest_window/quest_option.png");
        trashIcon = new Texture("icons/quest_window/trash.png");
        nameIcon = new Texture("icons/quest_window/name.png");
        descriptionIcon = new Texture("icons/quest_window/description.png");
        experienceIcon = new Texture("icons/quest_window/experience.png");

        checkboxOn = new Texture("icons/forms/checkbox_on.png");
        checkboxOff = new Texture("icons/forms/checkbox_off.png");

    }

    public void buildWindow() {
        setDualSectionMode(true);
        super.buildWindow();
        tiw.buildWindow();
        acw.buildWindow();
        itemCard.buildWindow();
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }

    @Override
    public void onShow(){
        prepareQuestsView();
    }

    public void selectQuestCallback(String uuid){
        prepareSelectedQuestView(uuid);
    }

    public LinkedHashMap getEmptyQuest() {
        LinkedHashMap obj = new LinkedHashMap();

        String uuid = Uuid.generate();
        obj.put("__uuid__", uuid);
        obj.put("__name__", "Новый квест #" + uuid);
        obj.put("__exp__", 0);
        obj.put("__is_received__", 0);
        obj.put("__is_complete__", 0);

        return obj;
    }

    public void addQuestCallback() {
        LinkedHashMap newQuest = this.getEmptyQuest();

        String newUuid = (String) newQuest.get("__uuid__");
        questsList.put(newUuid, newQuest);
        prepareQuestsView();
        prepareSelectedQuestView(newUuid);
    }

//    Deleting quest
    public void deleteQuestConfirm(String questUuid) {
        acw.setText("Удалить квест ?");
        acw.registerCallBack(this, "deleteQuest", new String[]{questUuid});
        acw.show();
    }

    public void deleteQuest(String questUuid){
        questsList.remove(questUuid);
        selectedQuest = null;
        questItems = new ButtonCommon[0];
        prepareQuestsView();
    }

//    Adding quest option
    public void addQuestOption(String questUuid){
        tiw.registerCallBack(
            this,
            "addQuestOptionDoneCallback",
            new String[]{questUuid,""}
        );
        tiw.setText("");
        tiw.show();
    }

    public void addQuestOptionDoneCallback(String questUuid, String optionText) {
        LinkedHashMap option = new LinkedHashMap();

        String uuid = Uuid.generate();
        String optionUuid = "quest_" + uuid;
        option.put("__uuid__", optionUuid);
        option.put("__text__", optionText);
        option.put("__exp__", 0);
        option.put("__is_available__", 1);
        option.put("__is_complete__", 0);

        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        quest.put(optionUuid, option);

        prepareOptionOfSelectedQuestView(questUuid, optionUuid);
    }

//    Change Quest text field
    public void editQuestTextField(String questUuid, String fieldName) {
        if (tiw.isShowWindow || !isWindowActive) { return; }
        LinkedHashMap obj = (LinkedHashMap) questsList.get(questUuid);
        String value = (String) obj.get(fieldName);

        tiw.registerCallBack(
            this,
            "textEditDoneCallback",
            new String[]{questUuid,fieldName,""}
        );
        tiw.setText(value != null ? value : "");
        tiw.show();
    }

    public void textEditDoneCallback(String uuid, String fieldName, String value){
        LinkedHashMap obj = (LinkedHashMap) questsList.get(uuid);
        obj.put(fieldName, value);

        this.prepareQuestsView();
        this.prepareSelectedQuestView(uuid);
    }

// Change Quest option text
    public void editQuestOptionText(String questUuid, String optionUuid) {
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        LinkedHashMap option = (LinkedHashMap) quest.get(optionUuid);

        tiw.registerCallBack(
            this,
            "editQuestOptionTextDone",
            new String[]{questUuid,optionUuid,""}
        );
        tiw.setText((String) option.get("__text__"));
        tiw.show();
    }

    public void editQuestOptionTextDone(String questUuid, String optionUuid, String text) {
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        LinkedHashMap option = (LinkedHashMap) quest.get(optionUuid);
        option.put("__text__", text);

        prepareOptionOfSelectedQuestView(questUuid, optionUuid);
    }

// Change Quest option experience
    public void editQuestOptionExp(String questUuid, String optionUuid) {
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        LinkedHashMap option = (LinkedHashMap) quest.get(optionUuid);

        tiw.registerCallBack(
            this,
            "editQuestOptionExpDone",
            new String[]{questUuid,optionUuid,""}
        );
        tiw.setText(String.valueOf((int)option.get("__exp__")));
        tiw.show();
    }

    public void editQuestOptionExpDone(String questUuid, String optionUuid, String text) {
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        LinkedHashMap option = (LinkedHashMap) quest.get(optionUuid);
        text = text.replaceAll("[^0-9]", "");
        if (text.isEmpty()){
            text = "0";
        }

        option.put("__exp__", Integer.parseInt(text));

        prepareOptionOfSelectedQuestView(questUuid, optionUuid);
    }

//    Deleting quest
    public void deleteQuestOptionConfirm(String questUuid, String optionUuid) {
        acw.setText("Удалить квестовую опцию ?");
        acw.registerCallBack(
            this,
            "deleteQuestOption",
            new String[]{questUuid, optionUuid}
        );
        acw.show();
    }

    public void deleteQuestOption(String questUuid, String optionUuid){
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        quest.remove(optionUuid);
        prepareSelectedQuestView(questUuid);
    }

//    Toggle available quest option
    public void toggleQuestOptionAvailable(String questUuid, String optionUuid) {
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        LinkedHashMap option = (LinkedHashMap) quest.get(optionUuid);
        int isAvailable = (int) option.get("__is_available__");
        isAvailable = isAvailable == 1 ? 0 : 1;
        option.put("__is_available__", isAvailable);

        prepareOptionOfSelectedQuestView(questUuid, optionUuid);
    }

//    RENDER
    public void render(SpriteBatch batch) {
        super.render(batch);

        if (isShowWindow) {
            renderItemsList(batch, items, false);
            rightSection.renderItemsList(batch, questItems, false);
        }

        if (tiw.isShowWindow) {
            tiw.render(batch);
            isWindowActive = false;
        } if (acw.isShowWindow) {
            acw.render(batch);
            isWindowActive = false;
        } else {
            isWindowActive = true;
        }

        if (itemCard.isShowWindow) {
            itemCard.render(batch);
            isWindowActive = false;
        }
    }


    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        if (!isShowWindow){
            return false;
        }

        // Карточка предмета: клик внутри — обрабатываем, снаружи — закрываем
        if (itemCard.isShowWindow) {
            if (itemCard.isTouchInsideBounds()) {
                itemCard.checkTouch(isDragged, isTouchUp);
            } else if (!isDragged && isTouchUp) {
                itemCard.hide();
            }
            return true;
        }

        if (tiw.isShowWindow && tiw.checkTouch(isDragged, isTouchUp)){
            return true;
        }

        if (acw.isShowWindow && acw.checkTouch(isDragged, isTouchUp)){
            return true;
        }

        return super.checkTouch(isDragged, isTouchUp);
    }


    @Override
    public boolean checkKey(int keyCode){
        // Карточка предмета модальна (см. checkTouch выше) — глушим скролл/клавиши карты,
        // пока она открыта, иначе колёсико мыши проваливается в карту и зумит её.
        if (itemCard.isShowWindow) {
            itemCard.checkKey(keyCode);
            return true;
        }

        if (tiw.checkKey(keyCode)){
            return true;
        }

        return super.checkKey(keyCode);
    }

    public boolean keyTyped(char character){
        return tiw.keyTyped(character);
    }

    public void prepareQuestsView(){
        items = new ButtonCommon[1000];

        int i = 0;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить квест");
        button.registerCallBack(
            this,
            "addQuestCallback"
        );
        items[i] = button;
        i++;

        for (Object keyEl: questsList.keySet()) {
            String key = keyEl.toString();
            LinkedHashMap quest = (LinkedHashMap) questsList.get(key);

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(questIcon);
            button.setText(font, (String) quest.get("__name__"));
            button.registerCallBack(
                this,
                "selectQuestCallback",
                new String[]{(String) quest.get("__uuid__")}
            );
            items[i] = button;
            i++;
        }

        items = Arrays.copyOfRange(items, 0, i);
    }

    public void prepareSelectedQuestView(String uuid) {
        questItems = new ButtonCommon[1000];
        LinkedHashMap quest = (LinkedHashMap) questsList.get(uuid);
        int i = 0;

        // ──────── О КВЕСТЕ ────────
        questItems[i++] = makeSectionHeader("── О КВЕСТЕ ──");

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(nameIcon);
        button.setText(font, "Название: " + quest.get("__name__"));
        button.registerCallBack(this, "editQuestTextField", new String[]{uuid, "__name__"});
        questItems[i++] = button;

        if (quest.containsKey("__description__")) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(descriptionIcon);
            button.setText(font, "Описание: " + quest.get("__description__"));
            button.registerCallBack(this, "editQuestTextField", new String[]{uuid, "__description__"});
            questItems[i++] = button;
        } else {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(plusIcon);
            button.setText(font, "Добавить описание");
            button.registerCallBack(this, "editQuestTextField", new String[]{uuid, "__description__"});
            questItems[i++] = button;
        }

        int exp = quest.containsKey("__exp__") ? (int) quest.get("__exp__") : 0;
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(experienceIcon);
        button.setText(font, "Опыт за квест: " + exp);
        button.registerCallBack(this, "editQuestExp", new String[]{uuid});
        questItems[i++] = button;

        // ──────── ЦЕЛИ ────────
        questItems[i++] = makeSectionHeader("── ЦЕЛИ ──");

        for (Object keyEl : quest.keySet()) {
            String key = keyEl.toString();
            if (key.contains("quest_")) {
                LinkedHashMap option = (LinkedHashMap) quest.get(key);
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(questOptionIcon);
                button.setText(font, (String) option.get("__text__"));
                button.registerCallBack(this, "prepareOptionOfSelectedQuestView",
                    new String[]{uuid, (String) option.get("__uuid__")});
                questItems[i++] = button;
            }
        }

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить цель");
        button.registerCallBack(this, "addQuestOption", new String[]{uuid});
        questItems[i++] = button;

        // ──────── НАГРАДЫ ────────
        questItems[i++] = makeSectionHeader("── НАГРАДЫ ──");

        for (Object keyEl : quest.keySet()) {
            String key = keyEl.toString();
            if (key.contains("reward_")) {
                LinkedHashMap reward = (LinkedHashMap) quest.get(key);
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(questOptionIcon);
                button.setText(font, (String) reward.get("__name__"));
                button.registerCallBack(this, "removeRewardConfirm",
                    new String[]{uuid, (String) reward.get("__uuid__")});
                questItems[i++] = button;
            }
        }

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить предмет в награду");
        button.registerCallBack(this, "prepareItemPickerView", new String[]{uuid});
        questItems[i++] = button;

        // ──────── ────────
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить квест");
        button.registerCallBack(this, "deleteQuestConfirm", new String[]{uuid});
        questItems[i++] = button;

        questItems = Arrays.copyOfRange(questItems, 0, i);
    }

    private ButtonCommon makeSectionHeader(String text) {
        ButtonCommon header = new ButtonCommon();
        header.setBackgrounds(buttonBGHover, buttonBGHover);
        header.setText(font, text);
        return header;
    }

//    Опыт за квест
    public void editQuestExp(String questUuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        int current = quest.containsKey("__exp__") ? (int) quest.get("__exp__") : 0;
        tiw.registerCallBack(this, "editQuestExpDone", new String[]{questUuid, ""});
        tiw.setText(String.valueOf(current));
        tiw.show();
    }

    public void editQuestExpDone(String questUuid, String value) {
        value = value.replaceAll("[^0-9]", "");
        if (value.isEmpty()) value = "0";
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        quest.put("__exp__", Integer.parseInt(value));
        prepareSelectedQuestView(questUuid);
    }

    public void prepareOptionOfSelectedQuestView(String questUuid, String optionUuid) {
        questItems = new ButtonCommon[1000];
        int i = 0;

        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        LinkedHashMap option = (LinkedHashMap) quest.get(optionUuid);

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(
            this,
            "prepareSelectedQuestView",
            new String[]{questUuid}
        );
        questItems[i] = button;
        i++;

        for (Object keyEl : option.keySet()) {
            String key = keyEl.toString();

            if (key.equals("__text__")){
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(nameIcon);

                button.setText(font, "Название : " + (String) option.get("__text__"));
                button.registerCallBack(
                    this,
                    "editQuestOptionText",
                    new String[]{questUuid, optionUuid}
                );
                questItems[i] = button;
                i++;
            }

            if (key.equals("__exp__")){
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(experienceIcon);
                int expCount = (int) option.get("__exp__");

                button.setText(font, "Опыт за выполнение : " + expCount);
                button.registerCallBack(
                    this,
                    "editQuestOptionExp",
                    new String[]{questUuid, optionUuid}
                );
                questItems[i] = button;
                i++;
            }

            if (key.equals("__is_available__")){
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                int isAvailable = (int) option.get("__is_available__");
                if (isAvailable == 0) {
                    button.setIcon(checkboxOff);
                }else {
                    button.setIcon(checkboxOn);
                }

                button.setText(font, "Доступно при получении квеста ");
                button.registerCallBack(
                    this,
                    "toggleQuestOptionAvailable",
                    new String[]{questUuid, optionUuid}
                );
                questItems[i] = button;
                i++;
            }
        }

        //        Удаление цели квеста
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить квестовую цель");
        button.registerCallBack(
            this,
            "deleteQuestOptionConfirm",
            new String[]{questUuid, optionUuid}
        );
        questItems[i] = button;
        i++;

        questItems = Arrays.copyOfRange(questItems, 0, i);
    }

//    Picker: список шаблонов предметов для выбора награды
    public void prepareItemPickerView(String questUuid) {
        questItems = new ButtonCommon[1000];
        int i = 0;

        // Кнопка назад
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "<--");
        button.registerCallBack(this, "prepareSelectedQuestView", new String[]{questUuid});
        questItems[i++] = button;

        // Строка поиска
        String searchLabel = itemSearchQuery.isEmpty() ? "Поиск по имени..." : "Поиск: " + itemSearchQuery;
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(nameIcon);
        button.setText(font, searchLabel);
        button.registerCallBack(this, "openItemSearch", new String[]{questUuid});
        questItems[i++] = button;

        // Список предметов с фильтрацией
        if (store.itemTemplates.isEmpty()) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, "Нет доступных предметов");
            questItems[i++] = button;
        } else {
            boolean anyFound = false;
            for (Object keyEl : store.itemTemplates.keySet()) {
                String templateUuid = keyEl.toString();
                LinkedHashMap template = (LinkedHashMap) store.itemTemplates.get(templateUuid);
                String name = (String) template.get("__name__");

                if (!itemSearchQuery.isEmpty() &&
                    !name.toLowerCase().contains(itemSearchQuery.toLowerCase())) {
                    continue;
                }

                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setIcon(questIcon);
                button.setText(font, name);
                button.registerCallBack(this, "showItemCard", new String[]{questUuid, templateUuid});
                questItems[i++] = button;
                anyFound = true;
            }

            if (!anyFound) {
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setText(font, "Ничего не найдено");
                questItems[i++] = button;
            }
        }

        questItems = Arrays.copyOfRange(questItems, 0, i);
    }

    public void openItemSearch(String questUuid) {
        if (tiw.isShowWindow || !isWindowActive) return;
        tiw.registerCallBack(this, "applyItemSearch", new String[]{questUuid, ""});
        tiw.setText(itemSearchQuery);
        tiw.show();
    }

    public void applyItemSearch(String questUuid, String query) {
        itemSearchQuery = query;
        prepareItemPickerView(questUuid);
    }

    public void showItemCard(String questUuid, String templateUuid) {
        LinkedHashMap template = (LinkedHashMap) store.itemTemplates.get(templateUuid);
        itemCard.registerCallBack(this, "selectRewardFromCard", new String[]{questUuid, templateUuid});
        itemCard.populate(template, font);
        itemCard.setX(x + width + 10);
        itemCard.setY(y);
        itemCard.show();
    }

    public void selectRewardFromCard(String questUuid, String templateUuid) {
        itemSearchQuery = "";
        addRewardItem(questUuid, templateUuid);
    }

    public void addRewardItem(String questUuid, String templateUuid) {
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        LinkedHashMap template = (LinkedHashMap) store.itemTemplates.get(templateUuid);

        String rewardUuid = "reward_" + Uuid.generate();
        LinkedHashMap reward = new LinkedHashMap();
        reward.put("__uuid__", rewardUuid);
        reward.put("__template_id__", templateUuid);
        reward.put("__name__", (String) template.get("__name__"));
        quest.put(rewardUuid, reward);

        prepareSelectedQuestView(questUuid);
    }

    public void removeRewardConfirm(String questUuid, String rewardUuid) {
        acw.setText("Убрать предмет из награды ?");
        acw.registerCallBack(this, "removeReward", new String[]{questUuid, rewardUuid});
        acw.show();
    }

    public void removeReward(String questUuid, String rewardUuid) {
        LinkedHashMap quest = (LinkedHashMap) questsList.get(questUuid);
        quest.remove(rewardUuid);
        prepareSelectedQuestView(questUuid);
    }
}
