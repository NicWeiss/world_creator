package com.nicweiss.editor.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.utils.Font;


public class TextObject extends BaseObject {
    private Font font;
    private String text;

    public TextObject(Font font, String text) {
        this.font = font;
        this.text = text;
    }

    @Override
    public void draw(Batch batch){
        font.draw(batch, text ,x, y);
    }
}
