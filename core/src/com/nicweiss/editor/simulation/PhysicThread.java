package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;

/**
 * Обрабатывает физику персонажа: коллизии, гравитацию, передвижение по карте.
 */
public class PhysicThread implements Runnable {
    public static Store store;

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // TODO: обработка коллизий, гравитация, перемещение playerPosition
                Thread.sleep(16); // ~60 Hz
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
