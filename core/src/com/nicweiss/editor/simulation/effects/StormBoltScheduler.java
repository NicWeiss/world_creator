package com.nicweiss.editor.simulation.effects;

import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;
import com.nicweiss.editor.utils.SkillCatalog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Гроза — переиспользует разряд молнии из WeatherRenderer (jagged bolt + вспышка + динамический
 * свет с неба в точку) — просто выставляет Store.lightningTargetWX/Y и Store.lightningBoltNew=true,
 * WeatherRenderer сам подхватит на следующем кадре. НЕ вписывается в модель SkillEffect (сам ничего
 * не рисует — это чистый планировщик очереди), поэтому живёт отдельным маленьким классом, который
 * SkillEffectRenderer тикает каждый кадр явно (update(dt)), как и AuraRenderer.
 *
 * У WeatherRenderer только ОДИН активный разряд одновременно, поэтому несколько ударов "Грозы"
 * разносятся по времени (см. trigger — strikes_count ударов, разбросанных по 4 сек, см.
 * SkillCatalog "elem_lightning_storm"), а не бьют одновременно.
 *
 * Урон (см. CombatSystem) — АоЕ: каждый удар наносит strike_damage всем поражаемым существам в
 * небольшом радиусе от точки СВОЕГО удара (не только курсора — удары разбросаны вокруг него).
 */
public class StormBoltScheduler {
    private static final float STRIKE_HIT_RADIUS_TILES = 1.2f;
    private static final float SCATTER_TILES = 1.0f;   // разброс точек ударов вокруг курсора
    private static final float STRIKES_WINDOW_SEC = 4f; // см. SkillCatalog — "за 4 сек"

    private final List<float[]> pending = new ArrayList<>(); // {worldX, worldY, delaySec, damage}

    /** "Гроза" — несколько ударов подряд по области цели. */
    public void trigger(int level) {
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_lightning_storm");
        LinkedHashMap<String, Double> stats = def != null ? def.compute(level) : new LinkedHashMap<>();
        int strikesCount = Math.max(1, (int) Math.round(stats.getOrDefault("strikes_count", 5.0)));
        double strikeDamage = stats.getOrDefault("strike_damage", 0.0);

        float baseX = FxContext.store.cursorWorldX, baseY = FxContext.store.cursorWorldY;
        float scatterPx = FxContext.store.tileSizeWidth * SCATTER_TILES;
        for (int i = 0; i < strikesCount; i++) {
            float ox = (float) (Math.random() * 2 - 1) * scatterPx;
            float oy = (float) (Math.random() * 2 - 1) * scatterPx;
            float delay = (float) (Math.random() * STRIKES_WINDOW_SEC);
            schedule(baseX + ox, baseY + oy, delay, strikeDamage);
        }
    }

    private void schedule(float wx, float wy, float delaySec, double damage) {
        pending.add(new float[]{wx, wy, delaySec, (float) damage});
    }

    public void update(float dt) {
        if (pending.isEmpty()) return;
        for (float[] b : pending) b[2] -= dt;
        for (Iterator<float[]> it = pending.iterator(); it.hasNext(); ) {
            float[] b = it.next();
            if (b[2] <= 0f) {
                FxContext.store.lightningTargetWX = b[0];
                FxContext.store.lightningTargetWY = b[1];
                FxContext.store.lightningBoltNew = true;

                float hitRadius = FxContext.store.tileSizeWidth * STRIKE_HIT_RADIUS_TILES;
                List<SimCreature> hits = CombatSystem.findAllInRadius(b[0], b[1], hitRadius);
                for (SimCreature victim : hits) CombatSystem.applyDamage(victim, b[3]);

                it.remove();
                break; // максимум один новый разряд за кадр — см. класс-комментарий
            }
        }
    }
}
