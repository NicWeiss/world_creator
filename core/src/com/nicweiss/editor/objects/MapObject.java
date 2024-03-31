package com.nicweiss.editor.objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.utils.Transform;

import java.util.Arrays;


public class MapObject  extends BaseObject {
    Transform transform;

    public int xPositionOnMap = 0, yPositionOnMap = 0;
    public boolean isRenderLighAndNigth = true;
    public int objectHeight;

    float[] point;

    public MapObject(){
        isEnableRenderLimits = true;
    }


    public void draw(Batch batch) {
        calcPosition();
        if (isRenderLighAndNigth) {
            if (store.dayCoefficient < 0.4) {
                if (store.isSelectedLightObject) {
                    calcLight("player");
                } else {
                    dynamicLightRed = (float) 0.2;
                    dynamicLightGreen = (float) 0.2;
                    dynamicLightBlue = (float) 0.2;
                }

                batch.setColor(
                        Math.max(staticLightRed, dynamicLightRed) + store.dayCoefficient,
                        Math.max(staticLightGreen, dynamicLightGreen) + store.dayCoefficient,
                        Math.max(staticLightBlue, dynamicLightBlue) + store.dayCoefficient,
                        opacity
                );
            } else {
                batch.setColor(
                        (float) 0.2 + store.dayCoefficient,
                        (float) 0.2 + store.dayCoefficient,
                        (float) 0.2 + store.dayCoefficient,
                        opacity
                );
            }
        }

        super.draw(batch);
    }


    public void calcLight(String environment){
        float dark;
        float distByX, distByY, dist;
        float start, end, lp;
        float rp, gp, bp, cz = 0;
        float localShiftX, localShiftY;
        int countFrom, countTo;

        float highestRp =  (float)0.2;
        float highestGp =  (float)0.2;
        float highestBp =  (float)0.2;


        if (environment == "player"){
            countFrom = 0;
            countTo = 1;
        } else {
            calcPosition();
            countFrom = 1;
            countTo = store.lightPoints.length;
        }

        for (int i = countFrom; i<countTo; i++) {
            float[] light = store.lightPoints[i];
            localShiftX = 0;
            localShiftY = 0;

            if (i > 0) {
                if (store.lightPoints[i][0] == 0){
                    continue;
                }

                localShiftX = store.shiftX;
                localShiftY = store.shiftY;
            }

            distByX = (float) (x - localShiftX + ((float) width / 2) - light[1]);
            distByY = (float) (y - localShiftY - (height * 0.1) - light[2]) * 1.45f;
            if (Math.abs(distByX)>400 || Math.abs(distByY)>400){
                continue;
            }

//            проверяем высоты
            float dx, dy, tmx, tmy, fx, fy;
            int heightOfLight, tx, ty;

            tx = xPositionOnMap;
            ty = yPositionOnMap;

            fx = i == 0 ? store.selectedTileX + 0.5f : (int) light[3] + 2;
            fy = i == 0 ? store.selectedTileY + 0.5f : (int) light[4] + 2;

            heightOfLight = i == 0 ? store.selectedTailObjectHigh : store.objectedMap[(int) light[3]][(int) light[4]].objectHeight;

            dx = (tx - fx) / 10f;
            dy = (ty - fy) / 10f;

            tmx = fx+0.25f;
            tmy = fy+0.25f;
            boolean isStopLight = false, isCycleDone = false;


            int l =0;
            while (!isCycleDone){
                l += 1;
                tmx = tmx + dx;
                tmy = tmy + dy;


                if ((int) tmx == tx && (int) tmy == ty){
                    isCycleDone = true;
                }

                if (tmx - 2 >= 0 && tmx - 2 < 1000 && tmy - 2 >= 0 && tmy - 2 < 1000 && !isCycleDone) {
                    int objHS = store.objectedMap[(int) (tmx) - 2][(int) (tmy) - 2].objectHeight;
//                    int objRS = store.objectedMap[Math.round(tmx) - 2][Math.round(tmy) - 2].objectHeight;
                    if((int)tmx == (int)(fx+0.30f) && (int)tmy == (int)(fy+0.30f)) {
                        objHS = heightOfLight;
                    }

                    if (objHS > heightOfLight) {
                        isStopLight = true;
                        isCycleDone = true;
                    }
                } else {
                    isCycleDone = true;
                }
            }

            if (isStopLight) {
//                cz = (10 - l )  * 0.08f;
            } else {
//            рассчёт
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

                if (rp > highestRp) {
                    highestRp = rp;
                }
                if (gp > highestGp) {
                    highestGp = gp;
                }
                if (bp > highestBp) {
                    highestBp = bp;
                }
            }
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

    public void calcPosition(){
        point = transform.cartesianToIsometric(
                (int)(xPositionOnMap * store.tileSizeWidth),
                (int)(yPositionOnMap * store.tileSizeHeight)
        );

        setX(point[0] + store.shiftX);
        setY(point[1] + store.shiftY);

        setWidth(img.getWidth() / store.tileDownScale);
        setHeight(img.getHeight() / store.tileDownScale);
    }

    public void setObjectHeight(int high) {
        objectHeight = high;
    }
}
