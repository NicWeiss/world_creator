package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;
import com.nicweiss.editor.utils.SkillCatalog;

/**
 * Ледяной Шип — тот же подход, что Огненный Шар: летящий снаряд (10 кадров, см. assets/skills/
 * mage/iceball/) от игрока к курсору, по прибытии/на препятствии — разовая анимация раскола (5
 * кадров, см. assets/skills/mage/ice/) в точке остановки (см. ImpactAnimEffect), БЕЗ
 * зацикленного наземного огня (лёд не горит долго).
 *
 * Урон (см. CombatSystem) — точечный, цель ищется рядом с курсором ДО спавна снаряда (тот же
 * приём, что у FireballEffect — снаряд летит В ЦЕЛЬ, а не в сырые координаты курсора), урон и
 * замедление (slow_pct на slow_duration_sec) применяются в onImpact.
 */
public final class IceSpikeEffect {
    private static final float SPEED = 900f;
    private static final float SIZE = 42f; // кадры квадратные (см. нарезку water_ice_*)
    private static final int FLIGHT_FRAME_COUNT = 10;

    private static final float SHATTER_SIZE = 64f;
    private static final int SHATTER_FRAME_COUNT = 5;
    private static final float AIM_SNAP_RANGE_TILES = 3f;

    private static Texture[] flightFrames;
    private static Texture[] shatterFrames;

    private IceSpikeEffect() {}

    private static Texture[] loadFlightFrames() {
        if (flightFrames != null) return flightFrames;
        Texture[] loaded = new Texture[FLIGHT_FRAME_COUNT];
        for (int i = 0; i < FLIGHT_FRAME_COUNT; i++) {
            Texture tex = FxContext.loadSkillTexture(String.format("mage/iceball/water_ice_%02d.png", i + 1));
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
            Texture tex = FxContext.loadSkillTexture(String.format("mage/ice/ice_shatter_%02d.png", i + 1));
            if (tex == null) return null;
            loaded[i] = tex;
        }
        shatterFrames = loaded;
        return shatterFrames;
    }

    public static void trigger(float fromWX, float fromWY, float toWX, float toWY, int level, EffectSink sink) {
        SimCreature target = CombatSystem.findNearestToCursor(FxContext.store.tileSizeWidth * AIM_SNAP_RANGE_TILES);
        float destX = target != null ? target.worldX : toWX;
        float destY = target != null ? target.worldY : toWY;

        Texture[] loaded = loadFlightFrames();
        if (loaded == null) { // текстур нет — откат на прежний росчерк+кольцо, эффект не теряется совсем
            float[] s1 = FxContext.worldToScreen(fromWX, fromWY);
            float[] s2 = FxContext.worldToScreen(destX, destY);
            sink.spawn(new StreakEffect(s1, s2, 0.55f, 0.85f, 1f, 0.20f));
            sink.spawn(new RingEffect(s2, 22f, 0.60f, 0.90f, 1f, 0.35f));
            applyDamage(target, level);
            return;
        }
        Texture[] shatter = loadShatterFrames(); // может быть null — тогда просто без раскола на импакте
        sink.spawn(new ProjectileEffect(fromWX, fromWY, destX, destY, loaded, 20f, SIZE, SIZE, SPEED,
            FxContext.LIGHT_COLOR_ICE,
            posWorld -> {
                if (shatter != null) {
                    sink.spawn(new ImpactAnimEffect(posWorld[0], posWorld[1], shatter, 18f, SHATTER_SIZE, SHATTER_SIZE));
                } else {
                    sink.spawn(new RingEffect(FxContext.worldToScreen(posWorld[0], posWorld[1]), 22f, 0.6f, 0.9f, 1f, 0.35f));
                }
                applyDamage(target, level);
            }));
    }

    private static void applyDamage(SimCreature target, int level) {
        if (target == null) return;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_cold_spike");
        java.util.LinkedHashMap<String, Double> stats = def != null ? def.compute(level) : new java.util.LinkedHashMap<>();
        double damage = stats.getOrDefault("damage", 0.0);
        double slowPct = stats.getOrDefault("slow_pct", 0.0);
        double slowDuration = stats.getOrDefault("slow_duration_sec", 0.0);

        CombatSystem.applyDamage(target, damage);
        CombatSystem.applySlow(target, (float) slowPct, (float) slowDuration);
    }
}
