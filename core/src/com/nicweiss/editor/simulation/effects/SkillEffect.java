package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Базовый класс визуального эффекта умения — единый жизненный цикл для всех эффектов
 * (StreakEffect/RingEffect/ProjectileEffect/...), управляемых SkillEffectRenderer. Каждый эффект
 * сам знает, как себя обновлять/рисовать/(опционально) освещать — SkillEffectRenderer только
 * хранит общий список и дёргает update/render/collectLight каждый кадр, не зная деталей
 * конкретного эффекта (см. класс-комментарий SkillEffectRenderer).
 */
public abstract class SkillEffect {
    protected float age = 0f;

    /** @return false — эффект пора убрать из списка (жизнь кончилась). */
    public abstract boolean update(float dt);

    public abstract void render(SpriteBatch batch);

    /** Переопределяют эффекты-источники динамического света (см. Store.skillLightPoints,
     *  FxContext.writeLight) — по умолчанию эффект не светит. idxRef[0] — общий курсор записи на
     *  все эффекты сразу; каждый вызов FxContext.writeLight сам проверяет границу массива. */
    public void collectLight(float[][] pts, int[] idxRef) {}
}
