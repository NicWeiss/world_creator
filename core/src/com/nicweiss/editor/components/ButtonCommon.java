package com.nicweiss.editor.components;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.nicweiss.editor.Interfaces.ObjectCallBack;
import com.nicweiss.editor.utils.Font;



public class ButtonCommon extends ObjectCallBack {
    private Font font;
    private String text;
    public int textPadding = 10;

    Texture background, backgroundHover;

    public float textHeight = 10, textWidth = 10;

    public ButtonCommon() {}

    public void setText(Font font, String buttonText){
        this.font = font;
        text = buttonText;
        textHeight = font.getHeight(text);
        textWidth = font.getWidth(text);
    }

//    @Override
    public void execTouch() throws Exception {
        execCallBack();
    }

    public boolean checkTouchAndExec(float x, float y) {
        checkTouch(x, y);
        if (isTouched) {
            try {
                execTouch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return isTouched;
    }

    @Override
    public void draw(Batch batch) {
        img = isTouched ? backgroundHover : background;
        super.draw(batch);
        font.draw( batch, text, x  + textPadding, y + textPadding);
    }

    public int getTextHeight(){
        return (int) (textHeight + (textPadding * 2));
    }

    public int getTextWidth(){
        return (int) (textWidth + (textPadding *2));
    }

    public void setBackgrounds(Texture background, Texture backgroundHover){
        this.background = background;
        this.backgroundHover = backgroundHover;
    }
}
