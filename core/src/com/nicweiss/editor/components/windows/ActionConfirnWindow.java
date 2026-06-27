package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.components.ButtonCommon;


public class ActionConfirnWindow extends Window{
    public static Store store;
    private String confirmText = "";
    private Integer confirmHeight = 10;

    Texture whiteColor;

    public ActionConfirnWindow() {
        super();
        windowName = "Подтверждение действия";
        windowWidth = 300;
        windowHeight = 100;

        whiteColor = new Texture("white.png");
    }

    public void buildWindow(){
        controlButtons = new ButtonCommon[2];
        controlButtons[0] = createControlButton(ActionConfirnWindow.class, "cancel", "Cancel");
        controlButtons[1] = createControlButton(ActionConfirnWindow.class, "confirm", "Confirm");
        windowColor = whiteColor;

        isScrollHidden = true;

        super.buildWindow();
    }

    @Override
    public void onShow() {
        repositionToCenter();
    }

    @Override
    public  void onHide(){

    }

    public void setText(String text) {
        confirmText = text;
        confirmHeight = (int) font.getHeight(confirmText) + 10;
    }

    public void cancel() {
        this.hide();
    }

    public void confirm() throws Exception {
        this.execCallBack();
        this.hide();
    }

    public void render(SpriteBatch batch) {
        super.render(batch);

        font.draw(
            batch, confirmText ,
            x + 20 , y + height - confirmHeight - windowHeader.getHeight()
        );
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        if (!isShowWindow) {
            return false;
        }

        super.checkTouch(isDragged, isTouchUp);

        if (isShowWindow) {
            return true;
        }

        return false;
    }

}
