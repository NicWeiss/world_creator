package com.nicweiss.editor.utils;

import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.objects.MapObject;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.FileDialog;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class FileManager {
    public static Store store;

    public int mapHeight = 0;
    public int mapWidth = 0;

    public void saveMap(MapObject[][] map, int width, int height){
        FileDialog fd = new FileDialog((java.awt.Frame)null);
        fd.setMode(FileDialog.SAVE);
        fd.setFile("*.imf");
        fd.setVisible(true);
        String filename = fd.getDirectory() + fd.getFile();

        if (filename != null){
            System.out.println("You chose " + filename);
            try (FileOutputStream stream = new FileOutputStream(filename)) {
                stream.write(String.valueOf(width).getBytes(StandardCharsets.UTF_8));
                stream.write(";".getBytes(StandardCharsets.UTF_8));
                stream.write(String.valueOf(height).getBytes(StandardCharsets.UTF_8));
//                stream.write((byte) height);

                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        stream.write(";".getBytes(StandardCharsets.UTF_8));
                        stream.write(String.valueOf(map[i][j].getUUID()).getBytes(StandardCharsets.UTF_8));
                        stream.write(",".getBytes(StandardCharsets.UTF_8));
                        stream.write(String.valueOf(map[i][j].getTextureId()).getBytes(StandardCharsets.UTF_8));
                    }
                }

                int dialogSize = store.dialogs.size();
                stream.write("Ð".getBytes(StandardCharsets.UTF_8));
                stream.write(String.valueOf(dialogSize).getBytes(StandardCharsets.UTF_8));
                store.dialogs.forEach((k, v) -> {
                    try {
                        stream.write("Ö".getBytes(StandardCharsets.UTF_8));
                        stream.write(String.valueOf(k).getBytes(StandardCharsets.UTF_8));
                        stream.write("Ø".getBytes(StandardCharsets.UTF_8));
                        stream.write(v.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                stream.flush();
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        fd.dispose();
    }

    public String[][][] openMap() {
        JSONParser parser = new JSONParser();
        store.dialogs = new LinkedHashMap();
        mapWidth = 0;
        mapHeight = 0;

        String[][][] map = new String[0][0][0];

        FileDialog fd = new FileDialog((java.awt.Frame)null);
        fd.setMode(FileDialog.LOAD);
        fd.setFile("*.imf");
        fd.setVisible(true);
        String filename = fd.getDirectory() + fd.getFile();

        if (fd.getFile() != null){
            System.out.println("You chose " + filename);
            try (FileInputStream fs = new FileInputStream(filename)) {
                byte[] bytesArray = fs.readAllBytes();
                String data = new String(bytesArray, StandardCharsets.UTF_8);
                String[] splitedData = data.split("Ð");
                String[] mapData = splitedData[0].split(";");
                String[] dialogData = splitedData[1].split("Ö");

                int dialogSize = Integer.parseInt(dialogData[0]);
                String[] dialogUUIDList = new String[dialogSize];
                mapWidth = Integer.parseInt(mapData[0]);
                mapHeight = Integer.parseInt(mapData[1]);

                map = new String[mapHeight][mapWidth][3];
                int k = 2;
                String uuid, surface, key, value;

                for (int i = 1; i <= dialogSize; i++) {
                    String[] subSplitedData = dialogData[i].split("Ø");
                    key = subSplitedData[0];
                    value = subSplitedData[1];

                    dialogUUIDList[i-1] = key;

                    store.dialogs.put(key, (LinkedHashMap) parser.parse(value));
                }

                for (int i = 0; i < mapHeight; i++) {
                    for (int j = 0; j < mapWidth; j++) {
                        String[] subSplitedData = mapData[k].split(",");
                        uuid = subSplitedData[0];
                        surface = subSplitedData[1];

                        map[i][j][0] = uuid;
                        map[i][j][1] = surface;
                        map[i][j][2] = "common";

                        if(Arrays.asList(dialogUUIDList).contains(uuid)){
                            map[i][j][2] = "dialog";
                        }
                        k++;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        fd.dispose();

        return map;
    }
}
