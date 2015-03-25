package com.kenzan.ribbonproxy;

import com.kenzan.ribbonproxy.annotation.Body;
import com.kenzan.ribbonproxy.annotation.CacheKeyGroup;
import com.kenzan.ribbonproxy.annotation.Cookie;
import com.kenzan.ribbonproxy.annotation.Header;
import com.kenzan.ribbonproxy.annotation.Http;
import com.kenzan.ribbonproxy.annotation.Hystrix;
import com.kenzan.ribbonproxy.annotation.Path;
import com.kenzan.ribbonproxy.annotation.Query;
import com.kenzan.ribbonproxy.cache.RestCache;
import com.kenzan.ribbonproxy.cache.RestCachingPolicy;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Builder;
import com.netflix.client.http.HttpResponse;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommand.Setter;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.niws.client.http.RestClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.ws.rs.core.UriBuilder;

import rx.Observable;

class JerseyInvocationHandler implements InvocationHandler{
    
    private class JerseyHystrixCommand extends HystrixCommand<Object>{
        
        private final MethodInfo methodInfo;
        private final Object[] args;

        public JerseyHystrixCommand(final MethodInfo methodInfo, final Object[] args) {
            super(methodInfo.setter);
            this.methodInfo = methodInfo;
            this.args = args;
        }
        
        @Override
        protected Object run() throws Exception {

            final HttpRequest request = methodInfo.toHttpRequest(args);
            final String cacheKey = methodInfo.getCacheKey(args);

            final RestCache cache = restAdapterConfig.getRestCache();
            final RestCachingPolicy cachingPolicy = new RestCachingPolicy();

            if (cache != null) {

                final Optional<Object> cachedObject = cache.get(cacheKey);

                if (cachedObject.isPresent()) {
                    return cachedObject.get();
                }
            }

            final HttpResponse httpResponse = restClient.executeWithLoadBalancer(request);

            final Object object;
            if (HttpResponse.class.equals(methodInfo.responseClass)) {
                object = httpResponse;
            } else {
                object = restAdapterConfig.getMessageSerializer().readValue(methodInfo.responseClass, httpResponse.getInputStream());
            }

            if (cache != null && cachingPolicy.isCachable(request, httpResponse)) {
                cache.set(cacheKey, object);
            }

            return object;
        }
        
    }
    
    private class MethodInfo{

        private final Setter setter;
        private final Parameter[] parameters;
        private final Class<?> responseClass;
        private final Map<String, String> headers;
        private final Http http;
        private final Hystrix hystrix;
        private final boolean isObservable;
        private final Optional<String> cacheKeyGroup;
        private List<String> cookies = new ArrayList<>();

        public MethodInfo(final Method method) {
            
            //GET HTTP ANNOTATION
            http = Arrays.stream(method.getAnnotations())
                            .filter(a -> Http.class.equals(a.annotationType()))
                            .map(a -> (Http)a)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No Http annotation present."));
            
            //GET HYSTRIX ANNOTATION
            hystrix = Arrays.stream(method.getAnnotations())
                            .filter(a -> Hystrix.class.equals(a.annotationType()))
                            .map(a -> (Hystrix)a)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No Hystrix annotation present."));

            cacheKeyGroup = Arrays.stream(method.getAnnotations())
                            .filter(a -> CacheKeyGroup.class.equals(a.annotationType()))
                            .map(a -> a == null ? null : ((CacheKeyGroup)a).value())
                            .findFirst();

            this.setter = Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hystrix.groupKey()))
                            .andCommandKey(HystrixCommandKey.Factory.asKey(hystrix.commandKey()));
            
            this.parameters = method.getParameters();
            
            this.headers = Arrays.stream(http.headers())
                .collect(Collectors.toMap(
                    t -> t.name(), 
                    t -> t.value()
                ));

            if(headers.containsKey("Cookie")){
                cookies.add(this.headers.remove("Cookie"));
            }
            
            cookies.addAll(Arrays.stream(http.cookies())
                .map(cookie -> cookie.name() + "=" + cookie.value())
                .collect(Collectors.toList()));
            
            @SuppressWarnings("rawtypes")
            final Class returnType = method.getReturnType();
            @SuppressWarnings("rawtypes")
            final Class httpClass = Class.class.equals(http.responseClass()) ? null : http.responseClass();
            
