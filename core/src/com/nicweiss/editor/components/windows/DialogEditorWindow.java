package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.ObjectCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.BOHelper;
import com.nicweiss.editor.utils.Uuid;

import org.json.simple.JSONObject;

import java.util.Arrays;

public class DialogEditorWindow extends Window implements CallBack  {
    private String uuid;
    public static Store store;
    BOHelper bo_helper;
    JSONObject dialog = new JSONObject();
    String[] dialogStack;
    TextInputWindow tiw = new TextInputWindow();

    int dialogCursor = 0;
    boolean isDialogLoaded = false;

    ButtonCommon[] items;

    public DialogEditorWindow() {
        super();
        bo_helper = new BOHelper();
        windowName = "Редактирование взаимодействия";
    }

    public void getBackCallback(){
        if (tiw.isShowWindow || !isWindowActive) { return; }
        dialogCursor--;
        setUUID(dialogStack[dialogCursor]);
    }

    public void selectDialogCallback(String uuid){
        if (tiw.isShowWindow || !isWindowActive) { return; }
        dialogCursor++;
        dialogStack[dialogCursor] = uuid;
        setUUID(uuid);
    }

    public void textEditCallback(String uuid, String key){
        if (tiw.isShowWindow || !isWindowActive) { return; }
        tiw.show();

//        JSONObject obj = (JSONObject) store.dialogs.get(uuid);
//        obj.put(key, "New value");
//        setUUID(uuid);
    }

    public void buildWindow() {
        super.buildWindow();
        tiw.buildWindow();
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }

    public void loadDialogs(String uuid){
        JSONObject obj = new JSONObject();
        JSONObject ob3 = new JSONObject();
        JSONObject ob4 = new JSONObject();
        JSONObject ob5 = new JSONObject();

// конструктор для отладки
        ob3.put("__uuid__", "jjgyqwYgwg4");
        ob3.put("__branchRestrictions__", "__activeQuest__:1");
        ob3.put("__isBranchHide__", Boolean.FALSE);
        ob3.put("__request__", "Я не знаю узнаю это место, где мы?");
        ob3.put("__response__", "Это сердце тёмного леса, без надёжного топора тут лучше не ходить. На твоё счастье у меня есть один запасной, вот держи!");
        ob3.put("__goTo__:"+uuid, "У меня ещё есть вопросоы");
        ob3.put("__close__", "Спасибо, до встречи!");
        ob3.put("__farewell__", "Будь осторожнее!");
        ob3.put("__onClose__",  "__giveWeapon__:1;__hideBranch__:Sfwi3wgkfd");
        store.dialogs.put("jjgyqwYgwg4", ob3);

        ob4.put("__uuid__", "Sfwi3wgkfd");
        ob4.put("__branchRestrictions__", "__activeQuest__:1");
        ob4.put("__isBranchHide__", Boolean.FALSE);
        ob4.put("__request__", "Кто ты?");
        ob4.put("__response__", "Это не имеет особого значения, важно то, почему ты в такой глуши и без какого либо снаряжения?");
        ob4.put("__dialog__:jjgyqwYgwg4", "");
        store.dialogs.put("Sfwi3wgkfd", ob4);


        ob5.put("__uuid__", "TYYwfwgkbe");
        ob5.put("__branchRestrictions__", "__activeQuest__:1");
        ob5.put("__isBranchHide__", Boolean.FALSE);
        ob5.put("__request__", "Как отсюда выбраться?");
        ob5.put("__response__", "Иди на северо-восток и выйдешь к \"Крайней деревне\"");
        ob5.put("__goTo__:"+uuid, "У меня ещё есть вопросоы");
        ob5.put("__close__", "Спасибо, до встречи!");
        ob5.put("__farewell__", "Будь осторожнее!");
        ob5.put("__onClose__", "__setMarker__:1312312312321;__hideBranch__:TYYwfwgkbe");
        store.dialogs.put("TYYwfwgkbe", ob5);


        obj.put("__uuid__", uuid);
        obj.put("__isBranchHide__", Boolean.FALSE);
        obj.put("__greeting__", "Здравствуй путник");
        obj.put("__response__", "Чем я могу тебе помочь?");
        obj.put("__close__", "Пока");
        obj.put("__farewell__", "Будь осторожнее!");
        obj.put("__dialog__:Sfwi3wgkfd", "");
        obj.put("__dialog__:TYYwfwgkbe", "");
        store.dialogs.put(uuid, obj);
        isDialogLoaded = true;
    }

    public JSONObject getEmptyDialog(String uuid){
        JSONObject obj = new JSONObject();

        obj.put("__uuid__", uuid);
        obj.put("__isBranchHide__", Boolean.FALSE);

        return obj;
    }

    public JSONObject getEmptyDialog(){ return getEmptyDialog(Uuid.generate());}

