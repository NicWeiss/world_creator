package com.nicweiss.editor.simulation.effects;

/**
 * Воитель: ближний бой (Удар/Рывок клинка/Теневой клинок) — росчерк от игрока к точке прицела
 * (курсор). Все три умения устроены одинаково (один-два росчерка + опциональный сплеш-эффект),
 * поэтому объединены в одну фабрику, а не растянуты на три почти пустых файла.
 */
public final class MeleeStrikeEffects {
    private MeleeStrikeEffects() {}

    public static void triggerStrike(EffectSink sink) {
        float[] center = FxContext.playerScreenPos();
        float[] target = FxContext.aimScreenPos();
        if (!SlashSwingEffect.trigger(center, target, sink)) {
            sink.spawn(new StreakEffect(center, target, 1f, 1f, 1f, 0.18f)); // ассетов нет — старый росчерк
        }
        addSplashIfInvested(center, target, sink);
    }

    public static void triggerBladeDash(EffectSink sink) {
        float[] center = FxContext.playerScreenPos();
        float[] target = FxContext.aimScreenPos();
        sink.spawn(new StreakEffect(center, target, 1f, 1f, 1f, 0.22f));
        sink.spawn(new RingEffect(target, 26f, 0.9f, 0.9f, 1f, 0.30f));
        addSplashIfInvested(center, target, sink);
    }

    public static void triggerShadowBlade(EffectSink sink) {
        float[] center = FxContext.playerScreenPos();
        float[] target = FxContext.aimScreenPos();
        sink.spawn(new StreakEffect(center, target, 0.55f, 0.35f, 0.75f, 0.22f));
        sink.spawn(new StreakEffect(FxContext.offset(center, 6, -4), FxContext.offset(target, 6, -4), 0.55f, 0.35f, 0.75f, 0.20f));
        addSplashIfInvested(center, target, sink);
    }

    /** Широкий взмах (Сплеш) — пассивка, не кастуется напрямую, но "если сплеш есть - то работает
     *  всегда": добавляем веерный росчерк от игрока к цели к КАЖДОЙ атаке Воителя, если хотя бы 1
     *  очко вложено (откат на прежнее кольцо, если ассетов почему-то нет). */
    private static void addSplashIfInvested(float[] center, float[] target, EffectSink sink) {
        if (FxContext.store.player != null && FxContext.store.player.skillLevels.getOrDefault("warrior_splash", 0) > 0) {
            if (!WideSplashEffect.trigger(center, target, sink)) {
                sink.spawn(new RingEffect(target, 34f, 0.85f, 0.85f, 0.5f, 0.35f));
            }
        }
    }
}
