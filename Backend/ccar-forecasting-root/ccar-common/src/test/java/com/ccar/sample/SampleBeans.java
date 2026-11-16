package com.ccar.sample;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Test fixtures for LoggingAspectTest. Deliberately outside com.ccar.common: the aspect skips ccar-common itself.
 */
public final class SampleBeans {

    private SampleBeans() {
    }

    @Service
    public static class SampleService {
        public String greet(String name) {
            return "hello " + name;
        }

        public String fail() {
            throw new IllegalStateException("boom");
        }
    }

    @RestController
    public static class SampleController {
        public Mono<String> mono() {
            return Mono.just("m");
        }

        public Flux<Integer> flux() {
            return Flux.just(1, 2, 3);
        }
    }

    /** Not a controller/service: must not be logged. */
    @Component
    public static class SampleComponent {
        public String work() {
            return "done";
        }
    }
}
