package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;

/**
 * Обрабатывает ввод в режиме симуляции.
 * Читает состояние клавиш из Store (обновляются GL-потоком),
 * применяет плавное движение камеры с velocity и friction.
 *
 * Зум недоступен. Мышь не влияет на карту.
 * Кнопка переключения симуляции работает отдельно через UserInterface.
 */
public class SimulationInputThread implements Runnable {
    public static Store store;

    private static final float ACCELERATION = 8f;   // px добавляемых к velocity за тик
    private static final float FRICTION      = 0.80f; // затухание velocity каждый тик
    private static final float STOP_THRESH   = 0.4f;  // ниже — считать скоростью 0
    private static final long  TICK_MS       = 16L;   // ~60 fps

    private float velX = 0f;
    private float velY = 0f;

    @Override
    public void run() {
        velX = 0f;
        velY = 0f;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Разгон по нажатым клавишам
                if (store.simKeyLeft)  velX -= ACCELERATION;
                if (store.simKeyRight) velX += ACCELERATION;
                if (store.simKeyUp)    velY += ACCELERATION;
                if (store.simKeyDown)  velY -= ACCELERATION;

                // Плавное торможение
                velX *= FRICTION;
                velY *= FRICTION;

                // Убираем микро-дрейф
                if (Math.abs(velX) < STOP_THRESH) velX = 0f;
                if (Math.abs(velY) < STOP_THRESH) velY = 0f;

                // Применяем к позиции камеры
                if (velX != 0f) store.shiftX -= (int) velX;
                if (velY != 0f) store.shiftY -= (int) velY;

                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Сбросить состояние при остановке
        velX = 0f;
        velY = 0f;
        store.simKeyUp    = false;
        store.simKeyDown  = false;
        store.simKeyLeft  = false;
        store.simKeyRight = false;
    }
}
