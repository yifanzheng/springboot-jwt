## Spring Security JWT 

### 概述

Spring Security 是 Spring 全家桶中一个功能强大且高度可定制的身份验证和访问控制框架。与所有 Spring 项目一样，我们可以轻松扩展 Spring Security 以满足自定义要求。 

由于 Spring Security 功能十分强大，相比于其他技术来说很难上手，很多刚接触 Spring Security 的开发者很难通过文档或者视频就能将其进行运用到实际开发中。

在公司实习的时候接触到的一个项目就使用了 Spring Security 这个强大的安全验证框架来完成用户的登录模块，并且也是自己负责的一个模块。当时自己对 Spring Security 基本不熟悉，可以说是第一次接触，查阅了很多关于这方面的资料，看得似懂非懂的，并且还在导师的指导下都花了将近一周的时间才勉强完成。

Spring Security 对于初学者来说，的确很难上手。于是自己在工作之余对这部分知识进行了学习，并实现了一个简单的项目，主要使用了 Spring Boot 技术集成 Spring Security 和 Spring Data Jpa 技术。这个项目实现的比较简单，还有很多地方需要优化，希望有兴趣的朋友可以一起完善，期待你的 PR。

### 项目下载

- git clone https://github.com/yifanzheng/spring-security-jwt.git 。

- 配置好 Maven 仓库，使用 IntelliJ IDEA 工具打开项目。

- 在 application.properties 配置文件中将数据库信息改成你自己的。

### 项目核心类说明

**WebCorsConfiguration**  

WebCorsConfiguration 配置类，主要解决 HTTP 请求跨域问题。这里需要注意的是，如果没有将 `Authorization` 头字段暴露给客户端的话，客户端是无法获取到 Token 信息的。

```java
/**
 * WebCorsConfiguration 跨域配置
 *
 * @author star
 */
@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

    /**
     * 设置swagger为默认主页
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("redirect:/swagger-ui.html");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
        WebMvcConfigurer.super.addViewControllers(registry);
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Collections.singletonList("*"));
        config.setAllowedMethods(Collections.singletonList("*"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        // 暴露 header 中的其他属性给客户端应用程序
        config.setExposedHeaders(Arrays.asList(
                "Authorization", "X-Total-Count", "Link",
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"
        ));
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

}
```

**WebSecurityConfig**  

WebSecurityConfig 配置类继承了 Spring Security 的 WebSecurityConfigurerAdapter 类。WebSecurityConfigurerAdapter 类提供了默认的安全配置，并允许其他类通过覆盖其方法来扩展它并自定义安全配置。

这里配置了如下内容：

- 忽略某些不需要验证的就能访问的资源路径；

- 设置 `CustomAuthenticationProvider` 自定义身份验证组件，用于验证用户的登录信息（用户名和密码）；

- 在 Spring Security 机制中配置需要验证后才能访问的资源路径、不需要验证就可以访问的资源路径以及指定某些资源只能被特定角色访问。

- 配置请求权限认证异常时的处理类；

- 将自定义的 `JwtAuthenticationFilter` 和 `JwtAuthorizationFilter` 两个过滤器添加到 Spring Security 机制中。

```java
/**
 * Web 安全配置
 *
 * @author star
 **/
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Import(SecurityProblemSupport.class)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private CorsFilter corsFilter;

    @Autowired
    private UserService userService;

    @Override
    public void configure(WebSecurity web) {
        web.ignoring()
                .antMatchers(HttpMethod.OPTIONS, "/**")
                .antMatchers("/app/**/*.{js,html}")
                .antMatchers("/v2/api-docs/**")
                .antMatchers("/webjars/springfox-swagger-ui/**")
                .antMatchers("/swagger-resources/**")
                .antMatchers("/i18n/**")
                .antMatchers("/content/**")
                .antMatchers("/swagger-ui.html")
                .antMatchers("/test/**");
    }

    @Override
    protected void configure(AuthenticationManagerBuilder authenticationManagerBuilder) {
        // 设置自定义身份验证组件，用于从数据库中验证用户登录信息（用户名和密码）
        authenticationManagerBuilder.authenticationProvider(new CustomAuthenticationProvider());
    }

    /**
     * 定义安全策略，设置 HTTP 访问规则
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling()
                // 当用户无权访问资源时发送 401 响应
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                // 当用户访问资源因权限不足时发送 403 响应
                .accessDeniedHandler(new AccessDeniedHandlerImpl())
             .and()
                // 禁用 CSRF
                .csrf().disable()
                .headers().frameOptions().disable()
             .and()
                .authorizeRequests()
                 // 指定路径下的资源需要进行验证后才能访问
                .antMatchers("/").permitAll()
                .antMatchers(HttpMethod.POST, SecurityConstants.AUTH_LOGIN_URL).permitAll()
                .antMatchers("/api/users/register").permitAll()
                // 只允许管理员访问
                .antMatchers("/api/users/detail").hasRole("ADMIN")
                // 其他请求需验证
                .anyRequest().authenticated()
             .and()
                // 添加用户登录验证过滤器，将登录请求交给此过滤器处理
                .addFilter(new JwtAuthenticationFilter(authenticationManager()))
                // 不需要 session（不创建会话）
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
             .and()
               .apply(securityConfigurationAdapter());
        super.configure(http);
    }

    private JwtConfigurer securityConfigurationAdapter() throws Exception{
        return new JwtConfigurer(new JwtAuthorizationFilter(authenticationManager()));
    }
}
```

