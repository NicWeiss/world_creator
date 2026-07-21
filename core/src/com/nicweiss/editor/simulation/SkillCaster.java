package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.SkillCatalog;

/**
 * Точка входа для применения умения. Вынесена в отдельный класс (а не метод внутри
 * SimulationInputThread/Player), чтобы этим же вызовом позже могли пользоваться NPC.
 *
 * Реализация ПОКА ЧИСТО ВИЗУАЛЬНАЯ (см. SkillEffectRenderer) — реальный урон/попадания по врагам не
 * применяются, т.к. боевой системы для NPC ещё нет (SpawnManager никому не уменьшает health, см. его
 * class-comment). AURA/SUSTAINED/STANCE (ауры Вестника, Вихрь смерти, Безумие, Электро-Щит) — это
 * тумблеры: повторный каст выключает, состояние живёт в Player.activeAuras и рисуется в
 * AuraRenderer. ACTIVE/TACTIC — разовый визуальный эффект (см. trigger). PASSIVE сюда вообще не
 * долетает — такие умения не бинд.уются на кнопку (см. SystemUI.openKeybindPicker).
 *
 * Перезарядка (см. SkillCatalog.cooldownSeconds/Player.skillCooldowns): пока умение на КД, повторный
 * каст полностью игнорируется (даже попытка выключить тумблер раньше времени — ждём). Таймер КД
 * заводится только при АКТИВАЦИИ (разовый эффект или включение тумблера), не при выключении.
 */
public class SkillCaster {
    public static Store store;

    private SkillCaster() {}

    public static void cast(String skillId) {
        if (skillId == null || store.player == null) return;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get(skillId);
        if (def == null || def.kind == SkillCatalog.SkillKind.PASSIVE) return;
        if (store.player.isSkillOnCooldown(skillId)) return; // ждём перезарядку

        boolean activated;

        if (def.kind == SkillCatalog.SkillKind.AURA
                || def.kind == SkillCatalog.SkillKind.SUSTAINED
                || def.kind == SkillCatalog.SkillKind.STANCE) {
            if (store.player.activeAuras.remove(skillId)) {
                store.player.toggleRemainingTime.remove(skillId); // выключили вручную раньше срока
                activated = false; // выключение — не активация, перезарядку не запускаем
            } else {
                store.player.activeAuras.add(skillId);
                // Если у умения есть duration_sec (см. SkillCatalog, пока только elem_lightning_shield) —
                // заводим таймер автоотключения (см. Player.tickToggleDurations). Умений без этого
                // стата в SkillDef.stats просто нет ключа — держатся включёнными до повторного нажатия.
                for (SkillCatalog.SkillStat stat : def.stats) {
                    if ("duration_sec".equals(stat.key)) {
                        int level = store.player.effectiveSkillLevel(skillId);
                        store.player.toggleRemainingTime.put(skillId, (float) stat.at(level));
                        break;
                    }
                }
                activated = true;
            }
        } else {
            if (store.skillEffectRenderer != null) {
                store.skillEffectRenderer.trigger(skillId, store.player.effectiveSkillLevel(skillId));
            }
            activated = true;
        }

        if (activated) {
            double cd = SkillCatalog.cooldownSeconds(def, store.player.effectiveSkillLevel(skillId));
            if (cd > 0) store.player.skillCooldowns.put(skillId, (float) cd);
        }
    }
}
