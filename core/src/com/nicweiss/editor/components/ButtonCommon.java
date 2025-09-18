package com.nicweiss.editor.components;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.nicweiss.editor.Interfaces.BaseCallBack;
import com.nicweiss.editor.utils.Font;

import java.util.ArrayList;
import java.util.List;


public class ButtonCommon extends BaseCallBack {
    private Font font;
    private String text;
    public int textPadding = 10;
    int iconSize = 0;
    private List<String> lines = new ArrayList<>();

    String key;

    Texture background, backgroundHover;
    Texture icon;

    public float textHeight = 10, textWidth = 10;

    public ButtonCommon() {}

    public void setText(Font font, String buttonText){
        this.font = font;
        text = buttonText;

        this.recalculateLines();
        height = getTextHeight();
    }

    public void setWidthByText(){
        textWidth = font.getWidth(text);
        width = getTextWidth();
    }

    public void setWidth(int width) {
        super.setWidth(width);

        recalculateLines();
        height = getTextHeight();
    }

    public String getText(){
        return text;
    }

//    @Override
    public void execTouch() throws Exception {
        execCallBack();
    }

    @Override
    public boolean checkTouchAndExec() {
        checkTouch(store.mouseX, store.mouseY);
        if (isTouched) {
            try {
                execTouch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return isTouched;
    }

    private void recalculateLines() {
        if (font == null || text == null) {
            return;
        }

        lines.clear();

        if ( width == 0) {
            lines.add(text);
            return;
        }

        float maxTextWidth = this.width - 20;
        maxTextWidth -= (iconSize * 1.5f);

        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split("\\s+");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                if (word.isEmpty()) {
                    continue;
                }
                String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
                if (font.getWidth(testLine) <= maxTextWidth) {
                    currentLine.append(currentLine.length() == 0 ? word : " " + word);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        lines.add(word);
                        currentLine = new StringBuilder();
                    }
                }
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        if (lines.isEmpty()) {
            lines.add("");
        }
    }

    @Override
    public void draw(Batch batch) {
        img = isTouched ? backgroundHover : background;

        this.recalculateLines();
        height = getTextHeight();

        super.draw(batch);

        if (icon != null){
            iconSize = 30;
            batch.draw(icon, x  + textPadding, y + height - iconSize, iconSize, iconSize);
        }

        float startY = y + getTextHeight() - (textPadding * 2);
        float startX = x + textPadding;
        if (icon != null) {
            startX += iconSize * 1.5f;
        }

        for (String line : lines) {
            font.draw(batch, line, startX, startY);
            startY -= textHeight + 5; // Сдвигаем вниз на высоту строки + небольшой отступ
        }
    }

    public int getTextHeight(){
        textHeight = font.getHeight("A");
        int lineCount = lines.size();
         int heightOfText = (int) ((lineCount * textHeight) + (textPadding * 2));

         if (lineCount > 1) {
             heightOfText = heightOfText + (lineCount * 5);
         }

         return heightOfText;
    }

    public int getTextWidth(){
        return (int) (textWidth + (textPadding * 2));
    }

    public void setBackgrounds(Texture background, Texture backgroundHover){
        this.background = background;
        this.backgroundHover = backgroundHover;
    }

    public void setDirective(String key){
        this.key = key;
    }

    public void setDirective(String key, String uuid){
        this.key = key;
        this.uuid = uuid;
    }

    public String getDirective(){
        return key;
    }

    public void setIcon(Texture icon) {
        this.icon = icon;
        iconSize = 30;
    }
}
