package com.nicweiss.editor.simulation;

import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.ItemModifierCatalog;
import com.nicweiss.editor.utils.Uuid;

import java.util.LinkedHashMap;

/**
 * Управляет 4 стеками зелий/свитков (store.stacks, см. ItemStack). Ёмкость ОДНОЙ ячейки стека —
 * 1 без пояса, + __mainStat__ надетого пояса / 4 (см. Player.beltCapacity, SystemUI.applyMainStat).
 *
 * Точки входа:
 *  - tryAddToStack — подбор с земли (см. DropManager.tryPickup) и быстрая укладка шифт-клик/
 *    удержание X (см. SystemUI.quickStackAt): приоритет неполная ячейка того же семейства
 *    (__type__, без учёта тира), затем свободная ячейка, иначе false (в инвентарь как есть);
 *  - tryAddToSlot/canPlaceInSlot — укладка перетаскиваемого предмета в КОНКРЕТНУЮ ячейку курсором
 *    (см. SystemUI drag&drop) — та же логика совместимости, но без перебора остальных ячеек;
 *  - removeFirst — вынимает первый предмет в буфер перетаскивания без применения эффекта (клик по
 *    занятой ячейке, см. SystemUI.tryPickupAt), в отличие от consumeFirst;
 *  - consumeFirst — тратит первый предмет и применяет эффект, по кнопке 1-4/D-pad (см.
 *    SimulationInputThread);
 *  - enforceCapacity — вызывается каждый кадр после пересчёта статов (см.
 *    SystemUI.recomputePlayerStats): при уменьшении ёмкости пояса лишнее из стеков уходит в
 *    инвентарь, а то, что не влезло и туда — падает на землю у игрока.
 */
public class StackManager {
    public static Store store;

    private StackManager() {}

    public static final int SLOT_COUNT = 4;
    private static final int BASE_CAPACITY_PER_SLOT = 1;

    private static final int INV_COLS = 10;
    private static final int INV_ROWS = 5;

    /** Ёмкость ОДНОЙ ячейки стека: 1 (без пояса) + __mainStat__ пояса / 4 (см. Player.beltCapacity). */
    public static int capacityPerSlot() {
        int beltCapacity = store.player != null ? store.player.beltCapacity : 0;
        return BASE_CAPACITY_PER_SLOT + beltCapacity / 4;
    }

    /**
     * Пытается положить расходник в стек (автопоиск ячейки). true = принято стеком, false = нет
     * места/не расходник — вызывающий код должен сам попробовать обычный инвентарь.
     */
    public static boolean tryAddToStack(LinkedHashMap itemData) {
        if (itemData == null || store.stacks == null) return false;
        String typeKey = (String) itemData.get("__type__");
        if (typeKey == null || !ItemModifierCatalog.isConsumableType(typeKey)) return false;

        int cap = capacityPerSlot();

        // 1. Ячейка того же семейства, но ещё не полная — приоритет выше, чем занятие новой.
        for (ItemStack s : store.stacks) {
            if (typeKey.equals(s.typeKey) && s.items.size() < cap) {
                s.items.add(itemData);
                return true;
            }
        }
        // 2. Свободная (ещё ничьей семье не назначенная) ячейка.
        for (ItemStack s : store.stacks) {
            if (s.typeKey == null) {
                s.typeKey = typeKey;
                s.items.add(itemData);
                return true;
            }
        }
        return false;
    }

    /** true, если item можно положить именно в ячейку slotIndex (та же ниша семейства/свободности). */
    public static boolean canPlaceInSlot(int slotIndex, LinkedHashMap item) {
        if (item == null || store.stacks == null || slotIndex < 0 || slotIndex >= SLOT_COUNT) return false;
        String typeKey = (String) item.get("__type__");
        if (typeKey == null || !ItemModifierCatalog.isConsumableType(typeKey)) return false;

        ItemStack s = store.stacks[slotIndex];
        if (s.typeKey == null) return true;
        return typeKey.equals(s.typeKey) && s.items.size() < capacityPerSlot();
    }

