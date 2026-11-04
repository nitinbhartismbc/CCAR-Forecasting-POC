
package com.ccar.auth.autoconfigure;

import com.ccar.auth.properties.AuthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(AuthProperties properties) {

        String issuerUri = properties.getIssuerUri();

        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException(
                    "ccar.auth.issuer-uri must be configured"
            );
        }

        return JwtDecoders.fromIssuerLocation(issuerUri);
    }
}
 