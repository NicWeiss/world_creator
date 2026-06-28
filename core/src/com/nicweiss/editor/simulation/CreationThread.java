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
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // TODO: NPC AI — передвижение, реакции, патрули
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
