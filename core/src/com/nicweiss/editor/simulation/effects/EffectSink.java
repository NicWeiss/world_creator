package com.nicweiss.editor.simulation.effects;

/**
 * Точка добавления НОВЫХ эффектов из уже идущего эффекта или из триггера умения (например, снаряд
 * по прибитии рождает наземный огонь/анимацию раскола, ДУМ периодически спавнит очаги огня) —
 * реализует SkillEffectRenderer, передаётся тем эффектам/фабрикам, которым это нужно.
 */
public interface EffectSink {
    void spawn(SkillEffect effect);
}
