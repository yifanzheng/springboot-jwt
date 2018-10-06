package com.example.jwtverify.util;

import io.jsonwebtoken.*;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Jwt工具类
 *
 * @author kevin
 * @date 2018-10-02 16:24
 **/
public class JwtUtil {
    private JwtUtil(){}

    /**
     * secret就是用来进行jwt的签发和jwt的验证,是服务端的私钥，可以任意指定
     */
    private static final String SECRET="secrect";

    /**
     * 有效时间100hours
     */
    private static final long EXPIRATION_TIME = 360000000L;

    /**
     * 一般是在请求头里加入Authorization，并加上Bearer标注
     */
    private static final String TOKEN_PREFIX="Bearer ";

    /**
     * 用户角色
     */
    private static final String ROLE="ROLE";

    /**
     * Authorization请求头
     */
    private static final String AUTO_HEADER="Authorization";

    /**
     * 根据用户名生成token
     * @param username
     * @return
     */
    public static String generateToken(String username){
        HashMap<String, Object> map = new HashMap<>();
        // 将用户名当作角色role信息，可以put任意的数据
        map.put(ROLE,username);
        //生成jwt字符串
        String jwt = Jwts.builder()
                .setClaims(map)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))//设置有效时间为100hours
                .signWith(SignatureAlgorithm.HS512, SECRET)//生成签证信息
                .compact();
        return TOKEN_PREFIX+jwt;//jwt前面一般都会加Bearer;一般是在请求头里加入Authorization，并加上Bearer标注
    }

    /**
     * 验证token，并将role信息添加到请求头
     * @param request
     * @return
     */
    public static HttpServletRequest validateTokenAndAddRoleToHeader(HttpServletRequest request) {
        String token = request.getHeader(AUTO_HEADER);
        if (token != null) {
            // 解析token
            try {
                Map<String, Object> body = Jwts.parser()
                        .setSigningKey(SECRET)
                        .parseClaimsJws(token.replace(TOKEN_PREFIX, ""))
                        .getBody();
                //使用代理模式重写getHeader方法
                HttpServletRequest requestPoxy = (HttpServletRequest)Proxy.newProxyInstance(request.getClass().getClassLoader(), request.getClass().getInterfaces(), new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                //获取方法名
                                String methodName= method.getName();
                                if (Objects.equals(methodName,"getHeaders")) {
                                    String key = String.valueOf(args[0]);

                                    if(body!=null&&body.containsKey(key)){
                                        //如果jwt存在这个数据，就直接返回
                                        //注意，getHeaders()返回值类型是Enumeration<String>，故要转换
                                        return Collections.enumeration(Arrays.asList(body.get(key)));
                                    }
                                }

                                return method.invoke(request,args);
                            }
                        }
                );
                return requestPoxy;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        } else {
            throw new RuntimeException("Missing token");
        }
    }

}