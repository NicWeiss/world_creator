package com.nicweiss.editor.simulation.effects;

import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;
import com.nicweiss.editor.utils.SkillCatalog;

/**
 * Воитель: ближний бой (Удар/Рывок клинка/Теневой клинок) — росчерк от игрока к точке прицела
 * (курсор). Все три умения устроены одинаково (один-два росчерка + опциональный сплеш-эффект),
 * поэтому объединены в одну фабрику, а не растянуты на три почти пустых файла.
 *
 * Урон (см. CombatSystem) — Удар точечный: цель должна ОДНОВРЕМЕННО (1) реально попадать в радиус
 * действия анимации от игрока (MELEE_REACH_TILES — "анимация должна касаться противника") И (2)
 * быть ближайшей к КУРСОРУ среди тех, до кого дотягивается удар (см.
 * findNearestToCursorWithinReach) — курсор выбирает КОГО из дотягиваемых бить, а не отменяет
 * требование физической близости к игроку. Применяется с задержкой в середину анимации
 * SlashSwingEffect (см. DelayedCallbackEffect).
 * Рывок клинка/Теневой клинок — урон и остановка/прохождение сквозь врагов считаются внутри
 * DashEffect (см. damagePerHit-параметр trigger()).
 */
public final class MeleeStrikeEffects {
    // Реальный радиус действия ближней атаки от игрока — в тайлах, домножается на tileSizeWidth
    // (мировые единицы). Раньше цель искалась в радиусе от КУРСОРА (2.5 тайла) — из-за этого можно
    // было "достать" врага, до которого анимация клинка визуально не дотягивалась.
    private static final float MELEE_REACH_TILES = 1.3f;

    private MeleeStrikeEffects() {}

    public static void triggerStrike(EffectSink sink, int level) {
        float[] center = FxContext.playerScreenPos();
        float[] target = FxContext.aimScreenPos();
        if (!SlashSwingEffect.trigger(center, target, sink)) {
            sink.spawn(new StreakEffect(center, target, 1f, 1f, 1f, 0.18f)); // ассетов нет — старый росчерк
        }
        addSplashIfInvested(center, target, sink);

        if (FxContext.store.player == null) return;
        float reach = FxContext.store.tileSizeWidth * MELEE_REACH_TILES;
        SimCreature victim = CombatSystem.findNearestToCursorWithinReach(
            FxContext.store.player.worldX, FxContext.store.player.worldY, reach);
        if (victim == null) return;

        double damage = computeDamage("warrior_strike", level, FxContext.store.player.physDamage);
        sink.spawn(new DelayedCallbackEffect(SlashSwingEffect.LIFE_SECONDS / 2f,
            () -> CombatSystem.applyDamage(victim, damage)));
    }

    /** Рывок клинка — игрок стремительно перемещается к курсору (см. DashEffect), останавливаясь
     *  и нанося урон на первом враге, встреченном по пути. */
    public static void triggerBladeDash(EffectSink sink, int level) {
        float[] center = FxContext.playerScreenPos();
        float[] target = FxContext.aimScreenPos();
        double damage = FxContext.store.player != null
            ? computeDamage("warrior_blade_dash", level, FxContext.store.player.physDamage) : 0;
        DashEffect.trigger(false, false, damage, sink);
        sink.spawn(new RingEffect(target, 26f, 0.9f, 0.9f, 1f, 0.30f)); // вспышка в точке прибытия
        addSplashIfInvested(center, target, sink);
    }

    /** Теневой клинок — тот же рывок, но проходит СКВОЗЬ врагов, нанося урон каждому встреченному
     *  на линии ровно один раз (см. DashEffect passThroughEnemies=true), следы тёмных цветов. */
    public static void triggerShadowBlade(EffectSink sink, int level) {
        float[] center = FxContext.playerScreenPos();
        float[] target = FxContext.aimScreenPos();
        double damage = FxContext.store.player != null
            ? computeDamage("warrior_shadow_blade", level, FxContext.store.player.physDamage) : 0;
        DashEffect.trigger(true, true, damage, sink);
        addSplashIfInvested(center, target, sink);
    }

    /** damage_pct-стат умения (проценты от базового физ. урона игрока) → абсолютный урон. */
    private static double computeDamage(String skillId, int level, int physDamage) {
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get(skillId);
        double pct = def != null ? def.compute(level).getOrDefault("damage_pct", 100.0) : 100.0;
        return physDamage * pct / 100.0;
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