            this.isObservable = Observable.class.equals(returnType);
            if(this.isObservable){
                this.responseClass = Optional.ofNullable(httpClass).orElseThrow(() ->
                        new IllegalStateException("Http responseClass is required for observables"));
            }else{
                this.responseClass = Optional.ofNullable(httpClass).orElse(returnType);
            }

        }
        
        private String getRenderedPath(final Object[] args) {
            
            final Map<String, Object> map = new HashMap<>();
            
            for(int i = 0; i < parameters.length; i++){
                final int count = i;
                Optional.ofNullable(parameters[count].getAnnotation(Path.class))
                .ifPresent(p -> {
                    map.put(p.value(), args[count]);
                });
            }
            
            String renderedPath = UriBuilder.fromPath(http.uriTemplate())
                            .buildFromEncodedMap(map)
                            .toString();
            return renderedPath;

        }
        
        private Map<String, String> getHeaders(final Object[] args){
            Map<String, String> allHeaders = this.headers;
            
            for(int i = 0; i < parameters.length; i++){
                final Parameter parameter = parameters[i];
                Header annotation = parameter.getAnnotation(Header.class);
                if(annotation != null){
                    allHeaders.put(annotation.name(), String.valueOf(args[i]));
                }
            }
            
            return allHeaders;
        }
        
        private Optional<Object> getBody(Object[] args){
            
            Object body = null;
            for(int i = 0; i < parameters.length; i++){
                final Parameter parameter = parameters[i];
                Body annotation = parameter.getAnnotation(Body.class);
                if(annotation != null){
                    body = args[i];
                }
            }
            
            return Optional.ofNullable(body);
        }
        

        private String getCacheKey(Object[] args) {
            if (cacheKeyGroup.isPresent()) {
                return cacheKeyGroup.get() + ":" + this.getRenderedPath(args);
            }

            return this.getRenderedPath(args);
        }

        private HttpRequest toHttpRequest(Object[] args) {

            final Builder requestBuilder = HttpRequest.newBuilder()
            .verb(this.http.method())
            .uri(this.getRenderedPath(args));
            
            this.getQueryParameters(args).forEach((k,v) -> {
                requestBuilder.queryParams(k, v);
            });
            
            this.getHeaders(args).forEach((k,v) -> {
                requestBuilder.header(k, v);
            });
            
            
            //Cookies
            List<String> requestCookies = new ArrayList<>();
            requestCookies.addAll(cookies);
            
            requestCookies.addAll(this.getCookies(args).entrySet().stream().map(entry -> 
              entry.getKey() + "=" + entry.getValue()  
            ).collect(Collectors.toList()));
            
            if(!requestCookies.isEmpty()){
                requestBuilder.header("Cookie", requestCookies.stream().collect(Collectors.joining(";")));
            }
            
            
            //Body
            this.getBody(args).ifPresent(t -> {
                try {
                    requestBuilder
                    .entity(restAdapterConfig.getMessageSerializer().writeValue(t));
                } catch (Exception e) {
                    e.printStackTrace();  ///XXX: decide there is better exception handling
                }
            });
            
            HttpRequest request = requestBuilder.build();
            return request;
        }
        
        private Map<String,String> getQueryParameters(Object[] args) {
            
            final Map<String, String> paramMap = new HashMap<>();
            
            for(int i = 0; i < parameters.length; i++){
                final Query queryParam = parameters[i].getAnnotation(Query.class);
                if(queryParam != null){
                    final Object arg = args[i];
                    
                    if(arg != null){
                        Optional<?> value = Optional.empty();
                        if(Optional.class.equals(arg.getClass())){
                            final Optional<?> optional = ((Optional<?>)arg);
                            
                            if(optional.isPresent()){
                                value = optional;
                            }
                        }else if(com.google.common.base.Optional.class.isAssignableFrom(arg.getClass())){
                            final com.google.common.base.Optional<?> optional = ((com.google.common.base.Optional<?>)arg);
                            
                            if(optional.isPresent()){
                                value = Optional.ofNullable(optional.get());
                            }
                        }else{
                            value = Optional.ofNullable(arg);
                        }

                        value.ifPresent(v -> paramMap.put(queryParam.value(), String.valueOf(v)) );
                    }
                }
            }
            return paramMap;
        }

        private Map<String, String> getCookies(final Object[] args){
            Map<String, String> allHeaders = this.headers;
            
            for(int i = 0; i < parameters.length; i++){
                final Parameter parameter = parameters[i];
                Cookie annotation = parameter.getAnnotation(Cookie.class);
                if(annotation != null){
                    allHeaders.put(annotation.name(), String.valueOf(args[i]));
                }
            }
            
            return allHeaders;
        }
    }
    
    private final Map<Method, MethodInfo> cache = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final RestAdapterConfig restAdapterConfig;

    public JerseyInvocationHandler(RestClient restClient, RestAdapterConfig restAdapterConfig) {
        this.restAdapterConfig = restAdapterConfig;
        this.restClient = restClient;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
   
        System.out.println("\n\n");
        System.out.println("args: " + method.getName() +  " " + (args == null ? "" : Arrays.asList(args)));
        
        MethodInfo methodInfo = cache.get(method);
        if(methodInfo == null){
            methodInfo = new MethodInfo(method);
            cache.put(method, methodInfo);
        }
        
        JerseyHystrixCommand command = new JerseyHystrixCommand(methodInfo, args);
        return methodInfo.isObservable ? command.observe() : command.execute();
    }
}