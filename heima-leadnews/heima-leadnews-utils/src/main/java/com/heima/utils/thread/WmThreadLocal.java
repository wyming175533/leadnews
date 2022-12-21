package com.heima.utils.thread;
import com.heima.model.wemedia.pojos.WmUser;
public class WmThreadLocal {
    public static final ThreadLocal<WmUser> WM_THREAD_LOCAL =new ThreadLocal<>();

    public static void set(WmUser vmUser){
        WM_THREAD_LOCAL.set(vmUser);
    }
    public static WmUser get(){
        return WM_THREAD_LOCAL.get();
    }
    public static void remove(){
        WM_THREAD_LOCAL.remove();
    }

}
