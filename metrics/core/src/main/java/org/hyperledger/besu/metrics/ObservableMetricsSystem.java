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
package org.hyperledger.besu.metrics;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.stream.Stream;

/** The observable metrics system is used to inspect metrics for debug reasons */
public interface ObservableMetricsSystem extends MetricsSystem {
  /**
   * Stream observations by category
   *
   * @param category the category
   * @return the observations stream
   */
  Stream<Observation> streamObservations(MetricCategory category);

  /**
   * Stream observations
   *
   * @return the observations stream
   */
  Stream<Observation> streamObservations();

  /** Unregister all the collectors and perform other cleanup tasks */
  void shutdown();
}
