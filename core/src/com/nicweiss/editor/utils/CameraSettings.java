package com.nicweiss.editor.utils;

import com.nicweiss.editor.Generic.Store;

public class CameraSettings {
    public static Store store;

    public static boolean upScale(){
        if (store.scaleTotal > -1700) {
            store.scale = -100;
            store.scaleTotal = store.scaleTotal - 100;
            store.isNeedToChangeScale = true;
            return true;
        }

        return false;
    }

    public static boolean downScale(){
        if (store.scaleTotal < 10000) {
            store.scale = 100;
            store.scaleTotal = store.scaleTotal + 100;
            store.isNeedToChangeScale = true;

            return true;
        }

        return false;
    }
}
