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
 */
package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class MetricsCLIOptions implements CLIOptions<MetricsConfiguration.Builder> {
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

  private MetricsCLIOptions() {}

  public static MetricsCLIOptions create() {
    return new MetricsCLIOptions();
  }

  public static MetricsCLIOptions fromConfiguration(final MetricsConfiguration config) {
    final MetricsCLIOptions metricsOptions = create();
    metricsOptions.timersEnabled = config.isTimersEnabled();
    metricsOptions.idleTimeout = config.getIdleTimeout();
    return metricsOptions;
  }

  @Override
  public MetricsConfiguration.Builder toDomainObject() {
    return MetricsConfiguration.builder().timersEnabled(timersEnabled).idleTimeout(idleTimeout);
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        TIMERS_ENABLED_FLAG + "=" + timersEnabled.toString(),
        IDLE_TIMEOUT_FLAG + "=" + idleTimeout);
  }
}
