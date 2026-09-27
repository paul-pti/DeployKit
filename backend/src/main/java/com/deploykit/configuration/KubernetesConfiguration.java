package com.deploykit.configuration;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(KubernetesProperties.class)
public class KubernetesConfiguration {

    /** The client connects lazily, so the application starts even when no cluster is reachable. */
    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient(KubernetesProperties properties) {
        Config config = Config.autoConfigure(StringUtils.hasText(properties.context()) ? properties.context() : null);
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
