package com.nicweiss.editor.Generic;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.nicweiss.editor.utils.Uuid;

import java.security.SecureRandom;
import java.util.Base64;


public class BaseObject implements Cloneable {
    public static Store store;

    protected float x;
    protected float y;
    protected float x_scale = 1;
    protected float y_scale = 1;
    protected int width = 0;
    protected int height = 0;
    protected Texture img, bgImg, surfaceImg;
    protected float rotation = 0;
    protected float opacity = 1;
    protected boolean deleted = false;
    protected String objectId;
    protected int textureId, surfaceId;
    protected String uuid;

    protected float defaultLight = (float)0.2;
    protected float staticLightRed=defaultLight, staticLightGreen=defaultLight, staticLightBlue=defaultLight;
    protected float dynamicLightRed=defaultLight, dynamicLightGreen=defaultLight, dynamicLightBlue=defaultLight;

    public boolean isTouched = false;
    public boolean isShowBackgroundWhileHover = false;
    public boolean isPlayerInside = false;
    public boolean isEnableRenderLimits = false;

    public void draw(Batch batch) {
        if (surfaceImg != null && surfaceId == textureId) {
            return;
        }

        if (img == null) return;
        if (isEnableRenderLimits) {
            if (x + img.getWidth() < 0 || x > store.display.get("width")) {
                return;
            }
            if (y + img.getHeight() < 0 || y > store.display.get("height")) {
                return;
            }
        }

        if (deleted) return;

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
        if (img != null) {
            batch.draw(img,
                x, y,
                0, 0,
                (float) width * x_scale, (float) height * y_scale,
                1, 1,
                rotation,
                0, 0,
                img.getWidth(), img.getHeight(),
                false, false);
        }
    }

    public void drawSurface(Batch batch) {
        if (isEnableRenderLimits) {
            if (x + img.getWidth() < 0 || x > store.display.get("width")) {
                return;
            }
            if (y + img.getHeight() < 0 || y > store.display.get("height")) {
                return;
            }
        }

        if (surfaceImg == null) {
            return;
        }

        batch.draw(surfaceImg,
            x, y,
            0, 0,
            (float) width * x_scale, (float) height * y_scale,
            1, 1,
            rotation,
            0, 0,
            surfaceImg.getWidth(), surfaceImg.getHeight(),
            false, false);
    }

    public void setTexture(Texture texture) {
        img = texture;
        width = img.getWidth();
        height = img.getHeight();
    }

    public void setSurfaceTexture(Texture texture) {
        surfaceImg = texture;
    }

    public Texture getTexture() {
        return img;
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
        int startX = (int) Math.min(x, x + width);
        int startY = (int) Math.min(y, y + height);
        int endX = (int) Math.max(x, x + width);
        int endY = (int) Math.max(y, y + height);

        if (touch_x >= startX && touch_x <= endX) {
            if (touch_y >= startY && touch_y <= endY) {
                this.isTouched = true;
                onTouch();
            }
        }
    }

    public void touchOut() {
        isTouched = false;
    }

    public void onTouch() {
//        Gdx.app.log("Touch: ", String.valueOf(textureId));
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

    public int getTextureId() {
        return textureId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public void setTextureId(int textureId) {
        this.textureId = textureId;
    }

    public void setSurfaceId(int textureId) {
        this.surfaceId = textureId;
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

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public void setUUID(String uuid) {
        this.uuid = uuid;
    }

    public String generateAndSetUUID(){
        uuid = Uuid.generate();

        return uuid;
    }

    public String getUUID(){
        return uuid;
    }
}
