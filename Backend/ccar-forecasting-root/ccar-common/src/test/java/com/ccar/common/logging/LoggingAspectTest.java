package com.ccar.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ccar.sample.SampleBeans.SampleComponent;
import com.ccar.sample.SampleBeans.SampleController;
import com.ccar.sample.SampleBeans.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingAspectTest {

    private final List<Logger> attached = new ArrayList<>();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void detach() {
        attached.forEach(l -> l.detachAppender(appender));
    }

    @Test
    void logsServiceCallAtInfoAndArgsAtDebug() {
        capture(SampleService.class, Level.DEBUG);
        SampleService proxy = proxy(new SampleService(), 500);

        assertThat(proxy.greet("bob")).isEqualTo("hello bob");

        assertThat(messages()).containsExactly(
                "DEBUG -> greet() args=[bob]",
                "INFO <- greet() completed in ms",
                "DEBUG <- greet() result=hello bob");
    }

    @Test
    void failureIsRethrownAndLoggedOnlyAtDebug() {
        capture(SampleService.class, Level.INFO);
        SampleService proxy = proxy(new SampleService(), 500);

        assertThatThrownBy(proxy::fail).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThat(messages()).isEmpty();
    }

    @Test
    void truncatesLongArguments() {
        capture(SampleService.class, Level.DEBUG);
        SampleService proxy = proxy(new SampleService(), 5);

        proxy.greet("abcdefgh");

        assertThat(messages().get(0)).isEqualTo("DEBUG -> greet() args=[abcd...(truncated)");
    }

    @Test
    void monoIsTimedWhenItCompletesNotWhenReturned() {
        capture(SampleController.class, Level.INFO);
        SampleController proxy = proxy(new SampleController(), 500);

        Mono<String> mono = proxy.mono();
        assertThat(messages()).isEmpty();

        assertThat(mono.block()).isEqualTo("m");
        assertThat(messages()).containsExactly("INFO <- mono() completed in ms");
    }

    @Test
    void fluxIsTimedWhenItCompletes() {
        capture(SampleController.class, Level.INFO);
        SampleController proxy = proxy(new SampleController(), 500);

        Flux<Integer> flux = proxy.flux();
        assertThat(messages()).isEmpty();

        assertThat(flux.collectList().block()).containsExactly(1, 2, 3);
        assertThat(messages()).containsExactly("INFO <- flux() completed in ms");
    }

    @Test
    void ignoresBeansThatAreNotControllersOrServices() {
        capture(SampleComponent.class, Level.DEBUG);
        SampleComponent proxy = proxy(new SampleComponent(), 500);

        assertThat(proxy.work()).isEqualTo("done");
        assertThat(messages()).isEmpty();
    }

    private <T> T proxy(T target, int maxLength) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new LoggingAspect(maxLength));
        return factory.getProxy();
    }

    private void capture(Class<?> type, Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        logger.setLevel(level);
        logger.addAppender(appender);
        attached.add(logger);
        appender.start();
    }

    /** "LEVEL message" with the variable duration removed. */
    private List<String> messages() {
        return appender.list.stream()
                .map(e -> e.getLevel() + " " + e.getFormattedMessage().replaceAll("in \\d+ ms", "in ms"))
                .toList();
    }
}
