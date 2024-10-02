/*
 * Copyright contributors to Hyperledger Besu.
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
 */
package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP;
import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;
import static org.hyperledger.besu.metrics.MetricsProtocol.PROMETHEUS;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PORT;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PUSH_PORT;

import org.hyperledger.besu.metrics.MetricsProtocol;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.Set;

import picocli.CommandLine;

/** Command line options for configuring metrics. */
public class MetricsOptionGroup {
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
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsHost;

  @CommandLine.Option(
      names = {"--metrics-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
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
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsPushHost;

  @CommandLine.Option(
      names = {"--metrics-push-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer metricsPushPort = DEFAULT_METRICS_PUSH_PORT;

  @CommandLine.Option(
      names = {"--metrics-push-interval"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
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

  /** Returns a newly created {@link MetricsOptionGroup} with default values. */
  public MetricsOptionGroup() {}

  /**
   * Returns whether metrics are enabled.
   *
   * @return true if metrics are enabled, otherwise false
   */
  public Boolean getMetricsEnabled() {
    return isMetricsEnabled;
  }

  /**
   * Returns the metrics protocol.
   *
   * @return the metrics protocol
   */
  public MetricsProtocol getMetricsProtocol() {
    return metricsProtocol;
  }

  /**
   * Returns the metrics host.
   *
   * @return the metrics host
   */
  public String getMetricsHost() {
    return metricsHost;
  }

  /**
   * Returns the metrics port.
   *
   * @return the metrics port
   */
  public Integer getMetricsPort() {
    return metricsPort;
  }

  /**
   * Returns the metric categories.
   *
   * @return the metric categories
   */
  public Set<MetricCategory> getMetricCategories() {
    return metricCategories;
  }

  /**
   * Returns whether metrics push is enabled.
   *
   * @return true if metrics push is enabled, otherwise false
   */
  public Boolean getMetricsPushEnabled() {
    return isMetricsPushEnabled;
  }

  /**
   * Returns the metrics push host.
   *
   * @return the metrics push host
   */
  public String getMetricsPushHost() {
    return metricsPushHost;
  }

  /**
   * Returns the metrics push port.
   *
   * @return the metrics push port
   */
  public Integer getMetricsPushPort() {
    return metricsPushPort;
  }

  /**
   * Returns the metrics push interval.
   *
   * @return the metrics push interval
   */
  public Integer getMetricsPushInterval() {
    return metricsPushInterval;
  }

  /**
   * Returns the metrics prometheus job.
   *
   * @return the metrics prometheus job
   */
  public String getMetricsPrometheusJob() {
    return metricsPrometheusJob;
  }
}
