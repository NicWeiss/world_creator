package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;
import com.nicweiss.editor.utils.SkillCatalog;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Волна Огня — угол охвата считается по направлению на курсор (с небольшого радиуса от игрока,
 * т.е. просто направление, а не точная удалённая точка); в позиции игрока разом спавнится десяток
 * очагов огня (см. GroundFireEffect, "летящий" конструктор), они расходятся дугой (веером по
 * углам внутри ARC_DEG) и гаснут через MAX_TILES клеток пути.
 *
 * Урон (см. CombatSystem) — АоЕ-конус: все поражаемые существа в секторе angle_deg на дальность
 * MAX_TILES от игрока получают cone_damage сразу при касте плюс burn_dps 3 тика (как ожог
 * Огненного Шара).
 */
public final class FireWaveEffect {
    private static final int COUNT = 10;
    private static final float ARC_DEG = 70f;
    private static final float MAX_TILES = 5f;               // дальность вдвое меньше прежней (было 10)
    private static final float SPEED_TILES_PER_SEC = 6f;      // скорость вдвое больше прежней (было 3)
    private static final int BURN_TICKS = 3;

    private FireWaveEffect() {}

    public static void trigger(int level, EffectSink sink) {
        if (FxContext.store.player == null) return;
        Texture[] fireFrames = GroundFireEffect.loadFrames();
        if (fireFrames == null) return;

        float px = FxContext.store.player.worldX, py = FxContext.store.player.worldY;
        float dx = FxContext.store.cursorWorldX - px, dy = FxContext.store.cursorWorldY - py;
        float baseAngle = (float) Math.atan2(dy, dx);
        float arcRad = (float) Math.toRadians(ARC_DEG);
        float tile = FxContext.store.tileSizeWidth;

        for (int i = 0; i < COUNT; i++) {
            float t = COUNT > 1 ? i / (float) (COUNT - 1) : 0.5f;
            float angle = baseAngle - arcRad / 2f + arcRad * t;
            sink.spawn(new GroundFireEffect(px, py, fireFrames, GroundFireEffect.W, GroundFireEffect.H,
                (float) Math.cos(angle), (float) Math.sin(angle),
                SPEED_TILES_PER_SEC * tile, MAX_TILES * tile));
        }

        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_fire_wave");
        LinkedHashMap<String, Double> stats = def != null ? def.compute(level) : new LinkedHashMap<>();
        double coneDamage = stats.getOrDefault("cone_damage", 0.0);
        double burnDps = stats.getOrDefault("burn_dps", 0.0);
        double angleDeg = stats.getOrDefault("angle_deg", (double) ARC_DEG);

        List<SimCreature> hits = CombatSystem.findAllInCone(px, py, (float) Math.toDegrees(baseAngle), (float) angleDeg, MAX_TILES * tile);
        for (SimCreature victim : hits) {
            CombatSystem.applyDamage(victim, coneDamage);
            for (int tick = 1; tick <= BURN_TICKS; tick++) {
                sink.spawn(new DelayedCallbackEffect(tick * 1f, () -> CombatSystem.applyDamage(victim, burnDps)));
            }
        }
    }
}
