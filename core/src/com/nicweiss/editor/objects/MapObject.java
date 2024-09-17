package com.nicweiss.editor.objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.utils.Transform;


public class MapObject  extends BaseObject {
    Transform transform;

    public int xPositionOnMap = 0, yPositionOnMap = 0;
    public boolean isRenderLighAndNigth = true;
    public int objectHeight;
    public float additionalDarkCoeff = 1;
    private float nearestLightDist = 999999;
    public boolean isDialogBind = false;

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

        if (isDialogBind){
            batch.setColor(
                0.56f - store.dayCoefficient / 4,
                0.77f - store.dayCoefficient / 4,
                0.55f - store.dayCoefficient / 4,
                opacity
            );
        }

        super.draw(batch);
    }


    public void calcLight(String environment){
        float dark;
        float distByX, distByY, dist;
        float start, end, lp;
        float rp, gp, bp;
        float localShiftX, localShiftY;
        int countFrom, countTo;
        boolean isLightSetted = false;

        float highestRp =  (float)0.2;
        float highestGp =  (float)0.2;
        float highestBp =  (float)0.2;


        if (environment.equals("player")){
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

//            Вычисление дисстанции до источника света
            dist = (float) Math.sqrt(distByX * distByX + distByY * distByY);

//            Если источник статический, то дальние источники отбрасываются
            if (environment.equals("global") && Math.abs(dist) > nearestLightDist){
                continue;
            }

//            проверяем высоты для ограничения освещения перекрытых объектов
            float dx, dy, tmx, tmy, fx, fy;
            int heightOfLight, tx, ty;

//            Точка для которой ведётся вычисление
            tx = xPositionOnMap;
            ty = yPositionOnMap;

//            Источник света
            fx = i == 0 ? (int) (store.selectedTileX + 0.5f) : (int) light[3] + 2;
            fy = i == 0 ? (int) (store.selectedTileY + 0.5f) : (int) light[4] + 2;

            heightOfLight = i == 0 ? store.selectedTailObjectHigh : store.objectedMap[(int) light[3]][(int) light[4]].objectHeight;

//            шаги для вычисления
            dx = (tx - fx) / 1000f;
            dy = (ty - fy) / 1000f;

//            позиционный буфер
            tmx = fx+0.25f;
            tmy = fy+0.25f;
            boolean isStopLight = false, isCycleDone = false;

            /*
              Цикл вычисления препятствий на пути от источника света
            Вычислени идёт по трём линиям и если средняя высота препятствий не превышет высоту источника света,
            то объект считаетя освещаемым

            Сравнение идёт между высотой источника света и высотой препятствия. Если препятствие выше,
            значит всё что за ним - не осещённое
            */
            int distanceFromObstacle = 0;

            while (!isCycleDone){
                tmx = tmx + dx;
                tmy = tmy + dy;

                if ((int) tmx == tx && (int) tmy == ty){
                    isCycleDone = true;
                }

                if (tmx - 2 >= 0 && tmx - 2 < store.mapHeight && tmy - 2 >= 0 && tmy - 2 < store.mapWidth && !isCycleDone) {
                    int heightDown = 100;
                    int heightUp = 100;

                    int heightMiddle = store.objectedMap[(int) (tmx) - 2][(int) (tmy) - 2].objectHeight;

                    int xRounded = Math.round(tmx) - 2;
                    int yRounded = Math.round(tmy) - 2;
                    if (xRounded >= 0 && xRounded < store.mapHeight && yRounded >= 0 && yRounded < store.mapWidth) {
                        heightDown = store.objectedMap[xRounded][yRounded].objectHeight;
                    }

                    int xCeil = (int) (Math.ceil(tmx) - 3);
                    int yCeil = (int) (Math.ceil(tmy) - 3);
                    if (xCeil>= 0 && xCeil < store.mapHeight && yCeil >= 0 && yCeil < store.mapWidth) {
                        heightUp = store.objectedMap[xCeil][yCeil].objectHeight;
                    }

                    int mh =(heightMiddle + heightDown + heightUp) / 3;
                    if((int)tmx == (int)(fx+0.30f) && (int)tmy == (int)(fy+0.30f)) {
                        mh = heightOfLight;
                    }

                    if (mh > heightOfLight) {
                        isStopLight = true;
                        isCycleDone = true;

                        int distanceX = Math.max(tx, (int) (tmx)) - Math.min(tx, (int) (tmx));
                        int distanceY = Math.max(ty, (int) (tmy)) - Math.min(ty, (int) (tmy));
                        distanceFromObstacle = Math.max(distanceX, distanceY);
                    }
                } else {
                    isCycleDone = true;
                }
            }

            if (isStopLight) {
                if (distanceFromObstacle < 4) {

                    float coeffMultiplication;

                    if (objectHeight > heightOfLight){
                        coeffMultiplication = 10;
                    }
                    else {
                        coeffMultiplication = 2.5f;
                    }

                    additionalDarkCoeff = 1f + ((distanceFromObstacle * coeffMultiplication) / 10f);
                    additionalDarkCoeff = Math.max(additionalDarkCoeff, 1);
                } else {
                    additionalDarkCoeff = 100;
                }
            } else {
                additionalDarkCoeff = 1;

                isLightSetted = true;
                nearestLightDist = Math.abs(dist)+40;
            }

//            рассчёт освещённости клетки в зависимости от удалённости от источника света
            start = 0;
            end = 120;
            lp = (dist - start) / (end - start) * 100;
            dark = (float) 1.6 - (lp / 100 * 80) / 100;

            if (dark < 0.2) {
                dark = (float) 0.2;
            }

            rp = ((float) 1 - (lp / ((dark * 100) + 35) * 50) / 500) / additionalDarkCoeff;
            gp = ((float) 1 - (lp / ((dark * 100) + 15) * 50) / 500) / additionalDarkCoeff;
            bp = ((float) 1 - (lp / ((dark * 100) + 5) * 50) / 500) / additionalDarkCoeff;


            if (rp > highestRp) {
                highestRp = rp;
            }
            if (gp > highestGp) {
                highestGp = gp;
            }
            if (bp > highestBp) {
                highestBp = bp;
            }

            if (highestRp > 0.9f && highestBp> 0.9f && highestGp > 0.9f){
                break;
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


        if (!isLightSetted){
            nearestLightDist = 99999;
//            calcLight(environment);
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
