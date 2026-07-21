package com.nicweiss.editor.simulation.effects;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Гроза — переиспользует разряд молнии из WeatherRenderer (jagged bolt + вспышка + динамический
 * свет с неба в точку) — просто выставляет Store.lightningTargetWX/Y и Store.lightningBoltNew=true,
 * WeatherRenderer сам подхватит на следующем кадре. НЕ вписывается в модель SkillEffect (сам ничего
 * не рисует — это чистый планировщик очереди), поэтому живёт отдельным маленьким классом, который
 * SkillEffectRenderer тикает каждый кадр явно (update(dt)), как и AuraRenderer.
 *
 * У WeatherRenderer только ОДИН активный разряд одновременно, поэтому несколько ударов "Грозы"
 * разносятся по времени (см. trigger — 3 удара с задержками), а не бьют одновременно.
 */
public class StormBoltScheduler {
    private final List<float[]> pending = new ArrayList<>(); // {worldX, worldY, delaySec}

    /** "Гроза" — несколько ударов подряд по области цели. */
    public void trigger() {
        float baseX = FxContext.store.cursorWorldX, baseY = FxContext.store.cursorWorldY;
        schedule(baseX, baseY, 0.00f);
        schedule(baseX + 40, baseY - 30, 0.15f);
        schedule(baseX - 30, baseY + 45, 0.30f);
    }

    private void schedule(float wx, float wy, float delaySec) {
        pending.add(new float[]{wx, wy, delaySec});
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
                it.remove();
                break; // максимум один новый разряд за кадр — см. класс-комментарий
            }
        }
    }
}