**CustomAuthenticationProvider**

CustomAuthenticationProvider 自定义用户身份验证组件类，它用于验证用户登录信息是否正确。

```java
/**
 * CustomAuthenticationProvider 自定义用户身份验证组件类
 *
 * <p>
 * 提供用户登录密码验证功能。根据用户名从数据库中取出用户信息，进行密码验证，验证通过则赋予用户相应权限。
 *
 * @author star
 */
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserService userService;

    public CustomAuthenticationProvider() {

        this.userService = SpringSecurityContextHelper.getBean(UserService.class);
    }
    @Override
    public Authentication authenticate(Authentication authentication) throws BadCredentialsException, UsernameNotFoundException {
        // 获取验证信息中的用户名和密码 （即登录请求中的用户名和密码）
        String userName = authentication.getName();
        String password = authentication.getCredentials().toString();
        // 根据登录名获取用户信息
        User user = userService.getUserByName(userName);
        // 验证登录密码是否正确。如果正确，则赋予用户相应权限并生成用户认证信息
        if (user != null && Objects.equals(user.getPassword(), password)) {
            List<String> roles = userService.listUserRoles(userName);
            // 如果用户角色为空，则默认赋予 ROLE_USER 权限
            if (CollectionUtils.isEmpty(roles)) {
                roles = Collections.singletonList(UserRoleConstants.ROLE_USER);
            }
            // 设置权限
            List<GrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            // 生成认证信息
            return new UsernamePasswordAuthenticationToken(userName, password, authorities);
        }
        // 验证不成功就抛出异常
        throw new BadCredentialsException("The userName or password error.");

    }

    @Override
    public boolean supports(Class<?> aClass) {
        return aClass.equals(UsernamePasswordAuthenticationToken.class);
    }

}
```

**JwtAuthenticationFilter**

JwtAuthenticationFilter 用户登录验证过滤器，主要配合 `CustomAuthenticationProvider` 对用户登录请求进行验证，检查登录名和登录密码。如果验证成功，则生成 token 返回。

此过滤器继承了 `UsernamePasswordAuthenticationFilter` 类，并重写三个方法：

- attemptAuthentication(): 此方法用于验证用户登录信息；

- 

