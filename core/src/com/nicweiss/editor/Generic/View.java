package com.nicweiss.editor.Generic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import com.nicweiss.editor.Main;


public abstract class View implements InputProcessor {
    public float fingerX, fingerY;
    public static Store store;

    public void init() {
    }


    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        fingerX = (screenX * Main.camera.viewportWidth) / Gdx.graphics.getWidth();
        fingerX = (fingerX - Main.camera.viewportWidth / 2 + store.display.get("width") / 2);

        fingerY = store.display.get("height") - ((screenY * store.display.get("height")) / Gdx.graphics.getHeight());

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
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

    }

    public void destruct() {

    }

//    @Override
//    protected void finalize() throws Throwable {
//        super.finalize();
//        Gdx.app.log("Debug", "View finalize");
//    }
}