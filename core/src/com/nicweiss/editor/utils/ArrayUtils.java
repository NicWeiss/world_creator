package com.nicweiss.editor.utils;

public class ArrayUtils {
    public static boolean checkIntInArray(int num, int[] arr){
        for (int i = 0; i < arr.length; i++){
            if (arr[i] == num){
                return true;
            }
        }
        return false;
    }
}
