package com.nicweiss.editor.creations;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;

public class Creation extends BaseObject {
    public int mapCellX, mapCellY;
    private float positionX, positionY;

    public void setPosition(float _x, float _y) {
        positionX = x = _x;
        positionY = y = _y;
    }

    public void setCell( int x, int y){
        mapCellX = x;
        mapCellY = y;
    }

    public void draw(SpriteBatch batch){
        x = (int) (positionX + store.shiftX);
        y = (int) (positionY + store.shiftY);
        width = img.getWidth() / store.tileDownScale;
        height = img.getHeight() / store.tileDownScale;

        super.draw(batch);
    }
}
