package com.nicweiss.editor.simulation;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.nicweiss.editor.Generic.Store;

/**
 * Полный обработчик ввода в режиме симуляции.
 *
 * Фоновый поток (run): накапливает velocity от клавиш/стика → пишет simCamDelta.
 * GL-поток (keyDown/keyUp/touchDown/pollFrame): обрабатывает события из Editor,
 *   обновляет simKey* и simStick*, делегирует в SystemUI.
 *
 * Editor знает только об этом классе — не о геймпаде и не о логике симуляции.
 */
public class SimulationInputThread implements Runnable {
    public static Store store;

    // ── Константы движения ────────────────────────────────────────────────────
    private static final float ACCEL_KEY   = 0.75f;
    private static final float ACCEL_KEY_Y = ACCEL_KEY * 0.5f; // изо-коррекция Y
    private static final float ACCEL_STICK = 1.25f;
    private static final float FRICTION    = 0.82f;
    private static final float STOP_THRESH = 0.3f;
    private static final long  TICK_MS     = 16L;

    private float velX = 0f, velY = 0f;
    private float accumX = 0f, accumY = 0f;

    // ── Геймпад: edge-detect и дебаунс стика для меню ───────────────────────
    private boolean prevLT = false, prevRT = false, prevStart = false;
    private boolean prevDUp = false, prevDDown = false, prevA = false;
    private float   stickNavTimer = 0f;
    private static final float STICK_NAV_DELAY = 0.28f;

    // ── Фоновый поток: velocity → simCamDelta ─────────────────────────────────

    @Override
    public void run() {
        velX = velY = accumX = accumY = 0f;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Пока системный UI открыт — мировое движение полностью заморожено
                if (store.systemUI != null && store.systemUI.isOpen()) {
                    velX = velY = 0f;
                    Thread.sleep(TICK_MS);
                    continue;
                }

                if (store.simKeyLeft)  velX -= ACCEL_KEY;
                if (store.simKeyRight) velX += ACCEL_KEY;
                if (store.simKeyUp)    velY += ACCEL_KEY_Y;
                if (store.simKeyDown)  velY -= ACCEL_KEY_Y;

                float sx = store.simStickX;
                float sy = store.simStickY;
                if (sx != 0f) velX += sx * ACCEL_STICK;
                if (sy != 0f) velY -= sy * ACCEL_STICK * 0.5f;

                velX *= FRICTION;
                velY *= FRICTION;
                if (Math.abs(velX) < STOP_THRESH) velX = 0f;
                if (Math.abs(velY) < STOP_THRESH) velY = 0f;

                accumX += velX;
                accumY += velY;
                int dx = (int) accumX;
                int dy = (int) accumY;
                accumX -= dx;
                accumY -= dy;

                if (dx != 0 || dy != 0) {
                    store.simCamDelta = ((long) dx << 32) | ((long) dy & 0xFFFFFFFFL);
                }

                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        velX = velY = accumX = accumY = 0f;
        store.simKeyUp = store.simKeyDown = store.simKeyLeft = store.simKeyRight = false;
        store.simStickX = store.simStickY = 0f;
    }

    // ── GL-поток: события клавиатуры ──────────────────────────────────────────

    /** @return true — клавиша поглощена (Editor не обрабатывает дальше) */
    public boolean keyDown(int keyCode) {
        // Навигация меню клавишами когда MENU-вкладка открыта
        if (store.systemUI != null && store.systemUI.isMenuOpen()) {
            if (keyCode == 19) { store.systemUI.gamepadNavigate(-1); return true; } // UP
            if (keyCode == 20) { store.systemUI.gamepadNavigate(+1); return true; } // DOWN
        }
        if (store.systemUI != null && store.systemUI.handleKeyDown(keyCode)) {
            return true;
        }
        if (!isUIOpen()) {
            if (keyCode == 19 || keyCode == 51) store.simKeyUp    = true;
            if (keyCode == 20 || keyCode == 47) store.simKeyDown  = true;
            if (keyCode == 21 || keyCode == 29) store.simKeyLeft  = true;
            if (keyCode == 22 || keyCode == 32) store.simKeyRight = true;
        }
        return true;
    }

    public boolean keyUp(int keyCode) {
        if (keyCode == 19 || keyCode == 51) store.simKeyUp    = false;
        if (keyCode == 20 || keyCode == 47) store.simKeyDown  = false;
        if (keyCode == 21 || keyCode == 29) store.simKeyLeft  = false;
        if (keyCode == 22 || keyCode == 32) store.simKeyRight = false;
        return true;
    }

    // ── GL-поток: клик мышью ─────────────────────────────────────────────────

    /** @return true — клик поглощён UI */
    public boolean touchDown(int mouseX, int mouseY) {
        store.isGamepadMode = false;
        if (store.systemUI != null) {
            if (store.systemUI.handleClick(mouseX, mouseY,
                    store.uiWidthOriginal, store.uiHeightOriginal)) {
                return true;
            }
        }
        // ЛКМ по лейблу/иконке дропа — попытка подбора в инвентарь.
        DropManager.tryPickupHovered();
        return false;
    }

    // ── GL-поток: опрос геймпада (вызывается каждый кадр) ────────────────────

