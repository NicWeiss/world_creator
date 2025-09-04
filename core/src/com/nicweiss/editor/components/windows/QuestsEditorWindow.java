package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.Uuid;

import org.json.simple.JSONObject;

import java.util.Arrays;


public class QuestsEditorWindow extends Window implements CallBack {
    private String uuid;
    public static Store store;

    BOHelper bo_helper;
    TextInputWindow tiw = new TextInputWindow();
    JSONObject questsList;

    ButtonCommon[] items;

    public QuestsEditorWindow() {
        super();
        bo_helper = new BOHelper();
        questsList = store.quests;
        windowName = "Редактирование квестов";
    }

    @Override
    public void onShow(){
        prepareQuestsView();
    }

    public void getBackCallback(){
//        if (tiw.isShowWindow || !isWindowActive) { return; }
//        dialogCursor--;
//        setUUID(dialogStack[dialogCursor]);
    }

    public void selectQuestCallback(String uuid){
//        if (tiw.isShowWindow || !isWindowActive) { return; }
//        dialogCursor++;
//        dialogStack[dialogCursor] = uuid;
//        setUUID(uuid);
    }

    public void textEditCallback(String uuid, String key){
//        if (tiw.isShowWindow || !isWindowActive) { return; }
//        JSONObject obj = (JSONObject) rootDialogList.get(uuid);
//        String value = (String) obj.get(key);
//
//        tiw.registerCallBack(
//            this,
//            "textEditDoneCallback",
//            new String[]{uuid,key,""}
//        );
//        tiw.setText(value);
//        tiw.show();
    }

    public void textEditDoneCallback(String uuid, String key, String value){

    }


    public void addQuestCallback() {
        System.out.println("addQuestCallback");
//        JSONObject parent_dialog = (JSONObject) rootDialogList.get(uuid);
//        JSONObject new_dialog = this.getEmptyDialog();
//
//        String new_uuid = (String) new_dialog.get("__uuid__");
//        rootDialogList.put(new_uuid, new_dialog);
//        parent_dialog.put("__dialog__:" + new_uuid, "-->");
//        this.selectDialogCallback(new_uuid);
    }

    public void buildWindow() {
        setDualSectionMode(true);
        super.buildWindow();
        tiw.buildWindow();
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }


    public void render(SpriteBatch batch) {
        super.render(batch);

        if (isShowWindow) {
            renderItemsList(batch, items, false);
//            rightSection.renderItemsList(batch, items, false);
        }

        if (tiw.isShowWindow) {
            tiw.render(batch);
            isWindowActive = false;
        } else {
            isWindowActive = true;
        }
    }


    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        if (!isShowWindow){
            return false;
        }

        if (tiw.checkTouch(isDragged, isTouchUp) || tiw.isShowWindow){
            return true;
        }

        if (isTouchUp && items != null) {
            for (ButtonCommon item : items) {
                item.checkTouchAndExec();
            }
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

        Texture buttonBG, buttonBGHover;
        buttonBG = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");

        Texture plusIcon = new Texture("icons/quest_window/plus.png");
        Texture questIcon = new Texture("icons/quest_window/quest.png");

        ButtonCommon button;

        int i = 0;

        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "Добавить квест");
        button.registerCallBack(
            this,
            "addQuestCallback"
        );
        button.setIcon(plusIcon);
        items[i] = button;
        i++;

        for (Object keyEl: questsList.entrySet()) {
            String key = keyEl.toString();
            JSONObject quest = (JSONObject) questsList.get(key);

            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);

            button.setText(font, (String) quest.get("__name__"));
            button.registerCallBack(
                this,
                "selectQuestCallback",
                new String[]{(String) quest.get("uuid")}
            );
            button.setIcon(questIcon);
            items[i] = button;
            i++;
        }


        items = Arrays.copyOfRange(items, 0, i);
    }
}