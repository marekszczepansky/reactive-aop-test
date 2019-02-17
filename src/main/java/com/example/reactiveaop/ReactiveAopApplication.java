package com.example.reactiveaop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.util.context.Context;

import java.time.Duration;
import java.time.Instant;

import static com.example.reactiveaop.ReactiveAopApplication.simplelog;
import static com.example.reactiveaop.TestWebFilter.IP_ADD;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@EnableAspectJAutoProxy
public class ReactiveAopApplication {

    public static void simplelog(String string) {
        System.out.printf("[%s] - %s%n", Instant.now(), string);
    }

    public static void main(String[] args) {
        SpringApplication.run(ReactiveAopApplication.class, args);
    }

}

@Configuration
class FunctionalCOnfiguration {

    @Bean
    RouterFunction<ServerResponse> routes(ReactiveService reactiveService) {
        return route(GET("/testme"), request -> ServerResponse
                .ok()
                .body(reactiveService.testme(), String.class));
    }

    @Bean
    ReactiveService service() {
        return new ReactiveService();
    }

    @Bean
    TestWebFilter wf() {
        return new TestWebFilter();
    }
}

class ReactiveService {

    Mono<String> testme() {
        simplelog("service testme called");
        return Mono.just("tested service")
                .delayElement(Duration.ofSeconds(2))
                .subscriberContext(context -> {
                    simplelog("final context test " + context);
                    return context;
                })
                .doOnNext(arg -> simplelog("arg = " + arg))
                ;
    }
}

@Aspect
@Component
class AspectTest {

    @Around("execution(* *..ReactiveService.*(..))")
    Object aroundAny(ProceedingJoinPoint joinPoint) throws Throwable {
        simplelog("aspect before");
        Mono<Object> result = new LoggerOperator((Mono<?>) joinPoint.proceed());
        simplelog("aspect after");
        final String[] ipAddress = {""};
        Mono<Object> result1 = result.subscriberContext(context -> {
            simplelog("Context on jointPoint result = " + context);
            ipAddress[0] = context.get(IP_ADD);
            return context;
        }).doOnNext(arg -> {
            simplelog("next on jointpoint result with IP: " + ipAddress[0]);
        });
        Mono<Object> ex = Mono.create(monoSink -> {
            simplelog("context on my mono " + monoSink.currentContext());
            monoSink.success("test 123");
        });
        return ex.flatMap(txt -> result1)
                .subscriberContext(context -> {
                    simplelog("context on flatMap result " + context);
                    return context;
                }).doOnNext(arg -> {
                    simplelog("next after flatmap with IP: " + ipAddress[0]);
                });
    }
}

class TestWebFilter implements WebFilter {

    public static final String IP_ADD = "IP_Add";

    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        String hostAddress = serverWebExchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        simplelog("web filter IP = " + hostAddress);
        return webFilterChain.filter(serverWebExchange).subscriberContext(Context.of(IP_ADD, hostAddress))
                .doOnSuccess(arg -> {
                    simplelog(String.format("web filter success arg = %s, host = %s", arg, hostAddress));
                });
    }
}

class LoggerOperator extends MonoOperator<Object, Object> {

    protected LoggerOperator(Mono<?> source) {
        super(source);
    }

    @Override
    public void subscribe(CoreSubscriber<? super Object> actual) {
        Context context = actual.currentContext();
        simplelog("logger operator subscriebe " + context);
        source.doOnNext(arg -> {
            simplelog("logger operator context = " + context);
        }).subscribe(actual);
    }


}