package com.nicweiss.editor.simulation;

import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.utils.NpcCatalog;

import java.util.LinkedHashMap;

/**
 * Runtime-экземпляр боевого NPC (воин/маг/монстр), созданный спавнером в симуляции — см.
 * {@link SpawnManager}. НЕ хранится в Store.creations (это статичные, редактируемые в редакторе
 * NPC) — живёт только в Store.simCreatures, существует только в режиме симуляции, не
 * сохраняется/загружается и не виден в NpcEditorWindow (см. FileManager, NpcEditorWindow —
 * оба работают исключительно со Store.creations).
 */
public class SimCreature extends Creation {
    public String typeKey;           // ключ NpcCatalog.TYPES
    public NpcCatalog.Faction faction;
    public NpcCatalog.Tier tier;

    public int maxHealth, health;
    public int damage;
    public float speedMultiplier = 1f;
    public LinkedHashMap<String, Integer> resistances = new LinkedHashMap<>();

    // Спавнер, который породил эту сущность (uuid здания-спавнера) — по нему спавнер считает,
    // сколько его подопечных ещё живы, и доспавнивает недостающих (см. SpawnManager).
    public String spawnerUuid;

    public boolean isAlive() {
        return health > 0;
    }
}
