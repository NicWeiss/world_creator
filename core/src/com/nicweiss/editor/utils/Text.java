package com.nicweiss.editor.utils;

public class Text {
    public static String[] split(String text, int n) {
        String[] results = text.split("(?<=\\G.{" + n + "})");

        return results;
    }
}
