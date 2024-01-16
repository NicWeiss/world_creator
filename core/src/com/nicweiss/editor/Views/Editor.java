package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.View;


public class Editor extends View{
    Texture tile;
    float opacity;
    int counter;
    int[][]  map;
    int tileSizeX;
    int tileSizeY;
    int shiftX = 0;
    int shiftY = 0;

    public Editor(){
        tile = new Texture("tile_selector.png");
        map = new int[][] {{0, 0, 0, 0, 0}, {0, 0, 0, 0, 0}, {0, 0, 0, 0, 0}, {0, 0, 0, 0, 0}, {0, 0, 0, 0, 0}};
        tileSizeX = 158;
        tileSizeY = 158;
        shiftY = 0 * tileSizeY;
        shiftX = 5 * tileSizeX;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean keyDown(int keyCode){
        if (keyCode == 19) {
            shiftY = shiftY - tileSizeY;
        }

        if (keyCode == 20) {
            shiftY = shiftY + tileSizeY;
        }

        if (keyCode == 21) {
            shiftX = shiftX + tileSizeX;
        }

        if (keyCode == 22) {
            shiftX = shiftX - tileSizeX;
        }

        Gdx.app.log("Debug", String.valueOf(tileSizeX) + " : " + String.valueOf(tileSizeY));

        return false;
    }

    public float[] cartesianToIsometric(int x, int y){
        float isometricX = x - y ;
        float isometricY = (float) ((x + y) / 1.8);

        return new float[] {isometricX, isometricY};
    }

    public void render(SpriteBatch batch) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glClearColor(1, 1, 1, 1);

        for (int i=0; i < map.length; i++)
        {
            int[] subMap = map[i];
            for (int j=0; j < subMap.length; j++){
                int element = subMap[j];

                float[] point = cartesianToIsometric(i*tileSizeX,j*tileSizeY);
                batch.draw(tile, point[0] + shiftX, point[1] + shiftY);

            }
        }
    }

}