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

    // Вода (см. Editor.WATER_TEXTURE_ID) и мостик (gp_12.png) — по воде ходить нельзя, кроме как
    // по мостику, стоящему НА ней (объектный слой). Дублируем константы тут (не тянем зависимость
    // на Editor) — по тому же принципу, что и derive-логика старых форматов в FileManager.
    private static final int WATER_TEXTURE_ID  = 10;
    private static final int BRIDGE_TEXTURE_ID = 12;

    // ── Мировая позиция (декартовые пиксели) ──────────────────────────────────
    public volatile float worldX = -1f;
    public volatile float worldY = -1f;

    // ── Характеристики ─────────────────────────────────────────────────────────
    // maxHealth/maxMana — не отдельные статы, а производные: (сила/магия * множитель) + плоский
    // бонус с модификаторов предметов (flatHealthBonus/flatManaBonus, см. "_health"/"_mana" в
    // SystemUI.applyMod). Пересчитываются в SystemUI.recomputePlayerStats() каждый кадр.
    // health/mana — текущие счётчики (то, что тратится на урон/каст и восполняется лечением/
    // реген-тиком), никогда не больше max*.
    public float maxHealth = 100f;
    public float health    = 100f;
    public float maxMana   = 0f;
    public float mana      = 0f;
    // Плоские бонусы к ёмкости с модификаторов предметов (например "armor_health", "helmet_mana") —
    // накапливаются в SystemUI.applyMod, складываются с формулой от силы/магии в recomputePlayerStats.
    public float flatHealthBonus  = 0f;
    public float flatManaBonus    = 0f;

    public float speed     = 1.0f;
    public int level       = 99;
    public int gold        = 0;

    // true у каждого нового Player: при первом пересчёте статов (см. SystemUI.recomputePlayerStats)
    // health/mana выставляются в половину только что посчитанной ёмкости, затем флаг гасится —
    // дальше recompute только клэмпит текущие значения к максимуму, не перезаписывая их.
    public boolean pendingInitialFill = true;

    // ── Опыт ───────────────────────────────────────────────────────────────────
    // experience — прогресс ВНУТРИ текущего уровня (0..experienceToNextLevel()), не общая сумма
    // за игру: при левел-апе остаток переносится на новый уровень (см. addExperience).
    public int experience = 0;

    // Требование к уровню — степенная кривая XP(L) = A*L^B, B=2.0 ("классический стандарт":
    // опыт растёт квадратично, ранние уровни пролетают быстро, эндгейм — ощутимый гринд).
    private static final double LEVEL_XP_A = 100.0;
    private static final double LEVEL_XP_B = 2.0;
    // Награда с врага — ОТДЕЛЬНАЯ, более пологая кривая (B=1.0, линейно по уровню врага).
    // Если считать награду от той же кривой (просто уменьшенной в N раз), "убийств на уровень"
    // остаётся постоянным на любом уровне — неинтересно и нереалистично (ловушка степенной функции,
    // см. обсуждение с пользователем). Разные показатели степени → чем выше уровень, тем больше
    // убийств нужно на левел-ап (на 1 ур. ~5 килов, на 10 ~50, на 40 ~200, на 99 ~500).
    private static final double REWARD_XP_A = 20.0;
    private static final double REWARD_XP_B = 1.0;

    /** Опыт, необходимый для перехода с текущего уровня на следующий. */
    public int experienceToNextLevel() {
        return (int) Math.round(LEVEL_XP_A * Math.pow(level, LEVEL_XP_B));
    }

    /**
     * Опыт за убийство врага уровня enemyLevel с модификатором-множителем (мини-боссы/элиты —
     * тот же уровень, но множитель выше). Растёт медленнее, чем experienceToNextLevel() — см. REWARD_XP_B.
     */
    public static int experienceForKill(int enemyLevel, float multiplier) {
        double reward = REWARD_XP_A * Math.pow(Math.max(1, enemyLevel), REWARD_XP_B) * multiplier;
        return Math.max(1, (int) Math.round(reward));
    }

    /** Начисляет опыт, обрабатывая один или несколько левел-апов подряд (остаток переносится). */
    public void addExperience(int amount) {
        if (amount <= 0) return;
        experience += amount;
        while (experience >= experienceToNextLevel()) {
            experience -= experienceToNextLevel();
            level++;
        }
    }

    // ── Базовые атрибуты (прокачиваются игроком) ─────────────────────────────
    public int baseStrength  = 20;
    public int baseMagic     = 20;
    public int baseDexterity = 20;

    // ── Вычисленные эффективные статы (= base + предметы + чармы) ────────────
    // Пересчитываются PlayerStatEngine.recompute() при любом изменении снаряжения.
    public int strength     = 20;
    public int magic        = 20;
    public int dexterity    = 20;
    public int energy       = 0;

    // Резисты — каждая стихия отдельно
    public int fireRes      = 0;
    public int coldRes      = 0;
    public int lightningRes = 0;

    // Пассивная регенерация здоровья/маны в секунду (от replenish_life / replenish_mana модов).
    public float lifeRegen = 0f;
    public float manaRegen = 0f;

    // Боевые
    public int attackSpeed  = 0;  // IAS %
    public int castSpeed    = 0;  // FCR %
    public int runSpeed     = 0;  // FRW %
    public int attackRating = 0;
    public int physDamage   = 0;
    public int magicDamage  = 0;

    // Защитные
    public int defence           = 0;  // плоская защита
    public int defenceRating     = 0;  // % повышенная защита
    public int physDamageReduce  = 0;  // снижение физического урона
    public int magicDamageReduce = 0;  // снижение магического урона

    // Контейнеры для артефактов (из пояса)
    public int containers   = 0;  // сколько слотов артефактов доступно (0-5)

    // Ёмкость пояса (__mainStat__ надетого пояса, 0/4/8/12/16) — увеличивает вместимость ячеек
    // стека: +beltCapacity/4 к каждой из 4 ячеек стека (см. SystemUI applyMainStat / стеки).
    public int beltCapacity  = 0;

    // Личи
    public int lifeLeech    = 0;
    public int manaLeech    = 0;

    // Поиск
    public float magicFind  = 0f;
    public float goldFind   = 0f;

    // Сила света — от экипированного факела (см. ItemModifierCatalog "torch", SystemUI.applyMainStat).
    // 0 = не экипирован/не светит. Свыше 0 — игрок сам становится динамическим источником света
    // в мире (см. MapObject.calcLitColor / Lighting.computeLitColor) цветом torchGlow*.
    public int lightPower = 0;
    // Скрытый параметр (не показывается в тултипе предмета) — цвет свечения текущего факела,
    // задаётся его редкостью (см. ItemGenerator.applyTorchRarityStats), 0 по умолчанию (нет факела).
    public float torchGlowR = 0f, torchGlowG = 0f, torchGlowB = 0f;

    // Высота игрока как источника света (см. Lighting.isLineOfSightBlocked) — равна высоте костра
    // (Editor.java: TextureObject("gp_11.png", 5), id=11, единственный light-object в редакторе).
    // По требованию пользователя: игрок должен блокироваться так же, как источник света такой
    // высоты (деревья/камни выше — блокируют, трава/невысокие декорации — нет), а НЕ высотой
    // тайла, на котором стоит (иначе даже трава перекрывала бы свет).
    public static final int LIGHT_SOURCE_HEIGHT = 5;

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

    /**
     * Пассивная регенерация здоровья/маны — вызывается из PhysicThread каждый тик.
     * Только от статов восполнения (lifeRegen/manaRegen, см. replenish_life/replenish_mana модов):
     * нет мода — нет восполнения. Скорость — ровно значение стата в очках/сек (не % от максимума).
     */
    public void tickRegen(float dt) {
        if (lifeRegen > 0 && health < maxHealth) {
            health = Math.min(maxHealth, health + lifeRegen * dt);
        }
        if (manaRegen > 0 && mana < maxMana) {
            mana = Math.min(maxMana, mana + manaRegen * dt);
        }
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
            int ai = mi - store.TILE_X_ANCHOR_EXTRA_OFFSET; // см. Store.TILE_X_ANCHOR_EXTRA_OFFSET
            if (ai < 0 || ai >= store.mapHeight) continue;
            for (int mj = Math.max(0, minJ); mj <= Math.min(store.mapWidth - 1, maxJ); mj++) {
                int aj = mj - store.TILE_Y_ANCHOR_EXTRA_OFFSET; // см. Store.TILE_Y_ANCHOR_EXTRA_OFFSET (=0)
                if (aj < 0 || aj >= store.mapWidth) continue;
                com.nicweiss.editor.objects.MapObject tile = store.objectedMap[ai][aj];
                boolean blockedByHeight = tile.objectHeight >= blockHeight;
                boolean blockedByWater  = tile.getSurfaceId() == WATER_TEXTURE_ID && tile.getTextureId() != BRIDGE_TEXTURE_ID;
                if (!blockedByHeight && !blockedByWater) continue;

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
