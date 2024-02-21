package com.nicweiss.editor.Generic;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;


public abstract class View implements InputProcessor {
    public boolean isDragged = false;
    public static Store store;
    public int[] pressedKeys = new int[100];
    private int keyIter = 0;

    private boolean storeKey(int keycode){
        int freeId = -1;

        for (int i = 0; i<pressedKeys.length; i++){
            if (pressedKeys[i] == keycode){
                return true;
            }
        }

        for (int i = 0; i<pressedKeys.length; i++){
            if (pressedKeys[i] == 0){
                freeId = i;
                break;
            }
        }

        if( freeId != -1) {
            pressedKeys[freeId] = keycode;
        }

        keyIter = 0;

        return  false;
    }

    protected boolean releaseKey(int keycode){
        int releaseId = -1;

        for (int i = 0; i<pressedKeys.length; i++){
            if (pressedKeys[i] == keycode){
                releaseId = i;
                break;
            }
        }

        if( releaseId != -1) {
            pressedKeys[releaseId] = 0;
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
//        fingerX = (screenX * Main.camera.viewportWidth) / Gdx.graphics.getWidth();
//        fingerX = (fingerX - Main.camera.viewportWidth / 2 + store.display.get("width") / 2);
//
//        fingerY = store.display.get("height") - ((screenY * store.display.get("height")) / Gdx.graphics.getHeight());
//
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        store.isDragged = isDragged = false;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        store.isDragged = isDragged = true;
        touchDown(screenX,screenY,pointer,0);
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
            keyIter = 17;
            for (int i = 0; i < pressedKeys.length; i++) {
                int keyCode = pressedKeys[i];
                if (keyCode != 0) {
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