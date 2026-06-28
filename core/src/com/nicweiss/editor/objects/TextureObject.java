package com.nicweiss.editor.objects;

import com.badlogic.gdx.graphics.Texture;

public class TextureObject {
    public Texture texture;
    public int     high;
    public boolean isTree;

    public TextureObject(String path, int high) {
        texture = new Texture(path);
        this.high   = high;
        this.isTree = false;
    }

    public TextureObject(String path, int high, boolean isTree) {
        texture = new Texture(path);
        this.high   = high;
        this.isTree = isTree;
    }
}
