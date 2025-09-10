package com.nicweiss.editor.components.windows;

import static com.nicweiss.editor.Main.stage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.components.ButtonCommon;


public class TextInputWindow extends Window{
    public static Store store;

    private Skin skin;
    private TextArea textArea;

    Texture whiteColor;

    public TextInputWindow() {
        super();
        windowName = "Редактирование";
        windowWidth = 600;
        windowHeight = 200;

        whiteColor = new Texture("white.png");

        skin = new Skin(Gdx.files.internal("data/uiskin.json")); // Replace with your skin path
        textArea = new TextArea("...", skin);
        stage.addActor(textArea);
    }

    public void buildWindow(){
        controlButtons = new ButtonCommon[2];
        controlButtons[0] = createControlButton(TextInputWindow.class, "cancel", "Cancel");
        controlButtons[1] = createControlButton(TextInputWindow.class, "apply", "Save");
        windowColor = whiteColor;

        isScrollHidden = true;

        super.buildWindow();
    }

    @Override
    public void onShow() {
        stage.setKeyboardFocus(textArea);
    }

    public void setText(String text) {
        textArea.setText(text);
    }

    public void cancel() {
        textArea.setText("");
        this.hide();
    }

    public void apply() throws Exception {
        this.setParams(2, textArea.getText());
        this.execCallBack();
        textArea.setText("");
        this.hide();
    }

    public void render(SpriteBatch batch) {
        super.render(batch);
        textArea.setBounds(x + 5,y, width-10, windowOperationalHeight);

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    public boolean checkTouch(boolean isDragged, boolean isTouchUp){
        super.checkTouch(isDragged, isTouchUp);

        if (isShowWindow) {
            stage.setKeyboardFocus(textArea);
            return true;
        }

        return false;
    }

    @Override
    public boolean checkKey(int keyCode){
        if (!isShowWindow || !isWindowActive) {
            return false;
        }

        if (keyCode == 157) {
            stage.keyDown(19);
            stage.keyUp(19);
            return true;
        }

        if (keyCode == 156) {
            stage.keyDown(20);
            stage.keyUp(20);
            return true;
        }

        return super.checkKey(keyCode);
    }

    public boolean keyTyped(char character) {
        if (!isShowWindow || !isWindowActive) {
            return false;
        }
        return true;
    }


}
