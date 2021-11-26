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
package org.hyperledger.besu.metrics.noop;

import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

public class NoOpValueCollector implements LabelledGauge {
  private final List<String> labelValuesCreated = new ArrayList<>();

  @Override
  public synchronized void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
    final String labelValuesString = String.join(",", labelValues);
    if (labelValuesCreated.contains(labelValuesString)) {
      throw new IllegalArgumentException(
          String.format("A gauge has already been created for label values %s", labelValuesString));
    }
    labelValuesCreated.add(labelValuesString);
  }
}
