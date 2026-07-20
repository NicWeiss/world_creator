package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;

/**
 * Точка входа для применения умения — сейчас ЗАГЛУШКА. Вынесена в отдельный класс (а не метод
 * внутри SimulationInputThread/Player), чтобы этим же вызовом позже могли пользоваться NPC —
 * реальные эффекты (урон/баффы/снаряды/ауры) будут проработаны отдельной задачей.
 */
public class SkillCaster {
    private SkillCaster() {}

    public static void cast(String skillId) {
        if (skillId == null) return;
        Gdx.app.log("SkillCaster", "cast: " + skillId);
    }
}
