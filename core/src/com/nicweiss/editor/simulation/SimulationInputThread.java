package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;

/**
 * Плавное пиксельное движение камеры в режиме симуляции.
 *
 * Источники ввода:
 *   - Клавиатура: Store.simKey* (обновляются Editor.keyDown/keyUp на GL-потоке)
 *   - Геймпад левый стик: Store.simStickX/Y (обновляются Editor.render на GL-потоке)
 *
 * Движение по пикселям (не по тайлам):
 *   - velocity накапливается с ускорением
 *   - friction даёт плавное торможение
 *   - суб-пиксельный аккумулятор сохраняет дробную часть между тиками
 */
public class SimulationInputThread implements Runnable {
    public static Store store;

    private static final float ACCEL_KEY     = 0.75f;  // ускорение X (влево/вправо)
    private static final float ACCEL_STICK   = 1.25f;  // ускорение от стика (аналоговый)
    // Изометрическая коррекция: 1 px screen-Y = v√2 мировых единиц,
    // тогда как 1 px screen-X = v/√2. Отношение 2:1 → делим velY на 2,
    // чтобы мировая скорость вверх/вниз совпала с влево/вправо.
    private static final float ACCEL_KEY_Y   = ACCEL_KEY * 0.5f;
    private static final float FRICTION      = 0.82f;
    private static final float STOP_THRESH   = 0.3f;
    private static final long  TICK_MS       = 16L;

    private float velX = 0f, velY = 0f;
    // Аккумуляторы суб-пиксельного остатка
    private float accumX = 0f, accumY = 0f;

    @Override
    public void run() {
        velX = velY = accumX = accumY = 0f;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // ── Ускорение от клавиатуры ───────────────────────────────────
                if (store.simKeyLeft)  velX -= ACCEL_KEY;
                if (store.simKeyRight) velX += ACCEL_KEY;
                if (store.simKeyUp)    velY += ACCEL_KEY_Y;
                if (store.simKeyDown)  velY -= ACCEL_KEY_Y;

                // ── Ускорение от геймпада (аналоговое) ───────────────────────
                float sx = store.simStickX;
                float sy = store.simStickY;
                if (sx != 0f) velX += sx * ACCEL_STICK;
                if (sy != 0f) velY -= sy * ACCEL_STICK * 0.5f; // Y стика инвертирован + изо-коррекция

                // ── Трение ───────────────────────────────────────────────────
                velX *= FRICTION;
                velY *= FRICTION;

                if (Math.abs(velX) < STOP_THRESH) velX = 0f;
                if (Math.abs(velY) < STOP_THRESH) velY = 0f;

                // ── Суб-пиксельный аккумулятор ───────────────────────────────
                // Хранит дробную часть, чтобы движение было плавным даже
                // при маленьких скоростях (нет "залипания" на целых пикселях)
                accumX += velX;
                accumY += velY;

                int dx = (int) accumX;
                int dy = (int) accumY;
                accumX -= dx;
                accumY -= dy;

                if (dx != 0 || dy != 0) {
                    // Атомарная запись обоих компонентов в один long:
                    // старшие 32 бита = dX, младшие 32 = dY.
                    // GL-поток читает и применяет их синхронно перед кадром.
                    store.simCamDelta = ((long) dx << 32) | ((long) dy & 0xFFFFFFFFL);
                }

                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Сброс при остановке
        velX = velY = accumX = accumY = 0f;
        store.simKeyUp = store.simKeyDown = store.simKeyLeft = store.simKeyRight = false;
        store.simStickX = store.simStickY = 0f;
    }
}
