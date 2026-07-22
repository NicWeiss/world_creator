package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;
import com.nicweiss.editor.utils.SkillCatalog;

/**
 * Огненный Шар — летящий снаряд с покадровой анимацией пламени (28 кадров, см. assets/skills/
 * mage/fireball/), реально летит от игрока к курсору, поворачиваясь по направлению полёта
 * (см. ProjectileEffect), и по прибытии/на препятствии поджигает землю (см. GroundFireEffect).
 * Кадры уже смотрят "вправо" (яркое ядро справа, хвост пламени слева) — поворот считается
 * напрямую через atan2 направления полёта (см. ProjectileEffect), без доп. смещения.
 *
 * Урон (см. CombatSystem) — точечный: цель ищется РЯДОМ С КУРСОРОМ (см. AIM_SNAP_RANGE_TILES) ДО
 * спавна снаряда, и если найдена — снаряд летит в ЕЁ позицию (не в сырые координаты курсора),
 * иначе визуально выглядело бы как промах при реальном попадании. direct_damage наносится в
 * onImpact, ожог (burn_dps) — 3 тика с интервалом 1 сек (см. DelayedCallbackEffect).
 */
public final class FireballEffect {
    private static final int FRAME_COUNT = 28;
    private static final float SPEED = 900f; // px/сек экранных
    // Размер отрисовки — фикс. История правок по требованию пользователя: было 96×55 → -70%
    // (×0.3) → +50% от того результата (×0.3×1.5=×0.45).
    private static final float W = 96f * 0.45f, H = 55f * 0.45f;
    private static final float IMPACT_FIRE_LIFE = 1.3f;
    private static final float AIM_SNAP_RANGE_TILES = 3f;
    private static final int BURN_TICKS = 3;

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

    public static void trigger(float fromWX, float fromWY, float toWX, float toWY, int level, EffectSink sink) {
        SimCreature target = CombatSystem.findNearestToCursor(FxContext.store.tileSizeWidth * AIM_SNAP_RANGE_TILES);
        float destX = target != null ? target.worldX : toWX;
        float destY = target != null ? target.worldY : toWY;

        Texture[] loaded = loadFrames();
        if (loaded == null) { // текстуры не нашлись — не теряем эффект целиком, откат на старое поведение
            float[] s1 = FxContext.worldToScreen(fromWX, fromWY);
            float[] s2 = FxContext.worldToScreen(destX, destY);
            sink.spawn(new StreakEffect(s1, s2, 1f, 0.45f, 0.15f, 0.20f));
            sink.spawn(new RingEffect(s2, 24f, 1f, 0.50f, 0.15f, 0.35f));
            applyDamage(target, level, sink);
            return;
        }
        Texture[] fireFrames = GroundFireEffect.loadFrames(); // может быть null — тогда просто не поджигаем
        sink.spawn(new ProjectileEffect(fromWX, fromWY, destX, destY, loaded, 24f, W, H, SPEED,
            FxContext.LIGHT_COLOR_FIRE,
            posWorld -> {
                if (fireFrames != null) {
                    sink.spawn(new GroundFireEffect(posWorld[0], posWorld[1], fireFrames,
                        GroundFireEffect.W, GroundFireEffect.H, IMPACT_FIRE_LIFE));
                }
                applyDamage(target, level, sink);
            }));
    }

    private static void applyDamage(SimCreature target, int level, EffectSink sink) {
        if (target == null) return;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_fire_ball");
        java.util.LinkedHashMap<String, Double> stats = def != null ? def.compute(level) : new java.util.LinkedHashMap<>();
        double directDamage = stats.getOrDefault("direct_damage", 0.0);
        double burnDps = stats.getOrDefault("burn_dps", 0.0);

        CombatSystem.applyDamage(target, directDamage);
        for (int tick = 1; tick <= BURN_TICKS; tick++) {
            sink.spawn(new DelayedCallbackEffect(tick * 1f, () -> CombatSystem.applyDamage(target, burnDps)));
        }
    }
}
