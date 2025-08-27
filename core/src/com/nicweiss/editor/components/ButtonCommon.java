package com.nicweiss.editor.components;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.nicweiss.editor.Interfaces.BaseCallBack;
import com.nicweiss.editor.utils.Font;



public class ButtonCommon extends BaseCallBack {
    private Font font;
    private String text;
    public int textPadding = 10;
    int iconSize = 0;
    int lastUniqueId = -999;

    String key;

    Texture background, backgroundHover;
    Texture icon;

    public float textHeight = 10, textWidth = 10;

    public ButtonCommon() {}

    public void setText(Font font, String buttonText){
        this.font = font;
        text = buttonText;

        textHeight = font.getHeight(text);
        height = getTextHeight();

        textWidth = font.getWidth(text);
        width = getTextWidth();
    }

    public String getText(){
        return text;
    }

//    @Override
    public void execTouch() throws Exception {
        execCallBack();
    }

    public boolean checkTouchAndExec() {
        checkTouch(store.mouseX, store.mouseY);
        if (isTouched) {
            try {
                execTouch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return isTouched;
    }

//    Отключено так как компонент не может корректно понять когда нужно а когда не нужно срабатывать
//    public void checkPressAndExec(float x, float y) {
//        checkTouch(x, y);
//        if (isTouched) {
//            try {
//                for (int[] el : store.pressedKeys) {
//                    if (el[0] == 0 && lastUniqueId != el[1] && store.isTouchUp) {
//                        lastUniqueId = el[1];
//                        execTouch();
//                    }
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }

    @Override
    public void draw(Batch batch) {
        img = isTouched ? backgroundHover : background;
        super.draw(batch);

        if (icon != null){
            iconSize = height;
            batch.draw(icon, x  + textPadding, y + textPadding - 10, iconSize, iconSize);
        }
        font.draw( batch, text, x  + textPadding + (iconSize * 1.5f), y + textPadding);
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

    public void setDirective(String key){
        this.key = key;
    }

    public void setDirective(String key, String uuid){
        this.key = key;
        this.uuid = uuid;
    }

    public String getDirective(){
        return key;
    }

    public void setIcon(Texture icon) {
        this.icon = icon;
    }
}
