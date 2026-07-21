package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;

/**
 * Огненный Шар — летящий снаряд с покадровой анимацией пламени (28 кадров, см. assets/skills/
 * mage/fireball/), реально летит от игрока к курсору, поворачиваясь по направлению полёта
 * (см. ProjectileEffect), и по прибытии/на препятствии поджигает землю (см. GroundFireEffect).
 * Кадры уже смотрят "вправо" (яркое ядро справа, хвост пламени слева) — поворот считается
 * напрямую через atan2 направления полёта (см. ProjectileEffect), без доп. смещения.
 */
public final class FireballEffect {
    private static final int FRAME_COUNT = 28;
    private static final float SPEED = 900f; // px/сек экранных
    // Размер отрисовки — фикс. История правок по требованию пользователя: было 96×55 → -70%
    // (×0.3) → +50% от того результата (×0.3×1.5=×0.45).
    private static final float W = 96f * 0.45f, H = 55f * 0.45f;
    private static final float IMPACT_FIRE_LIFE = 1.3f;

    private static Texture[] frames; // лениво грузится один раз, общий на все касты

    private FireballEffect() {}

    private static Texture[] loadFrames() {
        if (frames != null) return frames;
        Texture[] loaded = new Texture[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            Texture tex = FxContext.loadSkillTexture(String.format("mage/fireball/Effects_Fire_0_%02d.png", i + 1));
            if (tex == null) return null; // не все кадры на месте — не рискуем показать рваную анимацию
            loaded[i] = tex;
        }
        frames = loaded;
        return frames;
    }

    public static void trigger(float fromWX, float fromWY, float toWX, float toWY, EffectSink sink) {
        Texture[] loaded = loadFrames();
        if (loaded == null) { // текстуры не нашлись — не теряем эффект целиком, откат на старое поведение
            float[] s1 = FxContext.worldToScreen(fromWX, fromWY);
            float[] s2 = FxContext.worldToScreen(toWX, toWY);
            sink.spawn(new StreakEffect(s1, s2, 1f, 0.45f, 0.15f, 0.20f));
            sink.spawn(new RingEffect(s2, 24f, 1f, 0.50f, 0.15f, 0.35f));
            return;
        }
        Texture[] fireFrames = GroundFireEffect.loadFrames(); // может быть null — тогда просто не поджигаем
        sink.spawn(new ProjectileEffect(fromWX, fromWY, toWX, toWY, loaded, 24f, W, H, SPEED,
            FxContext.LIGHT_COLOR_FIRE,
            posWorld -> {
                if (fireFrames != null) {
                    sink.spawn(new GroundFireEffect(posWorld[0], posWorld[1], fireFrames,
                        GroundFireEffect.W, GroundFireEffect.H, IMPACT_FIRE_LIFE));
                }
            }));
    }
}
