package com.heima.utils.thread;

import com.heima.model.user.pojos.ApUser;
import com.heima.model.wemedia.pojos.WmUser;

public class AppThreadLocalUtil {
    public static final ThreadLocal<ApUser> AP_THREAD_LOCAL =new ThreadLocal<>();

    public static void set(ApUser apUser){
        AP_THREAD_LOCAL.set(apUser);
    }
    public static ApUser get(){
        return AP_THREAD_LOCAL.get();
    }
    public static void remove(){
        AP_THREAD_LOCAL.remove();
    }
}