```java
/**
 * JwtAuthenticationFilter 用户登录验证过滤器
 *
 * <p>
 * 用于验证使用 URL 地址是 {@link SecurityConstants#AUTH_LOGIN_URL} 进行登录的用户请求。
 * 通过检查请求中的用户名和密码参数，并调用 Spring 的身份验证管理器进行验证。
 * 如果用户名和密码正确，那么过滤器将创建一个 token，并在 Authorization 标头中将其返回。
 * 格式：Authorization: "Bearer + 具体 token 值"</p>
 *
 * @author star
 **/
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final AuthenticationManager authenticationManager;

    private final ThreadLocal<Boolean> rememberMeLocal = new ThreadLocal<>();

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
        // 指定需要验证的登录 URL
        super.setFilterProcessesUrl(SecurityConstants.AUTH_LOGIN_URL);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException {
        try {
            // 获取用户登录信息，JSON 反序列化成 UserDTO 对象
            UserLoginDTO loginUser = new ObjectMapper().readValue(request.getInputStream(), UserLoginDTO.class);
            rememberMeLocal.set(loginUser.getRememberMe());
            // 根据用户名和密码生成身份验证信息
            Authentication authentication = new UsernamePasswordAuthenticationToken(loginUser.getUserName(), loginUser.getPassword(), new ArrayList<>());
            // 这里返回 Authentication 后会通过我们自定义的 {@see CustomAuthenticationProvider} 进行验证
            return this.authenticationManager.authenticate(authentication);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    /**
     * 如果验证通过，就生成 token 并返回
     */
    @Override
    protected void successfulAuthentication(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain,
                                            Authentication authentication) {
        try {
            // 获取用户信息
            String username = null;
            // 获取身份信息
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                UserDetails user = (UserDetails) principal;
                username = user.getUsername();
            } else if (principal instanceof String) {
                username = (String) principal;
            }
            // 获取用户认证权限
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            // 获取用户角色权限
            List<String> roles = authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            boolean isRemember = this.rememberMeLocal.get();
            // 生成 token
            String token = JwtUtils.generateToken(username, roles, isRemember);
            // 将 token 添加到 Response Header 中返回
            response.addHeader(SecurityConstants.TOKEN_HEADER, token);
        } finally {
            // 清除变量
            this.rememberMeLocal.remove();
        }
    }

    /**
     * 如果验证证不成功，返回错误信息提示
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException) throws IOException {
        logger.warn(authenticationException.getMessage());

        if (authenticationException instanceof UsernameNotFoundException) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, authenticationException.getMessage());
            return;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authenticationException.getMessage());
    }
}
```

**JwtAuthorizationFilter**

JwtAuthorizationFilter 

```java
/**
 * JwtAuthorizationFilter 用户请求授权过滤器
 *
 * <p>
 * 提供请求授权功能。用于处理所有 HTTP 请求，并检查是否存在带有正确 token 的 Authorization 标头。
 * 如果 token 有效，则过滤器会将身份验证数据添加到 Spring 的安全上下文中，并授权此次请求访问资源。</p>
 *
 * @author star
 */
public class JwtAuthorizationFilter extends BasicAuthenticationFilter {

    private UserService userService;

    public JwtAuthorizationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
        this.userService = SpringSecurityContextHelper.getBean(UserService.class);
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {
        // 从 HTTP 请求中获取 token
        String token = this.getTokenFromHttpRequest(request);
        // 验证 token 是否有效
        if (StringUtils.isNotEmpty(token) && JwtUtils.validateToken(token)) {
            // 获取认证信息
            Authentication authentication = this.getAuthentication(token);
            // 将认证信息存入 Spring 安全上下文中
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // 放行请求
        filterChain.doFilter(request, response);

    }

    /**
     * 从 HTTP 请求中获取 token
     *
     * @param request HTTP 请求
     * @return 返回 token
     */
    private String getTokenFromHttpRequest(HttpServletRequest request) {
        String authorization = request.getHeader(SecurityConstants.TOKEN_HEADER);
        if (authorization == null || !authorization.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return null;
        }
        // 从请求头中获取 token
        return authorization.replace(SecurityConstants.TOKEN_PREFIX, "");
    }

    private Authentication getAuthentication(String token) {
        // 从 token 信息中获取用户名
        String userName = JwtUtils.getUserName(token);
        if (StringUtils.isNotEmpty(userName)) {
            // 从数据库中获取用户权限，保证权限的及时性
            List<String> roles = userService.listUserRoles(userName);
            // 如果用户角色为空，则默认赋予 ROLE_USER 权限
            if (CollectionUtils.isEmpty(roles)) {
                roles = Collections.singletonList(UserRoleConstants.ROLE_USER);
            }
            // 设置权限
            List<GrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            // 认证信息
            return new UsernamePasswordAuthenticationToken(userName, null, authorities);
        }
        return null;
    }
}
```




### 测试 API

**注册账号**

![注册账号](./asset/imgs/register.png)

**登录**

![登录](./asset/imgs/login.png)

**带上正确的 token 访问需要身份验证的资源**  

![correctToken](./asset/imgs/correctToken.png)  

**带上不正确的 token 访问需要身份验证的资源**

![incorrectToken](./asset/imgs/incorrectToken.png)


**不带 token 访问需要身份验证的资源** 
![noToken](./asset/imgs/noToken.png)


### 参考文档
- [https://www.callicoder.com/spring-boot-spring-security-jwt-mysql-react-app-part-2/
](https://www.callicoder.com/spring-boot-spring-security-jwt-mysql-react-app-part-2/
)
- [https://segmentfault.com/a/1190000009231329](https://segmentfault.com/a/1190000009231329)

- [https://www.springcloud.cc/spring-security.html](https://www.springcloud.cc/spring-security.html)
