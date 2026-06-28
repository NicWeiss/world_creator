package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;

/**
 * Обрабатывает магические эффекты: заклинания персонажей и NPC,
 * нанесение урона, визуальные эффекты на тайлах.
 */
public class MagickThread implements Runnable {
    public static Store store;

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // TODO: тики заклинаний, урон, визуальные эффекты
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
