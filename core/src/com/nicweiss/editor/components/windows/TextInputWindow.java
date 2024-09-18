package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.components.ButtonCommon;

public class TextInputWindow extends Window {
    public static Store store;

    protected int time = 100;
    protected String text = "";
    protected String input = "";
    protected String inputSymbol = "_";
    protected int cursor = 0;

    Texture whiteColor;

    public TextInputWindow() {
        super();
        windowName = "Редактирование";
        windowWidth = 600;
        windowHeight = 200;

        whiteColor = new Texture("white.png");
    }

    public void buildWindow(){
        controlButtons = new ButtonCommon[2];
        controlButtons[0] = createControlButton(TextInputWindow.class, "cancel", "Cancel");
        controlButtons[1] = createControlButton(TextInputWindow.class, "apply", "Save");
        windowColor = whiteColor;

        super.buildWindow();
    }

    public void cancel() {

        System.out.println("You chose cancel");
    }


    public void apply() {

        System.out.println("You chose apply");
    }

    public void render(SpriteBatch batch) {
        super.render(batch);

        if (inputSymbol == "_") {
            if (time == 0){
                inputSymbol = "..";
                buildText();
                time = 30;
            } else {
                time --;
            }
        } else {
            if (time == 0){
                inputSymbol = "_";
                buildText();
                time = 30;
            } else {
                time --;
            }
        }

        renderText(batch, text);
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        super.checkTouch(isDragged, isTouchUp);

        if (isShowWindow) {
            return true;
        }

        return false;
    }

    @Override
    public boolean checkKey(int keyCode){
        if (!isShowWindow || !isWindowActive) {
            return false;
        }

        inputSymbol = "_";
        time = 100;

        if (keyCode == 22) {
            cursor++;
            cursor = Math.min(cursor, input.length());
            buildText();
            return true;
        }
        if (keyCode == 21) {
            cursor--;
            cursor = Math.max(cursor, 0);
            buildText();
            return true;
        }

        return super.checkKey(keyCode);
    }

    public boolean keyTyped(char character){
        if (!isShowWindow || !isWindowActive) {
            return false;
        }

        inputSymbol = "_";
        time = 100;

        if (("" + character).equals("\b") && input.length() > 0 ){
            if (cursor == 0){
                return true;
            }

            input = input.substring(0, cursor - 1) + input.substring(cursor);
            cursor--;
            buildText();

            if (cursor == input.length()) {
                scrollDown();
            }

            return true;
        }

        input = input.substring(0, cursor) + character + input.substring(cursor);

        if (("" + character).equals("\n")){
            checkKey(20);
        }

        cursor++;
        buildText();

        if (cursor == input.length()) {
            scrollDown();
        }
        
        return true;
    }

    protected void buildText(){
        text = input.substring(0, cursor) + inputSymbol + input.substring(cursor);
    }
}
