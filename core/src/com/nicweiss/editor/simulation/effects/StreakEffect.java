package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Короткий росчерк-линия (удар Воителя, начало полёта снаряда и т.п.) — мягкое "свечение" +
 *  яркое ядро, угасает линейно за life секунд. Координаты — ЭКРАННЫЕ (эффект короткоживущий,
 *  камера не успевает сдвинуться, см. StreakEffect и родственные Projectile/ChainBolt). */
public class StreakEffect extends SkillEffect {
    private final float x1, y1, x2, y2, life, r, g, b;

    public StreakEffect(float[] from, float[] to, float r, float g, float b, float life) {
        this.x1 = from[0]; this.y1 = from[1]; this.x2 = to[0]; this.y2 = to[1];
        this.r = r; this.g = g; this.b = b; this.life = life;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < life;
    }

    @Override
    public void render(SpriteBatch batch) {
        float alpha = 1f - (age / life);
        FxContext.drawSeg(batch, x1, y1, x2, y2, 6f, r, g, b, alpha * 0.5f); // мягкое "свечение"
        FxContext.drawSeg(batch, x1, y1, x2, y2, 2f, 1f, 1f, 1f, alpha);    // яркое ядро
    }
}
