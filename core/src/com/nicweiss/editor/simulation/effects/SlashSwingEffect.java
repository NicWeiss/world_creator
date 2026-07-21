package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Взмах клинка Воителя — покадровая анимация росчерка-скетча (12 кадров, выкроены из реального
 * референса assets-source, см. assets/skills/warrior/attack/), "дорисовывающего" кольцо вокруг
 * игрока. Рисуется ТОЧНО вокруг игрока (без смещения к цели), но повёрнута так, чтобы середина
 * дуги (участок кольца, противоположный разрыву) указывала на курсор — то есть к моменту, когда
 * дуга дорисуется наполовину, она уже "дотягивается" до направления на цель. Разрыв кольца в
 * исходной текстуре расположен примерно на TEXTURE_RING_MID_DEG - 180° — поворот считается через
 * atan2 (тот же приём, что у ProjectileEffect/FireballEffect), от него отнимается этот баковый
 * угол середины дуги.
 *
 * Изометрическая компенсация: кольцо лежит на земле вокруг игрока, а не висит "плашмя к камере" —
 * поворот направления (какой стороной смотрит разрыв) должен считаться в НЕискажённом, "истинно
 * круглом" пространстве, и только ПОСЛЕ поворота весь силуэт сплющивается по экранному Y (как и
 * с любой плоской фигурой на изометрической земле — поворот вокруг вертикальной оси не меняет её
 * эллиптический силуэт на экране, а вот порядок операций важен: squash после rotate, не наоборот).
 * Обычный batch.draw(...,width,height,...,rotation,...) так не умеет (масштаб применяется ДО
 * поворота, что при неравномерном width/height даёт перекошенный эллипс) — поэтому вершины
 * считаются вручную (тот же приём по сути, что и FxContext.drawDottedRing, только для одного
 * текстурированного квада, а не россыпи точек).
 */
public class SlashSwingEffect extends SkillEffect {
    private static final int FRAME_COUNT = 12;
    private static final float FRAME_RATE = 60f; // в 2 раза быстрее — по требованию пользователя
    private static final float SIZE = 90f; // в 2 раза меньше — по требованию пользователя
    private static final float ISO_SQUASH = 0.55f; // тот же коэффициент, что у наземных аур (см. AuraRenderer)

    // Угол (в системе координат текстуры, 0°=вправо, против часовой), на который смотрит СЕРЕДИНА
    // нарисованной дуги (т.е. точка кольца напротив разрыва) в кадрах slash_XX.png — измерено по
    // самому изображению (разрыв кольца там расположен примерно на ~25°, значит середина дуги —
    // напротив, ~205°), плюс +90°+30°=+120° по требованию пользователя (два последовательных
    // сдвига по часовой стрелке относительно курсора — увеличение этого базового угла поворачивает
    // итоговую текстуру по часовой, см. формулу rotationDeg ниже).
    private static final float TEXTURE_RING_MID_DEG = 205f + 90f + 30f;

    private static Texture[] frames;

    private static Texture[] loadFrames() {
        if (frames != null) return frames;
        Texture[] loaded = new Texture[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            Texture tex = FxContext.loadSkillTexture(String.format("warrior/attack/slash_%02d.png", i + 1));
            if (tex == null) return null;
            loaded[i] = tex;
        }
        frames = loaded;
        return frames;
    }

    private final float[] center;
    private final float rotationDeg;
    private final float life;

    private SlashSwingEffect(float[] center, float rotationDeg, Texture[] myFrames) {
        this.center = center;
        this.rotationDeg = rotationDeg;
        this.life = myFrames.length / FRAME_RATE;
    }

    /** Возвращает true, если анимация была заспавнена (кадры на месте). Вызывающий код может
     *  откатиться на прежний росчерк-примитив, если ассетов нет. */
    public static boolean trigger(float[] center, float[] target, EffectSink sink) {
        Texture[] loaded = loadFrames();
        if (loaded == null) return false;
        float aimAngleDeg = (float) Math.toDegrees(Math.atan2(target[1] - center[1], target[0] - center[0]));
        float rotationDeg = aimAngleDeg - TEXTURE_RING_MID_DEG;
        sink.spawn(new SlashSwingEffect(center, rotationDeg, loaded));
        return true;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < life;
    }

    @Override
    public void render(SpriteBatch batch) {
        Texture[] loaded = loadFrames();
        if (loaded == null) return;
        int idx = Math.min(loaded.length - 1, (int) (age * FRAME_RATE));
        Texture frame = loaded[idx];

        float hw = SIZE / 2f, hh = SIZE / 2f;
        float cos = MathUtils.cosDeg(rotationDeg);
        float sin = MathUtils.sinDeg(rotationDeg);

        // 4 угла квадрата (истинно круглое, неискажённое пространство), считая от нижнего левого,
        // против часовой — тот же порядок, что использует сам LibGDX внутри Batch.draw.
        float[] cornersX = {-hw, -hw, hw, hw};
        float[] cornersY = {-hh, hh, hh, -hh};
        float[] vx = new float[4], vy = new float[4];
        for (int i = 0; i < 4; i++) {
            float rx = cos * cornersX[i] - sin * cornersY[i];
            float ry = sin * cornersX[i] + cos * cornersY[i];
            vx[i] = center[0] + rx;
            vy[i] = center[1] + ry * ISO_SQUASH; // сплющиваем ПОСЛЕ поворота — изометрическая компенсация
        }

        float color = Color.WHITE.toFloatBits();
        float[] verts = {
            vx[0], vy[0], color, 0f, 1f,
            vx[1], vy[1], color, 0f, 0f,
            vx[2], vy[2], color, 1f, 0f,
            vx[3], vy[3], color, 1f, 1f,
        };
        batch.draw(frame, verts, 0, verts.length);
    }
}
