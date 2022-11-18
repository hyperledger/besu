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
import org.hyperledger.besu.springmain.config.properties.MetricsProperties;
import org.hyperledger.besu.springmain.config.properties.P2PProperties;
import org.springframework.context.annotation.Bean;

import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;

public class MetricsSystemConfiguration {


    @Bean
    public MetricsSystem metricsSystem(MetricsConfiguration metricsConfiguration) {
        return MetricsSystemFactory.create(metricsConfiguration);
    }

    @Bean
    public MetricsConfiguration metricsConfiguration(MetricsProtocol metricsProtocol,
                                                     MetricsProperties metricsProperties,
                                                     Set<MetricCategory> metricCategories,
                                                     P2PProperties p2pProperties) {
        if (metricsProperties.isEnabled() && metricsProperties.isPushEnabled()) {
            throw new RuntimeException(
                    "--metrics-enabled option and --metrics-push-enabled option can't be used at the same "
                            + "time.  Please refer to CLI reference for more details about this constraint.");
        }

        MetricsCLIOptions unstableMetricsCLIOptions = MetricsCLIOptions.create();

        return unstableMetricsCLIOptions
                .toDomainObject()
                .enabled(metricsProperties.isEnabled())
                .host(
                        Strings.isNullOrEmpty(metricsProperties.getHost())
                                ? p2pProperties.getHost()
                                : metricsProperties.getHost())
                .port(metricsProperties.getPort())
                .protocol(metricsProtocol)
                .metricCategories(metricCategories)
                .pushEnabled(metricsProperties.isPushEnabled())
                .pushHost(
                        Strings.isNullOrEmpty(metricsProperties.getPushHost())
                                ? p2pProperties.getHost()
                                : metricsProperties.getPushHost())
                .pushPort(metricsProperties.getPushPort())
                .pushInterval(metricsProperties.getPushInterval())
                .hostsAllowlist(Arrays.asList(metricsProperties.getAllowList()))
                .prometheusJob(metricsProperties.getPrometheusJob())
                .build();
    }

    @Bean
    public Set<MetricCategory> metricCategories() {
        return DEFAULT_METRIC_CATEGORIES;
    }

    @Bean
    public MetricsProtocol metricsProtocol(MetricsProperties metricsProperties) {
        return MetricsProtocol.valueOf(metricsProperties.getProtocol().toUpperCase());
    }
}
