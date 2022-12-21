package com.heima.app.gateway.filter;



import com.heima.app.gateway.utils.AppJwtUtil;
import io.jsonwebtoken.Claims;
import org.apache.commons.lang.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


/**
 * @author wangyiming
 */
public class AuthorizeFilter implements Ordered, GlobalFilter {


    /**
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    //获取请求和响应对象
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
    //判断是否是登陆请求
        if(request.getURI().getPath().contains("/login")){
            //方行
            return chain.filter(exchange);
        }
    //否则 获取token 并校验合法性能
        String token=request.getHeaders().getFirst("token");
    if(StringUtils.isBlank(token)){
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }
        try {
            Claims claimsBody = getClaims(token);
            int result = AppJwtUtil.verifyToken(claimsBody);
            //表示token已经过期
            if(result==1 || result==2){
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }


        return chain.filter(exchange);
    }

    private static Claims getClaims(String token) {
        Claims claimsBody = AppJwtUtil.getClaimsBody(token);//1 和2 代表过期
        return claimsBody;
    }

    /**值越小 有限级越大
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
