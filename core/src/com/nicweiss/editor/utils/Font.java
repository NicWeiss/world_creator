package com.nicweiss.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

public class Font {
    private BitmapFont font;


    public Font(int size, Color color) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("Fonts/Roboto-Medium.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = (int) (size * 2);
        parameter.color = color;
        parameter.characters = " abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" +
                "!\"#$%&\\'()*+,-./:;<=>?@[\\\\]^_`{|}~абвгдеёжзийклмнопрстуфхцчшщъьыэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЬЫЭЮЯ";
        try {
            font = generator.generateFont(parameter);
        } catch (Exception e) {
            e.printStackTrace();
        }
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    public float getWidth(String text) {
        GlyphLayout layout = new GlyphLayout(font, text);
        return layout.width;
    }

    public float getHeight(String text) {
        GlyphLayout layout = new GlyphLayout(font, text);
        return layout.height;
    }

    public void draw(Batch batch, String text, float x, float y) {
        GlyphLayout layout = new GlyphLayout(font, text);
        font.draw(batch, text, x, y + layout.height);
    }
}
