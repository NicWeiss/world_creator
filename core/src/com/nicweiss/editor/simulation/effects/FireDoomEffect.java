package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.nicweiss.editor.utils.SkillCatalog;

/**
 * ДУМ — после активации в области radiusTiles (м=клетки, см. SkillCatalog "elem_fire_doom"
 * radius_m) вокруг игрока загорается множество очагов огня; область следует за игроком (см.
 * DoomSpawner — позиция берётся заново на каждый тик), каждая точка спавна проверяется на
 * проходимость.
 */
public final class FireDoomEffect {
    private FireDoomEffect() {}

    public static void trigger(int level, EffectSink sink) {
        if (FxContext.store.player == null) return;
        Texture[] fireFrames = GroundFireEffect.loadFrames();
        if (fireFrames == null) return;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_fire_doom");
        double radiusM = def != null ? def.compute(level).getOrDefault("radius_m", 3.0) : 3.0;
        sink.spawn(new DoomSpawner((float) radiusM, fireFrames, sink));
    }
}
