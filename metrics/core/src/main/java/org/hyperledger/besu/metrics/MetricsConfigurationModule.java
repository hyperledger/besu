package org.hyperledger.besu.metrics;

import dagger.Module;
import dagger.Provides;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class MetricsConfigurationModule {
    @Provides
    @Singleton
    @Named("MetricsConfiguration")
    MetricsConfiguration provideMetricsConfiguration() {
        return MetricsConfiguration.builder().build();
    }
}
