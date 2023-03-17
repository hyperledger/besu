/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.metrics;

import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine;

import java.util.Set;

import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;
import static org.hyperledger.besu.metrics.MetricsProtocol.PROMETHEUS;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PORT;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PUSH_PORT;

@SuppressWarnings("WeakerAccess")
@Module
@CommandLine.Command(
        abbreviateSynopsis = true,
        name = "metrics",
        mixinStandardHelpOptions = true,
        header = "@|bold,fg(cyan) Usage:|@",
        synopsisHeading = "%n",
        descriptionHeading = "%n@|bold,fg(cyan) Description:|@%n%n",
        optionListHeading = "%n@|bold,fg(cyan) Options:|@%n",
        footerHeading = "%n",
        footer = "Besu is licensed under the Apache License 2.0")

public class MetricsSystemModule {

  private static final String TIMERS_ENABLED_FLAG = "--Xmetrics-timers-enabled";
  private static final String IDLE_TIMEOUT_FLAG = "--Xmetrics-idle-timeout";

  @CommandLine.Option(
          names = TIMERS_ENABLED_FLAG,
          hidden = true,
          defaultValue = "true",
          description = "Whether to enable timer metrics (default: ${DEFAULT-VALUE}).")
  private Boolean timersEnabled = MetricsConfiguration.DEFAULT_METRICS_TIMERS_ENABLED;

  @CommandLine.Option(
          hidden = true,
          names = {IDLE_TIMEOUT_FLAG},
          paramLabel = "<INTEGER>",
          description = "Timeout for metrics TCP connections, in seconds (default: ${DEFAULT-VALUE})",
          arity = "1")
  private int idleTimeout = MetricsConfiguration.DEFAULT_METRICS_IDLE_TIMEOUT_SECONDS;

  @CommandLine.Option(
          names = {"--metrics-enabled"},
          description = "Set to start the metrics exporter (default: ${DEFAULT-VALUE})")
  private final Boolean isMetricsEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
          names = {"--metrics-protocol"},
          description =
                  "Metrics protocol, one of PROMETHEUS, OPENTELEMETRY or NONE. (default: ${DEFAULT-VALUE})")
  private MetricsProtocol metricsProtocol = PROMETHEUS;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
          names = {"--metrics-host"},
          paramLabel = "<HOST>",
          description = "Host for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
          arity = "1")
  private String metricsHost;

  @CommandLine.Option(
          names = {"--metrics-port"},
          paramLabel = "<PORT>",
          description = "Port for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
          arity = "1")
  private final Integer metricsPort = DEFAULT_METRICS_PORT;

  @CommandLine.Option(
          names = {"--metrics-category", "--metrics-categories"},
          paramLabel = "<category name>",
          split = ",",
          arity = "1..*",
          description =
                  "Comma separated list of categories to track metrics for (default: ${DEFAULT-VALUE})")
  private final Set<MetricCategory> metricCategories = DEFAULT_METRIC_CATEGORIES;

  @CommandLine.Option(
          names = {"--metrics-push-enabled"},
          description = "Enable the metrics push gateway integration (default: ${DEFAULT-VALUE})")
  private final Boolean isMetricsPushEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
          names = {"--metrics-push-host"},
          paramLabel = "<HOST>",
          description =
                  "Host of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
          arity = "1")
  private String metricsPushHost;

  @CommandLine.Option(
          names = {"--metrics-push-port"},
          paramLabel = "<PORT>",
          description =
                  "Port of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
          arity = "1")
  private final Integer metricsPushPort = DEFAULT_METRICS_PUSH_PORT;

  @CommandLine.Option(
          names = {"--metrics-push-interval"},
          paramLabel = "<INTEGER>",
          description =
                  "Interval in seconds to push metrics when in push mode (default: ${DEFAULT-VALUE})",
          arity = "1")
  private final Integer metricsPushInterval = 15;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
          names = {"--metrics-push-prometheus-job"},
          description = "Job name to use when in push mode (default: ${DEFAULT-VALUE})",
          arity = "1")
  private String metricsPrometheusJob = "besu-client";
  @Provides
  @Singleton
  MetricsSystem provideMetricsSystem(final MetricsConfiguration metricsConfig) {
    return MetricsSystemFactory.create(metricsConfig);
  }

  @Provides
  @Singleton
  MetricsConfiguration provideMetricsConfiguration() {
    return MetricsConfiguration.builder()
            .enabled(this.isMetricsEnabled)
            .protocol(this.metricsProtocol)
            .host(this.metricsHost)
            .port(this.metricsPort)
            .metricCategories(this.metricCategories)
            .pushEnabled(this.isMetricsPushEnabled)
            .pushHost(this.metricsPushHost)
            .pushPort(this.metricsPushPort)
            .pushInterval(this.metricsPushInterval)
            .prometheusJob(this.metricsPrometheusJob)
            .timersEnabled(this.timersEnabled)
            .idleTimeout(this.idleTimeout)
            .build();
  }
}
