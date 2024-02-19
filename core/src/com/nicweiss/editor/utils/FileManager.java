package com.nicweiss.editor.utils;

import java.awt.FileDialog;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class FileManager {
    public int mapHeight = 0;
    public int mapWidth = 0;

    public void saveMap(int[][] map, int width, int height){
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
                        stream.write(String.valueOf(map[i][j]).getBytes(StandardCharsets.UTF_8));
                    }
                }
                stream.flush();
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        fd.dispose();
    }

    public int[][] openMap() {
        mapWidth = 0;
        mapHeight = 0;

        int[][] map = new int[0][0];

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
                String[] splitedData = data.split(";");
                mapWidth = Integer.parseInt(splitedData[0]);
                mapHeight = Integer.parseInt(splitedData[1]);

                map = new int[mapHeight][mapWidth];
                int k = 2;

                for (int i = 0; i < mapHeight; i++) {
                    for (int j = 0; j < mapWidth; j++) {
                        map[i][j] = Integer.parseInt(splitedData[k]);
                        k++;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        fd.dispose();

        return map;
    }
}
