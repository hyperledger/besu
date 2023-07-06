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
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.cli.options.unstable.MetricsCLIOptions;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MetricsCLIOptionsTest
    extends AbstractCLIOptionsTest<MetricsConfiguration.Builder, MetricsCLIOptions> {

  @Override
  MetricsConfiguration.Builder createDefaultDomainObject() {
    return MetricsConfiguration.builder();
  }

  @Override
  MetricsConfiguration.Builder createCustomizedDomainObject() {
    return MetricsConfiguration.builder()
        .timersEnabled(!MetricsConfiguration.DEFAULT_METRICS_TIMERS_ENABLED)
        .idleTimeout(MetricsConfiguration.DEFAULT_METRICS_IDLE_TIMEOUT_SECONDS);
  }

  @Override
  MetricsCLIOptions optionsFromDomainObject(final MetricsConfiguration.Builder domainObject) {
    return MetricsCLIOptions.fromConfiguration(domainObject.build());
  }

  @Override
  MetricsCLIOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getMetricsCLIOptions();
  }
}