    /** Кладёт item в конкретную ячейку slotIndex (перетаскивание курсором) — см. canPlaceInSlot. */
    public static boolean tryAddToSlot(int slotIndex, LinkedHashMap item) {
        if (!canPlaceInSlot(slotIndex, item)) return false;
        ItemStack s = store.stacks[slotIndex];
        s.typeKey = (String) item.get("__type__");
        s.items.add(item);
        return true;
    }

    /** Вынимает первый (самый старый) предмет из ячейки БЕЗ применения эффекта — для буфера переноса. */
    public static LinkedHashMap removeFirst(int slotIndex) {
        if (store.stacks == null || slotIndex < 0 || slotIndex >= SLOT_COUNT) return null;
        ItemStack s = store.stacks[slotIndex];
        if (s.items.isEmpty()) return null;
        LinkedHashMap item = s.items.remove(0);
        if (s.items.isEmpty()) s.typeKey = null;
        return item;
    }

    /** Тратит первый (самый старый) предмет из ячейки slotIndex и применяет его эффект. */
    public static void consumeFirst(int slotIndex) {
        if (store.stacks == null || slotIndex < 0 || slotIndex >= SLOT_COUNT) return;
        ItemStack s = store.stacks[slotIndex];
        if (s.items.isEmpty()) return;

        LinkedHashMap item = s.items.remove(0);
        applyConsumableEffect(item);
        if (s.items.isEmpty()) s.typeKey = null;
    }

    private static void applyConsumableEffect(LinkedHashMap item) {
        if (store.player == null) return;
        Player p = store.player;
        String typeKey = (String) item.get("__type__");
        int value = item.containsKey("__mainStat__") ? (int) item.get("__mainStat__") : 0;

        if ("potion_health".equals(typeKey)) {
            p.health = Math.min(p.maxHealth, p.health + value);
        } else if ("potion_mana".equals(typeKey)) {
            p.mana = Math.min(p.maxMana, p.mana + value);
        } else if ("potion_recovery".equals(typeKey)) {
            p.health = p.maxHealth;
            p.mana = p.maxMana;
        }
        // scroll_teleport — пока плейсхолдер, просто тратится, эффекта нет (см. ТЗ).
    }

    /**
     * Обрезает стеки до текущей ёмкости — вызывать каждый кадр после пересчёта статов игрока
     * (капасити пояса могла упасть при снятии/смене пояса). Лишнее уходит в инвентарь, а то, что
     * не влезло и туда — падает на землю у игрока (см. DropManager.spawnDropAtPlayer).
     */
    public static void enforceCapacity() {
        if (store.stacks == null) return;
        int cap = capacityPerSlot();
        for (ItemStack s : store.stacks) {
            // С конца — трата всегда идёт с головы очереди (см. consumeFirst), поэтому "лишние"
            // (не влезающие в новую меньшую ёмкость) — это самые последние подобранные.
            while (s.items.size() > cap) {
                LinkedHashMap overflow = s.items.remove(s.items.size() - 1);
                if (!tryAddToInventory(overflow)) {
                    DropManager.spawnDropAtPlayer(overflow);
                }
            }
            if (s.items.isEmpty()) s.typeKey = null;
        }
    }

    /** Кладёт 1x1 предмет в первую свободную ячейку обычного инвентаря. */
    private static boolean tryAddToInventory(LinkedHashMap itemData) {
        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                if (!store.inventoryGrid[c][r]) {
                    store.inventoryGrid[c][r] = true;
                    itemData.put("__inv_x__", c);
                    itemData.put("__inv_y__", r);
                    String uuid = itemData.containsKey("__uuid__") ? (String) itemData.get("__uuid__") : Uuid.generate();
                    store.inventory.put(uuid, itemData);
                    return true;
                }
            }
        }
        return false;
    }
}
