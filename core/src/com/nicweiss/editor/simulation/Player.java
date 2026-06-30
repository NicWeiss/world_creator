package com.nicweiss.editor.simulation;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;

/**
 * Игрок в режиме симуляции.
 *
 * Позиция хранится в декартовых мировых координатах (worldX, worldY).
 * PhysicThread обновляет позицию через moveBy().
 * draw() всегда рисует игрока в центре экрана — карта движется вокруг него.
 */
public class Player extends BaseObject {

    // ── Мировая позиция (декартовые пиксели) ──────────────────────────────────
    public volatile float worldX = -1f;
    public volatile float worldY = -1f;

    // ── Характеристики ─────────────────────────────────────────────────────────
    public float maxHealth = 100f;
    public float health    = 100f;
    public float speed     = 1.0f;   // множитель скорости (1 = стандарт)
    public int level       = 1;
    // Поиск предметов/золота — проценты сверх базы (Magic Find / Gold Find), влияют на дроп (см. DropManager).
    public float magicFind = 0f;
    public float goldFind  = 0f;
    public int gold = 0; // накопленное золото — подбирается при наступании на кучку (см. DropManager.checkPickups)

    // ── Анимация ───────────────────────────────────────────────────────────────
    public Direction direction = Direction.DOWN;
    private float velX = 0f, velY = 0f; // последний ненулевой вектор движения

    public enum Direction { UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT }

    // ── Размер спрайта (доля тайла) ────────────────────────────────────────────
    private static final float SIZE_FACTOR = 0.30f * 2.4f; // radius * 2.4

    public Player() {
        buildTexture();
    }

    // ── Внешний интерфейс для PhysicThread ─────────────────────────────────────

    public boolean isInitialized() {
        return worldX >= 0;
    }

    /** Инициализирует позицию игрока в текущем центре камеры. */
    public void initAtCameraCenter() {
        float isoX = store.display.get("width")  / 2f - store.shiftX;
        float isoY = store.display.get("height") / 2f - store.shiftY;
        // isometricToCartesian: cartX = (isoX + 2*isoY)/2, cartY = (2*isoY - isoX)/2
        worldX = (isoX + 2f * isoY) / 2f;
        worldY = (2f * isoY - isoX) / 2f;
    }

    /**
     * Пробует сдвинуть игрока на (dWorldX, dWorldY) с учётом коллизий.
     * Использует скольжение по осям для плавного огибания углов.
     *
     * @param dWorldX декартовый сдвиг X
     * @param dWorldY декартовый сдвиг Y
     * @param blockHeightThreshold минимальная высота блокирующего тайла
     */
    public void moveBy(float dWorldX, float dWorldY, int blockHeightThreshold) {
        float r  = store.tileSizeWidth * 0.30f;
        float px = worldX;
        float py = worldY;
        float nx = px + dWorldX;
        float ny = py + dWorldY;

        if (!isCollidingAt(nx, ny, r, blockHeightThreshold)) {
            worldX = nx;
            worldY = ny;
        } else if (!isCollidingAt(nx, py, r, blockHeightThreshold)) {
            worldX = nx;
        } else if (!isCollidingAt(px, ny, r, blockHeightThreshold)) {
            worldY = ny;
        }

        // Обновляем направление для анимации по вектору движения
        float movedX = worldX - px;
        float movedY = worldY - py;
        if (Math.abs(movedX) > 0.01f || Math.abs(movedY) > 0.01f) {
            velX = movedX;
            velY = movedY;
            updateDirection();
        }
    }

    // ── Рендер ─────────────────────────────────────────────────────────────────

