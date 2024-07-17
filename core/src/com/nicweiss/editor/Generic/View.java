package com.nicweiss.editor.Generic;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.concurrent.ThreadLocalRandom;


public abstract class View implements InputProcessor {
    public boolean isDragged = false;
    public static Store store;
    private int keyIter = 0;
    public int lastTouchedButton=0;
    public int lastDraggedX = 0, lastDraggedY = 0;

    public View(){
        for (int i = 0; store.pressedKeys.length > i; i++) {
            store.pressedKeys[i][0] = -999;
            store.pressedKeys[i][1] = -999;
        }
    }

    protected boolean storeKey(int keycode){
        int freeId = -1;

        for (int i = 0; i<store.pressedKeys.length; i++){
            if (store.pressedKeys[i][0] == keycode){
                return true;
            }
        }

        for (int i = 0; i<store.pressedKeys.length; i++){
            if (store.pressedKeys[i][0] == -999){
                freeId = i;
                break;
            }
        }

        if( freeId != -1) {
            store.pressedKeys[freeId][0] = keycode;
            store.pressedKeys[freeId][1] = ThreadLocalRandom.current().nextInt(124567896, 987456325 + 1);
        }

        keyIter = 0;

        return  false;
    }

    protected boolean releaseKey(int keycode){
        int releaseId = -1;

        for (int i = 0; i<store.pressedKeys.length; i++){
            if (store.pressedKeys[i][0] == keycode){
                releaseId = i;
                break;
            }
        }

        if( releaseId != -1) {
            store.pressedKeys[releaseId][0] = -999;
            store.pressedKeys[releaseId][1] = -999;
        }

        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        releaseKey(keycode);
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        storeKey(keycode);
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        storeKey(button);
//        fingerX = (screenX * Main.camera.viewportWidth) / Gdx.graphics.getWidth();
//        fingerX = (fingerX - Main.camera.viewportWidth / 2 + store.display.get("width") / 2);
//
//        fingerY = store.display.get("height") - ((screenY * store.display.get("height")) / Gdx.graphics.getHeight());
//
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        releaseKey(button);
        store.isDragged = isDragged = false;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!store.isDragged) {
            lastDraggedX = screenX;
            lastDraggedY = screenY;
        }

        store.isDragged = isDragged = true;
        touchDown(screenX,screenY,pointer,-1);
        lastDraggedX = screenX;
        lastDraggedY = screenY;

        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    public boolean touchCancelled(int sx, int sy, int ex, int ey) {return false;}

    public void render(SpriteBatch batch) {
        keyIter ++;

        if (keyIter > 20) {
            keyIter = 19;
            for (int i = 0; i < store.pressedKeys.length; i++) {
                int keyCode = store.pressedKeys[i][0];
                if (keyCode > 10) { // исключаем коды мышей
                    keyDown(keyCode);
                }
            }
        }
    }

    public void renderUI(SpriteBatch batch) {
    }

    public void destruct() {
    }
}