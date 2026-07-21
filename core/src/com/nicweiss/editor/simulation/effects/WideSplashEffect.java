package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Пассивка Воителя "Широкий взмах" — одна чистая светящаяся дуга-волна (22 кадра, см.
 * assets/skills/warrior/splash/), проигрывается от игрока в сторону курсора при каждой атаке, если
 * в пассивку вложено хотя бы 1 очко (см. MeleeStrikeEffects.addSplashIfInvested). Кадры без
 * альфа-канала (чёрный фон) — рисуются аддитивным смешиванием, как GroundFireEffect.
 *
 * Дуга нарисована заново с нуля вокруг ОДНОГО общего центра окружности (см. gen_splash6.py) —
 * геометрически честное однородное увеличение из одной точки, без ресемплинга и без "разгибания".
 * Радиус и толщина растут НЕПРЕРЫВНО от кадра к кадру по одной плавной кривой (smoothstep, без
 * промежуточных дискретных "стадий" — та версия с альфа-кроссфейдом между фиксированными размерами
 * выглядела как мерцание).
 *
 * Запускается НЕ вместе с самой атакой, а с задержкой в START_DELAY = половина длительности
 * анимации атаки (SlashSwingEffect.LIFE_SECONDS/2) — по требованию пользователя волна должна
 * стартовать на середине взмаха клинка, а не одновременно с ним. Опорная точка (origin) — не
 * центр игрока, а чуть дальше в сторону курсора (см. OFFSET_DIST), там же, где сама анимация атаки
 * (кольцо SlashSwingEffect), а не у игрока.
 *
 * По умолчанию (rotation=0) волна уходит ВЛЕВО (aimAngleDeg=180°), поэтому
 * rotationDeg = aimAngleDeg - 180°.
 *
 * Изометрическая компенсация — ручное построение вершин: поворот вокруг origin в неискажённом
 * пространстве, и только ПОСЛЕ поворота сплющивание по экранному Y (тот же приём, что у
 * SlashSwingEffect).
 */
public class WideSplashEffect extends SkillEffect {
    private static final int FRAME_COUNT = 22;
    private static final float FRAME_RATE = 90f; // в 3 раза быстрее — по требованию пользователя
    private static final float WIDTH = 105f; // +50% от предыдущего размера — по требованию пользователя
    private static final float HEIGHT = WIDTH * (520f / 380f); // сохраняем пропорции исходного кадра
    private static final float ISO_SQUASH = 0.55f; // тот же коэффициент, что у SlashSwingEffect/аур

    // Стартует не одновременно с атакой, а на её середине.
    private static final float START_DELAY = SlashSwingEffect.LIFE_SECONDS / 2f;
    // Стартовая точка — между игроком и анимацией атаки (не у игрока и не у самой атаки).
    private static final float OFFSET_DIST = 21f;

    // Origin — центр окружности, вокруг которой нарисована дуга (см. CX=W*0.86, CY=H*0.5 в
    // gen_splash6.py), а не правый край и не центр квада.
    private static final float ORIGIN_X_FRAC = 0.86f;

    private static Texture[] frames;

    private static Texture[] loadFrames() {
        if (frames != null) return frames;
        Texture[] loaded = new Texture[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            Texture tex = FxContext.loadSkillTexture(String.format("warrior/splash/splash_%02d.png", i + 1));
            if (tex == null) return null;
            loaded[i] = tex;
        }
        frames = loaded;
        return frames;
    }

    private final float[] center;
    private final float rotationDeg;
    private final float life;

    private WideSplashEffect(float[] center, float rotationDeg, Texture[] myFrames) {
        this.center = center;
        this.rotationDeg = rotationDeg;
        this.life = START_DELAY + myFrames.length / FRAME_RATE;
    }

    /** Возвращает true, если анимация была заспавнена (кадры на месте). */
    public static boolean trigger(float[] center, float[] target, EffectSink sink) {
        Texture[] loaded = loadFrames();
        if (loaded == null) return false;
        float dx = target[0] - center[0], dy = target[1] - center[1];
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float[] origin = dist > 0.001f
            ? new float[]{center[0] + dx / dist * OFFSET_DIST, center[1] + dy / dist * OFFSET_DIST}
            : center;
        float aimAngleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        float rotationDeg = aimAngleDeg - 180f;
        sink.spawn(new WideSplashEffect(origin, rotationDeg, loaded));
        return true;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < life;
    }

    @Override
    public void render(SpriteBatch batch) {
        if (age < START_DELAY) return; // ещё не наступила середина анимации атаки
        Texture[] loaded = loadFrames();
        if (loaded == null) return;
        int idx = Math.min(loaded.length - 1, (int) ((age - START_DELAY) * FRAME_RATE));
        Texture frame = loaded[idx];

        float ox = WIDTH * ORIGIN_X_FRAC, oy = HEIGHT / 2f;
        float cos = MathUtils.cosDeg(rotationDeg);
        float sin = MathUtils.sinDeg(rotationDeg);

        float[] cornersX = {-ox, -ox, WIDTH - ox, WIDTH - ox};
        float[] cornersY = {-oy, HEIGHT - oy, HEIGHT - oy, -oy};
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
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.draw(frame, verts, 0, verts.length);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }
}
