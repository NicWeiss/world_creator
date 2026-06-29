package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;

import java.util.Random;

/**
 * Управляет погодой и временем суток.
 *
 * Дождь — стейт-машина:
 *   CLEAR  → 20% шанс каждые 30с → RAINING
 *   RAINING (1–3 мин) → COOLDOWN (2 мин) → CLEAR
 *
 * Во время дождя:
 *   - rainIntensity плавно растёт до 1.0, затем плавно падает
 *   - windMultiplier / windGustSpeed следуют за intensity
 *   - случайные вспышки молний (lightningFlash → Editor делает fade)
 */
public class WeatherThread implements Runnable {
    public static Store store;

    // ── Цикл дня ──────────────────────────────────────────────────────────────
    private static final long CYCLE_MS = 4L * 60 * 1000;  // 4-минутный цикл
    private static final long TICK_MS  = 16L;

    // ── Дождь ─────────────────────────────────────────────────────────────────
    private static final float RAIN_CHANCE        = 0.20f;       // 20% вероятность
    private static final long  RAIN_CHECK_MS      = 30_000L;     // проверка каждые 30с
    private static final long  RAIN_MIN_MS        = 60_000L;     // мин. длительность 1 мин
    private static final long  RAIN_MAX_MS        = 180_000L;    // макс. 3 мин
    private static final long  COOLDOWN_MS        = 120_000L;    // 2 мин кулдаун
    private static final float RAIN_FADE_IN_RATE  = 0.003f;      // +0.003/тик → ~5с до max
    private static final float RAIN_FADE_OUT_RATE = 0.0008f;     // -0.0008/тик → ~20с fade

    // ── Молнии ────────────────────────────────────────────────────────────────
    private static final long LIGHTNING_MIN_MS = 5_000L;
    private static final long LIGHTNING_MAX_MS = 20_000L;

    private enum RainState { CLEAR, RAINING, COOLDOWN }

    private final Random rng = new Random();
    private RainState rainState   = RainState.CLEAR;

    // ── Направление ветра ─────────────────────────────────────────────────────
    private float windAngle     = 0f;       // текущий угол (рад), 0 = вправо
    private float windAngleVel  = 0.02f;    // скорость вращения (рад/с)
    private long  nextWindShift = 0L;
    private long      rainEndMs   = 0;
    private long      cooldownEndMs = 0;
    private long      nextCheckMs   = 0;
    private long      nextLightningMs = Long.MAX_VALUE;
    private boolean   secondFlashPending = false;
    private long      secondFlashMs = 0;

