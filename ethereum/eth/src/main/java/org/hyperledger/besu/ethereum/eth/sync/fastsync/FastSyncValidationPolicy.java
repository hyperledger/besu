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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

public class FastSyncValidationPolicy implements ValidationPolicy {
  private final float targetFullValidationRate;
  private final HeaderValidationMode lightValidationMode;
  private final HeaderValidationMode fullValidationMode;
  private final LabelledMetric<Counter> fastSyncValidationCounter;

  public FastSyncValidationPolicy(
      final float targetFullValidationRate,
      final HeaderValidationMode lightValidationMode,
      final HeaderValidationMode fullValidationMode,
      final LabelledMetric<Counter> fastSyncValidationCounter) {
    this.targetFullValidationRate = targetFullValidationRate;
    this.lightValidationMode = lightValidationMode;
    this.fullValidationMode = fullValidationMode;
    this.fastSyncValidationCounter = fastSyncValidationCounter;
  }

  @Override
  public HeaderValidationMode getValidationModeForNextBlock() {
    final HeaderValidationMode mode =
        Math.random() < targetFullValidationRate ? fullValidationMode : lightValidationMode;
    fastSyncValidationCounter.labels(mode.name()).inc();
    return mode;
  }
}
