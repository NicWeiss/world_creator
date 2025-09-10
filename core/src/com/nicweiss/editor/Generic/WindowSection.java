package com.nicweiss.editor.Generic;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.BOHelper;

public class WindowSection {
    public static Store store;
    public boolean isScrollHidden = false;
    protected int x, y, width, height;
    private BOHelper bo_helper;
    protected BaseObject slider, sliderBG;
    protected BaseObject[] objects;
    protected boolean isTiled;
    protected boolean isSliderIsDragged;
    protected float sliderTouchY;
    protected int menuShift = 0;
    protected int totalLines;
    protected int itemWidth = 70, itemHeight = 80;
    protected Texture sliderColorBG, sliderColor, tilePickerSelector;
    protected int menuObjectSpace = 77;
    protected int controlButtonSize = 40;
    protected boolean isBuilt = false;
    protected int sliderY = 0;
    protected int padding = 5;
    protected int sliderGlobalY;

    public WindowSection() {
        sliderColorBG = new Texture("Buttons/separator.png");
        sliderColor = new Texture("Buttons/btn_background_hover.png");
        tilePickerSelector = new Texture("tile_pick_selector.png");
        this.bo_helper = new BOHelper();
    }

    public void setSize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        if (slider != null && !isSliderIsDragged) {
            sliderY = (int) (y + (slider.getHeight() * (menuShift)));
        }
    }

    public void build(){
        slider = bo_helper.constructObject(
            sliderColor, x, y, controlButtonSize, controlButtonSize, "slider", 0
        );
        sliderBG = bo_helper.constructObject(
            sliderColorBG, x, y, controlButtonSize, controlButtonSize, "slider", 0
        );

        isBuilt = true;
    }

    public void setObjects(BaseObject[] objects, boolean isTiled) {
        this.objects = objects;
        this.isTiled = isTiled;
    }

    public void render(SpriteBatch batch) {
        if (!isScrollHidden) {
            int sliderHeight = totalLines > 0 ? this.height / totalLines : this.height;
            sliderGlobalY = sliderY + this.height;
            sliderGlobalY = Math.min(sliderGlobalY, y + this.height);
            sliderGlobalY = sliderGlobalY - sliderHeight < y ? y + sliderHeight : sliderGlobalY;
            bo_helper.draw(batch, sliderBG, x + width - padding, y, padding - 20, this.height);
            bo_helper.draw(batch, slider, x + width - padding, sliderGlobalY, padding - 20, sliderHeight * -1);
        }
    }

    public boolean checkTouch(float touchX, float touchY, boolean isDragged, boolean isTouchUp) {
        if (touchX >= x && touchX <= x + width && touchY >= y && touchY <= y + height) {
            float diffy;
            if (!isTouchUp && !isDragged) {
                if (slider.isTouched) {
                    sliderTouchY = store.mouseY;
                    isSliderIsDragged = true;
                    return true;
                }
                if (objects != null) {
                    for (BaseObject obj : objects) {
                        if (obj != null && obj.isTouched) {
                            obj.isTouched = true;
                            return true;
                        }
                    }
                }
            }
            if (!isTouchUp && isDragged) {
                if (isSliderIsDragged) {
                    diffy = sliderTouchY - store.mouseY;
                    sliderY = (int)(sliderY - diffy);
                    sliderTouchY = store.mouseY;

                    int newMenuShift = (int) (Math.abs((sliderY-y) / slider.getHeight()));
                    if (menuShift != newMenuShift){
                        menuShift = newMenuShift;
                    }
                    return true;
                }
            } else {
                isSliderIsDragged = false;
            }
            return true;
        }
        return false;
    }

    public boolean checkKey(int keyCode) {
        if (store.mouseX >= x && store.mouseX <= x + width && store.mouseY >= y && store.mouseY <= y + height) {
            if (keyCode == 19 || keyCode == 157) {
                menuShift--;
                menuShift = Math.max(menuShift, 0);
                sliderY = (int) (y + (slider.getHeight() * (menuShift)));

                return true;
            }

            if (keyCode == 20 || keyCode == 156) {
                menuShift++;
                menuShift = menuShift < totalLines ? menuShift : menuShift - 1;
                sliderY = (int) (y + (slider.getHeight() * (menuShift)));

                return true;
            }

            if (keyCode == 21 || keyCode == 22) {
                return true;
            }
        }

        return false;
    }

    public void scrollDown() {
        int maxItems = (int)(height/2 / (itemWidth + 20));
        menuShift = Math.max(totalLines - maxItems, 0);
        sliderY = (int) (y + (slider.getHeight() * (menuShift)));
    }


    public void renderItemsList(SpriteBatch batch, BaseObject[] objects, boolean isTiled) {
        renderItems(batch, objects, isTiled, false);
    }

    public void renderItemsList(SpriteBatch batch, ButtonCommon[] objects, boolean isTiled) {
        renderItems(batch, objects, isTiled, false);
    }

    public void renderItemsListWithDetails(SpriteBatch batch, BaseObject[] objects, boolean isTiled) {
        renderItems(batch, objects, isTiled, true);
    }

    public void renderItems(SpriteBatch batch, BaseObject[] objects, boolean isTiled, boolean withDetails) {
        if (!isBuilt){return;}
        setObjects(objects, isTiled);

        int objectsCount = 0;

        if (objects != null){
            objectsCount = objects.length;
        }
        if (objectsCount == 0) {
            return;
        }

        if (isTiled == true) {
            itemHeight = 80;
        } else {
            itemHeight = 20;
        }

        int xShift = 20, yShift = (int)objects[0].getHeight() + 20;
        int _x, _y;
        int menuWidth;
        menuWidth = withDetails ? getSectionWidth() / 3 : getSectionWidth();
        int countPerLine = isTiled ? menuWidth / (itemWidth + 20) : 1;

//        Перерасчёт позиции слайдера
        int cpl = (int) Math.ceil((float)objectsCount / countPerLine);
        if (cpl != totalLines ) {
            totalLines = cpl;
            sliderY = (int) (y + (slider.getHeight() * (menuShift)));
        }
        menuShift = menuShift < totalLines ? menuShift : menuShift - 1;

//        Отрисовка объектов
        for (int i = Math.max(Math.min(menuShift * countPerLine, objectsCount), 0); i < objectsCount; i++) {
            _x = getSectionX() + xShift;
            _y = getSectionHeight() + getSectionY() - yShift;
            if (objects[i] == null){
                continue;
            }

            if (_y > getSectionY()) {
                if (!isTiled) {
                    objects[i].setWidth(menuWidth - 60);
                }
                bo_helper.draw(batch, objects[i], _x, _y, true);

                if (objects[i].isTouched) {
                    batch.draw(tilePickerSelector, _x, _y, itemWidth, 15);
                }
                objects[i].draw(batch);
            }

            xShift = xShift + (menuObjectSpace + 10);
            if ((xShift + itemHeight) > Math.min(menuWidth+slider.getWidth(), (itemHeight + 20) * countPerLine)) {
                xShift = 20;
                yShift = yShift + (itemHeight + 20);
            }
        }
    }

    public int getSectionX(){
        return this.x;
    }

    public int getSectionY(){
        return this.y;
    }

    public int getSectionWidth(){
        return this.width;
    }

    public int getSectionHeight(){return this.height;}
}