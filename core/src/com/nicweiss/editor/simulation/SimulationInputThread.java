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
    private boolean prevDLeft = false, prevDRight = false;
    private boolean prevL3 = false; // клик левого стика — отладочный дроп, см. pollFrame
    private float   stickNavTimer = 0f;
    private static final float STICK_NAV_DELAY = 0.28f;

    // ── Каст умений с геймпада (edge-detect, только когда UI закрыт, см. pollFrame) ─────────────
    private boolean prevCastX = false, prevCastY = false, prevCastB = false;
    private boolean prevCastR1 = false, prevCastR2 = false, prevCastR3 = false;

    // ── Подвкладки Навыков — L1/R1 (edge-detect, только когда вкладка Навыки открыта) ───────────
    private boolean prevSkillSubtabL1 = false, prevSkillSubtabR1 = false;

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

                // Клик-муав: ручной ввод (клавиши/стик) всегда отменяет текущую цель клика — иначе
                // они боролись бы друг с другом. Без ручного ввода — двигаемся к точке клика тем же
                // конвейером (велосити→трение→simCamDelta→PhysicThread.moveBy), направление переводим
                // в изометрическую дельту через Transform.cartesianToIsometric — точное обратное
                // преобразование к тому, что PhysicThread.applyInput делает с simCamDelta (см. там).
                boolean manualInput = store.simKeyLeft || store.simKeyRight
                                   || store.simKeyUp || store.simKeyDown || sx != 0f || sy != 0f;
                if (manualInput) {
                    store.hasMoveTarget = false;
                } else if (store.hasMoveTarget && store.player != null) {
                    float dCartX = store.moveTargetX - store.player.worldX;
                    float dCartY = store.moveTargetY - store.player.worldY;
                    float dist = (float) Math.sqrt(dCartX * dCartX + dCartY * dCartY);
                    float arriveEps = Math.max(4f, store.tileSizeWidth * 0.15f);
                    if (dist < arriveEps) {
                        store.hasMoveTarget = false;
                    } else {
                        float ux = dCartX / dist, uy = dCartY / dist;
                        float[] iso = com.nicweiss.editor.utils.Transform.cartesianToIsometric(ux, uy);
                        velX += iso[0] * ACCEL_STICK;
                        velY += iso[1] * ACCEL_STICK;
                    }
                }

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
        // Окно привязки умения ждёт ОДНО нажатие — либо привязывается (код из разрешённого пула),
        // либо просто отбрасывается (таймаут продолжает тикать, см. SystemUI.tick). Весь остальной
        // диспетчинг (движение/меню/каст) в это время не должен срабатывать.
        if (store.systemUI != null && store.systemUI.isCapturingKeybind()) {
            store.systemUI.captureKeyboardInput(keyCode);
            return true;
        }

        // Навигация меню клавишами когда MENU-вкладка открыта
        if (store.systemUI != null && store.systemUI.isMenuOpen()) {
            if (keyCode == 19) { store.systemUI.gamepadNavigate(-1); return true; } // UP
            if (keyCode == 20) { store.systemUI.gamepadNavigate(+1); return true; } // DOWN
        }
        if (store.systemUI != null && store.systemUI.handleKeyDown(keyCode)) {
            return true;
        }
        if (!isUIOpen()) {
            // Движение — только стрелками. W/A/S/D раньше дублировали стрелки, но освобождены под
            // умения (см. ниже) — вместо WASD теперь ещё движение по клику мыши (см. touchDown/run()).
            if (keyCode == 19) { store.simKeyUp    = true; store.hasMoveTarget = false; }
            if (keyCode == 20) { store.simKeyDown  = true; store.hasMoveTarget = false; }
            if (keyCode == 21) { store.simKeyLeft  = true; store.hasMoveTarget = false; }
            if (keyCode == 22) { store.simKeyRight = true; store.hasMoveTarget = false; }
            // Отладочный выброс лута+опыта (см. Store.debugDropTrigger) — Enter на клавиатуре,
            // L3 (клик левого стика) на геймпаде (см. pollFrame) — R3 освобождён под каст умений.
            if (keyCode == com.badlogic.gdx.Input.Keys.ENTER && store.debugDropTrigger != null) {
                store.debugDropTrigger.run();
            }
            // 1-4 — тратят первый предмет из соответствующей ячейки стека (см. StackManager).
            if (keyCode == com.badlogic.gdx.Input.Keys.NUM_1) StackManager.consumeFirst(0);
            if (keyCode == com.badlogic.gdx.Input.Keys.NUM_2) StackManager.consumeFirst(1);
            if (keyCode == com.badlogic.gdx.Input.Keys.NUM_3) StackManager.consumeFirst(2);
            if (keyCode == com.badlogic.gdx.Input.Keys.NUM_4) StackManager.consumeFirst(3);

            // Каст умения — Q/W/E/R/A/S/D/F/Z/X/C/V (см. SystemUI.keyToInputCode), Shift = комбо-ряд.
            String code = SystemUI.keyToInputCode(keyCode);
            if (code != null) castBoundSkillIfAny(code);
        }
        return true;
    }

    public boolean keyUp(int keyCode) {
        if (keyCode == 19) store.simKeyUp    = false;
        if (keyCode == 20) store.simKeyDown  = false;
        if (keyCode == 21) store.simKeyLeft  = false;
        if (keyCode == 22) store.simKeyRight = false;
        return true;
    }

    /** Если на inputCode (в текущем ряду — основном или комбо, определяется зажатым Shift) что-то
     *  привязано — вызывает SkillCaster.cast (заглушка). Клавиатура/мышь: модификатор комбо-ряда —
     *  Shift (см. castIfBound для геймпада, где модификатор — LT).
     *  @return true — умение найдено и вызвано. */
    private boolean castBoundSkillIfAny(String inputCode) {
        boolean shiftHeld = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                         || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
        return castIfBound(inputCode, shiftHeld, false);
    }

    /** Общая точка каста для клавиатуры/мыши (см. castBoundSkillIfAny) и геймпада (см. pollFrame,
     *  комбо-модификатор там — LT, а не Shift). gamepad=true — сверяем ТОЛЬКО с
     *  SkillSlot.gamepadInputCode, false — ТОЛЬКО с keyboardInputCode (раздельные поля, см.
     *  SkillSlot — привязки клавиатуры/мыши и геймпада никогда не пересекаются по построению).
     *  @return true — умение найдено и вызвано. */
    private boolean castIfBound(String inputCode, boolean comboHeld, boolean gamepad) {
        if (store.player == null) return false;
        com.nicweiss.editor.utils.SkillSlot[] slots = comboHeld ? store.player.comboSkillSlots : store.player.mainSkillSlots;
        for (com.nicweiss.editor.utils.SkillSlot slot : slots) {
            String bound = gamepad ? slot.gamepadInputCode : slot.keyboardInputCode;
            if (inputCode.equals(bound)) {
                SkillCaster.cast(slot.skillId);
                return true;
            }
        }
        return false;
    }

    // ── GL-поток: клик мышью ─────────────────────────────────────────────────

    /** button — libGDX-код кнопки мыши (0=ЛКМ, 1=ПКМ). @return true — клик поглощён UI. */
    public boolean touchDown(int mouseX, int mouseY, int button) {
        store.isGamepadMode = false;

        // Пикер ждёт ввод кнопки — ЛКМ/ПКМ тоже кандидат на привязку (см. keyDown — тот же принцип).
        if (store.systemUI != null && store.systemUI.isCapturingKeybind()) {
            store.systemUI.captureMouseInput(button);
            return true;
        }

        if (store.systemUI != null) {
            if (store.systemUI.handleClick(mouseX, mouseY,
                    store.uiWidthOriginal, store.uiHeightOriginal, button)) {
                return true;
            }
        }
        if (isUIOpen()) return false; // мимо панели — UI открыт, но ничего в мире не делаем

        if (button == 0) {
            // Приоритет: (1) подбор дропа под курсором, (2) привязанное умение на ЛКМ,
            // (3) иначе — клик-муав (движение к точке клика, см. run()/PhysicThread).
            if (DropManager.hoveredDrop != null) {
                DropManager.tryPickupHovered();
                return false;
            }
            if (castBoundSkillIfAny("MOUSE_LEFT")) return false;

            store.hasMoveTarget = true;
            store.moveTargetX = store.cursorWorldX;
            store.moveTargetY = store.cursorWorldY;
            return false;
        }

        if (button == 1) castBoundSkillIfAny("MOUSE_RIGHT");
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

        // Окно привязки умения ждёт ОДНО нажатие X/Y/B/R1/R2/R3 — весь обычный диспетчинг геймпада
        // (меню-навигация/подбор/каст) в этот кадр пропускаем (см. keyDown/touchDown — тот же принцип).
        if (store.systemUI != null && store.systemUI.isCapturingKeybind()) {
            boolean curX = safeButton(ctrl, m.buttonX);
            boolean curY = safeButton(ctrl, m.buttonY);
            boolean curB = safeButton(ctrl, m.buttonB);
            boolean curR1 = safeButton(ctrl, m.buttonR1);
            boolean curR2 = safeButton(ctrl, m.buttonR2);
            boolean curR3 = safeButton(ctrl, m.buttonRightStick);
            if      (curX)  store.systemUI.captureGamepadInput("PAD_X");
            else if (curY)  store.systemUI.captureGamepadInput("PAD_Y");
            else if (curB)  store.systemUI.captureGamepadInput("PAD_B");
            else if (curR1) store.systemUI.captureGamepadInput("PAD_R1");
            else if (curR2) store.systemUI.captureGamepadInput("PAD_R2");
            else if (curR3) store.systemUI.captureGamepadInput("PAD_R3");

            // ВАЖНО: синхронизируем edge-detect состояние под ТЕКУЩИЕ физические кнопки перед
            // выходом — иначе, если игрок ещё держит кнопку, которой только что забиндил умение
            // (например X), на следующем кадре (пикер уже закрылся) она читается как "новое
            // нажатие" по устаревшему prevBX=false внутри SystemUI (pollGamepad не вызывался все
            // кадры захвата) и сразу открывает НОВОЕ окно привязки (см. баг: "назначается и сразу
            // же перехватывается открывая новое окно"). Каст-трекеры (prevCastX и т.п.) синхронизируем
            // тоже — защитно, на случай похожего сценария при закрытии всего UI сразу после бинда.
            store.systemUI.syncGamepadButtonState(curX, safeButton(ctrl, m.buttonA));
            prevCastX = curX; prevCastY = curY; prevCastB = curB;
            prevCastR1 = curR1; prevCastR2 = curR2; prevCastR3 = curR3;
            return;
        }

        store.revealAllDropLabels = revealLabels || safeButton(ctrl, m.buttonL1);

        float ax   = ctrl.getAxis(m.axisLeftX);
        float ay   = ctrl.getAxis(m.axisLeftY);
        float dead = 0.12f;
        float dt   = com.badlogic.gdx.Gdx.graphics.getDeltaTime();

        boolean uiOpen = store.systemUI != null && store.systemUI.isOpen();

        // Подвкладки Навыков (Воитель/Вестник/Стихийник) — L1/R1 (бамперы), edge-detect. Раньше
        // переключались только мышью (см. баг: "по подвкладкам не работает перемещение с геймпада").
        if (store.systemUI != null && store.systemUI.isSkillsOpen()) {
            boolean padL1 = safeButton(ctrl, m.buttonL1);
            boolean padR1b = safeButton(ctrl, m.buttonR1);
            if (padL1  && !prevSkillSubtabL1) store.systemUI.skillsSwitchSubtab(-1);
            if (padR1b && !prevSkillSubtabR1) store.systemUI.skillsSwitchSubtab(+1);
            prevSkillSubtabL1 = padL1;
            prevSkillSubtabR1 = padR1b;
        } else {
            prevSkillSubtabL1 = false;
            prevSkillSubtabR1 = false;
        }

        if (uiOpen) {
            // UI открыт: стик не влияет на мир
            store.simStickX = 0f;
            store.simStickY = 0f;

            // Левый стик → навигация по меню/статам/умениям с дебаунсом (тот же контрол, что и
            // D-pad — раньше стик работал только в МЕНЮ; теперь дублирует D-pad везде, см.
            // SystemUI.gamepadNavigate/gamepadNavigateCol). Вкладка Навыки — единственная, где
            // используется и ось X (столбцы сетки умений/ячеек пикера), остальные — только Y.
            if (stickNavTimer > 0f) stickNavTimer -= dt;
            boolean rowNavTabs = store.systemUI.isMenuOpen() || store.systemUI.isStatsOpen() || store.systemUI.isSkillsOpen();
            if (stickNavTimer <= 0f && rowNavTabs) {
                if (Math.abs(ay) > 0.5f && Math.abs(ay) >= Math.abs(ax)) {
                    // Положительный Y стика = вниз (инвертирован) = следующий пункт
                    store.systemUI.gamepadNavigate(ay > 0 ? +1 : -1);
                    stickNavTimer = STICK_NAV_DELAY;
                } else if (store.systemUI.isSkillsOpen() && Math.abs(ax) > 0.5f) {
                    store.systemUI.gamepadNavigateCol(ax > 0 ? +1 : -1);
                    stickNavTimer = STICK_NAV_DELAY;
                }
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

        // D-pad и A — навигация меню, когда UI открыт; когда закрыт — D-pad тратит стеки (edge-detect)
        boolean dUp    = safeButton(ctrl, m.buttonDpadUp);
        boolean dDown  = safeButton(ctrl, m.buttonDpadDown);
        boolean dLeft  = safeButton(ctrl, m.buttonDpadLeft);
        boolean dRight = safeButton(ctrl, m.buttonDpadRight);
        boolean a      = safeButton(ctrl, m.buttonA);

        if (store.systemUI != null && store.systemUI.isOpen()) {
            if (dUp   && !prevDUp)   store.systemUI.gamepadNavigate(-1);
            if (dDown && !prevDDown) store.systemUI.gamepadNavigate(+1);
            if (store.systemUI.isSkillsOpen()) {
                if (dLeft  && !prevDLeft)  store.systemUI.gamepadNavigateCol(-1);
                if (dRight && !prevDRight) store.systemUI.gamepadNavigateCol(+1);
            }
            // A в инвентаре обрабатывается pollGamepad (различие короткого/долгого нажатия)
            if (a && !prevA && store.systemUI.isMenuOpen()) store.systemUI.gamepadActivate();
        } else {
            // UI закрыт: A подбирает предмет в фокусе (см. DropManager.renderLabels), D-pad —
            // тратит первый предмет из соответствующей ячейки стека (см. StackManager).
            if (a && !prevA) DropManager.tryPickupFocused();
            if (dUp    && !prevDUp)    StackManager.consumeFirst(0);
            if (dDown  && !prevDDown)  StackManager.consumeFirst(1);
            if (dLeft  && !prevDLeft)  StackManager.consumeFirst(2);
            if (dRight && !prevDRight) StackManager.consumeFirst(3);
        }

        // Левый триггер зажат — переключает HUD-ряд ячеек умений на комбо (см. PlayerHud,
        // тот же смысл, что и Shift на клавиатуре).
        store.leftTriggerHeld = lt;

        // Каст умений с геймпада — только когда UI закрыт (тот же принцип, что и клавиатура, см.
        // keyDown). LT — модификатор комбо-ряда (аналог Shift). R3 больше НЕ триггерит отладочный
        // дроп (остался только Enter на клавиатуре плюс L3, см. ниже) — освобождён под каст умений.
        if (!uiOpen) {
            boolean padB  = safeButton(ctrl, m.buttonB);
            boolean padR1 = safeButton(ctrl, m.buttonR1);
            boolean padR3 = safeButton(ctrl, m.buttonRightStick);
            if (bx    && !prevCastX)  castIfBound("PAD_X",  lt, true);
            if (by    && !prevCastY)  castIfBound("PAD_Y",  lt, true);
            if (padB  && !prevCastB)  castIfBound("PAD_B",  lt, true);
            if (padR1 && !prevCastR1) castIfBound("PAD_R1", lt, true);
            if (rt    && !prevCastR2) castIfBound("PAD_R2", lt, true);
            if (padR3 && !prevCastR3) castIfBound("PAD_R3", lt, true);
            prevCastX = bx; prevCastY = by; prevCastB = padB;
            prevCastR1 = padR1; prevCastR2 = rt; prevCastR3 = padR3;
        }

        // L3 (клик левого стика) — отладочный выброс лута+опыта (см. Store.debugDropTrigger,
        // Enter на клавиатуре — тот же триггер, см. keyDown). R3 больше не годится (занят кастом).
        boolean l3 = safeButton(ctrl, m.buttonLeftStick);
        if (l3 && !prevL3 && !uiOpen && store.debugDropTrigger != null) {
            store.debugDropTrigger.run();
        }
        prevL3 = l3;

        prevLT = lt; prevRT = rt; prevStart = start;
        prevDUp = dUp; prevDDown = dDown; prevA = a;
        prevDLeft = dLeft; prevDRight = dRight;
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
