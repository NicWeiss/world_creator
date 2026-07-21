package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;

/**
 * Ледяной Шип — тот же подход, что Огненный Шар: летящий снаряд (10 кадров, см. assets/skills/
 * warriors/iceball/) от игрока к курсору, по прибытии/на препятствии — разовая анимация раскола (5
 * кадров, см. assets/skills/warriors/ice/) в точке остановки (см. ImpactAnimEffect), БЕЗ
 * зацикленного наземного огня (лёд не горит долго).
 */
public final class IceSpikeEffect {
    private static final float SPEED = 900f;
    private static final float SIZE = 42f; // кадры квадратные (см. нарезку water_ice_*)
    private static final int FLIGHT_FRAME_COUNT = 10;

    private static final float SHATTER_SIZE = 64f;
    private static final int SHATTER_FRAME_COUNT = 5;

    private static Texture[] flightFrames;
    private static Texture[] shatterFrames;

    private IceSpikeEffect() {}

    private static Texture[] loadFlightFrames() {
        if (flightFrames != null) return flightFrames;
        Texture[] loaded = new Texture[FLIGHT_FRAME_COUNT];
        for (int i = 0; i < FLIGHT_FRAME_COUNT; i++) {
            Texture tex = FxContext.loadSkillTexture(String.format("warriors/iceball/water_ice_%02d.png", i + 1));
            if (tex == null) return null;
            loaded[i] = tex;
        }
        flightFrames = loaded;
        return flightFrames;
    }

    private static Texture[] loadShatterFrames() {
        if (shatterFrames != null) return shatterFrames;
        Texture[] loaded = new Texture[SHATTER_FRAME_COUNT];
        for (int i = 0; i < SHATTER_FRAME_COUNT; i++) {
            Texture tex = FxContext.loadSkillTexture(String.format("warriors/ice/ice_shatter_%02d.png", i + 1));
            if (tex == null) return null;
            loaded[i] = tex;
        }
        shatterFrames = loaded;
        return shatterFrames;
    }

    public static void trigger(float fromWX, float fromWY, float toWX, float toWY, EffectSink sink) {
        Texture[] loaded = loadFlightFrames();
        if (loaded == null) { // текстур нет — откат на прежний росчерк+кольцо, эффект не теряется совсем
            float[] s1 = FxContext.worldToScreen(fromWX, fromWY);
            float[] s2 = FxContext.worldToScreen(toWX, toWY);
            sink.spawn(new StreakEffect(s1, s2, 0.55f, 0.85f, 1f, 0.20f));
            sink.spawn(new RingEffect(s2, 22f, 0.60f, 0.90f, 1f, 0.35f));
            return;
        }
        Texture[] shatter = loadShatterFrames(); // может быть null — тогда просто без раскола на импакте
        sink.spawn(new ProjectileEffect(fromWX, fromWY, toWX, toWY, loaded, 20f, SIZE, SIZE, SPEED,
            FxContext.LIGHT_COLOR_ICE,
            posWorld -> {
                if (shatter != null) {
                    sink.spawn(new ImpactAnimEffect(posWorld[0], posWorld[1], shatter, 18f, SHATTER_SIZE, SHATTER_SIZE));
                } else {
                    sink.spawn(new RingEffect(FxContext.worldToScreen(posWorld[0], posWorld[1]), 22f, 0.6f, 0.9f, 1f, 0.35f));
                }
            }));
    }
}
