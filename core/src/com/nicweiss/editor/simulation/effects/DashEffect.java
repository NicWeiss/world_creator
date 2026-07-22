package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Общий "рывок" для Рывка клинка и Теневого клинка — игрок стремительно перемещается к точке
 * курсора В МИРОВЫХ координатах за DURATION секунд (пишет напрямую в Player.worldX/worldY, как
 * это уже делает обычное перемещение — см. Player.moveBy/PhysicThread), останавливаясь на первом
 * непроходимом препятствии (см. Player.isBlockedAt — та же проверка, что и у обычной ходьбы).
 *
 * Урон и коллизия с врагами (см. CombatSystem) — на каждом шаге проверяется, есть ли поражаемое
 * существо в радиусе HIT_RADIUS от текущей (кандидатной) позиции:
 *  - passThroughEnemies==false (Рывок клинка) — при первом попадании наносит урон ОДИН раз и
 *    останавливается (не двигается дальше в этом кадре), не долетая до курсора, если враг встал
 *    раньше — это и есть "останавливается на первом враге".
 *  - passThroughEnemies==true (Теневой клинок) — копит уже задетых существ (Set), наносит урон
 *    каждому встреченному ровно один раз, продолжает лететь до конца — "проходит сквозь врагов".
 *
 * Во время движения оставляет позади (референс пользователя — спидлайны а-ля Флэш):
 *  - веер коротких параллельных "линий скорости" (см. SpeedLineEffect) каждые SPEEDLINE_INTERVAL —
 *    светлые для Рывка клинка, тёмные для Теневого клинка (darkTrail);
 *  - полупрозрачные гаснущие копии спрайта игрока (см. DashGhostEffect) каждые GHOST_INTERVAL —
 *    тоже тонируются тёмным для Теневого клинка.
 */
public class DashEffect extends SkillEffect {
    private static final float DURATION = 0.14f;
    private static final float GHOST_INTERVAL = 0.01f; // в 2 раза чаще — то же кол-во теней, вдвое короче шлейф (см. DashGhostEffect.LIFE)
    private static final float SPEEDLINE_INTERVAL = 0.035f;
    private static final float LINE_LIFE = 0.22f; // дольше, чтобы растягивание/движение успевало быть заметным
    private static final float HIT_RADIUS_TILES = 1.2f; // радиус обнаружения врага на пути, в тайлах

    private static final float[] LINE_OFFSETS = {-20f, -10f, 0f, 10f, 20f}; // поперечное смещение линий веера, px
    private static final float[] LINE_MAX_LEN = {55f, 85f, 105f, 85f, 55f}; // длиннее в центре — как на референсе

    private static final float[] LIGHT_GLOW = {1f, 1f, 1f};
    private static final float[] LIGHT_CORE = {1f, 1f, 1f};
    private static final float[] DARK_GLOW = {0.05f, 0.04f, 0.07f};
    private static final float[] DARK_CORE = {0.12f, 0.1f, 0.16f};
    private static final float[] LIGHT_GHOST_TINT = {1f, 1f, 1f};
    private static final float[] DARK_GHOST_TINT = {0.18f, 0.15f, 0.22f};

    private final float startX, startY, targetX, targetY;
    private final boolean passThroughEnemies;
    private final boolean darkTrail;
    private final double damagePerHit;
    private final EffectSink sink;
    private final Set<SimCreature> alreadyHit = new LinkedHashSet<>();

    private float dirX = 1f, dirY = 0f;

    private float traveled; // 0..1
    private float ghostTimer;
    private float speedLineTimer;
    private boolean stopped;

    private DashEffect(float startX, float startY, float targetX, float targetY,
                        boolean passThroughEnemies, boolean darkTrail, double damagePerHit, EffectSink sink) {
        this.startX = startX;
        this.startY = startY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.passThroughEnemies = passThroughEnemies;
        this.darkTrail = darkTrail;
        this.damagePerHit = damagePerHit;
        this.sink = sink;

        float[] startScreen = FxContext.playerScreenPos();
        float[] targetScreen = FxContext.worldToScreenRelativeToPlayer(targetX, targetY);
        float ddx = targetScreen[0] - startScreen[0];
        float ddy = targetScreen[1] - startScreen[1];
        float dlen = (float) Math.sqrt(ddx * ddx + ddy * ddy);
        if (dlen > 0.001f) {
            dirX = ddx / dlen;
            dirY = ddy / dlen;
        }
    }

    public static void trigger(boolean passThroughEnemies, boolean darkTrail, double damagePerHit, EffectSink sink) {
        if (FxContext.store.player == null) return;
        float sx = FxContext.store.player.worldX;
        float sy = FxContext.store.player.worldY;
        float tx = FxContext.store.cursorWorldX;
        float ty = FxContext.store.cursorWorldY;
        sink.spawn(new DashEffect(sx, sy, tx, ty, passThroughEnemies, darkTrail, damagePerHit, sink));
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        if (stopped || FxContext.store.player == null) return false;

        traveled = Math.min(1f, age / DURATION);
        float curX = startX + (targetX - startX) * traveled;
        float curY = startY + (targetY - startY) * traveled;

        float hitRadius = FxContext.store.tileSizeWidth * HIT_RADIUS_TILES;
        if (passThroughEnemies) {
            List<SimCreature> hits = CombatSystem.findAllInRadius(curX, curY, hitRadius);
            for (SimCreature victim : hits) {
                if (alreadyHit.add(victim)) {
                    CombatSystem.applyDamage(victim, damagePerHit);
                }
            }
        } else if (alreadyHit.isEmpty()) {
            List<SimCreature> hits = CombatSystem.findAllInRadius(curX, curY, hitRadius);
            if (!hits.isEmpty()) {
                SimCreature victim = hits.get(0);
                alreadyHit.add(victim);
                CombatSystem.applyDamage(victim, damagePerHit);
                stopped = true; // "останавливается на первом враге" — не долетает дальше
            }
        }

        if (!stopped) {
            if (FxContext.store.player.isBlockedAt(curX, curY)) {
                stopped = true;
            } else {
                FxContext.store.player.worldX = curX;
                FxContext.store.player.worldY = curY;
            }
        }

        ghostTimer += dt;
        if (ghostTimer >= GHOST_INTERVAL) {
            ghostTimer = 0f;
            float[] tint = darkTrail ? DARK_GHOST_TINT : LIGHT_GHOST_TINT;
            sink.spawn(new DashGhostEffect(FxContext.store.player.worldX, FxContext.store.player.worldY,
                FxContext.store.player.getTexture(), tint[0], tint[1], tint[2]));
        }

        speedLineTimer += dt;
        if (speedLineTimer >= SPEEDLINE_INTERVAL) {
            speedLineTimer = 0f;
            spawnSpeedLineFan();
        }

        return traveled < 1f && !stopped;
    }

    private void spawnSpeedLineFan() {
        float wx = FxContext.store.player.worldX, wy = FxContext.store.player.worldY;
        float[] glow = darkTrail ? DARK_GLOW : LIGHT_GLOW;
        float[] core = darkTrail ? DARK_CORE : LIGHT_CORE;
        for (int i = 0; i < LINE_OFFSETS.length; i++) {
            sink.spawn(new SpeedLineEffect(wx, wy, dirX, dirY, LINE_OFFSETS[i], LINE_MAX_LEN[i], LINE_LIFE,
                glow[0], glow[1], glow[2], core[0], core[1], core[2]));
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        // сам рывок ничего не рисует — только двигает игрока и спавнит след/теневые копии
    }
}
