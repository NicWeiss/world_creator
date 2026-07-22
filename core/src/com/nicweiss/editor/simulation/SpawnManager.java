package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.utils.NpcCatalog;
import com.nicweiss.editor.utils.NpcGenerator;
import com.nicweiss.editor.utils.ObjectCatalog;
import com.nicweiss.editor.utils.Transform;
import com.nicweiss.editor.utils.Uuid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Тикает спавнеры раз в секунду в симуляции (вызывается из {@link CreationThread}, как
 * {@link DropManager#update}). Раз в тик: по каждому зданию-спавнеру (см. ObjectCatalog.isSpawner)
 * считает живых подопечных (Store.simCreatures со своим spawnerUuid, health&gt;0) и доспавнивает
 * недостающих до __maxCount__. Уровень каждого — __level__ спавнера ±2 (см. NpcGenerator.rollLevel),
 * тир/резисты — тоже NpcGenerator. Роль (воин/маг) для enemy/ally спавнеров — 50/50
 * (см. NpcCatalog.randomCombatType), монстр-спавнер всегда даёт monster_beast.
 *
 * Тем же тиком поддерживается стартовый отряд врагов (см. spawnStartingSquad/tickStartingSquad) —
 * своя "виртуальная" группа без здания-спавнера, привязанная к STARTING_SQUAD_SPAWNER_UUID.
 */
public class SpawnManager {
    public static Store store;

    private static final Random RANDOM = new Random();
    private static final float TICK_SECONDS = 1f;
    private static final int SPAWN_RADIUS_TILES = 5; // было 2 — по просьбе пользователя разброс шире
    private static final int MAX_SURFACE_HEIGHT = 3; // тот же порог, что DropManager.MAX_SURFACE_HEIGHT
    // Вода/мостик — та же логика проходимости, что у игрока (см. Player.isCollidingAt) и лута
    // (см. DropManager.isLandable): существа не должны спавниться в воде, куда не может дойти игрок.
    private static final int WATER_TEXTURE_ID  = 10;
    private static final int BRIDGE_TEXTURE_ID = 12;

    private static float tickTimer = 0f;

    private SpawnManager() {}

    /** Вызывать при входе в симуляцию — сбрасывает список заспавненных существ. */
    public static void init() {
        clear();
        tickTimer = 0f;
    }

    /** Вызывать при выходе из симуляции. */
    public static void clear() {
        if (store.simCreatures != null) {
            for (int i = 0; i < store.simCreatures.length; i++) store.simCreatures[i] = null;
        }
        store.simCreatureCount = -1;
    }

    public static void update(float dt) {
        if (store.objectedMap == null) return;
        tickTimer += dt;
        if (tickTimer < TICK_SECONDS) return;
        tickTimer -= TICK_SECONDS;

        if (store.buildingSettings != null) {
            for (int i = 0; i <= store.buildingCount; i++) {
                Creation b = store.buildings[i];
                if (b == null) continue;
                String uuid = b.getUUID();
                LinkedHashMap settings = store.buildingSettings.get(uuid);
                if (settings == null) continue;

                String objType = (String) settings.get("__objectType__");
                if (!ObjectCatalog.isSpawner(objType)) continue;

                tickSpawner(uuid, b, objType, settings);
            }
        }

        tickStartingSquad();
    }

    private static void tickSpawner(String spawnerUuid, Creation spawner, String objType, LinkedHashMap settings) {
        NpcCatalog.Faction faction = ObjectCatalog.spawnerFaction(objType);
        if (faction == null) return;

        int level    = settings.containsKey("__level__")    ? toInt(settings.get("__level__"))    : 1;
        int maxCount = settings.containsKey("__maxCount__") ? toInt(settings.get("__maxCount__")) : 1;
        level    = Math.max(1, level);
        maxCount = Math.max(0, maxCount);

        int alive = countAlive(spawnerUuid, null);
        int deficit = maxCount - alive;
        for (int n = 0; n < deficit; n++) {
            spawnOne(spawnerUuid, spawner, faction, level);
        }
    }

    private static int countAlive(String spawnerUuid, NpcCatalog.Faction faction) {
        int alive = 0;
        for (int i = 0; i <= store.simCreatureCount; i++) {
            SimCreature c = store.simCreatures[i];
            if (c == null || !c.isAlive()) continue;
            if (spawnerUuid != null && !spawnerUuid.equals(c.spawnerUuid)) continue;
            if (faction != null && c.faction != faction) continue;
            alive++;
        }
        return alive;
    }

    private static void spawnOne(String spawnerUuid, Creation spawner, NpcCatalog.Faction faction, int spawnerLevel) {
        String typeKey = NpcCatalog.randomCombatType(faction, RANDOM);
        NpcCatalog.TypeDef type = NpcCatalog.get(typeKey);
        if (type == null) return;

        int level = NpcGenerator.rollLevel(spawnerLevel);
        NpcCatalog.Tier tier = NpcGenerator.rollTier();
        int health = NpcGenerator.rollHealth(type, level, tier);
        int damage = NpcGenerator.rollDamage(type, level, tier);
        LinkedHashMap<String, Integer> resistances = NpcGenerator.rollResistances(tier);

        int originX = spawner.mapCellX - store.TILE_INDEX_BASE;
        int originY = spawner.mapCellY - store.TILE_INDEX_BASE;
        spawnCreatureNear(originX, originY, typeKey, faction, level, tier, health, damage, resistances, spawnerUuid);
    }

    // ── Стартовый отряд (см. UserInterface.startSimulation) ─────────────────────────────────────

    private static final String STARTING_SQUAD_SPAWNER_UUID = "__starting_squad_enemies__";
    private static final int STARTING_SQUAD_ENEMY_COUNT = 10; // "сделай 10 врагов"
    private static final int STARTING_SQUAD_HEALTH = 100000;  // по требованию пользователя

    /** Спавнит стартовый отряд (10 врагов, 1 союзник, 1 зверь) рядом с игроком на проходимой
     *  поверхности при старте симуляции — см. UserInterface.startSimulation(). Враги привязаны к
     *  STARTING_SQUAD_SPAWNER_UUID — тем же тиком, что и обычные спавнеры зданий, их число
     *  поддерживается на STARTING_SQUAD_ENEMY_COUNT (см. tickStartingSquad — "пусть после входа
     *  они обновляются"); союзник/зверь — разовые, не привязаны ни к какому спавнеру. */
    public static void spawnStartingSquad(Store store) {
        if (store.player == null) return;
        int originX = (int) (store.player.worldX / store.tileSizeWidth) - 1;
        int originY = (int) (store.player.worldY / store.tileSizeHeight) - 1;

        for (int i = 0; i < STARTING_SQUAD_ENEMY_COUNT; i++) {
            String typeKey = i % 2 == 0 ? "enemy_warrior" : "enemy_mage";
            spawnCreatureNear(originX, originY, typeKey, NpcCatalog.Faction.ENEMY, 1,
                NpcCatalog.Tier.NORMAL, STARTING_SQUAD_HEALTH, -1, null, STARTING_SQUAD_SPAWNER_UUID);
        }
        spawnCreatureNear(originX, originY, "ally_warrior", NpcCatalog.Faction.ALLY, 1,
            NpcCatalog.Tier.NORMAL, STARTING_SQUAD_HEALTH, -1, null, null);
        spawnCreatureNear(originX, originY, "monster_beast", NpcCatalog.Faction.MONSTER, 1,
            NpcCatalog.Tier.NORMAL, STARTING_SQUAD_HEALTH, -1, null, null);
    }

    /** "Обновление" стартового отряда — та же логика, что у обычных зданий-спавнеров (см.
     *  tickSpawner), но origin — ТЕКУЩАЯ позиция игрока (не точка первого спавна), и без здания. */
    private static void tickStartingSquad() {
        if (store.player == null) return;
        int alive = countAlive(STARTING_SQUAD_SPAWNER_UUID, NpcCatalog.Faction.ENEMY);
        int deficit = STARTING_SQUAD_ENEMY_COUNT - alive;
        if (deficit <= 0) return;

        int originX = (int) (store.player.worldX / store.tileSizeWidth) - 1;
        int originY = (int) (store.player.worldY / store.tileSizeHeight) - 1;
        for (int n = 0; n < deficit; n++) {
            String typeKey = RANDOM.nextBoolean() ? "enemy_warrior" : "enemy_mage";
            spawnCreatureNear(originX, originY, typeKey, NpcCatalog.Faction.ENEMY, 1,
                NpcCatalog.Tier.NORMAL, STARTING_SQUAD_HEALTH, -1, null, STARTING_SQUAD_SPAWNER_UUID);
        }
    }

    // ── Общий спавн одного существа (GL-поток — нужна Texture) ──────────────────────────────────

    /** damage=-1 → берётся из NpcCatalog.TypeDef.damageAtLevel(level) (обычный спавн); иначе —
     *  переопределяется явно (см. spawnStartingSquad, где damage/health фиксированы). */
    private static void spawnCreatureNear(int originX, int originY, String typeKey, NpcCatalog.Faction faction,
                                           int level, NpcCatalog.Tier tier, int health, int damage,
                                           LinkedHashMap<String, Integer> resistances, String spawnerUuid) {
        if (store.simCreatureCount + 1 >= store.simCreatures.length) return; // массив полон
        NpcCatalog.TypeDef type = NpcCatalog.get(typeKey);
        if (type == null) return;
        int[] tile = pickNearbyTile(originX, originY);

        Gdx.app.postRunnable(() -> {
            int slot = nextFreeSlot();
            if (slot < 0) return;

            SimCreature c = new SimCreature();
            c.setUUID(Uuid.generate());
            c.typeKey = typeKey;
            c.faction = faction;
            c.tier = tier;
            c.level = level;
            c.maxHealth = health;
            c.health = health;
            c.damage = damage >= 0 ? damage : type.damageAtLevel(level);
            c.speedMultiplier = type.speedMultiplier;
            if (resistances != null) c.resistances = resistances;
            c.spawnerUuid = spawnerUuid;
            c.setTexture(loadTypeTexture(type));
            // NPC не больше деревьев (лучше — меньше), см. Creation.targetMaxScreenSize.
            c.targetMaxScreenSize = store.tileSizeWidth * NpcCatalog.NPC_SIZE_TILE_MULT;

            int cellX = tile[0] + store.TILE_INDEX_BASE;
            int cellY = tile[1] + store.TILE_INDEX_BASE;
            c.setCell(cellX, cellY);
            // Рендер-позиция — +TILE_X_ANCHOR_EXTRA_OFFSET на X (подтверждено эмпирически, см.
            // NpcEditorWindow.createNpcAt) — та же конвенция mapCellX/Y, что у остальных сущностей.
            float[] iso = Transform.cartesianToIsometric(
                (int) ((cellX + store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth),
                (int) (cellY * store.tileSizeHeight)
            );
            c.setPosition(iso[0], iso[1]);
            // Декартовы мировые координаты для CombatSystem — ДОЛЖНЫ быть тем же самым
            // картезианским входом, что подаётся в cartesianToIsometric выше (см. iso), а НЕ
            // "сырым" tile*tileSize — иначе FxContext.worldToScreen(worldX,worldY) не совпадёт с
            // реальной экранной позицией спрайта (см. Creation.draw), и вся боевая логика
            // (таргетинг/коллизия рывка) целилась бы не туда, где враг фактически нарисован.
            c.worldX = (cellX + store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth;
            c.worldY = cellY * store.tileSizeHeight;

            c.slotIndex = slot;
            store.simCreatures[slot] = c;
            if (slot > store.simCreatureCount) store.simCreatureCount = slot;
        });
    }

    private static int nextFreeSlot() {
        for (int i = 0; i < store.simCreatures.length; i++) {
            if (store.simCreatures[i] == null) return i;
        }
        return -1;
    }

    /**
     * Ищет проходимый тайл рядом с точкой — "недалеко от спавнера/игрока" из ТЗ. Сначала несколько
     * случайных проб в полном радиусе (для разнообразия позиций), затем — если не повезло —
     * расширяющееся кольцевое сканирование (как DropManager.findLandingTile), чтобы НЕ скатываться
     * в одну и ту же точку при плотной застройке/препятствиях вокруг.
     */
    private static int[] pickNearbyTile(int originX, int originY) {
        for (int attempt = 0; attempt < 15; attempt++) {
            int dx = RANDOM.nextInt(SPAWN_RADIUS_TILES * 2 + 1) - SPAWN_RADIUS_TILES;
            int dy = RANDOM.nextInt(SPAWN_RADIUS_TILES * 2 + 1) - SPAWN_RADIUS_TILES;
            int tx = originX + dx, ty = originY + dy;
            if (isLandable(tx, ty)) return new int[]{tx, ty};
        }

        // Резервный план — кольцами наружу от 1 до SPAWN_RADIUS_TILES, собираем ВСЕ подходящие
        // тайлы кольца и берём случайный (не первый попавшийся) — иначе спавны опять слипаются.
        List<int[]> candidates = new ArrayList<>();
        for (int radius = 1; radius <= SPAWN_RADIUS_TILES; radius++) {
            candidates.clear();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue; // только кромка кольца
                    int tx = originX + dx, ty = originY + dy;
                    if (isLandable(tx, ty)) candidates.add(new int[]{tx, ty});
                }
            }
            if (!candidates.isEmpty()) return candidates.get(RANDOM.nextInt(candidates.size()));
        }
        return new int[]{originX, originY};
    }

    private static boolean isLandable(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= store.mapHeight || ty >= store.mapWidth) return false;
        // objectHeight — игровая "высота препятствия" тайла (0-50, см. Editor.textures). getHeight()
        // (унаследован от BaseObject) — это ПИКСЕЛЬНЫЙ размер спрайта тайла (~50-120px), а не она;
        // сравнение с ним всегда false для уже отрисованных тайлов — из-за этого спавн валился
        // в резервный фолбэк (точка спавнера) практически каждый раз, отсюда "все в одной точке".
        com.nicweiss.editor.objects.MapObject tile = store.objectedMap[tx][ty];
        if (tile.objectHeight >= MAX_SURFACE_HEIGHT) return false;
        if (tile.getSurfaceId() == WATER_TEXTURE_ID && tile.getTextureId() != BRIDGE_TEXTURE_ID) return false;
        return true;
    }

    private static final Map<String, Texture> TYPE_TEXTURE_CACHE = new HashMap<>();
    private static Texture fallback;

    private static Texture loadTypeTexture(NpcCatalog.TypeDef type) {
        String folder = type.imageFolder;
        if (folder == null) return fallbackTexture();

        Texture cached = TYPE_TEXTURE_CACHE.get(folder);
        if (cached != null) return cached;

        Texture t;
        try {
            t = new Texture("creations/" + folder + "/default.png");
        } catch (Exception e) {
            t = fallbackTexture();
        }
        TYPE_TEXTURE_CACHE.put(folder, t);
        return t;
    }

    private static Texture fallbackTexture() {
        if (fallback == null) fallback = new Texture("creations/creation.png");
        return fallback;
    }

    private static int toInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }
}