    public void pollFrame() {
        store.simStickX = 0f;
        store.simStickY = 0f;

        // Alt (клавиатура) или LB (геймпад) — показать подписи всех дропов в камере.
        boolean revealLabels = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
            || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);

        if (Controllers.getControllers().isEmpty()) {
            store.revealAllDropLabels = revealLabels;
            return;
        }

        Controller ctrl = Controllers.getControllers().first();
        ControllerMapping m = ctrl.getMapping();
        store.revealAllDropLabels = revealLabels || safeButton(ctrl, m.buttonL1);

        float ax   = ctrl.getAxis(m.axisLeftX);
        float ay   = ctrl.getAxis(m.axisLeftY);
        float dead = 0.12f;
        float dt   = com.badlogic.gdx.Gdx.graphics.getDeltaTime();

        boolean uiOpen = store.systemUI != null && store.systemUI.isOpen();

        if (uiOpen) {
            // UI открыт: стик не влияет на мир
            store.simStickX = 0f;
            store.simStickY = 0f;

            // Левый стик Y → навигация по меню с дебаунсом
            if (stickNavTimer > 0f) stickNavTimer -= dt;
            if (Math.abs(ay) > 0.5f && stickNavTimer <= 0f && store.systemUI.isMenuOpen()) {
                // Положительный Y стика = вниз (инвертирован) = следующий пункт
                store.systemUI.gamepadNavigate(ay > 0 ? +1 : -1);
                stickNavTimer = STICK_NAV_DELAY;
            }
        } else {
            // UI закрыт: стик управляет движением игрока
            store.simStickX = Math.abs(ax) > dead ? ax : 0f;
            store.simStickY = Math.abs(ay) > dead ? ay : 0f;
            stickNavTimer = 0f;
        }

        // Триггеры: кнопка L2/R2 или ось — но не правый стик!
        // Оси правого стика явно исключаем из детектирования триггеров.
        int axisCount  = ctrl.getAxisCount();
        int rightStickX = m.axisRightX; // обычно 2 или 3
        int rightStickY = m.axisRightY; // обычно 3 или 2

        boolean lt = safeButton(ctrl, m.buttonL2)
                  || safeTriggerAxis(ctrl, axisCount, 2, rightStickX, rightStickY)   // Linux/SDL LT
                  || safeTriggerAxis(ctrl, axisCount, 4, rightStickX, rightStickY);  // Windows LT
        boolean rt = safeButton(ctrl, m.buttonR2)
                  || safeTriggerAxis(ctrl, axisCount, 3, rightStickX, rightStickY)   // Linux/SDL RT
                  || safeTriggerAxis(ctrl, axisCount, 5, rightStickX, rightStickY);  // Windows RT
        boolean start = safeButton(ctrl, m.buttonStart);

        // Обнаруживаем ввод с геймпада — переключаем режим
        if (Math.abs(ax) > 0.3f || Math.abs(ay) > 0.3f
                || safeButton(ctrl, m.buttonA) || safeButton(ctrl, m.buttonB)
                || safeButton(ctrl, m.buttonX) || lt || rt || start) {
            store.isGamepadMode = true;
        }

        boolean bx = safeButton(ctrl, m.buttonX);
        boolean by = safeButton(ctrl, m.buttonY);
        boolean ba = safeButton(ctrl, m.buttonA);
        if (store.systemUI != null) {
            store.systemUI.pollGamepad(start, lt, rt, safeButton(ctrl, m.buttonB), bx, by, ba, ax, ay);
        }

        // D-pad и A — навигация меню (edge-detect)
        boolean dUp   = safeButton(ctrl, m.buttonDpadUp);
        boolean dDown = safeButton(ctrl, m.buttonDpadDown);
        boolean a     = safeButton(ctrl, m.buttonA);

        if (store.systemUI != null && store.systemUI.isOpen()) {
            if (dUp   && !prevDUp)   store.systemUI.gamepadNavigate(-1);
            if (dDown && !prevDDown) store.systemUI.gamepadNavigate(+1);
            // A в инвентаре обрабатывается pollGamepad (различие короткого/долгого нажатия)
            if (a && !prevA && store.systemUI.isMenuOpen()) store.systemUI.gamepadActivate();
        } else if (a && !prevA) {
            // UI закрыт: A подбирает предмет в фокусе (см. DropManager.renderLabels).
            DropManager.tryPickupFocused();
        }
        prevLT = lt; prevRT = rt; prevStart = start;
        prevDUp = dUp; prevDDown = dDown; prevA = a;
    }

    private boolean safeButton(Controller ctrl, int code) {
        return code >= 0 && ctrl.getButton(code);
    }

    /** Читает ось как триггер, только если она не совпадает с осями правого стика. */
    private boolean safeTriggerAxis(Controller ctrl, int axisCount,
                                    int axisIdx, int rightX, int rightY) {
        if (axisIdx < 0 || axisIdx >= axisCount) return false;
        if (axisIdx == rightX || axisIdx == rightY) return false; // это правый стик — пропускаем
        return ctrl.getAxis(axisIdx) > 0.5f;
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private boolean isUIOpen() {
        return store.systemUI != null && store.systemUI.isOpen();
    }
}
