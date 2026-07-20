package com.nicweiss.editor.utils;

/**
 * Одна привязываемая ячейка панели умений (см. Player.mainSkillSlots/comboSkillSlots) — какое
 * умение в ней лежит и какой ввод её вызывает. Чисто данные, без логики — рендер (PlayerHud,
 * SystemUI) и диспетчинг ввода (SimulationInputThread) читают/пишут поля напрямую.
 *
 * Клавиатура/мышь и геймпад хранятся в ОТДЕЛЬНЫХ полях (не в одном общем inputCode) — по
 * требованию пользователя: привязка кнопки геймпада не должна конфликтовать/затирать привязку
 * клавиши на той же ячейке (и наоборот). Один и тот же слот может одновременно иметь и клавиатурную,
 * и геймпад-привязку одного и того же умения (см. Player — дефолтный пресет "Удар" на ЛКМ + B).
 * Каст всегда проверяет ТОЛЬКО поле, соответствующее источнику нажатия (клавиатура/мышь →
 * keyboardInputCode, геймпад → gamepadInputCode, см. SimulationInputThread.castIfBound) —
 * конфликт между ними невозможен по построению.
 */
public class SkillSlot {
    /** null = ячейка пуста. */
    public String skillId;

    /** Код клавиатуры/мыши: "KEY_Q", "MOUSE_LEFT", "MOUSE_RIGHT" и т.п. null = не привязано. */
    public String keyboardInputCode;

    /** Код геймпада: "PAD_X", "PAD_Y", "PAD_B", "PAD_R1", "PAD_R2", "PAD_R3". null = не привязано. */
    public String gamepadInputCode;

    /** true — ячейка из комбо-ряда (требует зажатый Shift/LT в момент каста, см.
     *  SimulationInputThread) — чисто информационное поле, сам факт уже определяется тем,
     *  в каком массиве (mainSkillSlots/comboSkillSlots) лежит эта ячейка. */
    public boolean isCombo;
}
