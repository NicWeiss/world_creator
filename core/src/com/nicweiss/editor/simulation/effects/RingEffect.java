package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Пунктирное расширяющееся кольцо-всплеск (импакт удара, взрыв и т.п.) — растёт от 30% до 100%
 *  maxRadius и угасает линейно за life секунд. Координаты — ЭКРАННЫЕ. */
public class RingEffect extends SkillEffect {
    private final float cx, cy, maxRadius, life, r, g, b;

    public RingEffect(float[] pos, float maxRadius, float r, float g, float b, float life) {
        this.cx = pos[0]; this.cy = pos[1];
        this.maxRadius = maxRadius; this.r = r; this.g = g; this.b = b; this.life = life;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < life;
    }

    @Override
    public void render(SpriteBatch batch) {
        float t = age / life;
        float radius = maxRadius * (0.3f + 0.7f * t);
        FxContext.drawDottedRing(batch, cx, cy, radius, r, g, b, 1f - t);
    }
}
