package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.simulation.Player;

/**
 * Один "призрачный" отпечаток спрайта игрока, оставленный рывком (Рывок клинка/Теневой клинок) —
 * "сам пользователь должен несколько раз повториться словно шлейф и затухать": записывается через
 * равные интервалы во время DashEffect.update(), сразу проявляется и гаснет за LIFE секунд.
 * Размер копирует Player.draw() (см. Player.SIZE_FACTOR) — мировая позиция переводится в
 * экранную каждый кадр, т.к. камера следует за игроком и продолжает двигаться, пока рывок ещё идёт.
 *
 * tint — для Теневого клинка отпечатки должны быть тёмными (а не просто прозрачной копией
 * исходного спрайта), для Рывка клинка — обычный светлый (почти белый) оттенок.
 */
public class DashGhostEffect extends SkillEffect {
    private static final float LIFE = 0.14f; // в 2 раза короче (вместе с GHOST_INTERVAL в DashEffect — то же кол-во теней на вдвое короче пути)
    private static final float START_ALPHA = 0.55f;

    private final float wx, wy;
    private final Texture tex;
    private final float tintR, tintG, tintB;

    public DashGhostEffect(float wx, float wy, Texture tex, float tintR, float tintG, float tintB) {
        this.wx = wx;
        this.wy = wy;
        this.tex = tex;
        this.tintR = tintR;
        this.tintG = tintG;
        this.tintB = tintB;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < LIFE;
    }

    @Override
    public void render(SpriteBatch batch) {
        if (tex == null) return;
        float pw = FxContext.store.tileSizeWidth * Player.SIZE_FACTOR;
        float ph = FxContext.store.tileSizeHeight * Player.SIZE_FACTOR;
        float[] screen = FxContext.worldToScreenRelativeToPlayer(wx, wy);
        float alpha = START_ALPHA * (1f - age / LIFE);
        batch.setColor(tintR, tintG, tintB, alpha);
        batch.draw(tex, screen[0] - pw / 2f, screen[1] - ph / 2f, pw, ph);
        batch.setColor(1f, 1f, 1f, 1f);
    }
}
