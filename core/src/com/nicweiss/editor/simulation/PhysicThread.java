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

        store.player.moveBy(dCartX, dCartY, BLOCK_HEIGHT);
    }
}
