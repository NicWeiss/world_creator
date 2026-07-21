package com.nicweiss.editor.utils;

import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.simulation.Drop;

/**
 * Единая точка расчёта освещения предметов на земле (Drop) и NPC/объектов (Creation) — раньше
 * эта логика была продублирована 1-в-1 в обоих классах, что плохо (правки/баги приходилось чинить
 * дважды). Тайлы карты считают освещение отдельно (см. MapObject.calcLitColor — там есть DDA-раскаст
 * тени от препятствий; здесь — упрощённая версия без него, приемлемая для мелких объектов на земле).
 */
public class Lighting {
    public static Store store;

    private Lighting() {}

    /**
     * Позиция источника света для якоря тайла (1-based mapCellX/Y, cellIndex-конвенция) — формат,
     * совместимый с Light.addColoredPoint/store.lightPoints[i][1]/[2]. ВАЖНО: это НЕ рендер-позиция
     * сущности — Light.addColoredPoint кладёт точки в системе координат, смещённой на один тайл по
     * X относительно обычного рендер-якоря (см. Store.TILE_X_ANCHOR_EXTRA_OFFSET) — квирк только
     * для сравнения расстояний со store.lightPoints, к позиции спрайта на экране не относится.
     * Пишет результат в out (переиспользуемый буфер — без аллокаций на кадр) и возвращает его же.
     */
    public static float[] tileLightAnchor(int mapCellX, int mapCellY, float[] out) {
        float[] iso = Transform.cartesianToIsometric(
            (mapCellX - store.TILE_INDEX_BASE - store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth,
            (mapCellY - store.TILE_INDEX_BASE - store.TILE_Y_ANCHOR_EXTRA_OFFSET) * store.tileSizeHeight);
        out[0] = iso[0] + store.tileSizeWidth;
        out[1] = iso[1] + store.tileSizeHeight;
        return out;
    }

    /**
     * То же самое, что tileLightAnchor, но для ЛЮБОЙ НЕПРЕРЫВНОЙ мировой позиции (worldX/worldY —
     * не привязана к дискретной ячейке, в отличие от mapCellX/Y у Drop/Creation) — источник света,
     * который должен сравниваться со всеми остальными источниками в ТОЙ ЖЕ системе координат — те же
     * смещения (TILE_INDEX_BASE, TILE_X/Y_ANCHOR_EXTRA_OFFSET, см. Store), применённые напрямую к
     * непрерывной позиции (без округления до ячейки), чтобы свет двигался плавно вместе с
     * источником, а не "прыгал" по тайлам. Используется для игрока с факелом (см. playerLightAnchor)
     * И для летящих/движущихся эффектов умений (снаряды/наземный огонь — см. SkillEffectRenderer).
     */
    public static float[] worldLightAnchor(float worldX, float worldY, float[] out) {
        float cartX = worldX - (store.TILE_INDEX_BASE + store.TILE_X_ANCHOR_EXTRA_OFFSET) * store.tileSizeWidth;
        float cartY = worldY - (store.TILE_INDEX_BASE + store.TILE_Y_ANCHOR_EXTRA_OFFSET) * store.tileSizeHeight;
        float[] iso = Transform.cartesianToIsometric(cartX, cartY);
        out[0] = iso[0] + store.tileSizeWidth;
        out[1] = iso[1] + store.tileSizeHeight;
        return out;
    }

    private static float[] playerLightAnchor(float[] out) {
        return worldLightAnchor(store.player.worldX, store.player.worldY, out);
    }

    // Кэш позиции игрока-как-источника-света — calcLitColor вызывается на КАЖДЫЙ тайл КАЖДЫЙ кадр
    // (MapObject.calcLitColor), пересчитывать iso-трансформацию так часто расточительно. Инвалидация —
    // по факту реального изменения worldX/Y (игрок стоит на месте бóльшую часть времени).
    private static float cachedPlayerWorldX = Float.NaN, cachedPlayerWorldY = Float.NaN;
    private static final float[] cachedPlayerLightAnchor = new float[2];

    /** Кэшированная (см. выше) позиция игрока в формате tileLightAnchor — вызывать только когда store.player != null и инициализирован. */
    public static float[] ensurePlayerLightAnchor() {
        float wx = store.player.worldX, wy = store.player.worldY;
        if (wx != cachedPlayerWorldX || wy != cachedPlayerWorldY) {
            playerLightAnchor(cachedPlayerLightAnchor);
            cachedPlayerWorldX = wx;
            cachedPlayerWorldY = wy;
        }
        return cachedPlayerLightAnchor;
    }

    // ── Препятствия (деревья, камни — objectHeight) ────────────────────────────
    // Правила освещения едины для редактора и симуляции (по требованию пользователя): статичные
    // источники (store.lightPoints, MapObject.calcLight) уже раскастывают тень через objectHeight —
    // движущиеся источники симуляции (игрок с факелом, факел на земле) должны блокироваться так же.

    private static boolean inMapBounds(int ai, int aj) {
        return ai >= 0 && aj >= 0 && ai < store.mapHeight && aj < store.mapWidth;
    }

    /**
     * Индекс тайла (store.objectedMap) по непрерывной мировой позиции — та же компенсация
     * (TILE_INDEX_BASE, TILE_X/Y_ANCHOR_EXTRA_OFFSET), что и в Player.isCollidingAt, при переводе
     * мировых пиксельных координат в индекс массива карты.
     */
    public static int[] worldToArrayIndex(float worldX, float worldY, int[] out) {
        out[0] = (int) Math.floor(worldX / store.tileSizeWidth)  - store.TILE_INDEX_BASE - store.TILE_X_ANCHOR_EXTRA_OFFSET;
        out[1] = (int) Math.floor(worldY / store.tileSizeHeight) - store.TILE_INDEX_BASE - store.TILE_Y_ANCHOR_EXTRA_OFFSET;
        return out;
    }

    /**
     * Проверка прямой видимости между двумя тайлами карты (по индексам objectedMap) — блокируют
     * только объекты ВЫШЕ высоты источника (дерево/камень/стена); сами клетки источника и цели в
     * проверку не входят (факел не блокирует сам себя и не блокируется тайлом, на котором лежит).
     * Упрощённая версия DDA-раскаста из MapObject.calcLight (там — усреднение по трём линиям и
     * плавное затухание у края тени, приемлемо для статичных редко пересчитываемых источников) —
     * тут источник двигается каждый кадр (игрок/факел на земле), поэтому взято самое дешёвое: один
     * проход по прямой, бинарная блокировка (видно/не видно), без полутонов у границы тени.
     */
    public static boolean isLineOfSightBlocked(int fromAi, int fromAj, int toAi, int toAj) {
        int fromHeight = inMapBounds(fromAi, fromAj) ? store.objectedMap[fromAi][fromAj].objectHeight : 0;
        return isLineOfSightBlocked(fromHeight, fromAi, fromAj, toAi, toAj);
    }

    /**
     * То же самое, но высота источника задаётся явно, а не берётся с тайла под источником — нужно
     * игроку/факелу в руках (см. Player.LIGHT_SOURCE_HEIGHT): игрок стоит НА тайле (трава, высота
     * почти всегда ниже самого игрока), а не является этим тайлом — трава не должна перекрывать
     * его свет, а дерево/камень выше игрока — должны.
     */
    public static boolean isLineOfSightBlocked(int fromHeight, int fromAi, int fromAj, int toAi, int toAj) {
        if (store.objectedMap == null) return false;
        int steps = Math.max(Math.abs(toAi - fromAi), Math.abs(toAj - fromAj));
        if (steps <= 1) return false;
        for (int s = 1; s < steps; s++) {
            int ix = fromAi + Math.round((toAi - fromAi) * (float) s / steps);
            int iy = fromAj + Math.round((toAj - fromAj) * (float) s / steps);
            if (!inMapBounds(ix, iy)) continue;
            if (store.objectedMap[ix][iy].objectHeight > fromHeight) return true;
        }
        return false;
    }

    // Переиспользуемые буферы индексов — без аллокаций на кадр (см. computeLitColor ниже).
    private static final int[] srcIdxBuf = new int[2];
    private static final float[] skillLightAnchorBuf = new float[2];
    private static final int[] skillLightSrcBuf = new int[2];

    /**
     * Освещённость точки (selfIsoX/Y — в формате tileLightAnchor; selfMapCellX/Y — её же индекс
     * ячейки, для проверки препятствий, см. isLineOfSightBlocked) — дневная температура цвета,
     * затемнение дождём, статичные источники света (store.lightPoints) и сферы опыта (свои
     * источники, см. MapObject.EXP_ORB_LIGHT_*). excludeDrop — сфера опыта, которую не нужно
     * подсвечивать самой собой (передать null, если вызывающий — не Drop). Пишет в out (буфер
     * вызывающего, без аллокаций) и возвращает его же.
     */
    public static float[] computeLitColor(float selfIsoX, float selfIsoY, int selfMapCellX, int selfMapCellY,
                                           Drop excludeDrop, float[] out) {
        int toAi = selfMapCellX - store.TILE_INDEX_BASE;
        int toAj = selfMapCellY - store.TILE_INDEX_BASE;
        double angle  = store.dayPhase * 2.0 * Math.PI;
        float  cosA   = (float) Math.cos(angle);
        float  warmth = cosA * cosA;
        float  cool   = Math.max(0f, (float) Math.sin(angle));
        float  dayBright = Math.max(0f, store.dayCoefficient);
        float tR = 1f + (warmth * 0.22f - cool * 0.06f) * dayBright;
        float tG = 1f + (-warmth * 0.06f + cool * 0.03f) * dayBright;
        float tB = 1f + (-warmth * 0.28f + cool * 0.22f) * dayBright;

        float raw = Math.max(0.05f, (0.2f + store.dayCoefficient) * (1f - store.rainIntensity * 0.45f));
        float dayR = raw * tR, dayG = raw * tG, dayB = raw * tB;

        float lr = 0f, lg = 0f, lb = 0f;

        if (store.lightPoints != null) {
            int countTo = Math.min(store.lightPointsHighWaterMark + 1, store.lightPoints.length);
            for (int i = 1; i < countTo; i++) {
                float[] lp = store.lightPoints[i];
                if (lp[0] == 0) continue;
                float ddx = selfIsoX - lp[1];
                float ddy = (selfIsoY - lp[2]) * 1.45f;
                if (Math.abs(ddx) > 130f || Math.abs(ddy) > 130f) continue;
                float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                if (dist >= 120f) continue;
                float lpv  = dist / 120f * 100f;
                float dark = Math.max(0.2f, 1.6f - lpv / 100f * 0.8f);
                lr = Math.max(lr, Math.max(0f, 1f - (lpv / (dark * 100f + 35f) * 50f) / 500f) * lp[5]);
                lg = Math.max(lg, Math.max(0f, 1f - (lpv / (dark * 100f + 15f) * 50f) / 500f) * lp[6]);
                lb = Math.max(lb, Math.max(0f, 1f - (lpv / (dark * 100f + 5f)  * 50f) / 500f) * lp[7]);
            }
        }

        if (store.drops != null) {
            for (Drop d : store.drops) {
                if (d == null || d == excludeDrop || d.expAmount <= 0) continue;
                float odx = selfIsoX - d.getLightSourceIsoX();
                float ody = (selfIsoY - d.getLightSourceIsoY()) * 1.45f;
                if (Math.abs(odx) > MapObject.EXP_ORB_LIGHT_RADIUS || Math.abs(ody) > MapObject.EXP_ORB_LIGHT_RADIUS) continue;
                float odist = (float) Math.sqrt(odx * odx + ody * ody);
                if (odist >= MapObject.EXP_ORB_LIGHT_RADIUS) continue;
                float t = 1f - odist / MapObject.EXP_ORB_LIGHT_RADIUS;
                lr = Math.max(lr, MapObject.EXP_ORB_LIGHT_R * t);
                lg = Math.max(lg, MapObject.EXP_ORB_LIGHT_G * t);
                lb = Math.max(lb, MapObject.EXP_ORB_LIGHT_B * t);
            }
        }

        // Факел на земле — та же схема, что и у сфер опыта выше, но цвет свой (скрытый стат
        // предмета, см. ItemGenerator.applyTorchRarityStats), не фиксированный неоново-синий.
        if (store.drops != null) {
            for (Drop d : store.drops) {
                if (d == null || d == excludeDrop || !d.isLanded || d.itemData == null) continue;
                if (!"torch".equals(d.itemData.get("__type__"))) continue;
                int lightPower = (int) MapObject.itemFloat(d.itemData, "__mainStat__", 2f);
                float radius = MapObject.torchGroundRadius();
                float odx = selfIsoX - d.getLightSourceIsoX();
                float ody = (selfIsoY - d.getLightSourceIsoY()) * 1.45f;
                if (Math.abs(odx) > radius || Math.abs(ody) > radius) continue;
                float odist = (float) Math.sqrt(odx * odx + ody * ody);
                if (odist >= radius) continue;
                int srcAi = d.mapCellX - store.TILE_INDEX_BASE, srcAj = d.mapCellY - store.TILE_INDEX_BASE;
                if (isLineOfSightBlocked(srcAi, srcAj, toAi, toAj)) continue;
                float t = (1f - odist / radius) * MapObject.torchIntensity(lightPower);
                lr = Math.max(lr, MapObject.itemFloat(d.itemData, "__glowColorR__", 1f) * t);
                lg = Math.max(lg, MapObject.itemFloat(d.itemData, "__glowColorG__", 1f) * t);
                lb = Math.max(lb, MapObject.itemFloat(d.itemData, "__glowColorB__", 1f) * t);
            }
        }

        // Игрок как источник света (экипирован факел, см. Player.lightPower/torchGlow*) — освещает
        // и предметы/золото на земле рядом с собой, не только тайлы (см. MapObject.calcLitColor).
        if (store.player != null && store.player.isInitialized() && store.player.lightPower > 0) {
            float radius = MapObject.torchRadius(store.player.lightPower);
            float[] pp = ensurePlayerLightAnchor();
            float odx = selfIsoX - pp[0];
            float ody = (selfIsoY - pp[1]) * 1.45f;
            if (Math.abs(odx) <= radius && Math.abs(ody) <= radius) {
                float odist = (float) Math.sqrt(odx * odx + ody * ody);
                worldToArrayIndex(store.player.worldX, store.player.worldY, srcIdxBuf);
                if (odist < radius && !isLineOfSightBlocked(
                        com.nicweiss.editor.simulation.Player.LIGHT_SOURCE_HEIGHT, srcIdxBuf[0], srcIdxBuf[1], toAi, toAj)) {
                    float t = (1f - odist / radius) * MapObject.torchIntensity(store.player.lightPower);
                    lr = Math.max(lr, store.player.torchGlowR * t);
                    lg = Math.max(lg, store.player.torchGlowG * t);
                    lb = Math.max(lb, store.player.torchGlowB * t);
                }
            }
        }

        // Динамический свет эффектов умений (снаряды/наземный огонь, см. SkillEffectRenderer) —
        // та же схема, что у факела на земле выше, но радиус/яркость приходят готовыми из самого
        // источника (store.skillLightPoints), а не выводятся из "силы света" предмета. Высота
        // источника — как у костра (см. Player.LIGHT_SOURCE_HEIGHT, требование пользователя).
        if (store.skillLightPoints != null) {
            for (float[] sp : store.skillLightPoints) {
                if (sp[0] == 0f) continue;
                float radius = sp[3];
                worldLightAnchor(sp[1], sp[2], skillLightAnchorBuf);
                float odx = selfIsoX - skillLightAnchorBuf[0];
                float ody = (selfIsoY - skillLightAnchorBuf[1]) * 1.45f;
                if (Math.abs(odx) > radius || Math.abs(ody) > radius) continue;
                float odist = (float) Math.sqrt(odx * odx + ody * ody);
                if (odist >= radius) continue;
                worldToArrayIndex(sp[1], sp[2], skillLightSrcBuf);
                if (isLineOfSightBlocked(com.nicweiss.editor.simulation.Player.LIGHT_SOURCE_HEIGHT,
                        skillLightSrcBuf[0], skillLightSrcBuf[1], toAi, toAj)) continue;
                float t = (1f - odist / radius) * sp[4];
                lr = Math.max(lr, sp[5] * t);
                lg = Math.max(lg, sp[6] * t);
                lb = Math.max(lb, sp[7] * t);
            }
        }

        out[0] = Math.min(1f, Math.max(dayR, lr));
        out[1] = Math.min(1f, Math.max(dayG, lg));
        out[2] = Math.min(1f, Math.max(dayB, lb));
        return out;
    }
}
