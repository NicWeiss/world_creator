package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;

import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;
import com.nicweiss.editor.components.UserInterface;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;
import com.nicweiss.editor.objects.TextureObject;
import com.nicweiss.editor.utils.ArrayUtils;
import com.nicweiss.editor.utils.CameraSettings;
import com.nicweiss.editor.utils.Transform;
import com.nicweiss.editor.utils.Light;
import com.nicweiss.editor.utils.Perlin;

import java.util.Random;


public class Editor extends View{
    Texture hintUp, hintDown;
    // static — что бы recalcPathTile/recalcAllPaths (см. ниже) можно было вызывать статически из
    // UserInterface после загрузки карты, как и markShores(); Editor — синглтон, textures
    // выставляются один раз в конструкторе, безопасно.
    static TextureObject[] textures;

    Light light;
    UserInterface userInterface;
    Transform transform;

    int tileSizeX, tileSizeY;
    int selectedTileX, selectedTileY;
    int mouseX, mouseY;
    float cm = 0.01f;
    int[] lightObjectIds;
    boolean isImmediatelyReleaseKey = false;
    boolean isUiTouched = false;

    public Editor(){
        lightObjectIds = new int[] {11};

        hintUp = new Texture("tile_hint_up.png");
        hintDown = new Texture("tile_hint_down.png");

        textures = new TextureObject[] {
            new TextureObject("gp_0.png",  0),
            new TextureObject("gp_1.png",  0),
            new TextureObject("gp_2.png",  50, true),   // дуб
            new TextureObject("gp_3.png",  50, true),   // ель
            new TextureObject("gp_4.png",  20, true),   // осеннее дерево
            new TextureObject("gp_5.png",  2),
            new TextureObject("gp_6.png",  4),
            new TextureObject("gp_7.png",  10),
            new TextureObject("gp_8.png",  1),
            new TextureObject("gp_9.png",  50),
            new TextureObject("gp_10.png", 0),
            new TextureObject("gp_11.png", 5),
            new TextureObject("gp_12.png", 0),
            new TextureObject("gp_13.png", 0),
            new TextureObject("gp_14.png", 0),
            new TextureObject("gp_15.png", 0),
            new TextureObject("gp_16.png", 0),
            new TextureObject("gp_17.png", 0),
            new TextureObject("gp_18.png", 0),
            new TextureObject("gp_19.png", 0),
            new TextureObject("gp_20.png", 0),
            new TextureObject("gp_21.png", 0),
            new TextureObject("gp_22.png", 0),
            new TextureObject("gp_23.png", 0)
        };

        store.tileSizeWidth = tileSizeX = 158 / store.tileDownScale;
        store.tileSizeHeight = tileSizeY = 158 / store.tileDownScale;
        store.shiftY = 0;
        store.shiftX = 12 * tileSizeX;

        light = new Light();
        userInterface = new UserInterface(textures, light, lightObjectIds);
    }

    void defineUI() throws Exception {
        userInterface.build();
    }

    // Индекс текстуры воды в textures[] (gp_10.png) — раньше был заведён, но ничем не заполнялся
    // генератором (просто лежал неиспользуемым). Теперь — единственный тайл для рек, см. generateRivers.
    private static final int WATER_TEXTURE_ID = 10;

    // Земля/трава (1) и вода (WATER_TEXTURE_ID) — единственные типы, допустимые НА УРОВНЕ
    // ПОВЕРХНОСТИ (см. touchDown): они не могут существовать в объектном слое и наоборот. Всё
    // остальное (деревья, камни, мостики, здания...) — объекты, независимые от поверхности под ними.
    private static final int[] GROUND_TILE_IDS = { 1, WATER_TEXTURE_ID };

    // Объекты с высотой ниже этого порога (мостик, дорожка, мелкие камешки) считаются "плоскими" —
    // рисуются вместе с поверхностями (см. render()), а НЕ в проходе "Объекты", что бы порядок
    // сканирования тайлов не мог случайно нарисовать их поверх игрока/существ — у плоского объекта
    // физически нет высоты, что бы кого-то реально загораживать.
    private static final int FLAT_OBJECT_HEIGHT = 10;

    // ── Автотайлинг дорожек ─────────────────────────────────────────────────────────────────
    //
    // В пикере — ОДИН тайл-инструмент "дорожка" (PATH_PICKER_ID), а не 11 отдельных вариантов
    // поворотов/пересечений по отдельности (см. TileSelectorWindow). При установке (и при
    // затирании соседней дорожки другим объектом) клетка и её 4 соседа-дорожки пересчитываются:
    // по битовой маске "какие из 4 соседей тоже дорожка" выбирается нужный спрайт.
    //
    // Полный набор готовых тайлов (13..23), КАЖДЫЙ используется КАК ЕСТЬ — БЕЗ поворота и БЕЗ
    // отражения (art-пак уже содержит все нужные ориентации отдельными спрайтами):
    //   13           — перекрёсток 4 дорог (все 4 соседа)
    //   14, 15       — прямая дорога (2 спрайта — по одному на каждую из 2 осей)
    //   16, 17, 18, 19 — ПОВОРОТЫ на 90° (все 4 ориентации угла — 2 соседа под прямым углом)
    //   20, 21, 22, 23 — T-перекрёсток (3 соседа, один спрайт на каждую "недостающую" сторону)
    // Отдельного тайла-тупика (1 сосед) в наборе НЕТ — используется прямая (14/15) подходящей оси.
    //
    // Если поверхность под дорожкой — вода, ставится МОСТИК (gp_12) вместо дорожки: у мостика пока
    // нет вариаций художественно, поэтому автотайлинг на него не распространяется — фиксированный
    // спрайт независимо от соседей.

    private static final int PATH_PICKER_ID = 14; // видимый в пикере тайл — он же "первая дорожка" (изолированная, без соседей)
    private static final int PATH_CROSS       = 13;
    private static final int PATH_STRAIGHT_NS = 14;
    private static final int PATH_STRAIGHT_EW = 15;
    // Ориентации проверены аналитически: 4 контрольные точки — середины рёбер ромба, выведенные
    // из формулы Transform.cartesianToIsometric (S=i+1 → верх-право экрана, N=i-1 → низ-лево,
    // E=j+1 → верх-лево, W=j-1 → низ-право; проверено сверкой с фактическим наклоном спрайтов
    // gp_14/gp_15), и подтверждены проверкой прозрачности каждого спрайта в этих точках.
    private static final int PATH_CORNER_NE = 18;
    private static final int PATH_CORNER_NW = 16;
    private static final int PATH_CORNER_SE = 17;
    private static final int PATH_CORNER_SW = 19;
    private static final int PATH_T_MISSING_N = 20;
    private static final int PATH_T_MISSING_E = 21;
    private static final int PATH_T_MISSING_S = 22;
    private static final int PATH_T_MISSING_W = 23;
    private static final int[] PATH_TILE_IDS = {
        PATH_CROSS, PATH_STRAIGHT_NS, PATH_STRAIGHT_EW,
        PATH_CORNER_NE, PATH_CORNER_NW, PATH_CORNER_SE, PATH_CORNER_SW,
        PATH_T_MISSING_N, PATH_T_MISSING_S, PATH_T_MISSING_E, PATH_T_MISSING_W
    };
    private static final int BRIDGE_TEXTURE_ID = 12;

    private static boolean isPathTile(int textureId) {
        return ArrayUtils.checkIntInArray(textureId, PATH_TILE_IDS);
    }

    /**
     * Мостик (без вариаций — фиксированный спрайт) тем не менее должен СЧИТАТЬСЯ соседом для
     * автотайлинга дорожки: дорожка, упирающаяся в мостик у воды, должна плавно "втекать" в него
     * (прямая/угол), а не обрываться тупиком, будто мостика тут нет. Сам мостик при этом никогда
     * не пересчитывается (recalcPathTile ниже использует именно isPathTile, а не эту функцию, для
     * guard-проверки "это вообще дорожка?") — его спрайт остаётся неизменным.
     */
    private static boolean isPathOrBridgeTile(int textureId) {
        return isPathTile(textureId) || textureId == BRIDGE_TEXTURE_ID;
    }

    /**
     * Пересчитывает спрайт дорожки в клетке (i,j) по битовой маске из 4 соседей (тоже дорожка,
     * мостик, или нет) — см. класс-комментарий выше про соответствие маски и спрайта. Ничего не
     * делает, если клетка сейчас не дорожка (например, её только что затёрли другим объектом, или
     * это сам мостик — у него нет вариаций, см. isPathOrBridgeTile).
     */
    private static void recalcPathTile(int i, int j) {
        if (i < 0 || i >= store.mapHeight || j < 0 || j >= store.mapWidth) return;
        MapObject tile = store.objectedMap[i][j];
        if (!isPathTile(tile.getTextureId())) return;

        boolean n = i > 0                      && isPathOrBridgeTile(store.objectedMap[i - 1][j].getTextureId());
        boolean s = i < store.mapHeight - 1     && isPathOrBridgeTile(store.objectedMap[i + 1][j].getTextureId());
        boolean w = j > 0                      && isPathOrBridgeTile(store.objectedMap[i][j - 1].getTextureId());
        boolean e = j < store.mapWidth - 1      && isPathOrBridgeTile(store.objectedMap[i][j + 1].getTextureId());

        int id;
        int neighborCount = (n ? 1 : 0) + (s ? 1 : 0) + (e ? 1 : 0) + (w ? 1 : 0);

        if (neighborCount == 0) {
            id = PATH_STRAIGHT_NS; // изолированная дорожка — "первая дорожка", см. PATH_PICKER_ID
        } else if (neighborCount == 4) {
            id = PATH_CROSS;
        } else if (neighborCount == 3) {
            id = !n ? PATH_T_MISSING_N : !s ? PATH_T_MISSING_S : !e ? PATH_T_MISSING_E : PATH_T_MISSING_W;
        } else if (neighborCount == 1) {
            // Отдельного тайла-тупика в наборе нет — используем прямую подходящей оси (см. класс-
            // комментарий выше): визуально дорожка просто "обрывается" прямым сегментом.
            id = (n || s) ? PATH_STRAIGHT_NS : PATH_STRAIGHT_EW;
        } else { // neighborCount == 2
            if (n && s) {
                id = PATH_STRAIGHT_NS;
            } else if (e && w) {
                id = PATH_STRAIGHT_EW;
            } else if (n && e) {
                id = PATH_CORNER_NE;
            } else if (n && w) {
                id = PATH_CORNER_NW;
            } else if (s && e) {
                id = PATH_CORNER_SE;
            } else {
                id = PATH_CORNER_SW;
            }
        }

        tile.setTexture(textures[id].texture);
        tile.setTextureId(id);
        tile.setObjectHeight(textures[id].high);
        tile.flipX = false;
        tile.flipY = false;
    }

