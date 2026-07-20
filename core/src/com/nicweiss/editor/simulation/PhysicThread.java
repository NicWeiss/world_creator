package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;

/**
 * Физика игрока: инициализация, применение ввода, коллизия с картой.
 *
 * Читает simCamDelta (SimulationInputThread) и вызывает store.player.moveBy().
 */
public class PhysicThread implements Runnable {
    public static Store store;

    private static final int BLOCK_HEIGHT = 10;

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (store.isSimulationMode && store.player != null && store.objectedMap != null) {
                    if (!store.player.isInitialized()) {
                        store.player.initAtCameraCenter();
                    }
                    applyInput();
                    store.player.tickRegen(0.016f);
                }
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void applyInput() {
        long delta = store.simCamDelta;
        if (delta == 0L) return;
        store.simCamDelta = 0L;

        int dx = (int)(delta >> 32);
        int dy = (int)(delta & 0xFFFFFFFFL);

        // Изометрический пиксельный сдвиг → декартовые мировые единицы
        float dCartX = (dx + 2f * dy) / 2f;
        float dCartY = (2f * dy - dx) / 2f;

        float beforeX = store.player.worldX, beforeY = store.player.worldY;
        store.player.moveBy(dCartX, dCartY, BLOCK_HEIGHT);

        // Клик-муав: если движение было к цели клика (см. SimulationInputThread) и moveBy не сдвинул
        // игрока вообще (полностью заблокирован препятствием) — прерываем погоню за целью, как и
        // требовалось ("если встречается помеха — прерывать движение"). Достижение цели по расстоянию
        // проверяется в самом SimulationInputThread.run(), сюда попадает только случай блокировки.
        if (store.hasMoveTarget) {
            boolean moved = Math.abs(store.player.worldX - beforeX) > 0.01f
                         || Math.abs(store.player.worldY - beforeY) > 0.01f;
            if (!moved) store.hasMoveTarget = false;
        }
    }
}
