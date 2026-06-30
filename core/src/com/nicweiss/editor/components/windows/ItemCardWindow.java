package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;
import com.nicweiss.editor.utils.Font;
import com.nicweiss.editor.utils.ItemGenerator;
import com.nicweiss.editor.utils.ItemModifierCatalog;
import com.nicweiss.editor.utils.ItemModifierCatalog.ModifierDef;

import java.util.Arrays;
import java.util.LinkedHashMap;

public class ItemCardWindow extends Window implements CallBack {
    public static Store store;

    Texture buttonBG, buttonBGHover;
    ButtonCommon[] infoItems;

    public ItemCardWindow() {
        super();
        windowName = "О предмете";
        windowWidth = 420;
        windowHeight = 520;

        buttonBG = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");
    }

    public void buildWindow() {
        super.buildWindow();
        menuObjectSpace = 7;
        itemWidth = 20;
        itemHeight = 20;
    }

    public boolean isTouchInsideBounds() {
        return store.mouseX >= x && store.mouseX <= x + width
            && store.mouseY >= y && store.mouseY <= y + height;
    }

    public void populate(LinkedHashMap template, Font font) {
        infoItems = new ButtonCommon[100];
        int i = 0;

        ButtonCommon btn;

        // Название
        btn = new ButtonCommon();
        btn.setBackgrounds(buttonBG, buttonBGHover);
        btn.setText(font, "Название: " + template.get("__name__"));
        infoItems[i++] = btn;

        // Описание
        if (template.containsKey("__description__")) {
            btn = new ButtonCommon();
            btn.setBackgrounds(buttonBG, buttonBGHover);
            btn.setText(font, "Описание: " + template.get("__description__"));
            infoItems[i++] = btn;
        }

        // Размер
        int w = template.containsKey("__width__")  ? (int) template.get("__width__")  : 1;
        int h = template.containsKey("__height__") ? (int) template.get("__height__") : 1;
        btn = new ButtonCommon();
        btn.setBackgrounds(buttonBG, buttonBGHover);
        btn.setText(font, "Размер: " + w + "x" + h);
        infoItems[i++] = btn;

        // Характеристики
        if (template.containsKey("__stats__")) {
            String typeKey = (String) template.get("__type__");
            String rarityKey = ItemGenerator.currentRarity(template).key;
            LinkedHashMap stats = (LinkedHashMap) template.get("__stats__");
            for (Object statKey : stats.keySet()) {
                LinkedHashMap stat = (LinkedHashMap) stats.get(statKey.toString());
                int val = (int) stat.get("__value__");
                ModifierDef def = typeKey != null ? ItemModifierCatalog.findModifier(typeKey, statKey.toString()) : null;
                String label = def != null
                    ? def.name + ": " + val + def.unit + "  [" + def.min + ".." + def.effectiveMax(rarityKey) + "]"
                    : statKey + ": " + val;
                btn = new ButtonCommon();
                btn.setBackgrounds(buttonBG, buttonBGHover);
                btn.setText(font, label);
                infoItems[i++] = btn;
            }
        }

        // Кнопка "Выбрать" в самом низу
        btn = new ButtonCommon();
        btn.setBackgrounds(buttonBGHover, buttonBG);
        btn.setText(font, "Выбрать");
        btn.registerCallBack(this, "onSelect");
        infoItems[i++] = btn;

        infoItems = Arrays.copyOfRange(infoItems, 0, i);
    }

    // Вызывается кнопкой "Выбрать" — скрывает карточку и вызывает зарегистрированный коллбэк
    public void onSelect() throws Exception {
        this.hide();
        this.execCallBack();
    }

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);
        if (isShowWindow) {
            renderItemsList(batch, infoItems, false);
        }
    }

    @Override
    public boolean checkTouch(boolean isDragged, boolean isTouchUp) {
        if (!isShowWindow) return false;
        super.checkTouch(isDragged, isTouchUp);
        return isShowWindow;
    }
}
