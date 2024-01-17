package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;


public class Editor extends View{
    Texture dot, tile;
    int[][]  map;
    int tileSizeX, tileSizeY;
    int shiftX = 0, shiftY = 0;
    int tileDownScale = 3;
    int selectedTileX, selectedTileY;

    public Editor(){
        tile = new Texture("tile_selector.png");
        dot = new Texture("dot.png");
        map = new int[][] {
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0}
        };
        tileSizeX = 158 / tileDownScale;
        tileSizeY = 158 / tileDownScale;
        shiftY = 0 * tileSizeY;
        shiftX = map.length * tileSizeX;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        Vector3 v = Main.viewport.getCamera().unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        float mouseInViewportX = v.x - shiftX - (10 / tileDownScale) ;
        float mouseInViewportY = v.y - shiftY + (60 / tileDownScale);
        float[] dotPoint = isometricToCartesian(mouseInViewportX, mouseInViewportY);
        selectedTileX = (int) ((dotPoint[0]) / tileSizeX) - 1;
        selectedTileY = (int) ((dotPoint[1]) / tileSizeY);
//        Gdx.app.log("Debug", String.valueOf(selectedTileX) + " : " + String.valueOf(selectedTileY));
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

        return false;
    }

    public float[] cartesianToIsometric(int x, int y){
        float isometricX = x - y ;
        float isometricY = (float) ((x + y) / 2);

        return new float[] {isometricX, isometricY};
    }

    public float[] isometricToCartesian(float x, float y){
        float decartX=(2*y+x)/2;
        float decartY=(2*y-x)/2;
        return new float[] {decartX, decartY} ;
    }

    public void render(SpriteBatch batch) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glClearColor(1, 1, 1, 1);

        for (int i=map.length; i > 0; i--)
        {
            int[] subMap = map[i-1];
            for (int j=subMap.length; j > 0; j--){
                int element = subMap[j-1];

                if (i == selectedTileX && j == selectedTileY){
                    float[] point = cartesianToIsometric((selectedTileX)*tileSizeX,(selectedTileY)*tileSizeY);
                    batch.draw(dot, point[0] + shiftX + tileSizeX, point[1] + shiftY + tileSizeY - (80 / tileDownScale));
                }

                float[] point = cartesianToIsometric(i*tileSizeX,j*tileSizeY);
                batch.draw(tile, point[0] + shiftX, point[1] + shiftY, tile.getWidth() / tileDownScale, tile.getHeight() / tileDownScale );

            }
        }
    }

}