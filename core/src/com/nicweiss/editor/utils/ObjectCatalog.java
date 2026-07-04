package com.nicweiss.editor.utils;

import java.util.LinkedHashMap;

/**
 * Справочник типов объектов (не-NPC сущностей карты — здания/Store.buildings): спавнеры, сундук,
 * источник, переход. Аналог {@link ItemModifierCatalog}/{@link NpcCatalog}, но без генерации —
 * персональные настройки каждого экземпляра (уровень спавна, награды сундука и т.д.) хранятся
 * отдельно в Store.buildingSettings (uuid → LinkedHashMap с dunder-полями, см. ObjectEditorWindow)
 * и не описываются в каталоге — тут только таксономия типов + дефолтная текстура.
 */
public class ObjectCatalog {

    public static class TypeDef {
        public final String key;
        public final String label;
        public final String defaultImage; // относительный путь в assets/

        TypeDef(String key, String label, String defaultImage) {
            this.key = key;
            this.label = label;
            this.defaultImage = defaultImage;
        }
    }

    public static final LinkedHashMap<String, TypeDef> TYPES = new LinkedHashMap<>();
    static {
        // Три спавнера делят одну текстуру (assets/objects/spawn.png — единственная заведённая
        // под спавн в ТЗ), различаются типом (кого спавнят), не картинкой.
        TYPES.put("spawner_enemy",   new TypeDef("spawner_enemy",   "Спавнер врагов",    "objects/spawn.png"));
        TYPES.put("spawner_ally",    new TypeDef("spawner_ally",    "Спавнер союзников", "objects/spawn.png"));
        TYPES.put("spawner_monster", new TypeDef("spawner_monster", "Спавнер монстров",  "objects/spawn.png"));
        TYPES.put("chest",           new TypeDef("chest",           "Сундук",            "objects/chest.png"));
        TYPES.put("source",          new TypeDef("source",          "Источник",          "objects/source.png"));
        TYPES.put("portal",          new TypeDef("portal",          "Переход",           "objects/portal.png"));
    }

    // Целевой размер объекта на экране — множитель store.tileSizeWidth (см.
    // Creation.targetMaxScreenSize). Спавнер/сундук/источник должны умещаться на тайле,
    // переход — размером с дерево. История: 1.0/2.2 → 0.7/1.54 (×0.7) → 0.49/1.078 (×0.7 ещё раз).
    private static final float TILE_FIT_SIZE_MULT = 0.49f;
    private static final float PORTAL_SIZE_MULT   = 1.078f;

    private ObjectCatalog() {}

    public static TypeDef get(String key) {
        return key != null ? TYPES.get(key) : null;
    }

    public static boolean isSpawner(String key) {
        return key != null && key.startsWith("spawner_");
    }

    /** Множитель store.tileSizeWidth для целевого размера объекта на экране — см. TILE_FIT/PORTAL. */
    public static float targetSizeTileMult(String key) {
        return "portal".equals(key) ? PORTAL_SIZE_MULT : TILE_FIT_SIZE_MULT;
    }

    /** Faction, которую спавнит объект этого типа (только для spawner_*, иначе null). */
    public static NpcCatalog.Faction spawnerFaction(String key) {
        if ("spawner_enemy".equals(key))   return NpcCatalog.Faction.ENEMY;
        if ("spawner_ally".equals(key))    return NpcCatalog.Faction.ALLY;
        if ("spawner_monster".equals(key)) return NpcCatalog.Faction.MONSTER;
        return null;
    }
}
