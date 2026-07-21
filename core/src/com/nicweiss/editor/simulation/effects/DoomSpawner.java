package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * ДУМ — сам ничего не рисует (render — no-op), а периодически спавнит одиночный GroundFireEffect
 * в случайной точке вокруг ТЕКУЩЕЙ позиции игрока (пересчитывается каждый тик — "если игрок
 * двигается, точка спавна тоже двигается"), проверяя проходимость (см. Player.isBlockedAt).
 * Интервал спавна масштабируется обратно пропорционально radiusTiles ("кол-во огней умножай на
 * радиус" — по требованию пользователя: чем больше зона, тем больше очагов, а не одна и та же
 * плотность).
 */
public class DoomSpawner extends SkillEffect {
    private static final float LIFE = 6f; // см. SkillCatalog "elem_fire_doom" — "5 сек" (тик-эффект держится дольше урона)
    // Интервал спавна ПРИ РАДИУСЕ=1 клетка (кол-во огней ×8, потом ещё ×4 по прошлым требованиям
    // пользователя) — фактический интервал делится на radiusTiles (см. update).
    private static final float SPAWN_INTERVAL_AT_R1 = 0.15f / 8f / 4f;

    private final float radiusTiles;
    private final Texture[] fireFrames;
    private final EffectSink sink;
    private float spawnTimer;

    public DoomSpawner(float radiusTiles, Texture[] fireFrames, EffectSink sink) {
        this.radiusTiles = radiusTiles;
        this.fireFrames = fireFrames;
        this.sink = sink;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        if (age >= LIFE) return false;
        if (FxContext.store.player == null) return true;
        spawnTimer -= dt;
        if (spawnTimer > 0f) return true;
        spawnTimer += SPAWN_INTERVAL_AT_R1 / Math.max(1f, radiusTiles);

        float radiusPx = radiusTiles * FxContext.store.tileSizeWidth;
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
