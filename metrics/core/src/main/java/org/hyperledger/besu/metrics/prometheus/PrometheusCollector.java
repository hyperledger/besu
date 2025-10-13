/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.metrics.prometheus;

import org.hyperledger.besu.metrics.Observation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Label;
import io.prometheus.metrics.model.snapshots.Labels;

/** Wraps a native Prometheus collector inside the metric system */
public interface PrometheusCollector {

  /**
   * Get the identifier of the collector
   *
   * @return the identifier of the collector
   */
  String getIdentifier();

  /**
   * Register this collector to the specified registry
   *
   * @param registry the registry
   */
  void register(final PrometheusRegistry registry);

  /**
   * Unregister this collector from the specified registry
   *
   * @param registry the registry
   */
  void unregister(final PrometheusRegistry registry);

  /**
   * Stream the data points of this collector
   *
   * @return a stream of the data points of this collector
   */
  Stream<Observation> streamObservations();

  /**
   * Utility to get the label values as strings from native Prometheus labels
   *
   * @param labels the Prometheus labels
   * @return the label values as strings
   */
  static List<String> getLabelValues(final Labels labels) {
    return labels.stream().map(Label::getValue).toList();
  }

  /**
   * Add new values to an existing list of label values
   *
   * @param labelValues existing list of label values
   * @param values the values to add
   * @return a new list with new values appended to the original list
   */
  static List<String> addLabelValues(final List<String> labelValues, final String... values) {
    final var newList = new ArrayList<String>(labelValues.size() + values.length);
    newList.addAll(labelValues);
    Collections.addAll(newList, values);
    return newList;
  }
}
