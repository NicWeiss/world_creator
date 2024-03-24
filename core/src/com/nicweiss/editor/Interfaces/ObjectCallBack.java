package com.nicweiss.editor.Interfaces;

import com.nicweiss.editor.Generic.BaseObject;

import java.lang.reflect.Method;


public class ObjectCallBack extends BaseObject {
    public interface CallBack {
    }

    public CallBack callBack;
    public Method method;

    public void registerCallBack(CallBack classInstance, Method method) {
        this.callBack = classInstance;
        this.method = method;
    }

    public void execCallBack() throws Exception {
        method.invoke(callBack);
    }
}
