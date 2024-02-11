package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;

import java.util.function.Function;


public class Logo extends View{
    Texture img;

    public Logo(){
        img = new Texture("logo.png");
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        openMenu();
        return false;
    }

    private void openMenu(){
        Main.changeView(new Editor());
    }

    public void render(SpriteBatch batch) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glClearColor(1, 1, 1, 1);

        float displayHeight = store.display.get("height");
        float displayWidth = store.display.get("width");

        float imgSizeX = 600;
        float imgSizeY = 380;

        batch.draw(img, (displayWidth / 2) - (imgSizeX / 2),(displayHeight / 2) - (imgSizeY / 2), imgSizeX, imgSizeY);
        batch.setColor(1,1,1, 1);
    }

}