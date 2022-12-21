package com.heima.wemedia.interceptor;

import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.thread.WmThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;
@Slf4j
public class WmTokenInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从header中获取信息存入ThreadLocal中
        String userId=request.getHeader("userId");
        //将userId包装，若 userid为空，返回一个空对象（get()无值但optinoal不为null)）
        Optional<String> optional=Optional.ofNullable(userId);
        if(optional.isPresent()){
            //userId存在
            WmUser wmUser=new WmUser();
            wmUser.setId(Integer.valueOf(userId));
            WmThreadLocal.set(wmUser);

        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
          WmThreadLocal.remove();

    }




}
