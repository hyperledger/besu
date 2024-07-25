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
package org.hyperledger.besu.cli.options;

import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;
import static org.hyperledger.besu.metrics.MetricsProtocol.PROMETHEUS;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PORT;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PUSH_PORT;

import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.metrics.MetricsProtocol;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.Set;

import picocli.CommandLine;

/** Command line options for configuring metrics. */
public class MetricsOptionGroup {

  /** Command line options for configuring metrics. */
  public MetricsOptionGroup() {}

  /** Set to start the metrics exporter */
  @CommandLine.Option(
      names = {"--metrics-enabled"},
      description = "Set to start the metrics exporter (default: ${DEFAULT-VALUE})")
  public final Boolean isMetricsEnabled = false;

  /** Metrics protocol, one of PROMETHEUS, OPENTELEMETRY or NONE */
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--metrics-protocol"},
      description =
          "Metrics protocol, one of PROMETHEUS, OPENTELEMETRY or NONE. (default: ${DEFAULT-VALUE})")
  public MetricsProtocol metricsProtocol = PROMETHEUS;

  /** Host for the metrics exporter to listen on */
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--metrics-host"},
      paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
      description = "Host for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  public String metricsHost;

  /** Port for the metrics exporter to listen on */
  @CommandLine.Option(
      names = {"--metrics-port"},
      paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
      description = "Port for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  public final Integer metricsPort = DEFAULT_METRICS_PORT;

  /** Comma separated list of categories to track metrics for */
  @CommandLine.Option(
      names = {"--metrics-category", "--metrics-categories"},
      paramLabel = "<category name>",
      split = ",",
      arity = "1..*",
      description =
          "Comma separated list of categories to track metrics for (default: ${DEFAULT-VALUE})")
  public final Set<MetricCategory> metricCategories = DEFAULT_METRIC_CATEGORIES;

  /** Enable the metrics push gateway integration */
  @CommandLine.Option(
      names = {"--metrics-push-enabled"},
      description = "Enable the metrics push gateway integration (default: ${DEFAULT-VALUE})")
  public final Boolean isMetricsPushEnabled = false;

  /** Host of the Prometheus Push Gateway for push mode */
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--metrics-push-host"},
      paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
      description = "Host of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  public String metricsPushHost;

  /** Port of the Prometheus Push Gateway for push mode */
  @CommandLine.Option(
      names = {"--metrics-push-port"},
      paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
      description = "Port of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  public final Integer metricsPushPort = DEFAULT_METRICS_PUSH_PORT;

  /** Interval in seconds to push metrics when in push mode */
  @CommandLine.Option(
      names = {"--metrics-push-interval"},
      paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Interval in seconds to push metrics when in push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  public final Integer metricsPushInterval = 15;

  /** Job name to use when in push mode */
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--metrics-push-prometheus-job"},
      description = "Job name to use when in push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  public String metricsPrometheusJob = "besu-client";
}
