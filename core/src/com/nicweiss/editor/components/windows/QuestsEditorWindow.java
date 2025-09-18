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
    LinkedHashMap questsList, selectedQuest;

    Texture buttonBG, buttonBGHover, plusIcon, questIcon, trashIcon;
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
        trashIcon = new Texture("icons/quest_window/trash.png");

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
    public void onShow(){
        prepareQuestsView();
    }

    public void selectQuestCallback(String uuid){
        prepareSelectedQuestView(uuid);
    }

    public  LinkedHashMap getEmptyQuest() {
        LinkedHashMap obj = new LinkedHashMap();

        String uuid = Uuid.generate();
        obj.put("__uuid__", uuid);
        obj.put("__name__", "Новый квест #" + uuid);

        return obj;
    }

    public void addQuestCallback() {
        System.out.println("addQuestCallback");
        LinkedHashMap newQuest = this.getEmptyQuest();

        String newUuid = (String) newQuest.get("__uuid__");
        questsList.put(newUuid, newQuest);
        prepareQuestsView();
        prepareSelectedQuestView(newUuid);
    }

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

    public void addQuestOption(String questUuid){

    }

    public void editQuestTextField(String questUuid, String fieldName) {
        if (tiw.isShowWindow || !isWindowActive) { return; }
        LinkedHashMap obj = (LinkedHashMap) questsList.get(questUuid);
        String value = (String) obj.get(fieldName);

        tiw.registerCallBack(
            this,
            "textEditDoneCallback",
            new String[]{questUuid,fieldName,""}
        );
        tiw.setText(value);
        tiw.show();
    }


    public void textEditDoneCallback(String uuid, String fieldName, String value){
        LinkedHashMap obj = (LinkedHashMap) questsList.get(uuid);
        obj.put(fieldName, value);

        this.prepareQuestsView();
        this.prepareSelectedQuestView(uuid);
    }


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
        }else {
            isWindowActive = true;
        }
    }


    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        if (!isShowWindow){
            return false;
        }

        if (tiw.isShowWindow && tiw.checkTouch(isDragged, isTouchUp)){
            return true;
        }

        if (acw.isShowWindow && acw.checkTouch(isDragged, isTouchUp)){
            return true;
        }

        if (leftSection.checkTouch(store.mouseX, store.mouseY, isDragged, isTouchUp)){
            return true;
        }
        if (rightSection.checkTouch(store.mouseX, store.mouseY, isDragged, isTouchUp)){
            return true;
        }

        super.checkTouch(isDragged, isTouchUp);
        if (isShowWindow) {
            return true;
        }

        return false;
    }


    @Override
    public boolean checkKey(int keyCode){
        if (tiw.checkKey(keyCode)){
            return true;
        }

        return super.checkKey(keyCode);
    }

    public boolean keyTyped(char character){
        if (tiw.keyTyped(character)) {
            return true;
        }

        return false;
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
        boolean hasDescription = false;

//        button = new ButtonCommon();
//        button.setBackgrounds(buttonBG, buttonBGHover);
//        button.setText(font, "Добавить квест");
//        button.registerCallBack(
//            this,
//            "addQuestCallback"
//        );
//        button.setIcon(plusIcon);
//        questItems[i] = button;
//        i++;

        for (Object keyEl : quest.keySet()) {
            String key = keyEl.toString();

            if (key == "__name__") {
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);

                button.setText(font, (String) quest.get(key));
                button.registerCallBack(
                    this,
                    "editQuestTextField",
                    new String[]{uuid, "__name__"}
                );
                questItems[i] = button;
                i++;
            }

            if (key == "__description__") {
                hasDescription = true;
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);

                button.setText(font, (String) quest.get(key));
                button.registerCallBack(
                    this,
                    "editQuestTextField",
                    new String[]{uuid, "__description__"}
                );
                questItems[i] = button;
                i++;
            }
        }

        if (!hasDescription) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setIcon(plusIcon);
            button.setText(font, "Добавить описание");
            button.registerCallBack(
                this,
                "editQuestTextField",
                new String[]{uuid, "__description__"}
            );
            questItems[i] = button;
            i++;
        }

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(plusIcon);
        button.setText(font, "Добавить квестовую цель");
        button.registerCallBack(
            this,
            "addQuestOption",
            new String[]{uuid}
        );
        questItems[i] = button;
        i++;

//        Удаление квеста
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setIcon(trashIcon);
        button.setText(font, "Удалить квест");
        button.registerCallBack(
            this,
            "deleteQuestConfirm",
            new String[]{uuid}
        );
        questItems[i] = button;
        i++;

        questItems = Arrays.copyOfRange(questItems, 0, i);
    }
}