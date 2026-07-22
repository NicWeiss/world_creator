package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Универсальный "подождать N секунд → выполнить Runnable" эффект — не рисует ничего сам, только
 * отсчитывает время. Переиспользуется везде, где урон должен применяться с задержкой относительно
 * момента каста, синхронизированной с уже идущим визуальным эффектом (например урон Удара — на
 * середине анимации SlashSwingEffect, тики ожога Огненного Шара — раз в секунду).
 */
public class DelayedCallbackEffect extends SkillEffect {
    private final float delay;
    private final Runnable callback;
    private boolean fired = false;

    public DelayedCallbackEffect(float delaySeconds, Runnable callback) {
        this.delay = delaySeconds;
        this.callback = callback;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        if (!fired && age >= delay) {
            fired = true;
            callback.run();
            return false;
        }
        return !fired;
    }

    @Override
    public void render(SpriteBatch batch) {
        // ничего не рисует — только таймер
    }
}
