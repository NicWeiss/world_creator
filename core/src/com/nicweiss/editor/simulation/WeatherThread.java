package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;

/**
 * Управляет погодой и временем суток — плавно меняет dayCoefficient,
 * запускает пересчёт освещения при смене цикла.
 */
public class WeatherThread implements Runnable {
    public static Store store;

    // 4-минутный цикл. Тик 16ms (~60 fps) — изменение за тик ≈ 0.00023, визуально незаметно.
    private static final long CYCLE_MS = 4L * 60 * 1000;
    private static final long TICK_MS  = 16L;

    @Override
    public void run() {
        // Берём текущее значение освещения и вычисляем соответствующую фазу цикла,
        // чтобы при переходе в симуляцию не было скачка.
        // coeff = sin(phase)*0.55 + 0.45  →  sin(phase) = (coeff-0.45)/0.55
        float sinVal = Math.max(-1f, Math.min(1f,
            (store.dayCoefficient - 0.45f) / 0.55f));
        double initPhase = Math.asin(sinVal); // [-π/2 .. π/2]

        // phase = elapsed / CYCLE_MS * 2π  →  elapsed = phase * CYCLE_MS / (2π)
        long initElapsed = (long)(initPhase / (2 * Math.PI) * CYCLE_MS);
        long startMs = System.currentTimeMillis() - initElapsed;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                long elapsed = (System.currentTimeMillis() - startMs) % CYCLE_MS;
                float normalizedPhase = (float) elapsed / CYCLE_MS; // 0..1

                double phase = normalizedPhase * 2 * Math.PI;

                // sin [-1..1] → dayCoefficient [-0.10..1.0]
                float coeff = (float)(Math.sin(phase) * 0.55 + 0.45);
                store.dayCoefficient = Math.max(-0.10f, Math.min(1f, coeff));
                store.dayPhase       = normalizedPhase;

                // TODO: осадки, туман, ветер, гроза

                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
