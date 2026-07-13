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
    TextureObject[] textures;

    Light light;
    UserInterface userInterface;
    Transform transform;

    int tileSizeX, tileSizeY;
    int selectedTileX, selectedTileY;
    int mouseX, mouseY;
    float cm = 0.01f;
    int[] lightObjectIds, surfacesIds;
    boolean isImmediatelyReleaseKey = false;
    boolean isUiTouched = false;

    public Editor(){
        lightObjectIds = new int[] {11};
        surfacesIds = new int [] {1, 10};

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

//        light.recalcOnMap();
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
     * Превращает тайл в воду заданной глубины (objectHeight, ОТРИЦАТЕЛЬНАЯ). Если тайл уже вода
     * от другого прохода (река/озеро) — дно НЕ поднимается, остаётся более глубокое из значений.
     * isRiver — чисто визуальный флаг для water-шейдера (течение у реки vs волны у озера/пруда,
     * см. Editor.drawTile), на геймплей не влияет.
     */
    private void writeWaterTile(int i, int j, int depth, boolean isRiver) {
        MapObject tile = store.objectedMap[i][j];
        tile.isRiverWater = isRiver;

        if (tile.getTextureId() == WATER_TEXTURE_ID) {
            tile.setObjectHeight(Math.min(tile.objectHeight, depth));
            return;
        }

        tile.setSurfaceTexture(textures[1].texture);
        tile.setSurfaceId(1);
        tile.setTexture(textures[WATER_TEXTURE_ID].texture);
        tile.setTextureId(WATER_TEXTURE_ID);
        tile.isTree = false; // вода не качается ветром, даже если тут раньше стояло дерево
        tile.setObjectHeight(depth);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        storeKey(button);
        lastTouchedButton = button == -1 ? lastTouchedButton : button;

        mouseMoved(screenX,screenY);
        int arrPointX = selectedTileX-1;
        int arrPointY = selectedTileY-1;


        if (store.isSimulationMode && store.simulationInput != null && button == 0) {
            if (store.simulationInput.touchDown(store.mouseX, store.mouseY)) {
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
//                Очистка света
            int previousTextureId = store.objectedMap[arrPointX][arrPointY].getTextureId();
            int newTextureId = store.selectedTailId;

            if (!ArrayUtils.checkIntInArray(newTextureId, lightObjectIds) && ArrayUtils.checkIntInArray(previousTextureId, lightObjectIds)) {
                light.removePoint(arrPointX, arrPointY);
                light.recalcOnMapFromPoint(arrPointX, arrPointY);
            }

//                Обновление элемента карты
            store.objectedMap[arrPointX][arrPointY].setTexture(textures[newTextureId].texture);
            store.objectedMap[arrPointX][arrPointY].setTextureId(newTextureId);

            if (ArrayUtils.checkIntInArray(newTextureId, surfacesIds)){
                store.objectedMap[arrPointX][arrPointY].setSurfaceTexture(textures[newTextureId].texture);
                store.objectedMap[arrPointX][arrPointY].setSurfaceId(newTextureId);
            }

            store.objectedMap[arrPointX][arrPointY].setObjectHeight(textures[newTextureId].high);
            store.objectedMap[arrPointX][arrPointY].isTree = textures[newTextureId].isTree;
            store.objectedMap[arrPointX][arrPointY].setWidth(textures[newTextureId].texture.getWidth() / store.tileDownScale);
            store.objectedMap[arrPointX][arrPointY].setHeight(textures[newTextureId].texture.getHeight() / store.tileDownScale);

//                Уствновка света
            if (ArrayUtils.checkIntInArray(newTextureId, lightObjectIds)) {
                light.addPoint(arrPointX, arrPointY);
            }

            light.recalcOnMapFromPoint(arrPointX, arrPointY);
        }

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        releaseKey(button);
        isUiTouched = userInterface.checkTouch(isDragged, true, button);
        return super.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        store.isGamepadMode = false;
        store.mouseX = mouseX = screenX;
        store.mouseY = mouseY = (int) store.uiHeightOriginal - screenY;

        if (!userInterface.getMouseMoveBlockStatus()){
            calcPositionCursor();
        }

        userInterface.onMouseMoved();

        return false;
    }

    public void calcPositionCursor(){
        Vector3 v = Main.viewport.getCamera().unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        float mouseInViewportX = v.x - store.shiftX - (float)(10 / store.tileDownScale) ;
        float mouseInViewportY = v.y - store.shiftY + (float)(60 / store.tileDownScale);
        float[] dotPoint = transform.isometricToCartesian(mouseInViewportX, mouseInViewportY);
        store.playerPositionX = v.x ;
        store.playerPositionY = v.y;

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

                store.objectedMap[mapI][mapJ].drawSurface(batch);
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

//                Рисуем карту
                    drawTile(batch, store.objectedMap[mapI][mapJ]);
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
            store.systemUI.render(uiBatch, store.uiWidthOriginal, store.uiHeightOriginal);
        }
    }

    /**
     * Рисует один тайл карты — обычным шейдером SpriteBatch, либо (для воды) с шейдером искажения
     * поверхности (см. ShaderLibrary.water(), assets/shaders/water.*).
     *
     * ВАЖНО про порядок вызовов: раньше здесь сначала вызывался waterShader.bind() (это НАПРЯМУЮ
     * дёргает glUseProgram, минуя SpriteBatch), и только потом batch.setShader(...). Из-за этого
     * GL-программа переключалась на water-шейдер ДО того, как батч успевал сбросить (flush) уже
     * накопленные в буфере, ещё не отрисованные спрайты обычных тайлов/подложек/деревьев — и они
     * реально уходили на GPU уже ЧЕРЕЗ water-шейдер ("шейдер накладывается на всё подряд", хотя
     * код и выглядел так, будто применяется только к воде). Правильный порядок — сначала
     * batch.setShader(...) (он сам сбросит накопленное СТАРЫМ шейдером, и только потом привяжет
     * новый), и лишь после этого выставлять юниформы новому шейдеру.
     */
    private void drawTile(SpriteBatch batch, MapObject tile) {
        com.badlogic.gdx.graphics.glutils.ShaderProgram waterShader =
            tile.getTextureId() == WATER_TEXTURE_ID ? com.nicweiss.editor.utils.ShaderLibrary.water() : null;

        if (waterShader != null) {
            batch.setShader(waterShader); // флашит всё накопленное обычным шейдером, потом бинднт water-шейдер
            // store.cloudTime идёт ТОЛЬКО в режиме симуляции (см. WeatherThread/toggleSimulation) —
            // в редакторе он заморожен на 0, поэтому вода в редакторе намеренно статична, а
            // "оживает" только в симуляции. Так и задумано — не заменять на всегда-идущие часы.
            waterShader.setUniformf("u_time", store.cloudTime);
            // Позиция тайла В СЕТКЕ КАРТЫ (не локальные 0..1 UV спрайта) — что бы фаза волны была
            // связной между соседями и рябь складывалась в один общий узор, а не дёргалась на
            // каждом тайле независимо (см. water.frag). БЕЗ модуля на этот раз (в прошлый раз
            // модуль по 64 давал периодическую решётку) — сейчас безопасность края текстуры
            // обеспечивает alpha-фолбэк в шейдере, а не ограничение диапазона координаты.
            waterShader.setUniformf("u_worldPos", tile.xPositionOnMap, tile.yPositionOnMap);
            tile.draw(batch);
            batch.setShader(null); // флашит водный тайл, потом возвращает обычный шейдер батча
        } else {
            tile.draw(batch);
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