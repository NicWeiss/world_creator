package com.nicweiss.editor.Generic;

import java.util.HashMap;


public class Store {

    public static HashMap<String, Float> display = new HashMap();
    public static boolean isNeedToChangeScale = false;
    public static int scale = 0;
    public static int scaleTotal = 0;

    public Store(){
    }

    public static void cameraUpScale(){
        isNeedToChangeScale = true;
        scale = -100;
        scaleTotal = scaleTotal - 100;
    }

    public static void cameraDownScale(){
        isNeedToChangeScale = true;
        scale = 100;
        scaleTotal = scaleTotal + 100;
    }

}
