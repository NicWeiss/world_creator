package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.SkillCatalog;

/**
 * Точка входа для применения умения. Вынесена в отдельный класс (а не метод внутри
 * SimulationInputThread/Player), чтобы этим же вызовом позже могли пользоваться NPC.
 *
 * Реализация ПОКА ЧИСТО ВИЗУАЛЬНАЯ (см. SkillEffectRenderer) — реальный урон/попадания по врагам не
 * применяются, т.к. боевой системы для NPC ещё нет (SpawnManager никому не уменьшает health, см. его
 * class-comment). AURA/SUSTAINED/STANCE (ауры Вестника, Вихрь смерти, Безумие) — это тумблеры:
 * повторный каст выключает, состояние живёт в Player.activeAuras и рисуется в
 * SkillEffectRenderer.drawPersistentAuras. ACTIVE/TACTIC — разовый визуальный эффект (см. trigger).
 * PASSIVE сюда вообще не долетает — такие умения не бинд.уются на кнопку (см. SystemUI.openKeybindPicker).
 */
public class SkillCaster {
    public static Store store;

    private SkillCaster() {}

    public static void cast(String skillId) {
        if (skillId == null || store.player == null) return;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get(skillId);
        if (def == null || def.kind == SkillCatalog.SkillKind.PASSIVE) return;

        if (def.kind == SkillCatalog.SkillKind.AURA
                || def.kind == SkillCatalog.SkillKind.SUSTAINED
                || def.kind == SkillCatalog.SkillKind.STANCE) {
            if (!store.player.activeAuras.remove(skillId)) store.player.activeAuras.add(skillId);
            return;
        }

        if (store.skillEffectRenderer != null) {
            store.skillEffectRenderer.trigger(skillId, store.player.effectiveSkillLevel(skillId));
        }
    }
}
