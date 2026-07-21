package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Наземный огонь — стоит на месте (импакт Огненного Шара, см. FireballEffect, или ДУМ, см.
 * FireDoomEffect) либо разлетается от игрока по дуге (Волна Огня, см. FireWaveEffect) — работает В
 * МИРОВЫХ координатах, на экран переводится только при отрисовке, чтобы не "отставать" от камеры
 * на долгоживущих эффектах. "Летящий" огонь останавливается (визуально "развеивается") на
 * непроходимом препятствии — вода его тоже останавливает (в отличие от летящих снарядов, см.
 * ProjectileEffect/Player.isFlightBlockedAt) — это наземный эффект, не летит по воздуху.
 *
 * Кадры (assets/skills/mage/fire/fireB0001..25.png) экспортированы БЕЗ альфа-канала (чёрный
 * фон) — рисуются аддитивным смешиванием (чёрное ничего не добавляет, яркое пламя складывается
 * поверх сцены), иначе был бы виден чёрный квадрат вокруг каждого языка пламени. Кэш кадров общий
 * на все три умения-потребителя (Огненный Шар/Волна Огня/ДУМ), поэтому живёт статически здесь.
 */
public class GroundFireEffect extends SkillEffect {
    private static final int FRAME_COUNT = 25;
    private static Texture[] frames; // лениво грузится один раз, общий на импакт+волну+ДУМ

    public static final float W = 48f, H = 48f;

    /** Общий загрузчик кадров огня — используется FireballEffect/FireWaveEffect/FireDoomEffect,
     *  чтобы решить, доступна ли анимация вообще, ДО того как спавнить сами эффекты. */
    public static Texture[] loadFrames() {
        if (frames != null) return frames;
        Texture[] loaded = new Texture[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            String name = String.format("mage/fire/fireB%04d.png", i + 1);
            Texture tex = FxContext.loadSkillTexture(name);
            if (tex == null) return null;
            loaded[i] = tex;
        }
        frames = loaded;
        return frames;
    }

    private final Texture[] myFrames;
    private final float w, h;
    private final boolean traveling;
    private final float dirX, dirY, speed, maxDistance;
    private final float life; // только для нелетящих (импакт/ДУМ)
    private float wx, wy, travelled, alpha = 1f;

    /** Стационарный огонь (импакт Огненного Шара/ДУМ) — гаснет через life секунд. */
    public GroundFireEffect(float wx, float wy, Texture[] frames, float w, float h, float life) {
        this.wx = wx; this.wy = wy; this.myFrames = frames; this.w = w; this.h = h;
        this.traveling = false; this.life = life;
        this.dirX = 0; this.dirY = 0; this.speed = 0; this.maxDistance = 0;
    }

    /** Летящий огонь (Волна Огня) — гаснет по достижении maxDistance или на препятствии. */
    public GroundFireEffect(float wx, float wy, Texture[] frames, float w, float h,
                             float dirX, float dirY, float speed, float maxDistance) {
        this.wx = wx; this.wy = wy; this.myFrames = frames; this.w = w; this.h = h;
        this.traveling = true; this.dirX = dirX; this.dirY = dirY; this.speed = speed; this.maxDistance = maxDistance;
        this.life = 0;
    }

    @Override
    public boolean update(float dt) {
        if (traveling) {
            travelled += speed * dt;
            wx += dirX * speed * dt;
            wy += dirY * speed * dt;
            if (travelled >= maxDistance) return false;
            // "Огонь из стены огня развеивается" на непроходимом препятствии.
            if (FxContext.store.player != null && FxContext.store.player.isBlockedAt(wx, wy)) return false;
            float fadeStart = maxDistance * 0.75f; // гаснет плавно на последних 25% пути
            alpha = travelled <= fadeStart ? 1f : 1f - (travelled - fadeStart) / (maxDistance - fadeStart);
        } else {
            age += dt;
            if (age >= life) return false;
            float t = age / life;
            alpha = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f; // гаснет в последние 40% жизни
        }
        return true;
    }

    @Override
    public void render(SpriteBatch batch) {
        float[] screen = FxContext.worldToScreen(wx, wy);
        float animT = traveling ? travelled / speed : age;
        int frameIdx = ((int) (animT * 20f)) % myFrames.length;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.setColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        batch.draw(myFrames[frameIdx], screen[0] - w / 2f, screen[1] - h / 2f, w, h);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // возвращаем обычное смешивание
    }

    @Override
    public void collectLight(float[][] pts, int[] idxRef) {
        FxContext.writeLight(pts, idxRef, wx, wy, FxContext.LIGHT_COLOR_FIRE);
    }
}
