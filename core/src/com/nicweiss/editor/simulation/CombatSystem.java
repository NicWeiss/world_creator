package com.nicweiss.editor.simulation;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.simulation.effects.DamageNumberEffect;
import com.nicweiss.editor.simulation.effects.FxContext;
import com.nicweiss.editor.utils.NpcCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Единая точка боевой логики: поиск целей и нанесение урона по Store.simCreatures. Единственное
 * место в коде, которое пишет в SimCreature.health — это то, что делает "урон по союзникам
 * невозможен" СТРУКТУРНОЙ гарантией (см. applyDamage), а не соглашением, которое каждый вызывающий
 * код обязан не забыть проверить.
 *
 * Как и у SkillCaster/SpawnManager/FxContext — static Store store НЕ требует явного присваивания
 * (Store.player/simCreatures и т.п. объявлены static на самом Store, поэтому store.xxx компилируется
 * в статический доступ независимо от значения переменной store — грепом по всему проекту такого
 * присваивания нигде нет, и оно не нужно).
 */
public final class CombatSystem {
    public static Store store;

    private CombatSystem() {}

    // ── Поиск целей ──────────────────────────────────────────────────────────────────────────

    private static boolean isDamageable(SimCreature c) {
        return c != null && c.isAlive()
            && (c.faction == NpcCatalog.Faction.ENEMY || c.faction == NpcCatalog.Faction.MONSTER);
    }

