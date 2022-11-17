/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.springmain.config;

import java.util.Arrays;
import java.util.Set;
import com.google.common.base.Strings;
import org.hyperledger.besu.cli.options.unstable.MetricsCLIOptions;
import org.hyperledger.besu.metrics.MetricsProtocol;
import org.hyperledger.besu.metrics.MetricsSystemFactory;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;

public class MetricsSystemConfiguration {

    @Value("${p2p.host}")
    private String p2pHost;

    @Value("${metrics.enabled}")
    private boolean isMetricsEnabled;
    @Value("${metrics.push-enabled}")
    private boolean isMetricsPushEnabled;
    @Value("${metrics.host:}")
    private String metricsHost;

    @Value("${metrics.port}")
    private int metricsPort;

    @Value("${metrics.protocol}")
    private String metricsProtocol;

    @Value("${metrics.push-host:}")
    private String metricsPushHost;

    @Value("${metrics.push-port}")
    private int metricsPushPort;

    @Value("${metrics.push-interval}")
    private int metricsPushInterval;

    @Value("${metrics.allow-list:}")
    private String[] hostsAllowlist;

    @Value("${metrics.prometheus-job}")
    private String metricsPrometheusJob;

    @Bean
    public MetricsSystem metricsSystem() {
        return MetricsSystemFactory.create(metricsConfiguration());
    }

    @Bean
    private MetricsConfiguration metricsConfiguration() {
        if (isMetricsEnabled && isMetricsPushEnabled) {
            throw new RuntimeException(
                    "--metrics-enabled option and --metrics-push-enabled option can't be used at the same "
                            + "time.  Please refer to CLI reference for more details about this constraint.");
        }

        MetricsCLIOptions unstableMetricsCLIOptions = MetricsCLIOptions.create();

        return unstableMetricsCLIOptions
                .toDomainObject()
                .enabled(isMetricsEnabled)
                .host(
                        Strings.isNullOrEmpty(metricsHost)
                                ? p2pHost
                                : metricsHost)
                .port(metricsPort)
                .protocol(metricsProtocol())
                .metricCategories(metricCategories())
                .pushEnabled(isMetricsPushEnabled)
                .pushHost(
                        Strings.isNullOrEmpty(metricsPushHost)
                                ? p2pHost
                                : metricsPushHost)
                .pushPort(metricsPushPort)
                .pushInterval(metricsPushInterval)
                .hostsAllowlist(Arrays.asList(hostsAllowlist))
                .prometheusJob(metricsPrometheusJob)
                .build();
    }

    @Bean
    private Set<MetricCategory> metricCategories() {
        return DEFAULT_METRIC_CATEGORIES;
    }

    @Bean
    private MetricsProtocol metricsProtocol() {
        return MetricsProtocol.valueOf(metricsProtocol.toUpperCase());
    }
}
