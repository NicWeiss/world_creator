package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;
import com.nicweiss.editor.utils.FileManager;

import java.util.Random;


public class Editor extends View{
    Texture dot, tile;
    Texture[] textures;

    FileManager fileManager;

    int[][]  map;
    int tileSizeX, tileSizeY;
    int tileDownScale = 3;
    int selectedTileX, selectedTileY;
    int mapHeight = 150, mapWidth = 150;
    int shiftX = 0, shiftY = 0;

    public Editor(){
        fileManager = new FileManager();
        tile = new Texture("tile_selector.png");
        dot = new Texture("dot.png");
        textures = new Texture[] {
                new Texture("gp_0.png"),
                new Texture("gp_1.png"),
                new Texture("gp_2.png"),
                new Texture("gp_3.png"),
                new Texture("gp_4.png"),
                new Texture("gp_5.png"),
                new Texture("gp_6.png"),
                new Texture("gp_7.png"),
                new Texture("gp_8.png"),
                new Texture("gp_9.png")

        };

        defineMap();

        tileSizeX = 158 / tileDownScale;
        tileSizeY = 158 / tileDownScale;
        shiftY = 0;
        shiftX = 12 * tileSizeX;
    }

    private void defineMap() {

        Random rand = new Random();
        map = new int[mapHeight][mapWidth];
        int rn = 0, ts = 0;

        for(int i = 0; i<mapHeight; i++) {
            for(int j = 0; j<mapWidth; j++) {
                rn = rand.nextInt(101);
                ts = 1;
                if (rn > 30){ts=8;}
                if (rn > 60){ts=3;}
                if (rn > 80){ts=2;}

                if (rn == 96){ts=4;}
                if (rn == 97){ts=5;}
                if (rn == 98){ts=6;}
                if (rn == 99){ts=7;}
                if (rn == 100){ts=9;}
                map[i][j] = ts;
            }
        }
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
//        Gdx.app.log("Debug", String.valueOf(selectedTileX) + " : " + String.valueOf(selectedTileY));
        mouseMoved(screenX,screenY);
        int arrPointX = selectedTileX-1;
        int arrPointY = selectedTileY-1;

        if (
            arrPointX >= 0 &&
            arrPointX < mapHeight &&
            arrPointY >= 0 &&
            arrPointY < mapWidth
        ) {
            map[arrPointX][arrPointY] =1;
        }
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
        Gdx.app.log("Debug", String.valueOf(keyCode));
        super.keyDown(keyCode);
        if (keyCode == 19) {
            shiftY = shiftY - tileSizeY;
        }

        if (keyCode == 20) {
            shiftY = shiftY + tileSizeY;
        }

        if (keyCode == 21) {
            shiftX = shiftX + (tileSizeX*2);
        }

        if (keyCode == 22) {
            shiftX = shiftX - (tileSizeX*2);
        }

        if (keyCode == 157) {
            store.cameraUpScale();
        }

        if (keyCode == 156) {
            store.cameraDownScale();
        }

        if (keyCode == 155) {
            fileManager.saveMap(map, mapWidth, mapHeight);
        }

        if (keyCode == 154) {
            map = fileManager.openMap();
            mapHeight = fileManager.mapHeight;
            mapWidth = fileManager.mapHeight;
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

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glClearColor(1, 1, 1, 1);

        float[] cursorPoint = cartesianToIsometric(-1,-1);

//        Отрисовка карты
        for (int i=map.length; i > 0; i--)
        {
            int[] subMap = map[i-1];
            for (int j=subMap.length; j > 0; j--){
                int element = subMap[j-1];

                float[] point = cartesianToIsometric(i*tileSizeX,j*tileSizeY);
                int tileId = map[i-1][j-1];
                batch.draw(textures[tileId], point[0] + shiftX, point[1] + shiftY, tile.getWidth() / tileDownScale, tile.getHeight() / tileDownScale );

                if (i == selectedTileX && j == selectedTileY){
                    cursorPoint = cartesianToIsometric((selectedTileX)*tileSizeX,(selectedTileY)*tileSizeY);
                    batch.draw(textures[0], cursorPoint[0] + shiftX, cursorPoint[1] + shiftY, tile.getWidth() / tileDownScale, tile.getHeight() / tileDownScale);
                }
            }
        }

//        Отрисовка курсора
        if (cursorPoint[0] != -1 && cursorPoint[1] != -1) {
            batch.draw(dot, cursorPoint[0] + shiftX + tileSizeX, cursorPoint[1] + shiftY + tileSizeY - (80 / tileDownScale));
        }
    }

}