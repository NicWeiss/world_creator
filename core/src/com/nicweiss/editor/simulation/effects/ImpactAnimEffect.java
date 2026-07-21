package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Разовая (не зацикленная) анимация в точке удара — например раскол Ледяного Шипа (см.
 *  IceSpikeEffect). Проигрывает все кадры ОДИН раз и убирается — в отличие от GroundFireEffect,
 *  который зацикливает кадры на всё время жизни. Мировые координаты — фиксированная точка, без
 *  движения. */
public class ImpactAnimEffect extends SkillEffect {
    private final float wx, wy, life, w, h, frameRate;
    private final Texture[] frames;

    public ImpactAnimEffect(float wx, float wy, Texture[] frames, float frameRate, float w, float h) {
        this.wx = wx; this.wy = wy;
        this.frames = frames;
        this.frameRate = frameRate;
        this.life = frames.length / frameRate;
        this.w = w; this.h = h;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < life;
    }

    @Override
    public void render(SpriteBatch batch) {
        int frameIdx = Math.min(frames.length - 1, (int) (age * frameRate));
        float[] screen = FxContext.worldToScreen(wx, wy);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(frames[frameIdx], screen[0] - w / 2f, screen[1] - h / 2f, w, h);
    }
}
