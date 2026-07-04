package com.nicweiss.editor.utils;

import com.nicweiss.editor.utils.NpcCatalog.TierDef;
import com.nicweiss.editor.utils.NpcCatalog.TypeDef;

import java.util.LinkedHashMap;
import java.util.Random;

/**
 * Роллит характеристики конкретного экземпляра боевого NPC (уровень/тир/сопротивления) по правилам
 * {@link NpcCatalog} — используется {@link com.nicweiss.editor.simulation.SpawnManager} при спавне.
 * Не создаёт сам объект сущности (SimCreature) — только считает числа, аналогично тому, как
 * {@link ItemGenerator} считает значения для шаблона предмета, а не создаёт Drop.
 */
public class NpcGenerator {
    private static final Random RANDOM = new Random();

    private NpcGenerator() {}

    /** Уровень спавна = уровень спавнера ± 2 (см. ТЗ), не ниже 1. */
    public static int rollLevel(int spawnerLevel) {
        int delta = RANDOM.nextInt(5) - 2; // -2..+2
        return Math.max(1, spawnerLevel + delta);
    }

    /**
     * Тир юнита: прайм проверяется первым (реже и "сильнее" — приоритет), иначе старший, иначе
     * обычный. Независимые последовательные роллы дают ~1/100 прайм и ~1/50 старший (см. ТЗ).
     */
    public static NpcCatalog.Tier rollTier() {
        if (RANDOM.nextDouble() < NpcCatalog.PRIME_CHANCE) return NpcCatalog.Tier.PRIME;
        if (RANDOM.nextDouble() < NpcCatalog.ELDER_CHANCE) return NpcCatalog.Tier.ELDER;
        return NpcCatalog.Tier.NORMAL;
    }

    /** Сопротивления по тиру: ELDER — 1 случайная стихия, PRIME — 2 различных, 30-70% каждая. */
    public static LinkedHashMap<String, Integer> rollResistances(NpcCatalog.Tier tier) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        TierDef def = NpcCatalog.TIERS.get(tier);
        if (def == null || def.resistCount <= 0) return result;

        java.util.List<String> pool = new java.util.ArrayList<>(java.util.Arrays.asList(NpcCatalog.ELEMENTS));
        java.util.Collections.shuffle(pool, RANDOM);
        int count = Math.min(def.resistCount, pool.size());
        for (int i = 0; i < count; i++) {
            int pct = def.resistMinPct + RANDOM.nextInt(def.resistMaxPct - def.resistMinPct + 1);
            result.put(pool.get(i), pct);
        }
        return result;
    }

    /** Итоговое здоровье типа на уровне level с учётом множителя тира. */
    public static int rollHealth(TypeDef type, int level, NpcCatalog.Tier tier) {
        TierDef tierDef = NpcCatalog.TIERS.get(tier);
        float mult = tierDef != null ? tierDef.healthMult : 1f;
        return Math.max(1, Math.round(type.healthAtLevel(level) * mult));
    }

    /** Итоговый урон типа на уровне level с учётом множителя тира. */
    public static int rollDamage(TypeDef type, int level, NpcCatalog.Tier tier) {
        TierDef tierDef = NpcCatalog.TIERS.get(tier);
        float mult = tierDef != null ? tierDef.damageMult : 1f;
        return Math.max(1, Math.round(type.damageAtLevel(level) * mult));
    }
}