    /** Ближайшее к КУРСОРУ (не к игроку) поражаемое существо в радиусе maxRange — для точечных
     *  умений (Удар, Огненный Шар, Ледяной Шип, первый хоп Цепной молнии). */
    public static SimCreature findNearestToCursor(float maxRange) {
        float cx = store.cursorWorldX, cy = store.cursorWorldY;
        SimCreature best = null;
        float bestDistSq = maxRange * maxRange;
        for (int i = 0; i <= store.simCreatureCount; i++) {
            SimCreature c = store.simCreatures[i];
            if (!isDamageable(c)) continue;
            float dx = c.worldX - cx, dy = c.worldY - cy;
            float distSq = dx * dx + dy * dy;
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = c;
            }
        }
        return best;
    }

    /** Ближайшее к КУРСОРУ поражаемое существо, но ТОЛЬКО среди тех, кто реально в пределах
     *  reachRadius от anchor (позиция игрока) — для ближнего боя: "анимация должна касаться
     *  противника" (проверка дотягивания от игрока) И "одновременно выбирается тот, на кого
     *  указывает курсор" (среди дотягиваемых — самый близкий к прицелу). Если у курсора несколько
     *  целей в равной зоне охвата, но ближе к игроку есть только часть из них — берём только тех,
     *  до кого атака физически дотягивается. */
    public static SimCreature findNearestToCursorWithinReach(float anchorX, float anchorY, float reachRadius) {
        SimCreature best = null;
        float bestCursorDistSq = Float.MAX_VALUE;
        float reachSq = reachRadius * reachRadius;
        float cx = store.cursorWorldX, cy = store.cursorWorldY;
        for (int i = 0; i <= store.simCreatureCount; i++) {
            SimCreature c = store.simCreatures[i];
            if (!isDamageable(c)) continue;
            float rdx = c.worldX - anchorX, rdy = c.worldY - anchorY;
            if (rdx * rdx + rdy * rdy > reachSq) continue; // анимация до него не дотягивается
            float cdx = c.worldX - cx, cdy = c.worldY - cy;
            float cursorDistSq = cdx * cdx + cdy * cdy;
            if (cursorDistSq < bestCursorDistSq) {
                bestCursorDistSq = cursorDistSq;
                best = c;
            }
        }
        return best;
    }

    /** Все поражаемые существа в радиусе от точки — для круговых АоЕ (ДУМ/Туман/Гроза/Электро-Щит). */
    public static List<SimCreature> findAllInRadius(float wx, float wy, float radius) {
        List<SimCreature> result = new ArrayList<>();
        float radiusSq = radius * radius;
        for (int i = 0; i <= store.simCreatureCount; i++) {
            SimCreature c = store.simCreatures[i];
            if (!isDamageable(c)) continue;
            float dx = c.worldX - wx, dy = c.worldY - wy;
            if (dx * dx + dy * dy <= radiusSq) result.add(c);
        }
        return result;
    }

    /** Все поражаемые существа на отрезке (с допуском halfWidth) — для Теневого клинка/Рывка
     *  клинка (проверка коллизии по пройденному пути). */
    public static List<SimCreature> findAllOnLine(float x1, float y1, float x2, float y2, float halfWidth) {
        List<SimCreature> result = new ArrayList<>();
        float dx = x2 - x1, dy = y2 - y1;
        float lenSq = dx * dx + dy * dy;
        for (int i = 0; i <= store.simCreatureCount; i++) {
            SimCreature c = store.simCreatures[i];
            if (!isDamageable(c)) continue;
            float t = lenSq > 0.0001f ? ((c.worldX - x1) * dx + (c.worldY - y1) * dy) / lenSq : 0f;
            t = Math.max(0f, Math.min(1f, t));
            float px = x1 + dx * t, py = y1 + dy * t;
            float ddx = c.worldX - px, ddy = c.worldY - py;
            if (ddx * ddx + ddy * ddy <= halfWidth * halfWidth) result.add(c);
        }
        return result;
    }

    /** Все поражаемые существа в секторе (конусе) — для Волны Огня. dirDeg/angleDeg в градусах,
     *  углы стандартные (0°=вправо, против часовой), см. atan2-конвенцию остальных эффектов. */
    public static List<SimCreature> findAllInCone(float originWX, float originWY, float dirDeg, float angleDeg, float range) {
        List<SimCreature> result = new ArrayList<>();
        double halfRad = Math.toRadians(angleDeg / 2.0);
        double dirRad = Math.toRadians(dirDeg);
        for (int i = 0; i <= store.simCreatureCount; i++) {
            SimCreature c = store.simCreatures[i];
            if (!isDamageable(c)) continue;
            float dx = c.worldX - originWX, dy = c.worldY - originWY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > range || dist < 0.001f) continue;
            double angleTo = Math.atan2(dy, dx);
            double diff = Math.abs(normalizeAngle(angleTo - dirRad));
            if (diff <= halfRad) result.add(c);
        }
        return result;
    }

    private static double normalizeAngle(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    /** Ближайшее ЕЩЁ НЕ задетое существо от точки — для последовательных перескоков Цепной молнии
     *  (каждый вызов заново фильтрует isAlive(), так что цель, умершая от предыдущего хопа,
     *  автоматически выпадает из кандидатов). */
    public static SimCreature findNearestUnhit(float fromWX, float fromWY, Set<SimCreature> alreadyHit, float jumpRange) {
        SimCreature best = null;
        float bestDistSq = jumpRange * jumpRange;
        for (int i = 0; i <= store.simCreatureCount; i++) {
            SimCreature c = store.simCreatures[i];
            if (!isDamageable(c) || alreadyHit.contains(c)) continue;
            float dx = c.worldX - fromWX, dy = c.worldY - fromWY;
            float distSq = dx * dx + dy * dy;
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = c;
            }
        }
        return best;
    }

    // ── Урон ─────────────────────────────────────────────────────────────────────────────────

    /** Единственная точка нанесения урона. По союзникам урон структурно невозможен — жёсткий
     *  guard здесь работает даже если вызывающий код где-то ошибётся с фильтрацией. */
    public static void applyDamage(SimCreature target, double amount) {
        if (target == null || !target.isAlive() || target.faction == NpcCatalog.Faction.ALLY) return;

        target.health = Math.max(0, target.health - (int) Math.round(amount));
        if (store.skillEffectRenderer != null) {
            store.skillEffectRenderer.spawn(new DamageNumberEffect(target.worldX, target.worldY, (int) Math.round(amount)));
        }
        if (target.health <= 0) onDeath(target);
    }

    /** Небоевой дебафф (замедление Ледяного Шипа) — не проходит через applyDamage, здоровье не трогает. */
    public static void applySlow(SimCreature target, float pct, float durationSec) {
        if (target == null || !target.isAlive() || target.faction == NpcCatalog.Faction.ALLY) return;
        target.activeDebuffs.put("slow_pct:" + pct, durationSec);
    }

    /** Накопительный дебафф (Хрупкость) — каждый стак добавляет shredPerStackPct к общему срезу
     *  защиты цели, до потолка shredPerStackPct*maxStacks; длительность обновляется на durationSec
     *  при каждом новом стаке. */
    public static void applyFragilityStack(SimCreature target, float shredPerStackPct, int maxStacks, float durationSec) {
        if (target == null || !target.isAlive() || target.faction == NpcCatalog.Faction.ALLY) return;
        String key = "fragility_shred_pct";
        float current = target.activeDebuffs.getOrDefault(key, 0f);
        float cap = shredPerStackPct * maxStacks;
        target.activeDebuffs.put(key, Math.min(cap, current + shredPerStackPct));
        target.activeDebuffs.put(key + "_duration", durationSec);
    }

    private static void onDeath(SimCreature target) {
        int tileX = target.mapCellX - store.TILE_INDEX_BASE;
        int tileY = target.mapCellY - store.TILE_INDEX_BASE;
        DropManager.dropLoot(target.level, tileX, tileY);
        DropManager.dropExperience(target.level, 1f, tileX, tileY);
        if (target.slotIndex >= 0 && target.slotIndex < store.simCreatures.length
            && store.simCreatures[target.slotIndex] == target) {
            store.simCreatures[target.slotIndex] = null;
        }
    }

    // ── Хелсбар ──────────────────────────────────────────────────────────────────────────────

    private static final float BAR_WIDTH = 40f, BAR_HEIGHT = 5f, BAR_MARGIN = 6f;

    /** Небольшой хелсбар над существом — вызывается сразу после creature.draw(batch) в
     *  Editor.renderSimCreatures. Рисуется для ВСЕХ живых (включая союзника — так наглядно видно,
     *  что урон по нему никогда не проходит). */
    public static void renderHealthBar(SpriteBatch batch, SimCreature creature) {
        if (creature == null || !creature.isAlive() || creature.maxHealth <= 0) return;
        Texture pixel = FxContext.pixel();
        float barX = creature.getX() + creature.getWidth() / 2f - BAR_WIDTH / 2f;
        float barY = creature.getY() + creature.getHeight() + BAR_MARGIN;
        float frac = Math.max(0f, Math.min(1f, creature.health / (float) creature.maxHealth));

        batch.setColor(0.15f, 0.15f, 0.15f, 0.85f);
        batch.draw(pixel, barX, barY, BAR_WIDTH, BAR_HEIGHT);
        batch.setColor(1f - frac, frac, 0.1f, 0.95f);
        batch.draw(pixel, barX, barY, BAR_WIDTH * frac, BAR_HEIGHT);
        batch.setColor(1f, 1f, 1f, 1f);
    }
}
