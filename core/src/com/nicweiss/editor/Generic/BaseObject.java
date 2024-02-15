package com.nicweiss.editor.Generic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.nicweiss.editor.Main;


public class BaseObject {
    public static Store store;

    protected float x;
    protected float y;
    protected float x_scale = 1;
    protected float y_scale = 1;
    protected int width = 0;
    protected int height = 0;
    protected Texture img, bgImg;
    private TextureRegion imgRegion;
    protected float rotation = 0;
    protected float opacity = 1;
    protected boolean deleted = false;
    protected String objectId;

    public boolean isTouched = false;
    public boolean isShowBackgroundWhileHover = false;
    public boolean isRenderLighAndNigth = false;
    public boolean isPlayerInside = false;
    public boolean isOdd = false;


    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        img.dispose();
    }

    public void draw(Batch batch) {
        if (img == null) return;
        if (deleted) return;
        float dark = 0;
        float distByX, distByY, dist;
        float start, end, lp = 0;
        float dayCoefficient = (float)0;

        dayCoefficient = store.dayCoefficient;
//        batch.setColor(1, 1, 1, opacity);

        if (isRenderLighAndNigth){

            distByX = (float)Math.abs(x + (width / 2) - store.playerPositionX);
            distByY = (float)(Math.abs(y + (height / 2) - store.playerPositionY))*2;
            dist = (float)Math.sqrt(distByX*distByX + distByY*distByY);

            //            затенение
            start = 0;
            end = 240;
            lp = (dist - start) / (end - start) * 100;
            dark = (float)1.6-(lp/100 * 100)/100;

            if (dark< 0.2){dark = (float)0.2;}

            batch.setColor(dark+dayCoefficient, dark+dayCoefficient, dark+dayCoefficient, opacity);
        }

        if (bgImg != null && isTouched) {
            batch.draw(bgImg,
                    x-5, y-5,
                    0, 0,
                    (float)(width * x_scale)+10, (float)(height * y_scale)+10,
                    1, 1,
                    rotation,
                    0, 0,
                    img.getWidth()+5, img.getHeight()+5,
                    false, false);
        }

        batch.draw(img,
                x, y,
                0, 0,
                (float)width * x_scale, (float)height * y_scale,
                1, 1,
                rotation,
                0, 0,
                img.getWidth(), img.getHeight(),
                false, false);

        if (isRenderLighAndNigth && lp < 125) {
//            dark = dark - (float)1.6;
            batch.setColor((float)0.94+dayCoefficient, (float)0.75+dayCoefficient, (float)0.65+dayCoefficient, (float)1.3-dark);
            batch.draw(img,
                    x, y,
                    0, 0,
                    (float)width * x_scale, (float)height * y_scale,
                    1, 1,
                    rotation,
                    0, 0,
                    img.getWidth(), img.getHeight(),
                    false, false);
        }

    }

    public void setTexture(Texture texture) {
        img = texture;
        width = img.getWidth();
        height = img.getHeight();
    }

    public void setImage(String imageName) {
        Texture texture = new Texture(imageName);
        img = texture;
        width = img.getWidth();
        height = img.getHeight();
    }

    public void setBackgroundTexture(Texture texture){
        bgImg = texture;
    }

    public void checkTouch(float touch_x, float touch_y) {
        this.isTouched = false;
        if (touch_x >= x && touch_x <= x + width) {
            if (touch_y >= y && touch_y <= y + height) {
                this.isTouched = true;
                onTouch();
            }
        }
    }

    public void touchOut() {
        isTouched = false;
    }

    public void onTouch() {
        Gdx.app.log("Touch: ", "YEP!");
    }

    public void touch() {

    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getScaleOfX() {
        return x_scale;
    }

    public float getscaleOfY() {
        return y_scale;
    }

    public void setOpacity(float new_opacity) {
        opacity = new_opacity;
    }

    public void setX(float new_x) {
        x = new_x;
    }

    public void setY(float new_y) {
        y = new_y;
    }

    public void setScaleOfX(float new_scale_x) {
        x_scale = new_scale_x;
    }

    public void setScaleOfY(float new_scale_y) {
        y_scale = new_scale_y;
    }


    public void delete() {
        deleted = true;
    }

    public boolean status() {
        return deleted;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public float getWidth() {
        return width * x_scale;
    }

    public float getHeight() {
        return height * y_scale;
    }

    public  void setWidth(int newWidth){
        width = newWidth;
    }

    public void setHeight(int newHeight){
        height = newHeight;
    }

}
