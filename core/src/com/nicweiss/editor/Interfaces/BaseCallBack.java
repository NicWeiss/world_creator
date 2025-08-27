package com.nicweiss.editor.Interfaces;

import com.nicweiss.editor.Generic.BaseObject;

import java.lang.reflect.Method;


public class BaseCallBack extends BaseObject {
    private String[] params = null;

    public interface CallBack {
    }

    public CallBack callBack;
    public Method method = null;

    public void registerCallBack(CallBack classInstance, Method method) {
        this.callBack = classInstance;
        this.method = method;
    }

    public void registerCallBack(CallBack classInstance, String methodName) {
        this.callBack = classInstance;
        Method[] methods = this.callBack.getClass().getMethods();
        for (Method m : methods){
            if (m.getName() == methodName){
                this.method = m;
            }
        }
    }

    public void registerCallBack(CallBack classInstance, String methodName, String[] params) {
        registerCallBack(classInstance, methodName);
        this.params = params;

        if (this.method.getParameterCount() != this.params.length) {
            System.out.print(
                "!! Warning while registration callBack !! Method " + this.method.getName() +
                " has " + this.method.getParameterCount() +
                " params but received " + this.params.length + " \n"
            );

            this.method = null;
            this.params = null;
        }
    }
    
    public void execCallBack() throws Exception {
        if (this.method != null) {
            if (this.params != null) {
                method.invoke(callBack, this.params);
                return;
            }

            method.invoke(callBack);
        }
    }

    public void setParams(Integer id, String value) {
        this.params[id] = value;
    }
}
