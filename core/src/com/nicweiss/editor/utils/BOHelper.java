package com.nicweiss.editor.utils;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.Generic.Store;

public class BOHelper {
    public static Store store;

    public BaseObject constructObject(Texture texture, int x, int  y, int w, int h, String objId, int textureId){
        BaseObject tmp = new BaseObject();

        tmp.setTexture(texture);
        tmp.setX(x);
        tmp.setY(y);
        tmp.setWidth(w);
        tmp.setHeight(h);
        tmp.setObjectId(objId);
        tmp.setTextureId(textureId);

        return tmp;
    }

    public void draw(SpriteBatch batch, BaseObject bo, int x, int y){
        bo.setX(x);
        bo.setY(y);
        bo.draw(batch);
        bo.checkTouch(store.mouseX, store.mouseY);
    }

    public void draw(SpriteBatch batch, BaseObject bo, int x, int y, boolean check){
        bo.setX(x);
        bo.setY(y);
        bo.draw(batch);
        if (check) {
            bo.checkTouch(store.mouseX, store.mouseY);
        }
    }

    public void draw(SpriteBatch batch, BaseObject bo, int x, int y, int w, int h){
        bo.setX(x);
        bo.setY(y);
        bo.setWidth(w);
        bo.setHeight(h);
        bo.draw(batch);
        bo.checkTouch(store.mouseX, store.mouseY);
    }

    public void drawWithSize(SpriteBatch batch, BaseObject bo, int w, int h){
        bo.setWidth(w);
        bo.setHeight(h);
        bo.draw(batch);
        bo.checkTouch(store.mouseX, store.mouseY);
    }

    public  void drawButton(SpriteBatch batch, BaseObject bo,BaseObject bg, int x, int y){
        if (bo.isTouched){
            bg.setX(bo.getX() - 5);
            bg.setY(bo.getY() - 5);
            bg.setWidth((int)bo.getWidth() + 10);
            bg.setHeight((int)bo.getHeight() + 10);
            bg.draw(batch);
        }

        draw(batch, bo, x,y);
    }
}
