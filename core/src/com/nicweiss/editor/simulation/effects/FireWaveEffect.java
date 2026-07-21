package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;

/**
 * Волна Огня — угол охвата считается по направлению на курсор (с небольшого радиуса от игрока,
 * т.е. просто направление, а не точная удалённая точка); в позиции игрока разом спавнится десяток
 * очагов огня (см. GroundFireEffect, "летящий" конструктор), они расходятся дугой (веером по
 * углам внутри ARC_DEG) и гаснут через MAX_TILES клеток пути.
 */
public final class FireWaveEffect {
    private static final int COUNT = 10;
    private static final float ARC_DEG = 70f;
    private static final float MAX_TILES = 5f;               // дальность вдвое меньше прежней (было 10)
    private static final float SPEED_TILES_PER_SEC = 6f;      // скорость вдвое больше прежней (было 3)

    private FireWaveEffect() {}

    public static void trigger(EffectSink sink) {
        if (FxContext.store.player == null) return;
        Texture[] fireFrames = GroundFireEffect.loadFrames();
        if (fireFrames == null) return;

        float px = FxContext.store.player.worldX, py = FxContext.store.player.worldY;
        float dx = FxContext.store.cursorWorldX - px, dy = FxContext.store.cursorWorldY - py;
        float baseAngle = (float) Math.atan2(dy, dx);
        float arcRad = (float) Math.toRadians(ARC_DEG);
        float tile = FxContext.store.tileSizeWidth;

        for (int i = 0; i < COUNT; i++) {
            float t = COUNT > 1 ? i / (float) (COUNT - 1) : 0.5f;
            float angle = baseAngle - arcRad / 2f + arcRad * t;
            sink.spawn(new GroundFireEffect(px, py, fireFrames, GroundFireEffect.W, GroundFireEffect.H,
                (float) Math.cos(angle), (float) Math.sin(angle),
                SPEED_TILES_PER_SEC * tile, MAX_TILES * tile));
        }
    }
}
