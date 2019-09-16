/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

public class MetricsCLIOptionsTest
    extends AbstractCLIOptionsTest<MetricsConfiguration.Builder, MetricsCLIOptions> {

  @Override
  MetricsConfiguration.Builder createDefaultDomainObject() {
    return MetricsConfiguration.builder();
  }

  @Override
  MetricsConfiguration.Builder createCustomizedDomainObject() {
    return MetricsConfiguration.builder()
        .timersEnabled(!MetricsConfiguration.DEFAULT_TIMERS_ENABLED);
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
