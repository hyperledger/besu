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

import org.hyperledger.besu.cli.options.stable.MetricsOptions;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MetricsOptionsTest
    extends AbstractCLIOptionsTest<MetricsConfiguration.Builder, MetricsOptions> {

  @Override
  protected MetricsConfiguration.Builder createDefaultDomainObject() {
    return MetricsConfiguration.builder();
  }

  @Override
  protected MetricsConfiguration.Builder createCustomizedDomainObject() {
    return MetricsConfiguration.builder()
        .timersEnabled(!MetricsConfiguration.DEFAULT_METRICS_TIMERS_ENABLED)
        .idleTimeout(MetricsConfiguration.DEFAULT_METRICS_IDLE_TIMEOUT_SECONDS);
  }

  @Override
  protected MetricsOptions optionsFromDomainObject(
      final MetricsConfiguration.Builder domainObject) {
    return MetricsOptions.fromConfiguration(domainObject.build());
  }

  @Override
  protected MetricsOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getMetricsOptions();
  }
}
