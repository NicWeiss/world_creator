package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.ItemGenerator;
import com.nicweiss.editor.utils.ItemModifierCatalog;
import com.nicweiss.editor.utils.Transform;
import com.nicweiss.editor.utils.Uuid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * Спавнит лут (предметы и золото) при открытии сундуков и убийстве врагов.
 *
 * Использует {@link ItemGenerator} для ролла предмета: тип/класс, уровень (1..уровень врага),
 * основная характеристика, модификаторы и требования — всё по тем же правилам, что в редакторе.
 * Учитывает характеристики игрока (поиск вещей/золота — Magic Find/Gold Find, см. Player) и
 * уровень врага (определяет верхнюю границу уровня предмета).
 *
 * Заспавненные объекты попадают в store.drops — отдельный список объектов карты, с которыми
 * можно взаимодействовать (показывают подпись при приближении игрока, см. Drop.draw).
 */
public class DropManager {
    public static Store store;

    private static final Random RANDOM = new Random();
    // "Падать можно только на поверхности или на совсем низкие предметы" — порог высоты тайла.
    private static final int MAX_SURFACE_HEIGHT = 3;
    // Дальность разлёта в тайлах: база 1.5, +60% по требованию.
    private static final float MIN_SCATTER_TILES = 0.6f;
    private static final float MAX_SCATTER_TILES = 1.5f * 1.6f;

    private DropManager() {}

    private static final int MAX_ITEMS_PER_DROP = 4;

    /**
     * Главная точка входа: роллит и спавнит лут в точке (tileX, tileY) — индексы store.objectedMap,
     * там, где был открыт сундук или убит враг уровня enemyLevel.
     *
     * Золото и предметы считаются полностью независимо друг от друга:
     *  - золото роллится один раз с источника дропа, сразу всей суммой (своя вероятность и размер);
     *  - количество предметов роллится отдельно, затем КАЖДЫЙ предмет отдельно прогоняется
     *    через ItemGenerator (свои тип/класс/уровень/моды/требования).
     * Уровень врага и характеристики игрока (поиск вещей/золота) влияют и на качество, и на количество.
     */
    public static void dropLoot(int enemyLevel, int tileX, int tileY) {
        if (store.objectedMap == null) return;
        enemyLevel = Math.max(1, enemyLevel);

        rollGold(enemyLevel, tileX, tileY);

        int itemCount = rollItemCount(enemyLevel);
        for (int i = 0; i < itemCount; i++) {
            spawnItemDrop(rollItemTemplate(enemyLevel), tileX, tileY);
        }
    }

    /** Золото: один независимый ролл вероятности и размера на весь источник дропа. */
    private static void rollGold(int enemyLevel, int tileX, int tileY) {
        float goldFind = store.player != null ? store.player.goldFind : 0f;

        if (RANDOM.nextFloat() >= 0.9f) return; // золото выпадает не всегда

        int base = enemyLevel * (3 + RANDOM.nextInt(5));
        int amount = Math.round(base * (1f + goldFind / 100f));
        if (amount > 0) spawnGoldDrop(amount, tileX, tileY);
    }

    /**
     * Сколько предметов выпадет (0..MAX_ITEMS_PER_DROP) — независимо от золота. Каждый
     * следующий предмет роллится со снижающимся шансом; поиск вещей повышает базовый шанс.
     */
    private static int rollItemCount(int enemyLevel) {
        float magicFind = store.player != null ? store.player.magicFind : 0f;
        float chance = Math.min(0.9f, 0.35f + magicFind / 200f);

        int count = 0;
        for (int i = 0; i < MAX_ITEMS_PER_DROP; i++) {
            if (RANDOM.nextFloat() >= chance) break;
            count++;
            chance *= 0.5f; // каждый следующий предмет — вдвое менее вероятен
        }
        return count;
    }

    /** Роллит шаблон предмета целиком через ItemGenerator: тип, класс, уровень, основной показатель, моды, требования. */
    private static LinkedHashMap rollItemTemplate(int enemyLevel) {
        LinkedHashMap template = new LinkedHashMap();
        template.put("__uuid__", Uuid.generate());

        String[] typeKeys = ItemModifierCatalog.TYPES.keySet().toArray(new String[0]);
        String typeKey = typeKeys[RANDOM.nextInt(typeKeys.length)];

        ItemGenerator.applyType(template, typeKey, enemyLevel);
        template.put("__name__", ItemModifierCatalog.TYPES.get(typeKey).label);
        return template;
    }