    /**
     * Пересчитывает ВСЮ связную сеть дорожек, содержащую клетку (i,j) или её 4 соседей (BFS) —
     * после установки/затирания дорожки. Раньше пересчитывался только 1 шаг (клетка + её
     * непосредственные соседи) — при определённом порядке расстановки часть сети могла остаться с
     * устаревшим видом (например, при сборке кольца из 4 клеток обновление не докатывалось до
     * дальней клетки, и вместо угла оставался крест/неверный вариант). Сеть дорожек обычно
     * небольшая (участок дороги, не вся карта) — полный пересчёт компонента дёшев.
     */
    private static void recalcPathAround(int i, int j) {
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        java.util.Set<Long> queued = new java.util.HashSet<>();

        int[] seedI = { i, i - 1, i + 1, i,     i     };
        int[] seedJ = { j, j,     j,     j - 1, j + 1 };
        for (int s = 0; s < seedI.length; s++) {
            enqueuePathTile(seedI[s], seedJ[s], queue, queued);
        }

        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            recalcPathTile(c[0], c[1]);

            enqueuePathTile(c[0] - 1, c[1], queue, queued);
            enqueuePathTile(c[0] + 1, c[1], queue, queued);
            enqueuePathTile(c[0], c[1] - 1, queue, queued);
            enqueuePathTile(c[0], c[1] + 1, queue, queued);
        }
    }

    private static void enqueuePathTile(int i, int j, java.util.ArrayDeque<int[]> queue, java.util.Set<Long> queued) {
        if (i < 0 || i >= store.mapHeight || j < 0 || j >= store.mapWidth) return;
        // Мостик тоже ставим в очередь — что бы BFS "прошёл сквозь" него и пересчитал дорожку по
        // ДРУГУЮ сторону воды тоже (recalcPathTile сам не тронет мостик — у него нет вариаций).
        if (!isPathOrBridgeTile(store.objectedMap[i][j].getTextureId())) return;

        long key = ((long) i << 32) | (j & 0xFFFFFFFFL);
        if (!queued.add(key)) return; // уже в очереди/обработана

        queue.add(new int[] { i, j });
    }

    /**
     * Пересчитывает ВСЕ дорожки на карте — вызывается после загрузки сохранённой карты (см.
     * UserInterface.buildMapChunk). flipX/flipY НЕ персистятся (см. FileManager) — они полностью
     * выводятся из соседей (тот же принцип, что и isShore/markShores), поэтому дешевле пересчитать
     * заново, чем тянуть в формат сохранения ещё одно поле.
     */
    public static void recalcAllPaths() {
        for (int i = 0; i < store.mapHeight; i++) {
            for (int j = 0; j < store.mapWidth; j++) {
                recalcPathTile(i, j);
            }
        }
    }

    private static boolean isGroundTile(int textureId) {
        return ArrayUtils.checkIntInArray(textureId, GROUND_TILE_IDS);
    }

    void defineMap() {
        Random rand = new Random();
        Perlin perlin = new Perlin(rand.nextInt(9000));
        int[][] perlinMap = new int[store.mapHeight][store.mapWidth];

        for(int x = 0; x < store.mapHeight; x++) {
            for(int y = 0; y < store.mapWidth; y++) {
                float value = perlin.getNoise(x/15f,y/15f,2,0.6f);
                perlinMap[x][y] = (int)(value * 255) & 255;
            }
        }

        store.objectedMap = new MapObject[store.mapHeight][store.mapWidth];
        int rn, ts;

        for(int i = 0; i<store.mapHeight; i++) {
            for(int j = 0; j<store.mapWidth; j++) {
                rn = perlinMap[i][j];
                ts = 8;
                if (rn > 30) {
                    ts = 1;
                }
                if (rn > 140) {
                    ts = 3;
                }
                if (rn > 253) {
                    ts = 2;
                }

//                if (rn > 254){ts=4;}
                if (rn == 249) {
                    ts = rand.nextInt(3) + 5;
                }

                MapObject tmp = new MapObject();

                tmp.setSurfaceTexture(textures[1].texture);
                tmp.setSurfaceId(1);

                tmp.setObjectHeight(textures[ts].high);
                tmp.isTree = textures[ts].isTree;
                tmp.setTexture(textures[ts].texture);
                tmp.setTextureId(ts);

                tmp.xPositionOnMap = i + store.TILE_INDEX_BASE;
                tmp.yPositionOnMap = j + store.TILE_INDEX_BASE;
                tmp.generateAndSetUUID();
                store.objectedMap[i][j] = tmp;

//                if (ArrayUtils.checkIntInArray(ts, lightObjectIds)){
//                    light.addPoint(i, j);
//                }
            }
        }

        // Общее поле "рельефа" — единственный источник истины и для озёр (низины), и для рек
        // (текут вниз по склону), см. generateElevation/generateLakes/generateRivers.
        float[][] elevation = generateElevation(rand);

        // OWNER_NONE / id озера (LAKE_ID_BASE и выше) / индекс реки (0..RIVER_COUNT-1) на клетку —
        // чтобы отличать "продолжение своей же реки" от столкновения с ЧУЖОЙ водой.
        int[][] waterOwner = new int[store.mapHeight][store.mapWidth];
        for (int[] row : waterOwner) java.util.Arrays.fill(row, OWNER_NONE);

        int[] lakeIdCounter = { LAKE_ID_BASE };
        java.util.List<Lake> lakes = generateLakes(rand, elevation, waterOwner, lakeIdCounter);
        generateRivers(rand, elevation, waterOwner, lakes, lakeIdCounter);
        markShores();

//        light.recalcOnMap();
    }

    /**
     * Берег — суша, граничащая с водой. Вода — строго ПОВЕРХНОСТЬ (см. getSurfaceId(), не
     * getTextureId() — объектный слой независим от того, вода там или земля, см. класс-комментарий
     * про surfaceDepth в MapObject): помечает MapObject.isShore у всех тайлов, у которых хотя бы
     * один из 4 соседей — вода-поверхность. Отдельных спрайтов берега нет — граница читается через
     * тонирование (MapObject.calcLitColor) и лёгкую shore-подсветку при рендере (см.
     * Editor.drawSurfaceTile), а не через новые тайлы/auto-tiling — на карте пока только один
     * плоский тайл воды (gp_10.png), без спрайтов "уголок берега"/"прямой берег"/"мыс" под Wang
     * tiling. Вызывается и после процедурной генерации, и после загрузки сохранённой карты (см.
     * UserInterface.buildMapChunk) — isShore не персистится (дёшево пересчитать, не тянуть в v5).
     */
    public static void markShores() {
        for (int i = 0; i < store.mapHeight; i++) {
            for (int j = 0; j < store.mapWidth; j++) {
                MapObject tile = store.objectedMap[i][j];
                if (tile.getSurfaceId() == WATER_TEXTURE_ID) {
                    tile.isShore = false;
                    continue;
                }

                boolean nearWater =
                    (i > 0                      && store.objectedMap[i - 1][j].getSurfaceId() == WATER_TEXTURE_ID) ||
                    (i < store.mapHeight - 1     && store.objectedMap[i + 1][j].getSurfaceId() == WATER_TEXTURE_ID) ||
                    (j > 0                      && store.objectedMap[i][j - 1].getSurfaceId() == WATER_TEXTURE_ID) ||
                    (j < store.mapWidth - 1      && store.objectedMap[i][j + 1].getSurfaceId() == WATER_TEXTURE_ID);

                tile.isShore = nearWater;
            }
        }
    }

    /** Локальное обновление isShore вокруг ОДНОЙ клетки (см. touchDown) — без прохода по всей карте. */
    private void updateShoreAround(int i, int j) {
        boolean isWater = store.objectedMap[i][j].getSurfaceId() == WATER_TEXTURE_ID;
        store.objectedMap[i][j].isShore = !isWater && hasWaterNeighbor(i, j);

        int[] dI = { -1, 1, 0, 0 };
        int[] dJ = { 0, 0, -1, 1 };
        for (int d = 0; d < 4; d++) {
            int ni = i + dI[d], nj = j + dJ[d];
            if (ni < 0 || ni >= store.mapHeight || nj < 0 || nj >= store.mapWidth) continue;
            MapObject neighbor = store.objectedMap[ni][nj];
            if (neighbor.getSurfaceId() != WATER_TEXTURE_ID) {
                neighbor.isShore = hasWaterNeighbor(ni, nj);
            }
        }
    }

    private boolean hasWaterNeighbor(int i, int j) {
        return
            (i > 0                  && store.objectedMap[i - 1][j].getSurfaceId() == WATER_TEXTURE_ID) ||
            (i < store.mapHeight - 1 && store.objectedMap[i + 1][j].getSurfaceId() == WATER_TEXTURE_ID) ||
            (j > 0                  && store.objectedMap[i][j - 1].getSurfaceId() == WATER_TEXTURE_ID) ||
            (j < store.mapWidth - 1  && store.objectedMap[i][j + 1].getSurfaceId() == WATER_TEXTURE_ID);
    }

    private static final int   MANUAL_WATER_MAX_SEARCH_RADIUS = 20; // дальше не ищем — считаем максимальной глубиной
    private static final float MANUAL_WATER_DEPTH_PER_TILE    = 3f;
    private static final int   MANUAL_WATER_MAX_DEPTH         = 30;

    /**
     * Глубина для тайла воды, поставленного вручную в редакторе — растёт с расстоянием до
     * ближайшего "берега" (не-водной ПОВЕРХНОСТИ или края карты), подобно тому, как глубина озера
     * плавно спадает от центра к краю при процедурной генерации (см. generateLakes). Кольцевой
     * поиск (по Чебышёву) от центра наружу — как только встретили не-воду, это и есть расстояние.
     */
    private int computeManualWaterDepth(int i, int j) {
        for (int r = 1; r <= MANUAL_WATER_MAX_SEARCH_RADIUS; r++) {
            for (int di = -r; di <= r; di++) {
                for (int dj = -r; dj <= r; dj++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != r) continue; // только кольцо радиуса r

                    int ni = i + di, nj = j + dj;
                    boolean isShoreHere = ni < 0 || ni >= store.mapHeight || nj < 0 || nj >= store.mapWidth
                        || store.objectedMap[ni][nj].getSurfaceId() != WATER_TEXTURE_ID;

                    if (isShoreHere) {
                        return -Math.min(MANUAL_WATER_MAX_DEPTH, Math.round(r * MANUAL_WATER_DEPTH_PER_TILE));
                    }
                }
            }
        }
        return -MANUAL_WATER_MAX_DEPTH; // всё в радиусе поиска — вода, дальше не проверяем
    }

    // ── Рельеф, озёра и реки ────────────────────────────────────────────────────────────────
    //
    // Полный редизайн (см. обсуждение): вместо самодельного синус-меандра — единое поле "высот"
    // (Perlin-шум + общий уклон карты с севера на юг), общее для озёр и рек:
    //   • Озёра — связные области, где рельеф ниже порога (см. generateLakes), т.е. настоящие
    //     низины, а не произвольно расставленные круги. Порог берётся ПЕРСЕНТИЛЕМ по фактическому
    //     распределению высот этой карты (а не абсолютной константой) — площадь озёр стабильна
    //     независимо от масштаба/октав шума. Сглаживание клеточным автоматом убирает шум-артефакты
    //     по краям берега, а компоненты меньше LAKE_MIN_SIZE отбрасываются как лужи.
    //   • Реки — "капля", скатывающаяся вниз по локальному градиенту рельефа (техника из
    //     hydraulic erosion / steepest-descent river tracing), с инерцией направления (не дёргано
    //     меняет курс) и лёгким случайным дрожанием. Поскольку ВСЕ реки читают ОДНО и то же поле
    //     рельefa, соседние русла естественно сходятся в одни и те же низины (настоящие слияния,
    //     а не совпадение фаз синусоиды) и текут туда, где действительно ниже — а не по
    //     синтетической кривой.
    // Столкновение реки с ЧУЖОЙ водой (другая река/озеро, не тот, из которого она сама вытекает)
    // останавливает её и создаёт небольшое озеро-пруд на месте стыка, вместо продолжения слитно.

    private static final int OWNER_NONE   = -1;
    private static final int LAKE_ID_BASE = 1000; // у каждого озера/пруда — свой уникальный id (см. ниже, почему)

    private static final float ELEVATION_SCALE       = 130f;
    private static final int   ELEVATION_OCTAVES     = 3;
    private static final float ELEVATION_PERSISTENCE = 0.5f;
    // Общий уклон карты: север выше, юг ниже — задаёт ПРЕОБЛАДАЮЩЕЕ направление стока рек (юг),
    // не абсолютный запрет на любое отклонение (локальный рельеф всё ещё может вести куда угодно).
    private static final float ELEVATION_NS_BIAS = 0.6f;

    private static final float LAKE_AREA_FRACTION = 0.09f; // доля карты НИЖЕ порога озёр (до сглаживания/фильтра луж)
    private static final int   LAKE_SMOOTH_PASSES = 2;      // проходов клеточного автомата по маске озёр
    private static final int   LAKE_MIN_SIZE      = 45;     // отбрасываем связные пятна меньше — шумовые лужи
    private static final float LAKE_DEPTH_RANGE   = 0.5f;   // на сколько ниже порога рельеф даёт максимальную глубину
    private static final int   LAKE_MAX_DEPTH     = 34;

    private static final int   RIVER_COUNT           = 14;
    private static final int   RIVER_MAX_DEPTH       = 30;
    private static final float RIVER_HALF_WIDTH      = 4.2f;   // половина ширины русла, в тайлах
    private static final float RIVER_STEP_LENGTH     = 1f;     // шаг капли, в тайлах
    private static final float RIVER_INERTIA         = 0.75f;  // доля прежнего направления при повороте — плавные, не дёрганые изгибы
    private static final float RIVER_JITTER          = 0.16f;  // случайное дрожание направления на шаг — живая, не идеально гладкая линия
    private static final float RIVER_GRADIENT_SAMPLE = 2.5f;   // на каком расстоянии (тайлы) считать наклон рельефа
    private static final int   RIVER_SOURCE_TAPER_TILES = 30;  // за сколько тайлов от истока глубина плавно нарастает до максимума
    private static final float RIVER_FROM_LAKE_CHANCE   = 0.4f; // шанс, что река начинается из озера, а не от края карты
    private static final int   RIVER_EDGE_CANDIDATES    = 6;    // сколько случайных точек сев. края пробуем — исток берём с самым высоким рельефом
    // При столкновении двух рек (см. generateRivers) образуется БОЛЬШОЕ озеро, а не маленький
    // прудик размером с само русло — множитель к RIVER_HALF_WIDTH и собственная (большая) глубина.
    private static final float RIVER_CONFLUENCE_LAKE_SCALE = 6f;
    private static final int   RIVER_CONFLUENCE_LAKE_DEPTH = 40;

    /** Одно озеро: id и точка на берегу (уже суша), откуда может вытекать река, + направление наружу. */
    private static final class Lake {
        final int id;
        final int shoreI, shoreJ;
        final float dirI, dirJ;

        Lake(int id, int shoreI, int shoreJ, float dirI, float dirJ) {
            this.id = id;
            this.shoreI = shoreI;
            this.shoreJ = shoreJ;
            this.dirI = dirI;
            this.dirJ = dirJ;
        }
    }

    /** Единое поле "высот" для рек и озёр — Perlin-шум + общий уклон карты с севера на юг. */
    private float[][] generateElevation(Random rand) {
        Perlin elevPerlin = new Perlin(rand.nextInt(9000));
        float[][] elevation = new float[store.mapHeight][store.mapWidth];

        for (int i = 0; i < store.mapHeight; i++) {
            float southBias = (i / (float) store.mapHeight) * ELEVATION_NS_BIAS;
            for (int j = 0; j < store.mapWidth; j++) {
                float n = elevPerlin.getNoise(i / ELEVATION_SCALE, j / ELEVATION_SCALE, ELEVATION_OCTAVES, ELEVATION_PERSISTENCE);
                elevation[i][j] = n - southBias;
            }
        }

        return elevation;
    }

    /**
     * Озёра — связные компоненты клеток, чей рельеф ниже порога (persentиль LAKE_AREA_FRACTION
     * по фактическому распределению высот ЭТОЙ карты — площадь озёр стабильна независимо от
     * калибровки шума), сглаженные клеточным автоматом (правило большинства соседей — убирает
     * одиночные шумовые "дырки"/"наросты" по краю берега) и отфильтрованные по минимальному
     * размеру (LAKE_MIN_SIZE — отсеивает шумовые лужи-артефакты). Глубина — по тому, насколько
     * рельеф клетки ниже порога (глубже низина — глубже вода).
     * @return список озёр с точкой возможного истока реки (см. Lake).
     */
    private java.util.List<Lake> generateLakes(Random rand, float[][] elevation, int[][] waterOwner, int[] lakeIdCounter) {
        int total = store.mapHeight * store.mapWidth;
        float[] sorted = new float[total];
        int idx = 0;
        for (int i = 0; i < store.mapHeight; i++) {
            for (int j = 0; j < store.mapWidth; j++) {
                sorted[idx++] = elevation[i][j];
            }
        }
        java.util.Arrays.sort(sorted);
        float threshold = sorted[Math.max(0, Math.min(total - 1, (int) (total * LAKE_AREA_FRACTION)))];

        boolean[][] mask = new boolean[store.mapHeight][store.mapWidth];
        for (int i = 0; i < store.mapHeight; i++) {
            for (int j = 0; j < store.mapWidth; j++) {
                mask[i][j] = elevation[i][j] < threshold;
            }
        }
        for (int pass = 0; pass < LAKE_SMOOTH_PASSES; pass++) {
            mask = smoothLakeMask(mask);
        }

        int[] dI = { -1, 1, 0, 0 };
        int[] dJ = { 0, 0, -1, 1 };

        boolean[][] visited = new boolean[store.mapHeight][store.mapWidth];
        java.util.List<Lake> lakes = new java.util.ArrayList<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();

        for (int i = 0; i < store.mapHeight; i++) {
            for (int j = 0; j < store.mapWidth; j++) {
                if (!mask[i][j] || visited[i][j]) continue;

                java.util.List<int[]> cells = new java.util.ArrayList<>();
                visited[i][j] = true;
                queue.add(new int[] { i, j });
                while (!queue.isEmpty()) {
                    int[] c = queue.poll();
                    cells.add(c);
                    for (int d = 0; d < 4; d++) {
                        int ni = c[0] + dI[d], nj = c[1] + dJ[d];
                        if (ni < 0 || ni >= store.mapHeight || nj < 0 || nj >= store.mapWidth) continue;
                        if (visited[ni][nj] || !mask[ni][nj]) continue;
                        visited[ni][nj] = true;
                        queue.add(new int[] { ni, nj });
                    }
                }

                if (cells.size() < LAKE_MIN_SIZE) continue; // шумовая лужа, не настоящее озеро

                int id = lakeIdCounter[0]++;
                java.util.List<int[]> shoreCells = new java.util.ArrayList<>();

                for (int[] c : cells) {
                    int ci = c[0], cj = c[1];
                    float depthT = Math.min(1f, (threshold - elevation[ci][cj]) / LAKE_DEPTH_RANGE);
                    float smooth = depthT * depthT * (3f - 2f * depthT);
                    int depth = -Math.round(LAKE_MAX_DEPTH * smooth);
                    if (depth < 0) {
                        writeWaterTile(ci, cj, depth, false);
                        waterOwner[ci][cj] = id;
                    }

                    for (int d = 0; d < 4; d++) {
                        int ni = ci + dI[d], nj = cj + dJ[d];
                        if (ni < 0 || ni >= store.mapHeight || nj < 0 || nj >= store.mapWidth) continue;
                        if (!mask[ni][nj]) { shoreCells.add(c); break; }
                    }
                }

                if (!shoreCells.isEmpty()) {
                    int[] shore = shoreCells.get(rand.nextInt(shoreCells.size()));
                    int si = shore[0], sj = shore[1];

                    java.util.List<int[]> landNeighbors = new java.util.ArrayList<>();
                    for (int d = 0; d < 4; d++) {
                        int ni = si + dI[d], nj = sj + dJ[d];
                        if (ni < 0 || ni >= store.mapHeight || nj < 0 || nj >= store.mapWidth) continue;
                        if (!mask[ni][nj]) landNeighbors.add(new int[] { ni, nj });
                    }

                    if (!landNeighbors.isEmpty()) {
                        int[] land = landNeighbors.get(rand.nextInt(landNeighbors.size()));
                        float outI = land[0] - si;
                        float outJ = land[1] - sj;
                        float len = (float) Math.sqrt(outI * outI + outJ * outJ);
                        if (len > 0.0001f) { outI /= len; outJ /= len; } else { outI = 1f; outJ = 0f; }
                        lakes.add(new Lake(id, land[0], land[1], outI, outJ));
                    }
                }
            }
        }

        return lakes;
    }

    /** Один проход клеточного автомата (правило большинства соседей) — сглаживает край маски озёр. */
    private boolean[][] smoothLakeMask(boolean[][] mask) {
        boolean[][] out = new boolean[store.mapHeight][store.mapWidth];

        for (int i = 0; i < store.mapHeight; i++) {
            for (int j = 0; j < store.mapWidth; j++) {
                int waterNeighbors = 0, total = 0;
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        if (di == 0 && dj == 0) continue;
                        int ni = i + di, nj = j + dj;
                        if (ni < 0 || ni >= store.mapHeight || nj < 0 || nj >= store.mapWidth) continue;
                        total++;
                        if (mask[ni][nj]) waterNeighbors++;
                    }
                }
                out[i][j] = waterNeighbors > total / 2f ? true : (waterNeighbors < total / 2f ? false : mask[i][j]);
            }
        }

        return out;
    }

    /**
     * Реки — "капли", скатывающиеся вниз по локальному градиенту общего поля рельефа (см.
     * generateElevation), с инерцией направления и лёгким случайным дрожанием (hydraulic-erosion
     * стиль трассировки, а не синтетическая кривая) — см. класс-комментарий выше про весь блок.
     * Исток — либо точка на северном краю карты (из нескольких случайных кандидатов берём самый
     * высокий рельеф — правдоподобнее для настоящего истока), либо берег готового озера (см.
     * Lake.shoreI/J — река стартует уже НА СУШЕ рядом с озером и течёт в направлении Lake.dirI/J).
     * Глубина плавно (smoothstep) нарастает от истока (RIVER_SOURCE_TAPER_TILES).
     *
     * Капля останавливается (и на этом месте появляется небольшой пруд, см. stampPond):
     *  — при столкновении с ЧУЖОЙ водой (другая река, либо чужое озеро — но не то, из которого
     *    сама река вытекает, см. originLakeId) — слияние, а не продолжение единым руслом;
     *  — если рельеф локально плоский (некуда течь) — река "теряется" небольшим озерцом.
     * Если капля просто уходит за пределы карты — это её естественный, "успешный" конец.
     */
    private void generateRivers(Random rand, float[][] elevation, int[][] waterOwner, java.util.List<Lake> lakes, int[] lakeIdCounter) {
        int radius = Math.round(RIVER_HALF_WIDTH);
        int maxSteps = store.mapHeight * 3;

        for (int r = 0; r < RIVER_COUNT; r++) {
            float posI, posJ, dirI, dirJ;
            int originLakeId = OWNER_NONE;

            if (!lakes.isEmpty() && rand.nextFloat() < RIVER_FROM_LAKE_CHANCE) {
                Lake lake = lakes.get(rand.nextInt(lakes.size()));
                posI = lake.shoreI;
                posJ = lake.shoreJ;
                dirI = lake.dirI;
                dirJ = lake.dirJ;
                originLakeId = lake.id;
            } else {
                int bestCol = rand.nextInt(store.mapWidth);
                float bestElev = elevation[0][bestCol];
                for (int c = 1; c < RIVER_EDGE_CANDIDATES; c++) {
                    int col = rand.nextInt(store.mapWidth);
                    if (elevation[0][col] > bestElev) { bestElev = elevation[0][col]; bestCol = col; }
                }
                posI = 0;
                posJ = bestCol;
                dirI = 1f;
                dirJ = 0f;
            }

            float travelled = 0f;

            for (int step = 0; step < maxSteps; step++) {
                int ci = Math.round(posI);
                int cj = Math.round(posJ);
                if (ci < 0 || ci >= store.mapHeight || cj < 0 || cj >= store.mapWidth) break; // вышла за карту — естественный конец

                // Полный 2D-диск вокруг ТЕКУЩЕЙ позиции (а не 1D-линия вдоль перпендикуляра к
                // направлению) — на диагональном направлении движения линия точек-семплов на
                // целочисленных смещениях перепрыгивает часть клеток решётки после округления,
                // отсюда "прерывистая" (не сплошная) река на скриншоте. Диск даёт гарантированно
                // сплошное покрытие: соседние шаги (RIVER_STEP_LENGTH=1) сильно перекрываются, т.к.
                // шаг много меньше радиуса реки.
                boolean collided = false;
                for (int di = -radius; di <= radius && !collided; di++) {
                    for (int dj = -radius; dj <= radius && !collided; dj++) {
                        int oi = ci + di;
                        int oj = cj + dj;
                        if (oi < 0 || oi >= store.mapHeight || oj < 0 || oj >= store.mapWidth) continue;
                        if (Math.sqrt(di * di + dj * dj) >= RIVER_HALF_WIDTH) continue;

                        int owner = waterOwner[oi][oj];
                        if (owner != OWNER_NONE && owner != r && owner != originLakeId) collided = true;
                    }
                }

                if (collided) {
                    stampPond(ci, cj, RIVER_HALF_WIDTH * RIVER_CONFLUENCE_LAKE_SCALE, RIVER_CONFLUENCE_LAKE_DEPTH, rand, waterOwner, lakeIdCounter[0]++);
                    break;
                }

                float taper = Math.min(1f, travelled / RIVER_SOURCE_TAPER_TILES);
                taper = taper * taper * (3f - 2f * taper);

                if (taper > 0.001f) {
                    for (int di = -radius; di <= radius; di++) {
                        for (int dj = -radius; dj <= radius; dj++) {
                            int oi = ci + di;
                            int oj = cj + dj;
                            if (oi < 0 || oi >= store.mapHeight || oj < 0 || oj >= store.mapWidth) continue;

                            float distFromCenter = (float) Math.sqrt(di * di + dj * dj);
                            if (distFromCenter >= RIVER_HALF_WIDTH) continue;

                            float widthT = distFromCenter / RIVER_HALF_WIDTH;
                            float widthSmooth = 1f - (widthT * widthT * (3f - 2f * widthT));
                            int depth = -Math.round(RIVER_MAX_DEPTH * widthSmooth * taper);
                            if (depth >= 0) continue;

                            // Уже вода ЭТОЙ ЖЕ реки (соседний шаг) или чужого озера-истока —
                            // просто пишем; уже вода ДРУГОГО озера сюда никогда не попадёт — такое
                            // столкновение отловлено выше и обрывает реку раньше этой строки.
                            writeWaterTile(oi, oj, depth, true);
                            waterOwner[oi][oj] = r;
                        }
                    }
                }

                // Локальный градиент рельефа (конечные разности) — течём в сторону убывания высоты.
                int si = clamp(Math.round(posI + RIVER_GRADIENT_SAMPLE), 0, store.mapHeight - 1);
                int ni = clamp(Math.round(posI - RIVER_GRADIENT_SAMPLE), 0, store.mapHeight - 1);
                int sj = clamp(Math.round(posJ + RIVER_GRADIENT_SAMPLE), 0, store.mapWidth - 1);
                int nj = clamp(Math.round(posJ - RIVER_GRADIENT_SAMPLE), 0, store.mapWidth - 1);
                float gi = elevation[si][cj] - elevation[ni][cj];
                float gj = elevation[ci][sj] - elevation[ci][nj];

                // Инерция (плавный, не дёрганый поворот) + небольшое случайное дрожание (живая линия).
                float newDirI = dirI * RIVER_INERTIA - gi * (1f - RIVER_INERTIA);
                float newDirJ = dirJ * RIVER_INERTIA - gj * (1f - RIVER_INERTIA);
                newDirI += (rand.nextFloat() - 0.5f) * RIVER_JITTER;
                newDirJ += (rand.nextFloat() - 0.5f) * RIVER_JITTER;

                float len = (float) Math.sqrt(newDirI * newDirI + newDirJ * newDirJ);
                if (len < 0.0001f) {
                    // Рельеф локально плоский — течь некуда, река "теряется" небольшим озерцом.
                    stampPond(ci, cj, RIVER_HALF_WIDTH * 1.6f, RIVER_MAX_DEPTH, rand, waterOwner, lakeIdCounter[0]++);
                    break;
                }
                dirI = newDirI / len;
                dirJ = newDirJ / len;

                posI += dirI * RIVER_STEP_LENGTH;
                posJ += dirJ * RIVER_STEP_LENGTH;
                travelled += RIVER_STEP_LENGTH;
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Пруд/озеро с чуть неровным (шум) краем — на месте слияния рек или там, где река "теряется". */
    private void stampPond(int centerI, int centerJ, float radius, int maxDepth, Random rand, int[][] waterOwner, int ownerId) {
        Perlin shapePerlin = new Perlin(rand.nextInt(9000));
        int r = Math.round(radius) + 3;

        for (int di = -r; di <= r; di++) {
            for (int dj = -r; dj <= r; dj++) {
                int i = centerI + di;
                int j = centerJ + dj;
                if (i < 0 || i >= store.mapHeight || j < 0 || j >= store.mapWidth) continue;

                float dist = (float) Math.sqrt(di * di + dj * dj);
                float noiseVal = shapePerlin.getNoise(i / 10f, j / 10f);
                float effectiveRadius = radius * (1f + noiseVal * 0.3f);
                if (dist >= effectiveRadius) continue;

                float t = dist / effectiveRadius;
                float smooth = 1f - (t * t * (3f - 2f * t));
                int depth = -Math.round(maxDepth * smooth);
                if (depth >= 0) continue;

                // Math.min внутри writeWaterTile — если тут уже была более глубокая вода (озеро,
                // из которого выросла эта капля), дно НЕ поднимается. Пруд/слияние — всегда "озеро"
                // (волны, не течение) для шейдера: место впадения реки должно ЭЛЕГАНТНО слиться в
                // озеро, а не выглядеть как обрыв текущей реки.
                writeWaterTile(i, j, depth, false);
                waterOwner[i][j] = ownerId;
            }
        }
    }

    /**
     * Превращает тайл в воду заданной глубины (surfaceDepth, ОТРИЦАТЕЛЬНАЯ) — уровень ПОВЕРХНОСТИ.
     * Используется ТОЛЬКО процедурной генерацией (реки/озёра/пруды) — в отличие от РУЧНОЙ
     * установки воды в редакторе (см. touchDown), тут объектный слой ВСЕГДА очищается под водой:
     * река/озеро "смывает" деревья/камни, которые оказались на пути её генерации, а не оставляет
     * их торчать посреди воды. При ручной установке — наоборот, пользователь управляет этим сам
     * (объект, если стоял, остаётся стоять на новой воде) — это осознанный выбор, а не
     * автоматический побочный эффект генерации.
     */
    private void writeWaterTile(int i, int j, int depth, boolean isRiver) {
        MapObject tile = store.objectedMap[i][j];
        tile.isRiverWater = isRiver;

        if (tile.getSurfaceId() == WATER_TEXTURE_ID) {
            tile.surfaceDepth = Math.min(tile.surfaceDepth, depth);
        } else {
            tile.setSurfaceTexture(textures[WATER_TEXTURE_ID].texture);
            tile.setSurfaceId(WATER_TEXTURE_ID);
            tile.surfaceDepth = depth;
        }

        // Объектный слой — всегда синхронизирован с поверхностью (both = вода): что бы там ни
        // стояло (дерево/камень из первичной генерации ландшафта), река/озеро это "смывает".
        tile.setTexture(textures[WATER_TEXTURE_ID].texture);
        tile.setTextureId(WATER_TEXTURE_ID);
        tile.setObjectHeight(textures[WATER_TEXTURE_ID].high); // сброс высоты — не осталась от смытого объекта
        tile.isTree = false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        storeKey(button);
        // button != -1 — настоящее НОВОЕ нажатие (не продолжение протаскивания, см.
        // View.touchDragged), начинаем новый "мазок" — сбрасываем точку, от которой достраивается
        // линия Брезенхэма (см. bresenhamLine ниже), что бы не тянуть отрезок от прошлого мазка.
        if (button != -1) {
            lastPaintedTileX = Integer.MIN_VALUE;
        }
        lastTouchedButton = button == -1 ? lastTouchedButton : button;

        mouseMoved(screenX,screenY);
        int arrPointX = selectedTileX-1;
        int arrPointY = selectedTileY-1;


        // button==0 (ЛКМ) ИЛИ button==1 (ПКМ) — расширено с "только ЛКМ", т.к. умения теперь можно
        // привязать и на ПКМ (см. SimulationInputThread.touchDown — приоритет подбор/каст/клик-муав).
        if (store.isSimulationMode && store.simulationInput != null && (button == 0 || button == 1)) {
            if (store.simulationInput.touchDown(store.mouseX, store.mouseY, button)) {
                return false;
            }
        }

        if(!isDragged){
            isUiTouched = userInterface.checkTouch(false, false, button);
        }
        if (isUiTouched){
            return false;
        }

        if (lastTouchedButton == 2 && isDragged){
            int scaleX = 0, scaleY = 0;
            float cf=0;

            int scaleCoefficientX = (Math.abs(screenX - lastDraggedX));
            int scaleCoefficientY = (Math.abs(screenY - lastDraggedY));

            if (screenX>lastDraggedX) {scaleX = 0 - scaleCoefficientX;}
            if (screenX<lastDraggedX) {scaleX = scaleCoefficientX;}
            if (screenY>lastDraggedY) {scaleY = scaleCoefficientY;}
            if (screenY<lastDraggedY) {scaleY = 0 - scaleCoefficientY;}

            cf = store.scaleTotal / 1000*(float)0.55;

            store.shiftX = store.shiftX - (int)(scaleX+(scaleX*cf));
            store.shiftY = store.shiftY - (int)(scaleY+(scaleY*cf));
        }

        if (
            lastTouchedButton == 0 &&
            arrPointX >= 0 &&
            arrPointX < store.mapHeight &&
            arrPointY >= 0 &&
            arrPointY < store.mapWidth &&
            store.selectedTailId > 0
        ) {
            // View.touchDragged(...) зовёт touchDown(...,-1) на КАЖДОЕ событие движения мыши при
            // зажатой кнопке — но эти события не гарантированно приходят на каждый пройденный
            // тайл: при быстром протаскивании курсор может "перепрыгнуть" сразу через несколько
            // клеток между двумя последовательными событиями, оставляя пропущенные клетки
            // нетронутыми — отсюда разрывы в нарисованной линии (см. баг-репорт со скриншотом).
            // Достраиваем отрезок от предыдущей закрашенной клетки до текущей (алгоритм
            // Брезенхэма) — все клетки на пути получают то же самое размещение.
            if (lastPaintedTileX != Integer.MIN_VALUE) {
                bresenhamLine(lastPaintedTileX, lastPaintedTileY, arrPointX, arrPointY, this::placeTileAt);
            } else {
                placeTileAt(arrPointX, arrPointY);
            }
            lastPaintedTileX = arrPointX;
            lastPaintedTileY = arrPointY;
        }

        return false;
    }

    // ── Достройка линии протаскивания (см. touchDown выше) ──────────────────────────────────
    private int lastPaintedTileX = Integer.MIN_VALUE, lastPaintedTileY = Integer.MIN_VALUE;

    private interface TileVisitor {
        void visit(int i, int j);
    }

    /** Целочисленный алгоритм Брезенхэма — вызывает visitor на каждой клетке отрезка (i0,j0)-(i1,j1). */
    private static void bresenhamLine(int i0, int j0, int i1, int j1, TileVisitor visitor) {
        int di = Math.abs(i1 - i0), dj = -Math.abs(j1 - j0);
        int si = i0 < i1 ? 1 : -1, sj = j0 < j1 ? 1 : -1;
        int err = di + dj;
        int i = i0, j = j0;

        while (true) {
            visitor.visit(i, j);
            if (i == i1 && j == j1) break;
            int e2 = 2 * err;
            if (e2 >= dj) { err += dj; i += si; }
            if (e2 <= di) { err += di; j += sj; }
        }
    }

    /** Устанавливает выбранный в пикере тайл в клетку (arrPointX,arrPointY) — см. touchDown. */
    private void placeTileAt(int arrPointX, int arrPointY) {
        if (arrPointX < 0 || arrPointX >= store.mapHeight || arrPointY < 0 || arrPointY >= store.mapWidth) return;

//                Очистка света
        MapObject tile = store.objectedMap[arrPointX][arrPointY];
        int previousTextureId = tile.getTextureId();
        int newTextureId = store.selectedTailId;

        if (!ArrayUtils.checkIntInArray(newTextureId, lightObjectIds) && ArrayUtils.checkIntInArray(previousTextureId, lightObjectIds)) {
            light.removePoint(arrPointX, arrPointY);
            light.recalcOnMapFromPoint(arrPointX, arrPointY);
        }

//                Обновление элемента карты — строгое разделение слоёв (см. GROUND_TILE_IDS):
        // земля и вода — ИСКЛЮЧИТЕЛЬНО поверхность, они не могут "просочиться" в объектный
        // слой и наоборот. Объект (дерево/камень/мостик/здание), НАОБОРОТ, ставится НЕЗАВИСИМО
        // от того, что сейчас на поверхности — вода под ним никогда не трогается и не теряется
        // (дерево может стоять прямо в воде, см. touchDown ниже). А вот ручная установка
        // ЗЕМЛИ/ВОДЫ, наоборот, полностью ОЧИЩАЕТ объектный слой — что бы там ни стояло
        // (дерево/камень/мостик/дорожка), тайл становится "чистой" поверхностью без отдельного
        // объекта (по требованию пользователя — иначе руками закрашивая поверхность, легко
        // случайно оставить "мусорные" объекты под ней).
        if (isGroundTile(newTextureId)) {
            tile.setSurfaceTexture(textures[newTextureId].texture);
            tile.setSurfaceId(newTextureId);
            tile.surfaceDepth = newTextureId == WATER_TEXTURE_ID ? computeManualWaterDepth(arrPointX, arrPointY) : 0;
            tile.isRiverWater = false; // волны, не течение — разумный вид для произвольно нарисованной формы

            boolean wasPath = isPathOrBridgeTile(previousTextureId); // включая мостик, что бы соседи пересчитались при его сносе тоже

            // Синхронизируем объектный слой с новой поверхностью (см. BaseObject.draw()'s
            // оптимизацию surfaceId==textureId — объектный слой не дублирует отрисовку).
            tile.setTexture(textures[newTextureId].texture);
            tile.setTextureId(newTextureId);
            tile.isTree = false;
            tile.flipX = false;
            tile.flipY = false;
            tile.setObjectHeight(textures[newTextureId].high);
            tile.setWidth(textures[newTextureId].texture.getWidth() / store.tileDownScale);
            tile.setHeight(textures[newTextureId].texture.getHeight() / store.tileDownScale);

            if (wasPath) {
                // Затёрли дорожку — соседние дорожки могли потерять связь, пересчитываем их вид.
                recalcPathAround(arrPointX, arrPointY);
            }
        } else if (newTextureId == PATH_PICKER_ID) {
            // Дорожка — единственный видимый в пикере инструмент вместо 11 отдельных
            // спрайтов-вариантов (см. класс-комментарий про автотайлинг выше). Если поверхность
            // под ней — вода, ставим МОСТИК (фиксированный спрайт, без вариаций); иначе —
            // дорожку-плейсхолдер, её точный вид пересчитает recalcPathAround ниже по соседям.
            boolean onWater = tile.getSurfaceId() == WATER_TEXTURE_ID;
            int placedId = onWater ? BRIDGE_TEXTURE_ID : PATH_STRAIGHT_NS;
            tile.setTexture(textures[placedId].texture);
            tile.setTextureId(placedId);
            tile.setObjectHeight(textures[placedId].high);
            tile.isTree = false;
            tile.flipX = false;
            tile.flipY = false;
            tile.setWidth(textures[placedId].texture.getWidth() / store.tileDownScale);
            tile.setHeight(textures[placedId].texture.getHeight() / store.tileDownScale);
            // Пересчитываем и для мостика тоже — сам он не изменится (recalcPathTile трогает
            // только настоящие дорожки, см. isPathOrBridgeTile), но соседние дорожки теперь видят
            // в нём связанного соседа и должны "втечь" в него, а не остаться тупиком у кромки воды.
            recalcPathAround(arrPointX, arrPointY);
        } else {
            boolean wasPath = isPathOrBridgeTile(previousTextureId); // включая мостик, что бы соседи пересчитались при его сносе тоже
            tile.setTexture(textures[newTextureId].texture);
            tile.setTextureId(newTextureId);
            tile.setObjectHeight(textures[newTextureId].high);
            tile.isTree = textures[newTextureId].isTree;
            tile.flipX = false;
            tile.flipY = false;
            tile.setWidth(textures[newTextureId].texture.getWidth() / store.tileDownScale);
            tile.setHeight(textures[newTextureId].texture.getHeight() / store.tileDownScale);
            if (wasPath) {
                // Затёрли дорожку другим объектом — соседние дорожки могли потерять связь,
                // пересчитываем их вид (сама клетка recalcPathTile корректно пропустит — она
                // больше не дорожка).
                recalcPathAround(arrPointX, arrPointY);
            }
        }

//                Уствновка света
        if (ArrayUtils.checkIntInArray(newTextureId, lightObjectIds)) {
            light.addPoint(arrPointX, arrPointY);
        }

        // Берег (см. markShores) — локальное обновление вокруг только что изменённой клетки,
        // без пересчёта всей карты (дорого при рисовании воды перетаскиванием мыши).
        updateShoreAround(arrPointX, arrPointY);

        light.recalcOnMapFromPoint(arrPointX, arrPointY);
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        releaseKey(button);
        isUiTouched = userInterface.checkTouch(isDragged, true, button);
        lastPaintedTileX = Integer.MIN_VALUE; // конец "мазка" (см. touchDown/bresenhamLine)
        return super.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        store.isGamepadMode = false;
        store.mouseX = mouseX = screenX;
        store.mouseY = mouseY = (int) store.uiHeightOriginal - screenY;

        if (!userInterface.getMouseMoveBlockStatus()){
            // Координаты САМОГО СОБЫТИЯ (screenX/screenY), а не calcPositionCursor()'s обычный
            // "живой" опрос Gdx.input.getX/getY() — на клике/касании важно посчитать именно ТУ
            // точку, где произошло событие: если между постановкой события в очередь и его
            // обработкой Gdx.input успел обновиться (курсор чуть сдвинулся, доп. потоки в проекте —
            // SimulationInputThread/PhysicThread и т.д.), "живой" опрос даст УЖЕ ДРУГУЮ клетку, чем
            // та, по которой реально кликнули — отсюда "кликал на один тайл, ставится на другой".
            calcPositionCursor(screenX, screenY);
        }

        userInterface.onMouseMoved();

        return false;
    }

    /** Пересчёт по "живой" текущей позиции мыши — для случаев без конкретного события (клавиатура, зум). */
    public void calcPositionCursor(){
        calcPositionCursor(Gdx.input.getX(), Gdx.input.getY());
    }

    /** Пересчёт по ТОЧНЫМ координатам конкретного события (клик/движение мыши) — см. mouseMoved. */
    public void calcPositionCursor(int rawScreenX, int rawScreenY){
        Vector3 v = Main.viewport.getCamera().unproject(new Vector3(rawScreenX, rawScreenY, 0));
        float mouseInViewportX = v.x - store.shiftX - (float)(10 / store.tileDownScale) ;
        float mouseInViewportY = v.y - store.shiftY + (float)(60 / store.tileDownScale);
        float[] dotPoint = transform.isometricToCartesian(mouseInViewportX, mouseInViewportY);
        store.playerPositionX = v.x ;
        store.playerPositionY = v.y;
        // Мировая точка под курсором в декартовых координатах — источник цели для клик-муава
        // (см. SimulationInputThread.touchDown, Store.moveTargetX/Y).
        store.cursorWorldX = dotPoint[0];
        store.cursorWorldY = dotPoint[1];

        light.setUserPoint(v.x, v.y);
        selectedTileX = (int) ((dotPoint[0]) / tileSizeX) - 1;
        selectedTileY = (int) ((dotPoint[1]) / tileSizeY);

        store.selectedTileX = (dotPoint[0] / tileSizeX) - 1;
        store.selectedTileY = dotPoint[1] / tileSizeY;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        isImmediatelyReleaseKey = true;
        if (store.systemUI != null && store.systemUI.handleScroll(amountY)) return true;
        if (amountY > 0) {
            keyDown(156);
        } else {
            keyDown(157);
        }
        store.isDragged = false;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        isUiTouched = userInterface.checkTouch(true, false, lastTouchedButton);

        return super.touchDragged(screenX, screenY, pointer);
    }

    @Override
    public boolean keyDown(int keyCode){
        if (store.isSimulationMode && store.simulationInput != null) {
            return store.simulationInput.keyDown(keyCode);
        }

        if(userInterface.checkKey(keyCode)){
            return false;
        }

        super.keyDown(keyCode);

        boolean isNeedDownScale = false;
        boolean isNeedUpScale = false;

        if (keyCode == 157 && !store.isNeedToChangeScale) {
            isNeedUpScale = CameraSettings.upScale();
            calcPositionCursor();
        }

        if (keyCode == 156 && !store.isNeedToChangeScale) {
            isNeedDownScale = CameraSettings.downScale();
            calcPositionCursor();
        }

        if (keyCode == 19 || isNeedUpScale) {
            int scale = isNeedUpScale? tileSizeY/2 : tileSizeY;
            store.shiftY = store.shiftY - scale;
            calcPositionCursor();
        }

        if (keyCode == 20 || isNeedDownScale ) {
            int scale = isNeedDownScale? tileSizeY/2 : tileSizeY;
            store.shiftY = store.shiftY + scale;
            calcPositionCursor();
        }

        if (keyCode == 22 || isNeedUpScale) {
            int scale = isNeedUpScale ? tileSizeX : (tileSizeX*2);
            store.shiftX = store.shiftX - scale;
            calcPositionCursor();
        }

        if (keyCode == 21 || isNeedDownScale) {
            int scale = isNeedDownScale? tileSizeX : (tileSizeX*2);
            store.shiftX = store.shiftX + scale;
            calcPositionCursor();
        }

        if (isImmediatelyReleaseKey){
            isImmediatelyReleaseKey = false;
            releaseKey(keyCode);
        }

        return false;
    }

    @Override
    public boolean keyUp(int keyCode) {
        if (store.isSimulationMode && store.simulationInput != null) {
            return store.simulationInput.keyUp(keyCode);
        }
        return super.keyUp(keyCode);
    }

    @Override
    public boolean keyTyped(char character){
        if (userInterface.keyTyped(character)){
            return true;
        }

        return false;
    }

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);

        if (store.isNeedToChangeScale) {
            calcPositionCursor();
            return;
        }

        // Карта строится чанками — пропускаем рендер тайлов до готовности
        if (store.isMapLoading || store.objectedMap == null) {
            return;
        }

        float[] cursorPoint = transform.cartesianToIsometric(-1,-1);
        int mapI, mapJ;
        float[] point;

//        Задаём задний фон
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glClearColor(store.dayCoefficient, store.dayCoefficient, store.dayCoefficient, 1);


//        Симуляция: PhysicThread двигает player; здесь только центрируем камеру
        if (store.isSimulationMode && store.player != null && store.player.isInitialized()) {
            float[] isoPos = Transform.cartesianToIsometric(store.player.worldX, store.player.worldY);
            store.shiftX = (int)(store.display.get("width")  / 2 - isoPos[0]);
            store.shiftY = (int)(store.display.get("height") / 2 - isoPos[1]);
        }

//        Геймпад: опрос левого стика на GL-потоке, результат в Store для SimulationInputThread
        if (store.isSimulationMode && store.simulationInput != null) {
            store.simulationInput.pollFrame();
        }

//        Снимок источников света от эффектов умений — ДО отрисовки тайлов (MapObject.calcLitColor
//        читает Store.skillLightPoints на каждый тайл), иначе освещение отставало бы на кадр
//        (см. SkillEffectRenderer.updateLightSnapshot).
        if (store.isSimulationMode && store.skillEffectRenderer != null) {
            store.skillEffectRenderer.updateLightSnapshot();
        }

//        Смена времени суток (только в dev-режиме — в симуляции управляют треды)
        if (!store.isSimulationMode) {
            if (!store.isDay) {
                store.dayCoefficient = store.dayCoefficient - cm;
                if (store.dayCoefficient < -0.10) store.dayCoefficient = (float)-0.10;
            }
            if (store.isDay) {
                store.dayCoefficient = store.dayCoefficient + cm;
                if (store.dayCoefficient > 1) store.dayCoefficient = 1;
            }
        }

//        Расчёт области карты для отрисовки
        int d = (int) (
                (store.display.get("width") / tileSizeX) + (store.display.get("height") / tileSizeY)
                        + (Math.abs(store.shiftX) / (tileSizeX*2))
                        +(Math.abs(store.shiftY) / (tileSizeY)));

        float fc = Math.min(Math.max(store.scaleTotal / 10*(float)5, 20), 150);

        int e1 = (-(store.shiftX / (tileSizeX*2)) - (store.shiftY / (tileSizeY)) - 10);
        int e2 = ((store.shiftX / (tileSizeX*2)) - (store.shiftY / (tileSizeY)))-(int)fc;

//        Отрисовка карты
//        1 Поверхности
        for (int i=Math.min(d, store.mapHeight); i > Math.max(e1,0); i--) {
            mapI = i - 1;

            for (int j = Math.min(e2 + (int) (fc * 2), store.mapWidth); j > Math.max(e2, 0); j--) {
                mapJ = j - 1;

//                Ограничение отрисовки на основе отрисовывемого элемента
                point = transform.cartesianToIsometric(i * tileSizeX, j * tileSizeY);

                if (point[0] + store.shiftX - (tileSizeX * 2) > store.display.get("width")) {
                    break;
                }
                if (point[1] + store.shiftY + (tileSizeX * 2) < 0) {
                    break;
                }

                if (point[0] + store.shiftX + (tileSizeX * 2) < 0) {
                    continue;
                }
                if (point[1] + store.shiftY - (tileSizeY * 2) > store.display.get("height")) {
                    continue;
                }

                drawSurfaceTile(batch, store.objectedMap[mapI][mapJ]);

                // Плоские объекты (мостик/дорожка/мелкие камешки — objectHeight < FLAT_OBJECT_HEIGHT)
                // рисуем ЗДЕСЬ, вместе с поверхностями, а НЕ в проходе "Объекты" ниже — тот
                // чередует отрисовку объектов с игроком/существами строго по порядку сканирования
                // тайлов, и когда сканирование доходит до такого объекта ПОСЛЕ игрока, он рисуется
                // поверх — хотя у плоского объекта нет высоты, что бы реально загораживать
                // стоящего персонажа (см. баг-репорт: "мостик и дорожка перекрывают игрока").
                // Высокие объекты (деревья и т.д.) сознательно ОСТАЮТСЯ в проходе "Объекты" — им
                // нужна честная изометрическая сортировка по глубине относительно игрока/существ.
                if (store.objectedMap[mapI][mapJ].objectHeight < FLAT_OBJECT_HEIGHT) {
                    drawTile(batch, store.objectedMap[mapI][mapJ]);
                }

                store.objectedMap[mapI][mapJ].isPlayerInside = false;
                store.objectedMap[mapI][mapJ].isRenderLighAndNigth = true;
            }
        }

//        Объекты на поверхностях
        for (int i=Math.min(d, store.mapHeight); i > Math.max(e1,0); i--)
        {
            mapI = i - 1;

            for (int j=Math.min(e2+(int)(fc*2), store.mapWidth); j > Math.max(e2,0); j--){
                mapJ = j - 1;

//                Ограничение отрисовки на основе отрисовывемого элемента
                point = transform.cartesianToIsometric(i * tileSizeX, j * tileSizeY);

                if (point[0] + store.shiftX - (tileSizeX*2)>store.display.get("width")){
                    break;
                }
                if (point[1] + store.shiftY + (tileSizeX*2)< 0){
                    break;
                }

                if (point[0] + store.shiftX + (tileSizeX*2)< 0){
                    continue;
                }
                if (point[1] + store.shiftY - (tileSizeY*2)>store.display.get("height")){
                    continue;
                }

//                рисуем целевой элемент для установки
                if (i == selectedTileX && j == selectedTileY && store.selectedTailId > 0){

                    batch.setColor(0.75f,0.62f,0.12f,1);
                    cursorPoint = transform.cartesianToIsometric((selectedTileX)*tileSizeX,(selectedTileY)*tileSizeY);

                    int sx, sy;
                    float sw, sh;
                    Texture t = textures[store.selectedTailId].texture;

                    sx = (int) (cursorPoint[0] + store.shiftX);
                    sy = (int) (cursorPoint[1] + store.shiftY);
                    sw = (float) t.getWidth() / store.tileDownScale;
                    sh = (float) t.getHeight() / store.tileDownScale;

//                    Рисуем рамку и выбранный элемент внутри
                    batch.draw(hintUp,sx + 2, sy + 4, sw, sh);
                    batch.draw(textures[store.selectedTailId].texture, sx, sy, sw, sh);
                    batch.draw(hintDown,sx + 3, sy + 3, sw, sh);
                } else {
//                    Делаем подсветку для элемента под курсором
                    if (i == selectedTileX && j == selectedTileY){
                        batch.setColor(0.56f,0.57f,0.75f,1);
                        store.objectedMap[mapI][mapJ].isRenderLighAndNigth = false;
                    }

//                Рисуем карту — плоские объекты (мостик/дорожка/мелкие камешки) уже нарисованы
                    // выше, в проходе поверхностей (см. FLAT_OBJECT_HEIGHT) — не дублируем.
                    if (store.objectedMap[mapI][mapJ].objectHeight >= FLAT_OBJECT_HEIGHT) {
                        drawTile(batch, store.objectedMap[mapI][mapJ]);
                    }
                    store.objectedMap[mapI][mapJ].isPlayerInside = false;
                    store.objectedMap[mapI][mapJ].isRenderLighAndNigth = true;
                }

//                Рисуем существ и здания на карте
                renderCreations(batch, mapI, mapJ, false);
                renderBuildings(batch, mapI, mapJ, false);
                renderDrops(batch, mapI, mapJ, false);
                renderSimCreatures(batch, mapI, mapJ, false);

//                Рисуем игрока в его тайловой позиции (как creations)
                if (store.isSimulationMode && store.player != null && store.player.isInitialized()) {
                    int pi = (int)(store.player.worldX / tileSizeX);
                    int pj = (int)(store.player.worldY / tileSizeY);
                    if (i == pi && j == pj) {
                        // Земляной слой умений (ауры под ногами, см. SkillEffectRenderer.renderGround) —
                        // строго ДО игрока, иначе спрайт ауры перекрывал бы персонажа сверху.
                        if (store.skillEffectRenderer != null) {
                            store.skillEffectRenderer.renderGround(batch);
                        }
                        store.player.draw(batch);
                    }
                }

//                на смежных, если они на уровне земли
                renderCreations(batch, mapI, mapJ+1, true);
                renderCreations(batch, mapI+1, mapJ, true);
                renderBuildings(batch, mapI, mapJ+1, true);
                renderBuildings(batch, mapI+1, mapJ, true);
                renderDrops(batch, mapI, mapJ+1, true);
                renderDrops(batch, mapI+1, mapJ, true);
                renderSimCreatures(batch, mapI, mapJ+1, true);
                renderSimCreatures(batch, mapI+1, mapJ, true);
            }
        }

        // ── Подписи дропов: единый проход поверх карты (без перекрытий, с фокусом) ──
        if (store.isSimulationMode) {
            com.nicweiss.editor.simulation.DropManager.renderLabels(batch);
        }

        // ── Дождь и молния ────────────────────────────────────────────────────
        if (store.isSimulationMode && store.weatherRenderer != null) {
            store.weatherRenderer.render(batch);
        }

        // ── Визуальные эффекты применения умений (см. SkillCaster/SkillEffectRenderer) ─────────
        if (store.isSimulationMode && store.skillEffectRenderer != null) {
            store.skillEffectRenderer.render(batch);
        }
    }

    @Override
    public void renderUI(SpriteBatch uiBatch) {
        userInterface.render(uiBatch);
        if (store.isSimulationMode && store.playerUI != null) {
            store.playerUI.render(uiBatch);
        }
        if (store.isSimulationMode && store.playerHud != null) {
            store.playerHud.render(uiBatch);
        }
        if (store.isSimulationMode && store.systemUI != null) {
            // Таймаут ожидания ввода в окне привязки умения (см. SystemUI.tick) — опрашивается
            // каждый кадр, не блокирует поток.
            store.systemUI.tick(Gdx.graphics.getDeltaTime());
            store.systemUI.render(uiBatch, store.uiWidthOriginal, store.uiHeightOriginal);
        }
    }

    /**
     * Рисует ОБЪЕКТНЫЙ слой одного тайла (дерево/камень/мостик/здание...) — всегда обычным
     * шейдером SpriteBatch. Земля и вода — строго ПОВЕРХНОСТЬ (см. GROUND_TILE_IDS/drawSurfaceTile
     * ниже), поэтому water/shore-шейдеры сюда не относятся в принципе: объект существует
     * независимо от того, что под ним (дерево может стоять прямо в воде).
     */
    private void drawTile(SpriteBatch batch, MapObject tile) {
        tile.draw(batch);
    }

    /**
     * Рисует ПОВЕРХНОСТЬ одного тайла (земля/вода) — обычным шейдером, либо (для воды) с шейдером
     * искажения поверхности, либо (для берега) с лёгкой анимированной подсветкой кромки (см.
     * ShaderLibrary.water()/shore(), assets/shaders/water.*|shore.frag). Земля и вода строго на
     * уровне ПОВЕРХНОСТИ (см. GROUND_TILE_IDS) — поэтому шейдеры теперь привязаны к
     * drawSurface(), а не к объектному слою (см. drawTile) — иначе они бы никогда не применялись
     * к тайлам, где поверхность и объект совпадают (BaseObject.draw() пропускает отрисовку
     * объектного слоя в этом случае, см. историю правок).
     *
     * ВАЖНО про порядок вызовов: раньше здесь сначала вызывался waterShader.bind() (это НАПРЯМУЮ
     * дёргает glUseProgram, минуя SpriteBatch), и только потом batch.setShader(...). Из-за этого
     * GL-программа переключалась на water-шейдер ДО того, как батч успевал сбросить (flush) уже
     * накопленные в буфере, ещё не отрисованные спрайты обычных тайлов — и они реально уходили на
     * GPU уже ЧЕРЕЗ water-шейдер ("шейдер накладывается на всё подряд"). Правильный порядок —
     * сначала batch.setShader(...) (он сам сбросит накопленное СТАРЫМ шейдером, и только потом
     * привяжет новый), и лишь после этого выставлять юниформы новому шейдеру.
     */
    private void drawSurfaceTile(SpriteBatch batch, MapObject tile) {
        com.badlogic.gdx.graphics.glutils.ShaderProgram waterShader =
            tile.getSurfaceId() == WATER_TEXTURE_ID ? com.nicweiss.editor.utils.ShaderLibrary.water() : null;

        if (waterShader != null) {
            batch.setShader(waterShader); // флашит всё накопленное обычным шейдером, потом бинднт water-шейдер
            // store.cloudTime идёт ТОЛЬКО в режиме симуляции (см. WeatherThread/toggleSimulation) —
            // в редакторе он заморожен на 0, поэтому вода в редакторе намеренно статична, а
            // "оживает" только в симуляции. Так и задумано — не заменять на всегда-идущие часы.
            waterShader.setUniformf("u_time", store.cloudTime);
            // Позиция тайла В СЕТКЕ КАРТЫ (не локальные 0..1 UV спрайта) — что бы фаза волны была
            // связной между соседями и рябь складывалась в один общий узор, а не дёргалась на
            // каждом тайле независимо (см. water.frag).
            waterShader.setUniformf("u_worldPos", tile.xPositionOnMap, tile.yPositionOnMap);

            // Настоящая бесшовная рефракция — цвет воды читается из ОТДЕЛЬНОЙ, реально бесшовной
            // (repeat-wrap) фотографии water_pattern.jpg по мировым координатам, а не из маленького
            // изолированного спрайта тайла (см. ShaderLibrary.waterPattern(), water.frag). Второй
            // текстурный юнит — SpriteBatch сам биндит свою (спрайтовую) текстуру в юнит 0 и этого
            // не знает, поэтому явно возвращаем активный юнит на 0 после бинда паттерна.
            com.badlogic.gdx.graphics.Texture pattern = com.nicweiss.editor.utils.ShaderLibrary.waterPattern();
            if (pattern != null) {
                pattern.bind(1);
                waterShader.setUniformi("u_waterPattern", 1);
                com.badlogic.gdx.Gdx.gl.glActiveTexture(com.badlogic.gdx.graphics.GL20.GL_TEXTURE0);
            }

            tile.drawSurface(batch);
            batch.setShader(null); // флашит водный тайл, потом возвращает обычный шейдер батча
            return;
        }

        com.badlogic.gdx.graphics.glutils.ShaderProgram shoreShader =
            tile.isShore ? com.nicweiss.editor.utils.ShaderLibrary.shore() : null;

        if (shoreShader != null) {
            batch.setShader(shoreShader);
            shoreShader.setUniformf("u_time", store.cloudTime); // как и у воды — идёт только в симуляции
            shoreShader.setUniformf("u_worldPos", tile.xPositionOnMap, tile.yPositionOnMap);
            tile.drawSurface(batch);
            batch.setShader(null);
        } else {
            tile.drawSurface(batch);
        }
    }

    public void renderCreations(SpriteBatch batch, int mapI, int mapJ, boolean filterByHeight) {
        for (Creation creation: store.creations) {
            if (creation != null){
                if (creation.mapCellX != (mapI + store.TILE_INDEX_BASE) || creation.mapCellY != (mapJ + store.TILE_INDEX_BASE) ){
                    continue;
                }
                if (filterByHeight) {
                    MapObject el = store.objectedMap[mapI][mapJ];
                    if (el.getHeight() != 0) { continue; }
                }
                creation.draw(batch);
            }
        }
    }

    public void renderBuildings(SpriteBatch batch, int mapI, int mapJ, boolean filterByHeight) {
        for (int i = 0; i <= store.buildingCount; i++) {
            Creation b = store.buildings[i];
            if (b != null) {
                if (b.mapCellX != (mapI + store.TILE_INDEX_BASE) || b.mapCellY != (mapJ + store.TILE_INDEX_BASE)) { continue; }
                if (filterByHeight) {
                    MapObject el = store.objectedMap[mapI][mapJ];
                    if (el.getHeight() != 0) { continue; }
                }
                b.draw(batch);
            }
        }
    }

    public void renderDrops(SpriteBatch batch, int mapI, int mapJ, boolean filterByHeight) {
        if (!store.isSimulationMode) return; // дропы существуют только в симуляции, редактору не нужны
        for (int i = 0; i <= store.dropCount; i++) {
            com.nicweiss.editor.simulation.Drop drop = store.drops[i];
            if (drop != null) {
                if (drop.mapCellX != (mapI + store.TILE_INDEX_BASE) || drop.mapCellY != (mapJ + store.TILE_INDEX_BASE)) { continue; }
                if (filterByHeight) {
                    MapObject el = store.objectedMap[mapI][mapJ];
                    if (el.getHeight() != 0) { continue; }
                }
                drop.draw(batch);
            }
        }
    }

    // Боевые NPC спавнеров (см. SpawnManager) — только в симуляции, не редактируются, не
    // сохраняются, отдельно от store.creations (статичные NPC редактора).
    public void renderSimCreatures(SpriteBatch batch, int mapI, int mapJ, boolean filterByHeight) {
        if (!store.isSimulationMode) return;
        for (int i = 0; i <= store.simCreatureCount; i++) {
            com.nicweiss.editor.simulation.SimCreature creature = store.simCreatures[i];
            if (creature != null) {
                if (creature.mapCellX != (mapI + store.TILE_INDEX_BASE) || creature.mapCellY != (mapJ + store.TILE_INDEX_BASE)) { continue; }
                if (filterByHeight) {
                    MapObject el = store.objectedMap[mapI][mapJ];
                    if (el.getHeight() != 0) { continue; }
                }
                creature.draw(batch);
            }
        }
    }
}