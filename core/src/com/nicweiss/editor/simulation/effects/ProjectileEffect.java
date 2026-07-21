package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.function.Consumer;

/**
 * Летящий снаряд с покадровой анимацией — общий механизм для Огненного Шара и Ледяного Шипа (см.
 * FireballEffect/IceSpikeEffect — конфигурируют кадры/размер/скорость/цвет света/поведение по
 * прибытии, сам класс не знает про конкретное умение). Летит по прямой от старта к цели В МИРОВЫХ
 * координатах (иначе при движении игрока камера "утаскивала" бы снаряд с его настоящей
 * траектории — см. GroundFireEffect), поворачивается по направлению полёта (угол считается один
 * раз при спавне из ЭКРАННОЙ проекции — прямая в мире линейна и в изометрии, угол не меняется по
 * пути), останавливается на первом непроходимом препятствии (см. Player.isFlightBlockedAt — вода
 * не мешает, снаряд летит по воздуху, но настоящее препятствие по высоте останавливает) и в точке
 * остановки (по препятствию или по прибытии) вызывает onImpact — тот спавнит следующий эффект
 * (наземный огонь/анимацию раскола) через переданный при конструировании EffectSink.
 */
public class ProjectileEffect extends SkillEffect {
    private final float wx1, wy1, wx2, wy2, angleDeg, life, w, h, frameRate;
    private final Texture[] frames;
    private final float[] lightColor; // null — снаряд не светит
    private final Consumer<float[]> onImpact; // получает {worldX, worldY} точки остановки

    private float curWX, curWY;

    public ProjectileEffect(float fromWX, float fromWY, float toWX, float toWY,
                             Texture[] frames, float frameRate, float w, float h, float speed,
                             float[] lightColor, Consumer<float[]> onImpact) {
        this.wx1 = fromWX; this.wy1 = fromWY; this.wx2 = toWX; this.wy2 = toWY;
        this.curWX = fromWX; this.curWY = fromWY;
        float[] s1 = FxContext.worldToScreen(fromWX, fromWY);
        float[] s2 = FxContext.worldToScreen(toWX, toWY);
        this.angleDeg = (float) Math.toDegrees(Math.atan2(s2[1] - s1[1], s2[0] - s1[0]));
        float screenDist = (float) Math.hypot(s2[0] - s1[0], s2[1] - s1[1]);
        this.life = Math.max(0.15f, Math.min(0.6f, screenDist / speed));
        this.w = w; this.h = h;
        this.frames = frames;
        this.frameRate = frameRate;
        this.lightColor = lightColor;
        this.onImpact = onImpact;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        float t = Math.min(1f, life > 0 ? age / life : 1f);
        curWX = wx1 + (wx2 - wx1) * t;
        curWY = wy1 + (wy2 - wy1) * t;

        boolean blocked = FxContext.store.player != null && FxContext.store.player.isFlightBlockedAt(curWX, curWY);
        if (blocked || age >= life) {
            if (onImpact != null) onImpact.accept(new float[]{curWX, curWY});
            return false;
        }
        return true;
    }

    @Override
    public void render(SpriteBatch batch) {
        float[] screen = FxContext.worldToScreen(curWX, curWY);
        int frameIdx = ((int) (age * frameRate)) % frames.length;
        Texture frame = frames[frameIdx];
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(frame, screen[0] - w / 2f, screen[1] - h / 2f, w / 2f, h / 2f, w, h,
            1f, 1f, angleDeg, 0, 0, frame.getWidth(), frame.getHeight(), false, false);
    }

    @Override
    public void collectLight(float[][] pts, int[] idxRef) {
        if (lightColor != null) FxContext.writeLight(pts, idxRef, curWX, curWY, lightColor);
    }
}