    public void render(SpriteBatch batch) {
        super.render(batch);

        if (isShowWindow) {
            renderItemsList(batch, items, false);
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

    public void setUUID(String uuid){
        this.uuid = uuid;

        if (!isShowWindow){
            dialogStack = new String[500];
            dialogStack[0] = uuid;
            dialogCursor = 0;
        } else {
            for (int i = 0; i < dialogStack.length; i++) {
                if (dialogStack[i].equals(uuid)) {
                    dialogCursor = i;
                    break;
                }
            }
        }

//        if (!isDialogLoaded) {
//            loadDialogs(uuid);
//        }

        dialog = (JSONObject) store.dialogs.get(uuid);

        if (dialog == null){
            dialog = getEmptyDialog(uuid);
            store.dialogs.put(uuid, dialog);
        }

        prepareDialogView();
    }

    public void prepareDialogView(){
        items = new ButtonCommon[1000];

        Texture buttonBG, buttonBGHover;
        buttonBG = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");

        Texture restrictionIcon = new Texture("icons/dialog_window/restriction.png");
        Texture farewellIcon = new Texture("icons/dialog_window/farewell.png");
        Texture greetingIcon = new Texture("icons/dialog_window/greeting.png");
        Texture hideIcon = new Texture("icons/dialog_window/hide.png");
        Texture replicIcon = new Texture("icons/dialog_window/replic.png");
        Texture requestIcon = new Texture("icons/dialog_window/request.png");
        Texture responseIcon = new Texture("icons/dialog_window/response.png");
        Texture closeIcon = new Texture("icons/dialog_window/close.png");
        Texture actionIcon = new Texture("icons/dialog_window/action.png");
        Texture addDialogIcon = new Texture("icons/dialog_window/add_dialog.png");

        ButtonCommon button;

        int i = 0;

//        Возвращение
        if (dialogCursor > 0) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, "...");
            button.registerCallBack(this, "getBackCallback");
            items[i] = button;
            i++;
        }

        if (dialog.get("__branchRestrictions__") != null){
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, dialog.get("__branchRestrictions__").toString());
            button.setIcon(restrictionIcon);
            items[i] = button;
            i++;
        }

//        состояние отображения
        if (dialog.get("__isBranchHide__") != null){
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, dialog.get("__isBranchHide__").toString());
            button.setIcon(hideIcon);
            items[i] = button;
            i++;
        }

//        реплика от собеседника
        if (dialog.get("__request__") != null){
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, dialog.get("__request__").toString());
            button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__request__"});
            button.setIcon(requestIcon);
            items[i] = button;
            i++;
        } else if (dialog.get("__greeting__") == null) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, "Добавить реплику от собеседника");
            button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__request__"});
            button.setIcon(addDialogIcon);
            items[i] = button;
            i++;
        }

//        Приветствие
        if (dialog.get("__greeting__") != null){
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, dialog.get("__greeting__").toString());
            button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__greeting__"});
            button.setIcon(greetingIcon);
            items[i] = button;
            i++;
        } else {
            if (dialogCursor == 0) {
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setText(font, "Добавить приветствие");
                button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__greeting__"});
                button.setIcon(addDialogIcon);
                items[i] = button;
                i++;
            }
        }

//        Ответ собеседнику
        if (dialog.get("__response__") != null){
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, dialog.get("__response__").toString());
            button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__response__"});
            button.setIcon(responseIcon);
            items[i] = button;
            i++;
        } else {
            if (dialogCursor > 0) {
                button = new ButtonCommon();
                button.setBackgrounds(buttonBG, buttonBGHover);
                button.setText(font, "Добавить ответ собеседнику");
                button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__response__"});
                button.setIcon(addDialogIcon);
                items[i] = button;
                i++;
            }
        }

//        Ветви диалога
        for (Object el: dialog.keySet()) {
            String key = el.toString();
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);

            if (key.contains("__dialog__")) {
                String[] subKeys = key.split(":");
                JSONObject subDialog = (JSONObject) store.dialogs.get(subKeys[1]);
                dialog.replace(key, subDialog);
                button.setText(font, subDialog.get("__request__").toString());
                button.registerCallBack(this, "selectDialogCallback", new String[]{subKeys[1]});
                button.setIcon(replicIcon);
                items[i] = button;
                i++;
            }
        }

//        Переходы на другие ветви
        for (Object el: dialog.keySet()) {
            String key = el.toString();
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);

            if (key.contains("__goTo__")) {
                String[] subKeys = key.split(":");
                button.setText(font, dialog.get(key).toString());
                button.registerCallBack(this, "selectDialogCallback", new String[]{subKeys[1]});
                button.setIcon(replicIcon);
                items[i] = button;
                i++;
            }
        }

//      add Dialog button
        button = new ButtonCommon();
        button.setBackgrounds(buttonBG, buttonBGHover);
        button.setText(font, "Добавить реплику");
        button.setIcon(addDialogIcon);
        items[i] = button;
        i++;

//        Строка закрытия
        if (dialog.get("__close__") != null){
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, dialog.get("__close__").toString());
            button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__close__"});
            button.setIcon(closeIcon);
            items[i] = button;
            i++;
        } else {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, "Добавить реплику для завершения диалога");
            button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__close__"});
            button.setIcon(addDialogIcon);
            items[i] = button;
            i++;
        }

//        Строка действий при закрытии
        if (dialog.get("__onClose__") != null){
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, dialog.get("__onClose__").toString());
            button.setIcon(actionIcon);
            items[i] = button;
            i++;
        } else {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, "Добавить события при завершении диалога");
            button.setIcon(addDialogIcon);
            items[i] = button;
            i++;
        }

//        Реплика прощания
        if (dialog.get("__farewell__") != null){
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, dialog.get("__farewell__").toString());
            button.setIcon(farewellIcon);
            button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__farewell__"});
            items[i] = button;
            i++;
        } else {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, "Добавить прощальную реплику собеседника");
            button.registerCallBack(this, "textEditCallback", new String[]{uuid, "__farewell__"});
            button.setIcon(addDialogIcon);
            items[i] = button;
            i++;
        }

        items = Arrays.copyOfRange(items, 0, i);
    }
}
