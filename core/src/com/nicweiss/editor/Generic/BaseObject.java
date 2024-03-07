package com.nicweiss.editor.Generic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.nicweiss.editor.Main;

import java.util.Arrays;


public class BaseObject implements Cloneable {
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
    protected int textureId;

    protected float defaultLight = (float)0.2;
    protected float staticLightRed=defaultLight, staticLightGreen=defaultLight, staticLightBlue=defaultLight;
    protected float dynamicLightRed=defaultLight, dynamicLightGreen=defaultLight, dynamicLightBlue=defaultLight;

    public boolean isTouched = false;
    public boolean isShowBackgroundWhileHover = false;
    public boolean isRenderLighAndNigth = false;
    public boolean isPlayerInside = false;
    public boolean isEnableRenderLimits = false;


//    @Override
//    protected void finalize() throws Throwable {
//        super.finalize();
//        img.dispose();
//    }

    public void draw(Batch batch) {
        if (isEnableRenderLimits) {
            if (x + img.getWidth() < 0 || x > store.display.get("width")) {
                return;
            }
            if (y + img.getHeight() < 0 || y > store.display.get("height")) {
                return;
            }
        }

        if (img == null) return;
        if (deleted) return;
//
        if (isRenderLighAndNigth){
            if (store.dayCoefficient< 0.4){
                if (store.isSelectedLightObject) {
                    calcLight("player");
                } else {
                    dynamicLightRed = (float)0.2;
                    dynamicLightGreen = (float)0.2;
                    dynamicLightBlue = (float)0.2;
                }

                batch.setColor(
                        Math.max(staticLightRed,dynamicLightRed) + store.dayCoefficient,
                        Math.max(staticLightGreen,dynamicLightGreen) + store.dayCoefficient,
                        Math.max(staticLightBlue,dynamicLightBlue) + store.dayCoefficient,
                        opacity
                );
            }else {
                batch.setColor(
                        (float) 0.2 + store.dayCoefficient,
                        (float) 0.2 + store.dayCoefficient,
                        (float) 0.2 + store.dayCoefficient,
                        opacity
                );
            }
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

//        checkTouch(store.mouseX, store.mouseY);
    }

    public void calcLight(String environment){
        float dark;
        float distByX, distByY, dist;
        float start, end, lp;
        float rp, gp, bp;
        float localShiftX, localShiftY;
        int countFrom, countTo;

        float highestRp =  (float)0.2;
        float highestGp =  (float)0.2;
        float highestBp =  (float)0.2;

        if (environment == "player"){
            countFrom = 0;
            countTo = 1;
        } else {
            countFrom = 1;
            countTo = store.lightPoints.length;
        }

        for (int i = countFrom; i<countTo; i++) {
            localShiftX = 0;
            localShiftY = 0;

            if (i > 0) {
                if (store.lightPoints[i][0] == 0){
                    continue;
                }

                localShiftX = store.shiftX;
                localShiftY = store.shiftY;
            }

            distByX = (float) Math.abs(x - localShiftX + (width / 2) - store.lightPoints[i][1]);
            distByY = (float) (Math.abs(y - localShiftY - (height * 0.1) - store.lightPoints[i][2])) * (float)1.45;
            if (Math.abs(distByX)>400 || Math.abs(distByY)>400){
                continue;
            }
            dist = (float) Math.sqrt(distByX * distByX + distByY * distByY);

            //            затенение
            start = 0;
            end = 120;
            lp = (dist - start) / (end - start) * 100;
            dark = (float) 1.6 - (lp / 100 * 80) / 100;

            if (dark < 0.2) {
                dark = (float) 0.2;
            }

            rp = (float) 1 - (lp / ((dark * 100) + 35) * 50) / 500;
            gp = (float) 1 - (lp / ((dark * 100) + 15) * 50) / 500;
            bp = (float) 1 - (lp / ((dark * 100) + 5) * 50) / 500;

            if (rp > highestRp){highestRp = rp;}
            if (gp > highestGp){highestGp = gp;}
            if (bp > highestBp){highestBp = bp;}
//                }
        }

        if (highestRp < 0.2) {
            highestRp = (float) 0.2;
        }
        if (highestGp < 0.2) {
            highestGp = (float) 0.2;
        }
        if (highestBp < 0.2) {
            highestBp = (float) 0.2;
        }

        if (environment == "player") {
            dynamicLightRed = highestRp;
            dynamicLightGreen = highestGp;
            dynamicLightBlue = highestBp;
        } else {
            staticLightRed = highestRp;
            staticLightGreen = highestGp;
            staticLightBlue = highestBp;
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
}
