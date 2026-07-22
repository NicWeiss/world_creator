package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;

import java.util.List;

/**
 * ДУМ — сам ничего не рисует (render — no-op), а периодически спавнит одиночный GroundFireEffect
 * в случайной точке вокруг ТЕКУЩЕЙ позиции игрока (пересчитывается каждый тик — "если игрок
 * двигается, точка спавна тоже двигается"), проверяя проходимость (см. Player.isBlockedAt).
 * Интервал спавна масштабируется обратно пропорционально radiusTiles ("кол-во огней умножай на
 * радиус" — по требованию пользователя: чем больше зона, тем больше очагов, а не одна и та же
 * плотность).
 *
 * Урон (см. CombatSystem) — раз в секунду наносит dps всем поражаемым существам в текущей области
 * (findAllInRadius вокруг ТЕКУЩЕЙ позиции игрока — область следует за игроком, как и сам визуал).
 */
public class DoomSpawner extends SkillEffect {
    private static final float LIFE = 6f; // см. SkillCatalog "elem_fire_doom" — "5 сек" (тик-эффект держится дольше урона)
    // Интервал спавна ПРИ РАДИУСЕ=1 клетка (кол-во огней ×8, потом ещё ×4 по прошлым требованиям
    // пользователя) — фактический интервал делится на radiusTiles (см. update).
    private static final float SPAWN_INTERVAL_AT_R1 = 0.15f / 8f / 4f;
    private static final float DAMAGE_TICK_INTERVAL = 1f; // dps — урон в секунду

    private final float radiusTiles;
    private final double dps;
    private final Texture[] fireFrames;
    private final EffectSink sink;
    private float spawnTimer;
    private float damageTimer;

    public DoomSpawner(float radiusTiles, double dps, Texture[] fireFrames, EffectSink sink) {
        this.radiusTiles = radiusTiles;
        this.dps = dps;
        this.fireFrames = fireFrames;
        this.sink = sink;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        if (age >= LIFE) return false;
        if (FxContext.store.player == null) return true;

        float radiusPx = radiusTiles * FxContext.store.tileSizeWidth;

        damageTimer += dt;
        if (damageTimer >= DAMAGE_TICK_INTERVAL) {
            damageTimer -= DAMAGE_TICK_INTERVAL;
            List<SimCreature> hits = CombatSystem.findAllInRadius(
                FxContext.store.player.worldX, FxContext.store.player.worldY, radiusPx);
            for (SimCreature victim : hits) CombatSystem.applyDamage(victim, dps);
        }

        spawnTimer -= dt;
        if (spawnTimer > 0f) return true;
        spawnTimer += SPAWN_INTERVAL_AT_R1 / Math.max(1f, radiusTiles);

        float ang = (float) (Math.random() * Math.PI * 2);
        float dist = (float) (Math.sqrt(Math.random()) * radiusPx); // равномерно по площади круга
        float wx = FxContext.store.player.worldX + (float) Math.cos(ang) * dist;
        float wy = FxContext.store.player.worldY + (float) Math.sin(ang) * dist;
        if (!FxContext.store.player.isBlockedAt(wx, wy)) { // непроходимая точка — просто пропускаем тик
            sink.spawn(new GroundFireEffect(wx, wy, fireFrames, GroundFireEffect.W, GroundFireEffect.H, 1.3f));
        }
        return true;
    }

    @Override
    public void render(SpriteBatch batch) {
        // ДУМ сам ничего не рисует — визуал целиком через спавненные GroundFireEffect.
    }
}
