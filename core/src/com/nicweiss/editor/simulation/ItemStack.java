package com.nicweiss.editor.simulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Одна ячейка стека зелий/свитков (см. StackManager). typeKey — __type__ семейства, которому
 * сейчас "принадлежит" ячейка (null = ячейка свободна, может принять любое семейство). items —
 * очередь конкретных экземпляров предмета (разных тиров одного семейства) в порядке подбора:
 * трата всегда идёт с головы (items.get(0)), см. StackManager.consumeFirst.
 */
public class ItemStack {
    public String typeKey;
    public final List<LinkedHashMap> items = new ArrayList<>();
}
