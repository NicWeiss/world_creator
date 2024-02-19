package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.Function;

class RecourceLoader implements Callable<String> {
    Editor localEditorClass;
    public static Store store;

    public RecourceLoader(Editor editorClass) {
        localEditorClass = editorClass;
    }

    @Override
    public String call() throws Exception {
        localEditorClass.defineMap();
        localEditorClass.defineUI();
        store.isEditorLoadComplete = true;

        return null;
    }
}

public class Logo extends View{
    Texture img;
    Editor editorClass;

    public Logo(){
        img = new Texture("logo.png");
        editorClass = new Editor();
        RecourceLoader resurceLoader = new RecourceLoader(editorClass);

        FutureTask<String> task = new FutureTask(resurceLoader);
        Thread myThready = new Thread(task);
        myThready.start();
    }

    private void openMenu(){
        Main.changeView(editorClass);
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

        if (store.isEditorLoadComplete) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            openMenu();
        }
    }

}