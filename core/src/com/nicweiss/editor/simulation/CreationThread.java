package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;

/**
 * Управляет поведением NPC: реакции, маршруты, AI.
 * Запускается в режиме симуляции, прерывается при возврате в редактор.
 */
public class CreationThread implements Runnable {
    public static Store store;

    @Override
    public void run() {
        long lastTime = System.nanoTime();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                long now = System.nanoTime();
                float dt = (now - lastTime) / 1_000_000_000f;
                lastTime = now;

                if (store.drops != null) {
                    DropManager.update(dt);
                }

                if (store.isSimulationMode) {
                    SpawnManager.update(dt);
                }

                // TODO: NPC AI — передвижение, реакции, патрули
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
