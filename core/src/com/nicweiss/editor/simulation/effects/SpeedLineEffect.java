package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Тонкая "линия скорости" (спидлайн а-ля Флэш) — НЕ статичный отрезок, который просто тускнеет на
 * месте, а по-настоящему АНИМИРУЕТСЯ за свою жизнь: рождается короткой рядом с игроком и на глазах
 * РАСТЯГИВАЕТСЯ (длина растёт от MIN_LEN_FRAC до полной) и ДВИГАЕТСЯ — целиком уезжает назад,
 * отставая от анкора (см. drift), пока гаснет. Мировой анкор (не экранный) — там, где был игрок в
 * момент спавна линии — переводится в экранные координаты каждый кадр через
 * FxContext.worldToScreenRelativeToPlayer, поэтому линия правдоподобно "остаётся позади", пока
 * игрок продолжает рывок. Направление/перпендикулярное смещение — уже готовые ЭКРАННЫЕ единичные
 * векторы (считаются один раз на весь рывок в DashEffect, т.к. путь прямой).
 *
 * Ядро линии НЕ зафиксировано белым (в отличие от StreakEffect) — тонируется отдельно, чтобы у
 * Теневого клинка следы были по-настоящему тёмными, а не светящимися.
 */
public class SpeedLineEffect extends SkillEffect {
    private static final float MIN_LEN_FRAC = 0.12f; // с чего начинает расти длина
    private static final float DRIFT = 46f; // насколько вся линия целиком "уезжает" от анкора за свою жизнь, px

    private final float wx, wy;
    private final float dirX, dirY, perpOffset, maxLen, life;
    private final float glowR, glowG, glowB, coreR, coreG, coreB;

    public SpeedLineEffect(float wx, float wy, float dirX, float dirY, float perpOffset,
                            float maxLen, float life,
                            float glowR, float glowG, float glowB,
                            float coreR, float coreG, float coreB) {
        this.wx = wx; this.wy = wy;
        this.dirX = dirX; this.dirY = dirY;
        this.perpOffset = perpOffset;
        this.maxLen = maxLen;
        this.life = life;
        this.glowR = glowR; this.glowG = glowG; this.glowB = glowB;
        this.coreR = coreR; this.coreG = coreG; this.coreB = coreB;
    }

    private static float smoothstep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < life;
    }

    @Override
    public void render(SpriteBatch batch) {
        float t = age / life;
        float[] anchor = FxContext.worldToScreenRelativeToPlayer(wx, wy);
        float perpX = -dirY, perpY = dirX;
        float px = anchor[0] + perpX * perpOffset;
        float py = anchor[1] + perpY * perpOffset;

        float drift = DRIFT * t; // линия целиком отъезжает назад от анкора по мере угасания
        float length = maxLen * (MIN_LEN_FRAC + (1f - MIN_LEN_FRAC) * smoothstep(t)); // и растягивается

        float nearX = px - dirX * drift;
        float nearY = py - dirY * drift;
        float farX = px - dirX * (drift + length);
        float farY = py - dirY * (drift + length);

        float alpha = 1f - t;
        FxContext.drawSeg(batch, nearX, nearY, farX, farY, 2.5f, glowR, glowG, glowB, alpha * 0.5f);
        FxContext.drawSeg(batch, nearX, nearY, farX, farY, 1f, coreR, coreG, coreB, alpha);
    }
}