    @Override
    public void run() {
        float sinVal = Math.max(-1f, Math.min(1f,
            (store.dayCoefficient - 0.45f) / 0.55f));
        double initPhase = Math.asin(sinVal);
        long initElapsed = (long)(initPhase / (2 * Math.PI) * CYCLE_MS);
        long startMs = System.currentTimeMillis() - initElapsed;

        nextCheckMs = System.currentTimeMillis();
        startRain(System.currentTimeMillis()); // DEBUG: немедленный старт для отладки

        while (!Thread.currentThread().isInterrupted()) {
            try {
                long now = System.currentTimeMillis();

                // ── Цикл дня/ночи ─────────────────────────────────────────────
                long elapsed = (now - startMs) % CYCLE_MS;
                float normalizedPhase = (float) elapsed / CYCLE_MS;
                double phase = normalizedPhase * 2 * Math.PI;
                float coeff = (float)(Math.sin(phase) * 0.55 + 0.45);
                store.dayCoefficient = Math.max(-0.10f, Math.min(1f, coeff));
                store.dayPhase       = normalizedPhase;
                store.cloudTime      = (now - startMs) / 1000f;

                // ── Направление ветра: плавно меняется раз в 12-30 секунд ───────
                if (now >= nextWindShift) {
                    nextWindShift = now + 30_000L + (long)(rng.nextFloat() * 90_000L); // 30с..2мин
                    windAngleVel  = (rng.nextFloat() - 0.5f) * 0.08f; // -0.04..+0.04 рад/с
                }
                windAngle += windAngleVel * TICK_MS / 1000f;
                store.windDirX = (float)Math.cos(windAngle);
                store.windDirY = (float)Math.sin(windAngle);

                // ── Стейт-машина дождя ────────────────────────────────────────
                tickRain(now);

                // ── Молнии ────────────────────────────────────────────────────
                tickLightning(now);

                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void tickRain(long now) {
        switch (rainState) {

            case CLEAR:
                // Плавно гасим остаток intensity если есть
                if (store.rainIntensity > 0f) {
                    store.rainIntensity = Math.max(0f, store.rainIntensity - RAIN_FADE_OUT_RATE);
                    updateWindFromIntensity();
                }
                // Проверяем шанс начала дождя
                if (now >= nextCheckMs) {
                    nextCheckMs = now + RAIN_CHECK_MS;
                    if (rng.nextFloat() < RAIN_CHANCE) {
                        startRain(now);
                    }
                }
                break;

            case RAINING:
                // Плавно набираем intensity до 1
                if (store.rainIntensity < 1f) {
                    store.rainIntensity = Math.min(1f, store.rainIntensity + RAIN_FADE_IN_RATE);
                    updateWindFromIntensity();
                }
                // Проверяем окончание дождя
                if (now >= rainEndMs) {
                    rainState    = RainState.COOLDOWN;
                    cooldownEndMs = now + COOLDOWN_MS;
                    nextLightningMs = Long.MAX_VALUE; // молнии прекращаются
                }
                break;

            case COOLDOWN:
                // Плавно гасим intensity
                if (store.rainIntensity > 0f) {
                    store.rainIntensity = Math.max(0f, store.rainIntensity - RAIN_FADE_OUT_RATE);
                    updateWindFromIntensity();
                }
                if (now >= cooldownEndMs && store.rainIntensity <= 0f) {
                    rainState   = RainState.CLEAR;
                    nextCheckMs = now + RAIN_CHECK_MS;
                }
                break;
        }
    }

    private void startRain(long now) {
        rainState = RainState.RAINING;
        long duration = RAIN_MIN_MS + (long)(rng.nextFloat() * (RAIN_MAX_MS - RAIN_MIN_MS));
        rainEndMs = now + duration;
        scheduleLightning(now);
    }

    /** windMultiplier и windGustSpeed плавно следуют за rainIntensity */
    private void updateWindFromIntensity() {
        float i = store.rainIntensity;
        store.windMultiplier = 1f + i * 2f;          // 1..3
        store.windGustSpeed  = 1f + i * 1.5f;        // 1..2.5
    }

    // ── Молнии ────────────────────────────────────────────────────────────────

    private void tickLightning(long now) {
        // Двойная вспышка: прямо в яркую фазу (без предвспышки)
        if (secondFlashPending && now >= secondFlashMs) {
            secondFlashPending = false;
            store.lightningFlash = 0.55f + rng.nextFloat() * 0.35f; // 0.55..0.9
        }

        if (now < nextLightningMs) return;
        if (store.rainIntensity < 0.4f) {
            scheduleLightning(now);
            return;
        }

        // Выбираем случайный тайл-цель в видимой области для разряда молнии
        // Цель: случайная точка в поле видимости, НО на удалении от игрока (центра экрана).
        // Используем полярные координаты: дистанция [min..max] от центра экрана.
        float W       = store.display.get("width");
        float H       = store.display.get("height");
        float minDist = Math.min(W, H) * 0.50f; // ×2 от игрока
        float maxDist = Math.min(W, H) * 0.528f; // +10%
        float angle   = rng.nextFloat() * 2f * (float)Math.PI;
        float dist    = minDist + rng.nextFloat() * (maxDist - minDist);
        // Экранные координаты относительно центра
        float tScrX = W / 2f + (float)Math.cos(angle) * dist;
        float tScrY = H / 2f + (float)Math.sin(angle) * dist;
        // Зажимаем в границах экрана с отступом
        tScrX = Math.max(40, Math.min(W - 40, tScrX));
        tScrY = Math.max(40, Math.min(H - 40, tScrY));
        // Конвертируем экранные → мировые декартовые координаты
        float isoX = tScrX - store.shiftX;
        float isoY = tScrY - store.shiftY;
        store.lightningTargetWX = (isoX + 2f * isoY) / 2f;
        store.lightningTargetWY = (2f * isoY - isoX) / 2f;
        store.lightningBoltNew  = true;

        // Первая вспышка: 1.5 = запускает предвспышечное затемнение в Editor
        store.lightningFlash = 1.2f + rng.nextFloat() * 0.4f; // 1.2..1.6

        // Планируем вторую (двойная молния — реалистичный эффект)
        if (rng.nextFloat() < 0.6f) {
            secondFlashPending = true;
            secondFlashMs = now + 150 + (long)(rng.nextFloat() * 200);
        }

        scheduleLightning(now);
    }

    private void scheduleLightning(long now) {
        long delay = LIGHTNING_MIN_MS + (long)(rng.nextFloat() * (LIGHTNING_MAX_MS - LIGHTNING_MIN_MS));
        // Во время сильного дождя молнии чаще
        if (store.rainIntensity > 0.7f) delay = (long)(delay * 0.5f);
        nextLightningMs = now + delay;
    }
}
