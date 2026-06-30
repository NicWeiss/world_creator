package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;
import com.nicweiss.editor.components.ButtonCommon;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;

public class AssetPickerWindow extends Window implements CallBack {
    public static Store store;

    Texture buttonBG, buttonBGHover;
    ButtonCommon[] items;

    // Сохранённый коллбэк родительского окна
    private CallBack storedCb;
    private Method storedMethod;
    private String storedEntityUuid;

    public AssetPickerWindow() {
        super();
        windowName = "Выбор ассета";
        buttonBG     = new Texture("Buttons/btn_background.png");
        buttonBGHover = new Texture("Buttons/btn_background_hover.png");
    }

    public boolean isTouchInsideBounds() {
        return store.mouseX >= x && store.mouseX <= x + width
            && store.mouseY >= y && store.mouseY <= y + height;
    }

    public void buildWindow() {
        super.buildWindow();
        menuObjectSpace = 7;
        itemWidth  = 70;
        itemHeight = 80;
    }

    // directory: путь внутри assets, например "creations" или "objects"
    // parentCb: объект-получатель коллбэка (напр. NpcEditorWindow)
    // parentMethod: имя метода с сигнатурой (String uuid, String filePath)
    // entityUuid: uuid редактируемой сущности
    public void populate(String directory, CallBack parentCb, String parentMethod, String entityUuid) {
        this.storedCb = parentCb;
        this.storedEntityUuid = entityUuid;

        for (Method m : parentCb.getClass().getMethods()) {
            if (m.getName().equals(parentMethod)) {
                this.storedMethod = m;
                break;
            }
        }

        items = new ButtonCommon[200];
        int i = 0;

        // Gdx.files.internal().file() возвращает путь от CWD (корня проекта),
        // а ассеты лежат в assets/ — добавляем префикс явно.
        File dir = Gdx.files.internal("assets/" + directory).file();
        if (dir.exists() && dir.isDirectory()) {
            File[] pngFiles = dir.listFiles(
                (d, name) -> name.toLowerCase().endsWith(".png")
            );
            if (pngFiles != null) {
                for (File f : pngFiles) {
                    // Абсолютный путь — надёжно работает на desktop независимо от настроек classpath
                    String absolutePath = f.getAbsolutePath();
                    try {
                        Texture tex = new Texture(Gdx.files.absolute(absolutePath));
                        ButtonCommon btn = new ButtonCommon();
                        btn.setBackgrounds(buttonBG, buttonBGHover);
                        btn.setIcon(tex);
                        btn.setText(font, "");
                        btn.setWidth(itemWidth);
                        btn.registerCallBack(this, "selectAsset", new String[]{absolutePath});
                        items[i++] = btn;
                    } catch (Exception e) {
                        Gdx.app.error("AssetPickerWindow", "Cannot load " + absolutePath + ": " + e.getMessage());
                    }
                }
            }
        } else {
            Gdx.app.error("AssetPickerWindow", "Directory not found: " + dir.getAbsolutePath());
        }

        if (i == 0) {
            ButtonCommon btn = new ButtonCommon();
            btn.setBackgrounds(buttonBGHover, buttonBGHover);
            // Показываем реальный искомый путь — чтобы сразу было видно, опечатка это или папки просто нет.
            btn.setText(font, "Нет ассетов\n(" + dir.getAbsolutePath() + ")");
            btn.setWidth(itemWidth * 4);
            items[i++] = btn;
        }

        items = Arrays.copyOfRange(items, 0, i);
    }

    public void selectAsset(String filePath) {
        this.hide();
        if (storedMethod != null && storedCb != null) {
            try {
                storedMethod.invoke(storedCb, storedEntityUuid, filePath);
            } catch (Exception e) {
                Gdx.app.error("AssetPickerWindow", "Callback failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);
        if (isShowWindow) {
            renderItemsList(batch, items, true);
        }
    }

    @Override
    public boolean checkTouch(boolean isDragged, boolean isTouchUp) {
        if (!isShowWindow) return false;
        // Поглощаем тач полностью, раз окно было открыто на момент клика — даже если этим же
        // кликом окно само себя скрыло. Иначе тот же клик проваливается в окно снизу.
        super.checkTouch(isDragged, isTouchUp);
        return true;
    }

    @Override
    public boolean checkKey(int keyCode) {
        return super.checkKey(keyCode);
    }
}
