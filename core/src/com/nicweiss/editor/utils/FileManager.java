package com.nicweiss.editor.utils;

import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.components.windows.LoadingWindow;
import com.nicweiss.editor.creations.Creation;
import com.nicweiss.editor.objects.MapObject;

import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import java.awt.FileDialog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Сохраняет и загружает состояние мира в архив .wcf (ZIP + DEFLATE).
 *
 * Структура архива:
 *   manifest.json  — версия, размер карты
 *   map.bin        — бинарные данные тайлов (textureId + dialogBound + UUID)
 *   dialogs.json   — дерево диалогов
 *   quests.json    — квесты
 *   items.json     — шаблоны предметов
 *   npcs.json      — сущности (позиции + имена)
 *   buildings.json — объекты/здания (позиции + имена)
 */
public class FileManager {
    public static Store store;

    public int mapHeight = 0;
    public int mapWidth  = 0;

    private byte[] pendingMapBin;

    // ═══════════════════════════════════════════════════════════════════════
    // СОХРАНЕНИЕ
    // ═══════════════════════════════════════════════════════════════════════

    public void saveMap(MapObject[][] map, int width, int height) {
        FileDialog fd = new FileDialog((java.awt.Frame) null);
        fd.setMode(FileDialog.SAVE);
        fd.setFile("*.wcf");
        fd.setVisible(true);

        if (fd.getFile() == null) { fd.dispose(); return; }

        String filename = fd.getDirectory() + fd.getFile();
        if (!filename.endsWith(".wcf")) filename += ".wcf";
        fd.dispose();

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(filename))) {
            zip.setLevel(Deflater.BEST_COMPRESSION);

            putEntry(zip, "manifest.json",  buildManifest(width, height));
            putEntry(zip, "map.bin",        buildMapBin(map, width, height));
            putEntry(zip, "dialogs.json",   toJson(store.dialogs));
            putEntry(zip, "quests.json",    toJson(store.quests));
            putEntry(zip, "items.json",     toJson(store.itemTemplates));
            putEntry(zip, "npcs.json",      buildNpcsJson());
            putEntry(zip, "buildings.json", buildBuildingsJson());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЗАГРУЗКА
    // ═══════════════════════════════════════════════════════════════════════

    /** Показывает FileDialog и возвращает выбранный путь (или null). Вызывать на GL-потоке. */
    public String pickFile() {
        FileDialog fd = new FileDialog((java.awt.Frame) null);
        fd.setMode(FileDialog.LOAD);
        fd.setFile("*.wcf");
        fd.setVisible(true);
        if (fd.getFile() == null) { fd.dispose(); return null; }
        String path = fd.getDirectory() + fd.getFile();
        fd.dispose();
        return path;
    }

    /**
     * Загружает архив с отчётом о прогрессе.
     * Вызывать из фонового потока. Обновляет LoadingWindow через volatile-поля.
     * Возвращает данные карты или пустой массив при ошибке.
     */
    public String[][][] loadFile(String filename, LoadingWindow lw) {
        final int TOTAL = 7;
        resetStore();

        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(filename))) {
            lw.setStep(1, TOTAL, "Открытие архива", 0);

            ZipEntry entry;
            int stepNum = 1;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] data = readEntry(zip);
                String name = entry.getName();
                stepNum++;
                lw.setStep(stepNum, TOTAL, name, stepNum * 100 / TOTAL);

                switch (name) {
                    case "map.bin":
                        pendingMapBin = data;
                        break;
                    case "dialogs.json":
                        store.dialogs.clear();
                        store.dialogs.putAll(parseJsonMap(data));
                        break;
                    case "quests.json":
                        store.quests.clear();
                        store.quests.putAll(parseJsonMap(data));
                        break;
                    case "items.json":
                        store.itemTemplates.clear();
                        store.itemTemplates.putAll(parseJsonMap(data));
                        break;
                    case "npcs.json":
                        readNpcs(data);
                        break;
                    case "buildings.json":
                        readBuildings(data);
                        break;
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new String[0][0][0];
        }

        return buildMapResult();
    }


    // ═══════════════════════════════════════════════════════════════════════
    // КАРТА — запись
    // ═══════════════════════════════════════════════════════════════════════

    private byte[] buildMapBin(MapObject[][] map, int width, int height) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(width);
        dos.writeInt(height);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                MapObject tile = map[i][j];
                dos.writeShort(tile.getTextureId());
                dos.writeBoolean(tile.isDialogBind);
                byte[] uuid = tile.getUUID().getBytes(StandardCharsets.UTF_8);
                dos.writeShort(uuid.length);
                dos.write(uuid);
            }
        }

        return baos.toByteArray();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КАРТА — чтение
    // ═══════════════════════════════════════════════════════════════════════

    private String[][][] buildMapResult() {
        if (pendingMapBin == null) return new String[0][0][0];

        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pendingMapBin))) {
            int width  = dis.readInt();
            int height = dis.readInt();
            mapWidth  = width;
            mapHeight = height;

            String[][][] result = new String[height][width][3];

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int textureId   = dis.readShort() & 0xFFFF;
                    boolean dialog  = dis.readBoolean();
                    int uuidLen     = dis.readShort() & 0xFFFF;
                    byte[] uuidBytes = new byte[uuidLen];
                    dis.readFully(uuidBytes);

                    result[i][j][0] = new String(uuidBytes, StandardCharsets.UTF_8);
                    result[i][j][1] = String.valueOf(textureId);
                    result[i][j][2] = dialog ? "dialog" : "common";
                }
            }

            return result;

        } catch (IOException e) {
            e.printStackTrace();
            return new String[0][0][0];
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // СУЩНОСТИ (NPC)
    // ═══════════════════════════════════════════════════════════════════════

    private byte[] buildNpcsJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i <= store.creationCount; i++) {
            Creation cr = store.creations[i];
            if (cr == null) continue;
            String uuid = cr.getUUID();
            Map<String, Object> npc = new LinkedHashMap<>();
            npc.put("uuid", uuid);
            npc.put("name", store.npcs.containsKey(uuid) ? store.npcs.get(uuid).toString() : "NPC");
            npc.put("x", cr.mapCellX);
            npc.put("y", cr.mapCellY);
            list.add(npc);
        }
        return JSONValue.toJSONString(list).getBytes(StandardCharsets.UTF_8);
    }

    // Сырые данные NPC/зданий — заполняются в фоновом потоке, объекты создаются на GL-потоке
    public List<Map<String, Object>> pendingNpcs      = new ArrayList<>();
    public List<Map<String, Object>> pendingBuildings = new ArrayList<>();

    private void readNpcs(byte[] data) {
        pendingNpcs.clear();
        try {
            Object parsed = new JSONParser().parse(new String(data, StandardCharsets.UTF_8));
            if (!(parsed instanceof List)) return;
            for (Object item : (List<?>) parsed) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) item;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("uuid", m.get("uuid"));
                entry.put("name", m.get("name"));
                entry.put("x",    ((Number) m.get("x")).intValue());
                entry.put("y",    ((Number) m.get("y")).intValue());
                pendingNpcs.add(entry);
                // имена фиксируем сразу — нет GL-вызовов
                store.npcs.put((String) entry.get("uuid"), entry.get("name").toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ОБЪЕКТЫ (здания)
    // ═══════════════════════════════════════════════════════════════════════

    private byte[] buildBuildingsJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i <= store.buildingCount; i++) {
            Creation b = store.buildings[i];
            if (b == null) continue;
            String uuid = b.getUUID();
            Map<String, Object> building = new LinkedHashMap<>();
            building.put("uuid", uuid);
            building.put("name", store.buildingNames.containsKey(uuid)
                ? store.buildingNames.get(uuid).toString() : "Объект");
            building.put("x", b.mapCellX);
            building.put("y", b.mapCellY);
            list.add(building);
        }
        return JSONValue.toJSONString(list).getBytes(StandardCharsets.UTF_8);
    }

    private void readBuildings(byte[] data) {
        pendingBuildings.clear();
        try {
            Object parsed = new JSONParser().parse(new String(data, StandardCharsets.UTF_8));
            if (!(parsed instanceof List)) return;
            for (Object item : (List<?>) parsed) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) item;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("uuid", m.get("uuid"));
                entry.put("name", m.get("name"));
                entry.put("x",    ((Number) m.get("x")).intValue());
                entry.put("y",    ((Number) m.get("y")).intValue());
                pendingBuildings.add(entry);
                store.buildingNames.put((String) entry.get("uuid"), entry.get("name").toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JSON-утилиты
    // ═══════════════════════════════════════════════════════════════════════

    private byte[] toJson(LinkedHashMap<String, Object> map) {
        return JSONValue.toJSONString(map).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildManifest(int width, int height) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", 1);
        m.put("width",  width);
        m.put("height", height);
        return JSONValue.toJSONString(m).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Парсит JSON в LinkedHashMap<String,Object>.
     * json-simple возвращает Long для целых чисел — fixValue конвертирует их в Integer,
     * чтобы код UI мог использовать привычные приведения типов.
     */
    private LinkedHashMap<String, Object> parseJsonMap(byte[] data) {
        try {
            Object parsed = new JSONParser().parse(new String(data, StandardCharsets.UTF_8));
            if (parsed instanceof Map) return fixNumbers((Map<?, ?>) parsed);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new LinkedHashMap<>();
    }

    private LinkedHashMap<String, Object> fixNumbers(Map<?, ?> src) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : src.entrySet()) {
            result.put(e.getKey().toString(), fixValue(e.getValue()));
        }
        return result;
    }

    private Object fixValue(Object val) {
        if (val instanceof Long)   return ((Long) val).intValue();
        if (val instanceof Map)    return fixNumbers((Map<?, ?>) val);
        if (val instanceof List)   return fixList((List<?>) val);
        return val;
    }

    private List<Object> fixList(List<?> src) {
        List<Object> result = new ArrayList<>();
        for (Object item : src) result.add(fixValue(item));
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ZIP-утилиты
    // ═══════════════════════════════════════════════════════════════════════

    private void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private byte[] readEntry(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // СБРОС СОСТОЯНИЯ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Очищает Store перед загрузкой новых данных.
     * Используем clear() вместо замены объектов, чтобы не сломать ссылки
     * в уже открытых окнах (questsList = store.quests и т.д.).
     */
    private void resetStore() {
        store.dialogs.clear();
        store.quests.clear();
        store.itemTemplates.clear();
        store.npcs.clear();
        store.buildingNames.clear();
        store.creationCount = -1;
        store.buildingCount = -1;
        pendingMapBin = null;
        pendingNpcs.clear();
        pendingBuildings.clear();
        mapWidth  = 0;
        mapHeight = 0;
    }
}