    /**
     * Рисует игрока в центре экрана.
     * Вызывается GL-потоком после рендера карты.
     */
    public void draw(SpriteBatch batch) {
        if (!isInitialized() || img == null) return;
        float pw = store.tileSizeWidth  * SIZE_FACTOR;
        float ph = store.tileSizeHeight * SIZE_FACTOR;
        x = store.display.get("width")  / 2f - pw / 2f;
        y = store.display.get("height") / 2f - ph / 2f;
        width  = (int) pw;
        height = (int) ph;
        batch.setColor(1, 1, 1, 1);
        super.draw(batch);
    }

    // ── Приватные методы ───────────────────────────────────────────────────────

    private void updateDirection() {
        // Конвертируем декартовый вектор движения → экранное направление
        // isoX = cartX - cartY, isoY = (cartX + cartY)/2
        float screenDX = velX - velY;
        float screenDY = (velX + velY) / 2f;

        boolean up    = screenDY >  0.1f;
        boolean down  = screenDY < -0.1f;
        boolean right = screenDX >  0.1f;
        boolean left  = screenDX < -0.1f;

        if (up    && right) direction = Direction.UP_RIGHT;
        else if (up   && left)  direction = Direction.UP_LEFT;
        else if (down && right) direction = Direction.DOWN_RIGHT;
        else if (down && left)  direction = Direction.DOWN_LEFT;
        else if (up)            direction = Direction.UP;
        else if (down)          direction = Direction.DOWN;
        else if (right)         direction = Direction.RIGHT;
        else if (left)          direction = Direction.LEFT;
    }

    /**
     * Тест круг vs AABB тайла.
     * Тайл [mi][mj] в декартовом пространстве:
     *   x: [(mi+1)*tileW, (mi+2)*tileW],  y: [(mj+1)*tileH, (mj+2)*tileH]
     */
    private boolean isCollidingAt(float px, float py, float r, int blockHeight) {
        if (store.objectedMap == null) return false;
        float tileW = store.tileSizeWidth;
        float tileH = store.tileSizeHeight;

        // mi занимает [(mi+1)*tileW, (mi+2)*tileW], поэтому:
        // mi_max = floor((px+r)/tileW)-1,  mi_min = floor((px-r)/tileW)-2
        int minI = (int) Math.floor((px - r) / tileW) - 2;
        int maxI = (int) Math.floor((px + r) / tileW) - 1;
        int minJ = (int) Math.floor((py - r) / tileH) - 2;
        int maxJ = (int) Math.floor((py + r) / tileH) - 1;

        for (int mi = Math.max(0, minI); mi <= Math.min(store.mapHeight - 1, maxI); mi++) {
            int ai = mi - 1; // эмпирически найденный корректный индекс массива для данной геометрии тайлов
            if (ai < 0 || ai >= store.mapHeight) continue;
            for (int mj = Math.max(0, minJ); mj <= Math.min(store.mapWidth - 1, maxJ); mj++) {
                if (store.objectedMap[ai][mj].objectHeight < blockHeight) continue;

                float tx1 = (mi + 1) * tileW;
                float tx2 = (mi + 2) * tileW;
                float ty1 = (mj + 1) * tileH;
                float ty2 = (mj + 2) * tileH;

                float cx = Math.max(tx1, Math.min(px, tx2));
                float cy = Math.max(ty1, Math.min(py, ty2));
                float dx = px - cx;
                float dy = py - cy;
                if (dx * dx + dy * dy < r * r) return true;
            }
        }
        return false;
    }

    /** Генерирует текстуру игрока процедурно (до появления спрайтов). */
    private void buildTexture() {
        int ps = 32;
        Pixmap pmap = new Pixmap(ps, ps, Pixmap.Format.RGBA8888);
        pmap.setColor(0, 0, 0, 0);
        pmap.fill();
        pmap.setColor(0.2f, 0.6f, 1f, 1f);
        pmap.fillCircle(ps / 2, ps / 2, ps / 2 - 2);
        pmap.setColor(1f, 1f, 1f, 1f);
        pmap.fillCircle(ps / 2, ps / 2, ps / 4);
        img = new Texture(pmap);
        pmap.dispose();
    }
}
