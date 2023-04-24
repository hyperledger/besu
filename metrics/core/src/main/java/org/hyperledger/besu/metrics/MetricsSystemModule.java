/*
 * Copyright Hyperledger Besu Contributors.
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

/**
 * Dagger module for providing the {@link MetricsSystem} and {@link ObservableMetricsSystem}
 * instances.
 */
@Module
public class MetricsSystemModule {

  @Provides
  @Singleton
  MetricsSystem provideMetricsSystem(final MetricsConfiguration metricsConfig) {
    return MetricsSystemFactory.create(metricsConfig);
  }

  @Provides
  @Singleton
  ObservableMetricsSystem provideObservableMetricsSystem(final MetricsConfiguration metricsConfig) {
    return MetricsSystemFactory.create(metricsConfig);
  }
}