    // Создание Texture требует активного GL-контекста, который привязан к рендер-потоку.
    // dropLoot может быть вызван из любого потока (фоновый AI/таймер) — поэтому весь спавн,
    // включая загрузку текстуры, откладывается на GL-поток через postRunnable.
    public static void spawnItemDrop(LinkedHashMap itemTemplate, int tileX, int tileY) {
        Gdx.app.postRunnable(() -> {
            Drop drop = createDrop(tileX, tileY);
            if (drop == null) return;

            drop.itemData = itemTemplate;
            drop.setTexture(loadItemTexture(itemTemplate));

            String rarityKey = itemTemplate.containsKey("__rarity__") ? (String) itemTemplate.get("__rarity__") : "common";
            String name = (String) itemTemplate.get("__name__");
            // Чёрный полупрозрачный фон у всех предметов, цвет текста — по редкости.
            drop.setLabel(name != null ? name : "Предмет", ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, rarityTextColor(rarityKey));
        });
    }

    public static void spawnGoldDrop(int amount, int tileX, int tileY) {
        if (amount <= 0) return;
        Gdx.app.postRunnable(() -> {
            Drop drop = createDrop(tileX, tileY);
            if (drop == null) return;

            drop.goldAmount = amount;
            drop.setTexture(goldTexture());
            // Чёрный фон 60% alpha, зелёный текст — как у предметов, но свой цвет.
            drop.setLabel("$ " + amount, ITEM_LABEL_BG, ITEM_LABEL_BG_ALPHA, new float[]{0.2f, 0.9f, 0.2f});
        });
    }

    // ── Подписи: видимость, фокус, раскладка без перекрытия ("кирпичики") ──────

    private static final float ITEM_LABEL_BG_ALPHA = 0.6f;
    private static final float[] ITEM_LABEL_BG = {0f, 0f, 0f};
    private static final float PROXIMITY_RADIUS_FACTOR = 2.5f; // в тайлах — видимость подписи рядом с игроком
    private static final float HOVER_RADIUS_FACTOR = 0.6f;     // в тайлах — радиус наводки мышью
    private static final float LABEL_GAP = 6f;                 // отступ подписи от иконки
    private static final float LABEL_ROW_GAP = 3f;             // отступ между "кирпичиками" подписей

    /**
     * Единый проход по всем дропам: решает, чьи подписи видимы (рядом с игроком, под курсором,
     * сфокусированный, либо все — если зажат Alt/LB), укладывает их без перекрытия и рисует.
     * Вызывать один раз за кадр, после основного цикла отрисовки тайлов/существ.
     */
    // Порог подбора: расстояние в изометрических пикселях, в пределах которого золото подбирается.
    private static final float PICKUP_RADIUS_FACTOR = 1.0f;

