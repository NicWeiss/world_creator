package com.nicweiss.editor.objects;

import com.badlogic.gdx.graphics.Texture;

public class TextureObject {
    public Texture  texture;
    public int high;

    public TextureObject(String path, int high) {
        texture = new Texture(path);
        this.high = high;
    }
}
