package com.nicweiss.editor.creations;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.utils.Lighting;

public class Creation extends BaseObject {
    public int mapCellX, mapCellY;
    public int level = 1; // уровень существа — используется DropManager.dropLoot при его смерти
    private float positionX, positionY;

    // Целевой размер БОЛЬШЕЙ стороны спрайта на экране (в пикселях), масштаб считается от неё с
    // сохранением пропорций — см. draw(). null = старое поведение (img.getWidth()/tileDownScale),
    // которое годится только для арта, заранее нарисованного "в масштаб" (как тайлы/деревья);
    // текстуры NPC/объектов (см. NpcCatalog/ObjectCatalog) приходят произвольного разрешения,
    // поэтому им выставляется targetMaxScreenSize при назначении типа (см. NpcEditorWindow,
    // ObjectEditorWindow, UserInterface.buildEntities, SpawnManager).
    public Float targetMaxScreenSize = null;

    public void setPosition(float _x, float _y) {
        positionX = x = _x;
        positionY = y = _y;
    }

    public void setCell( int x, int y){
        mapCellX = x;
        mapCellY = y;
    }

    public void draw(SpriteBatch batch){
        x = (int) (positionX + store.shiftX);
        y = (int) (positionY + store.shiftY);

        if (targetMaxScreenSize != null) {
            float scale = targetMaxScreenSize / (float) Math.max(img.getWidth(), img.getHeight());
            width  = Math.round(img.getWidth()  * scale);
            height = Math.round(img.getHeight() * scale);
        } else {
            width = img.getWidth() / store.tileDownScale;
            height = img.getHeight() / store.tileDownScale;
        }

        // NPC/объекты освещаются так же, как обычные тайлы/дропы — см. Lighting.computeLitColor
        // (общая логика с Drop, не дублируем день/ночь/дождь/источники света в двух местах).
        float[] lit = computeLitColor();
        batch.setColor(lit[0], lit[1], lit[2], 1f);

        super.draw(batch);
        batch.setColor(1, 1, 1, 1);
    }

    // ── Освещение — тонкая обёртка над общей Lighting, см. Drop (там та же обёртка) ────────────

    private int litForCellX = Integer.MIN_VALUE, litForCellY = Integer.MIN_VALUE;
    private final float[] lightSourcePos = new float[2];
    private final float[] litColorBuf = new float[3];

    private void ensureLightSourcePos() {
        if (litForCellX == mapCellX && litForCellY == mapCellY) return;
        Lighting.tileLightAnchor(mapCellX, mapCellY, lightSourcePos);
        litForCellX = mapCellX;
        litForCellY = mapCellY;
    }

    public float getLightSourceIsoX() { ensureLightSourcePos(); return lightSourcePos[0]; }
    public float getLightSourceIsoY() { ensureLightSourcePos(); return lightSourcePos[1]; }

    private float[] computeLitColor() {
        return Lighting.computeLitColor(getLightSourceIsoX(), getLightSourceIsoY(), mapCellX, mapCellY, null, litColorBuf);
    }
}
