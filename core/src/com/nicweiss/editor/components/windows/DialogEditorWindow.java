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

/**
 * Класс {@code DialogEditorWindow} представляет окно редактора диалогов.
 * Он позволяет пользователю создавать, редактировать и связывать
 * диалоговые ветви.
 * Реализует интерфейс {@code CallBack} для обработки обратных вызовов.
 *
 * @author nicweiss
 */
public class DialogEditorWindow extends Window implements CallBack {
    private String uuid;
    public static Store store;

    BOHelper bo_helper;
    JSONObject dialog = new JSONObject();
    String[] dialogStack;
    TextInputWindow tiw = new TextInputWindow();
    JSONObject rootDialogList;

    int dialogCursor = 0;
    ButtonCommon[] items;

    /**
     * Конструктор класса. Инициализирует вспомогательные объекты
     * и устанавливает имя окна.
     */
    public DialogEditorWindow() {
        super();
        bo_helper = new BOHelper();
        rootDialogList = store.dialogs;
        windowName = "Редактирование взаимодействия";
    }

    /**
     * Обратный вызов для кнопки "Назад". Переходит на предыдущий
     * уровень в стеке диалогов.
     */
    public void getBackCallback(){
        if (tiw.isShowWindow || !isWindowActive) { return; }
        dialogCursor--;
        setUUID(dialogStack[dialogCursor]);
    }

    /**
     * Обратный вызов для выбора диалога. Переходит на новый
     * уровень диалога, добавляя его в стек.
     * @param uuid Уникальный идентификатор диалога, к которому нужно перейти.
     */
    public void selectDialogCallback(String uuid){
        if (tiw.isShowWindow || !isWindowActive) { return; }
        dialogCursor++;
        dialogStack[dialogCursor] = uuid;
        setUUID(uuid);
    }

    /**
     * Открывает окно для редактирования текста выбранного поля.
     * @param uuid Уникальный идентификатор диалога.
     * @param key Ключ поля, которое нужно отредактировать.
     */
    public void textEditCallback(String uuid, String key){
        if (tiw.isShowWindow || !isWindowActive) { return; }
        JSONObject obj = (JSONObject) rootDialogList.get(uuid);
        String value = (String) obj.get(key);

        tiw.registerCallBack(
            this,
            "textEditDoneCallback",
            new String[]{uuid,key,""}
        );
        tiw.setText(value);
        tiw.show();
    }

    /**
     * Обратный вызов, который обновляет значение поля после
     * завершения редактирования текста.
     * @param uuid Уникальный идентификатор диалога.
     * @param key Ключ отредактированного поля.
     * @param value Новое значение.
     */
    public void textEditDoneCallback(String uuid, String key, String value){
        JSONObject obj = (JSONObject) rootDialogList.get(uuid);
        obj.put(key, value);
        setUUID(uuid);
    }

    /**
     * Добавляет новую пустую ветвь диалога к текущей.
     * @param uuid Уникальный идентификатор родительского диалога.
     */
    public void addDialogLineCallback(String uuid) {
        JSONObject parent_dialog = (JSONObject) rootDialogList.get(uuid);
        JSONObject new_dialog = this.getEmptyDialog();

        String new_uuid = (String) new_dialog.get("__uuid__");
        rootDialogList.put(new_uuid, new_dialog);
        parent_dialog.put("__dialog__:" + new_uuid, "-->");
        this.selectDialogCallback(new_uuid);
    }

    /**
     * Инициализирует окно и его дочерние компоненты.
     */
    public void buildWindow() {
        super.buildWindow();
        tiw.buildWindow();
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }

    /**
     * Создает пустой объект диалога с заданным UUID.
     * @param uuid Уникальный идентификатор для нового диалога.
     * @return Объект {@code JSONObject} с пустым диалогом.
     */
    public JSONObject getEmptyDialog(String uuid){
        JSONObject obj = new JSONObject();

        obj.put("__uuid__", uuid);
        obj.put("__isBranchHide__", Boolean.FALSE);

        return obj;
    }

    /**
     * Создает пустой объект диалога с автоматически сгенерированным UUID.
     * @return Объект {@code JSONObject} с пустым диалогом.
     */
    public JSONObject getEmptyDialog(){ return getEmptyDialog(Uuid.generate());}

    /**
     * Метод отрисовки окна и его компонентов.
     * @param batch Объект {@code SpriteBatch} для отрисовки.
     */
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

    /**
     * Обрабатывает события касания или клика.
     * @param isDragged Флаг, указывающий, происходит ли перетаскивание.
     * @param isTouchUp Флаг, указывающий, был ли отпущен клик/касание.
     * @return {@code true} если событие было обработано, {@code false} в противном случае.
     */
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

    /**
     * Обрабатывает нажатия клавиш.
     * @param keyCode Код нажатой клавиши.
     * @return {@code true} если клавиша была обработана, {@code false} в противном случае.
     */
    @Override
    public boolean checkKey(int keyCode){
        if (tiw.checkKey(keyCode)){
            return true;
        }

        return super.checkKey(keyCode);
    }

    /**
     * Обрабатывает символьный ввод.
     * @param character Введенный символ.
     * @return {@code true} если символ был обработан, {@code false} в противном случае.
     */
    public boolean keyTyped(char character){
        if (tiw.keyTyped(character)) {
            return true;
        }

        return false;
    }

    /**
     * Устанавливает корневой узел для списка диалогов.
     * @param rootKey Ключ корневого узла.
     */
    public void setRoot(String rootKey) {
        if (store.dialogs.containsKey(rootKey)) {
            rootDialogList = (JSONObject) store.dialogs.get(rootKey);
        } else {
            JSONObject newRootDialogList = new JSONObject();
            store.dialogs.put(rootKey, newRootDialogList);
            rootDialogList = newRootDialogList;
        }
    }

    /**
     * Устанавливает текущий редактируемый диалог по его UUID.
     * @param uuid Уникальный идентификатор диалога.
     */
    public void setUUID(String uuid){
        this.uuid = uuid;

        if (!isShowWindow){
            dialogStack = new String[500];
            dialogStack[0] = uuid;
            dialogCursor = 0;
        } else {
            for (int i = 0; i < dialogStack.length; i++) {
                if (dialogStack[i] != null && dialogStack[i].equals(uuid)) {
                    dialogCursor = i;
                    break;
                }
            }
        }

        dialog = (JSONObject) rootDialogList.get(uuid);

        if (dialog == null){
            dialog = getEmptyDialog(uuid);
            rootDialogList.put(uuid, dialog);
        }

        prepareDialogView();
    }

    /**
     * Подготавливает и отображает кнопки для текущего диалога,
     * основываясь на его полях.
     */
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

//        Кнопка "Возвращение"
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
        } else if (dialogCursor > 0) {
            button = new ButtonCommon();
            button.setBackgrounds(buttonBG, buttonBGHover);
            button.setText(font, "Добавить обращение к собеседнику");
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

//        Ответ собеседника
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
                button.setText(font, "Добавить ответ собеседника");
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
                JSONObject subDialog = (JSONObject) rootDialogList.get(subKeys[1]);
                dialog.replace(key, subDialog);
                if (subDialog.get("__request__") != null) {
                    button.setText(font, subDialog.get("__request__").toString());
                } else {
                    button.setText(font, "");
                }
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
        button.registerCallBack(this, "addDialogLineCallback", new String[]{uuid});
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