    /**
     * Проверяет, наступил ли игрок на кучку золота (isLanded, goldAmount > 0).
     * При наступании: gold → player.gold, дроп убирается из store.drops, сетка надписей
     * пересчитается сама — renderLabels перечитывает store.drops каждый кадр с нуля.
     * Вызывается на GL-потоке из renderLabels, поэтому потокобезопасно.
     */
    private static void checkPickups() {
        if (store.player == null || !store.player.isInitialized()) return;

        float[] iso = Transform.cartesianToIsometric(store.player.worldX, store.player.worldY);
        float playerIsoX = iso[0];
        float playerIsoY = iso[1];
        float pickupRadius = store.tileSizeWidth * PICKUP_RADIUS_FACTOR;

        for (int i = 0; i < store.drops.length; i++) {
            Drop d = store.drops[i];
            if (d == null || !d.isLanded || d.goldAmount <= 0) continue;

            float dx = playerIsoX - d.getWorldIsoX();
            float dy = playerIsoY - d.getWorldIsoY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= pickupRadius) {
                store.player.gold += d.goldAmount;
                store.drops[i] = null;
            }
        }
    }

    public static void renderLabels(SpriteBatch batch) {
        checkPickups();

        List<Drop> visible = new ArrayList<>();
        for (Drop d : store.drops) {
            if (d != null && d.isLanded && d.hasLabel() && isOnScreen(d)) visible.add(d);
        }
        if (visible.isEmpty()) return;

        boolean playerReady = store.player != null && store.player.isInitialized();
        float playerScreenX = 0, playerScreenY = 0;
        if (playerReady) {
            float[] iso = Transform.cartesianToIsometric(store.player.worldX, store.player.worldY);
            playerScreenX = iso[0] + store.shiftX;
            playerScreenY = iso[1] + store.shiftY;
        }

        Drop iconHovered = findHovered(visible);

        boolean revealAll = store.revealAllDropLabels;
        List<Drop> shown = new ArrayList<>();
        for (Drop d : visible) {
            boolean near = playerReady && isWithinProximity(d, playerScreenX, playerScreenY);
            if (revealAll || near || d == iconHovered) shown.add(d);
        }
        if (shown.isEmpty()) return;

        // Идеальная позиция каждой надписи — по центру своего предмета.
        int n = shown.size();
        float[] lx = new float[n], ly = new float[n], lw = new float[n], lh = new float[n];
        for (int i = 0; i < n; i++) {
            Drop d = shown.get(i);
            lw[i] = d.getLabelWidth();
            lh[i] = d.getLabelHeight();
            lx[i] = d.getIconScreenCenterX() - lw[i] / 2f;
            ly[i] = d.getIconScreenCenterY() - lh[i] / 2f;
        }

        // Симметричное попарное расталкивание: каждая пара перекрывающихся надписей
        // разводится в стороны — ОБОИХ двигаем, по оси с наименьшим перекрытием.
        // Надписи расходятся облаком вокруг своих предметов, а не тянутся в одну сторону.
        for (int iter = 0; iter < 40; iter++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (lx[i] >= lx[j] + lw[j] || lx[i] + lw[i] <= lx[j] ||
                        ly[i] >= ly[j] + lh[j] || ly[i] + lh[i] <= ly[j]) continue;

                    float ciX = lx[i] + lw[i] * 0.5f, cjX = lx[j] + lw[j] * 0.5f;
                    float ciY = ly[i] + lh[i] * 0.5f, cjY = ly[j] + lh[j] * 0.5f;
                    float dx = ciX - cjX;
                    float dy = ciY - cjY;

                    // Перекрытие по каждой оси (с запасом LABEL_ROW_GAP)
                    float ovX = (lw[i] + lw[j]) * 0.5f + LABEL_ROW_GAP - Math.abs(dx);
                    float ovY = (lh[i] + lh[j]) * 0.5f + LABEL_ROW_GAP - Math.abs(dy);

                    if (ovX <= ovY) {
                        // Разводим по X: меньший сдвиг
                        float push = ovX * 0.5f;
                        if (dx >= 0) { lx[i] += push; lx[j] -= push; }
                        else          { lx[i] -= push; lx[j] += push; }
                    } else {
                        // Разводим по Y
                        float push = ovY * 0.5f;
                        if (dy >= 0) { ly[i] += push; ly[j] -= push; }
                        else          { ly[i] -= push; ly[j] += push; }
                    }
                    moved = true;
                }
            }
            if (!moved) break;
        }

        // Фокус: сначала — наведение мышью на прямоугольник надписи,
        // потом — наведение на иконку, потом — ближайший к игроку.
        // Используем playerPositionX/Y — viewport-space координаты курсора (из unproject),
        // они совпадают с пространством лейблов (isoX + shiftX), в отличие от mouseX/Y (UI-пиксели).
        float curX = store.playerPositionX;
        float curY = store.playerPositionY;
        Drop hoverCandidate = null;
        for (int i = 0; i < n; i++) {
            if (curX >= lx[i] && curX <= lx[i] + lw[i] &&
                curY >= ly[i] && curY <= ly[i] + lh[i]) {
                hoverCandidate = shown.get(i);
                break;
            }
        }
        if (hoverCandidate == null) hoverCandidate = iconHovered != null && shown.contains(iconHovered) ? iconHovered : null;

        Drop focus = hoverCandidate;
        if (focus == null && playerReady) {
            float best = Float.MAX_VALUE;
            for (Drop d : shown) {
                float dx = playerScreenX - d.getIconScreenCenterX();
                float dy = playerScreenY - d.getIconScreenCenterY();
                float dist = dx * dx + dy * dy;
                if (dist < best) { best = dist; focus = d; }
            }
        }

        // hoveredDrop — строго наведение курсором (без фолбэка на "ближайший к игроку"),
        // именно его подбираем по клику/кнопке A. focusedDrop — для подсветки (с фолбэком).
        hoveredDrop = hoverCandidate;
        focusedDrop = focus;

        for (int i = 0; i < n; i++) {
            shown.get(i).drawLabelAt(batch, lx[i], ly[i], shown.get(i) == focus);
        }
    }

    // ── Подбор предмета в инвентарь ─────────────────────────────────────────────

    // Дроп, чья подпись сейчас подсвечена (с фолбэком на ближайшего к игроку) — для рендера.
    public static Drop focusedDrop = null;
    // Дроп строго под курсором (лейбл или иконка), без фолбэка — именно его подбираем по клику/A.
    public static Drop hoveredDrop = null;

    private static final int INV_COLS = 10;
    private static final int INV_ROWS = 5;

    /** Кнопка A геймпада: подбирает предмет в фокусе (с фолбэком на ближайший к игроку — нет курсора). */
    public static void tryPickupFocused() {
        tryPickup(focusedDrop);
    }

    /** ЛКМ: подбирает предмет, только если курсор реально наведён на его лейбл/иконку. */
    public static void tryPickupHovered() {
        tryPickup(hoveredDrop);
    }

    /**
     * Золото подбирается само наступанием (см. checkPickups), сюда попадают только предметы.
     * Ищет в инвентаре свободный прямоугольник под размер предмета, сканируя от краёв к центру
     * (сверху-слева построчно). Если место есть — занимает ячейки и убирает дроп с земли;
     * если нет — дроп просто подпрыгивает на месте (см. Drop.bounce).
     */
    private static void tryPickup(Drop d) {
        if (d == null || !d.isLanded || d.itemData == null) return;

        int w = d.itemData.containsKey("__width__") ? (int) d.itemData.get("__width__") : 1;
        int h = d.itemData.containsKey("__height__") ? (int) d.itemData.get("__height__") : 1;

        int[] slot = findInventorySlot(w, h);
        if (slot == null) {
            d.bounce();
            return;
        }

        for (int c = slot[0]; c < slot[0] + w; c++) {
            for (int r = slot[1]; r < slot[1] + h; r++) {
                store.inventoryGrid[c][r] = true;
            }
        }
        d.itemData.put("__inv_x__", slot[0]);
        d.itemData.put("__inv_y__", slot[1]);
        String uuid = (String) d.itemData.get("__uuid__");
        store.inventory.put(uuid != null ? uuid : com.nicweiss.editor.utils.Uuid.generate(), d.itemData);

        for (int i = 0; i < store.drops.length; i++) {
            if (store.drops[i] == d) { store.drops[i] = null; break; }
        }
        focusedDrop = null;
    }

    /** Ищет первый свободный прямоугольник w×h, сканируя построчно сверху-слева — от краёв к центру. */
    private static int[] findInventorySlot(int w, int h) {
        for (int r = 0; r <= INV_ROWS - h; r++) {
            for (int c = 0; c <= INV_COLS - w; c++) {
                if (slotFree(c, r, w, h)) return new int[]{c, r};
            }
        }
        return null;
    }

    private static boolean slotFree(int col, int row, int w, int h) {
        for (int c = col; c < col + w; c++) {
            for (int r = row; r < row + h; r++) {
                if (store.inventoryGrid[c][r]) return false;
            }
        }
        return true;
    }

    private static Drop findHovered(List<Drop> candidates) {
        // playerPositionX/Y — viewport-space координаты курсора (unproject от Main.viewport),
        // совпадают с пространством иконок (isoX + shiftX). mouseX/Y — UI-пиксели, не подходят.
        float curX = store.playerPositionX;
        float curY = store.playerPositionY;
        Drop best = null;
        float bestDist = Float.MAX_VALUE;
        for (Drop d : candidates) {
            float dx = curX - d.getIconScreenCenterX();
            float dy = curY - d.getIconScreenCenterY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float hoverRadius = Math.max(d.getIconFootprint(), store.tileSizeWidth) * HOVER_RADIUS_FACTOR;
            if (dist <= hoverRadius && dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    private static boolean isWithinProximity(Drop d, float playerScreenX, float playerScreenY) {
        float dx = playerScreenX - d.getIconScreenCenterX();
        float dy = playerScreenY - d.getIconScreenCenterY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist <= store.tileSizeWidth * PROXIMITY_RADIUS_FACTOR;
    }

    private static boolean isOnScreen(Drop d) {
        Float dispW = store.display.get("width");
        Float dispH = store.display.get("height");
        if (dispW == null || dispH == null) return true;

        float cx = d.getIconScreenCenterX();
        float cy = d.getIconScreenCenterY();
        float margin = store.tileSizeWidth * 3f;
        return cx > -margin && cx < dispW + margin && cy > -margin && cy < dispH + margin;
    }

    /** Выбрасывает предмет из инвентаря на землю рядом с игроком. */
    public static void spawnDropAtPlayer(LinkedHashMap itemData) {
        if (store.player == null || store.tileSizeWidth == 0) return;
        int tileX = (int)(store.player.worldX / store.tileSizeWidth);
        int tileY = (int)(store.player.worldY / store.tileSizeHeight);
        spawnItemDrop(itemData, tileX, tileY);
    }

    /** Продвигает анимации всех активных дропов на dt секунд. Вызывается из CreationThread. */
    public static void update(float dt) {
        for (Drop d : store.drops) {
            if (d != null && !d.isLanded) {
                d.updateThrow(dt);
            }
        }
    }

    // ── Внутреннее ────────────────────────────────────────────────────────────

    private static Drop createDrop(int originTileX, int originTileY) {
        int slot = nextFreeSlot();
        if (slot < 0) return null;

        int[] target = pickScatterTile(originTileX, originTileY);

        // Якорь позиции — тот же, что у тайлов (MapObject.calcPosition) и существ (Creation):
        // cellIndex_1based * tileSize, без дополнительного центрирования.
        float[] startIso = Transform.cartesianToIsometric(
            (originTileX + 1) * store.tileSizeWidth,
            (originTileY + 1) * store.tileSizeHeight
        );
        float[] endIso = Transform.cartesianToIsometric(
            (target[0] + 1) * store.tileSizeWidth,
            (target[1] + 1) * store.tileSizeHeight
        );

        Drop drop = new Drop();
        drop.mapCellX = target[0] + 1;
        drop.mapCellY = target[1] + 1;
        drop.initThrow(startIso[0], startIso[1], endIso[0], endIso[1]);

        store.drops[slot] = drop;
        if (slot > store.dropCount) store.dropCount = slot;
        return drop;
    }

    private static int nextFreeSlot() {
        for (int i = 0; i < store.drops.length; i++) {
            if (store.drops[i] == null) return i;
        }
        return -1;
    }

    /** Роллит случайное направление и дальность разлёта от точки спавна, ищет подходящий тайл рядом. */
    private static int[] pickScatterTile(int originX, int originY) {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        float dist = MIN_SCATTER_TILES + RANDOM.nextFloat() * (MAX_SCATTER_TILES - MIN_SCATTER_TILES);
        int candidateX = originX + Math.round((float) Math.cos(angle) * dist);
        int candidateY = originY + Math.round((float) Math.sin(angle) * dist);

        if (isLandable(candidateX, candidateY)) return new int[]{candidateX, candidateY};
        return findLandingTile(candidateX, candidateY);
    }

    /** Ищет ближайший подходящий для падения тайл: только поверхности/совсем низкие объекты. */
    private static int[] findLandingTile(int originX, int originY) {
        if (isLandable(originX, originY)) return new int[]{originX, originY};

        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int tx = originX + dx, ty = originY + dy;
                    if (isLandable(tx, ty)) return new int[]{tx, ty};
                }
            }
        }
        return new int[]{originX, originY};
    }

    private static boolean isLandable(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= store.mapHeight || ty >= store.mapWidth) return false;
        return store.objectedMap[tx][ty].getHeight() < MAX_SURFACE_HEIGHT;
    }

    private static Texture loadItemTexture(LinkedHashMap itemTemplate) {
        String imagePath = (String) itemTemplate.get("__image__");
        if (imagePath != null) {
            try {
                return new Texture(Gdx.files.absolute(imagePath));
            } catch (Exception ignored) {}
        }
        return fallbackTexture();
    }

    private static Texture fallback;
    private static Texture fallbackTexture() {
        if (fallback == null) fallback = new Texture("items_button.png");
        return fallback;
    }

    private static Texture goldTexture;
    private static Texture goldTexture() {
        if (goldTexture == null) goldTexture = buildGoldTexture();
        return goldTexture;
    }

    /** Простая процедурная текстура монеты — до появления готового арта (см. Player.buildTexture). */
    private static Texture buildGoldTexture() {
        int s = 32;
        Pixmap pmap = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pmap.setColor(0, 0, 0, 0);
        pmap.fill();
        pmap.setColor(0.95f, 0.78f, 0.15f, 1f);
        pmap.fillCircle(s / 2, s / 2, s / 2 - 2);
        pmap.setColor(0.6f, 0.45f, 0.05f, 1f);
        pmap.drawCircle(s / 2, s / 2, s / 2 - 2);
        Texture t = new Texture(pmap);
        pmap.dispose();
        return t;
    }

    // Фон у всех предметов одинаковый (чёрный, alpha 60%) — редкость теперь различается цветом текста.
    private static float[] rarityTextColor(String rarityKey) {
        switch (rarityKey) {
            case "rare":   return new float[]{1f, 0.97f, 0.1f};        // лимонно-жёлтый
            case "unique": return new float[]{1f, 0.55f, 0.08f};     // мандариново-оранжевый
            default:       return new float[]{1f, 1f, 1f};          // белый текст
        }
    }
}
