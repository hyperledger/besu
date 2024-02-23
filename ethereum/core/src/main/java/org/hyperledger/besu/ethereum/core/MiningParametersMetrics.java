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
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class MiningParametersMetrics {
  public static final String MIN_GAS_PRICE_GAUGE = "min_gas_price";
  public static final String MIN_PRIORITY_FEE_GAUGE = "min_priority_fee";

  public MiningParametersMetrics(
      final MetricsSystem metricsSystem, final MiningParameters miningParameters) {

    metricsSystem.createGauge(
        BesuMetricCategory.ETHEREUM,
        MIN_GAS_PRICE_GAUGE,
        "Gauge to measure the runtime value of min-gas-price",
        () -> miningParameters.getMinTransactionGasPrice().toBigInteger().doubleValue());

    metricsSystem.createGauge(
        BesuMetricCategory.ETHEREUM,
        MIN_PRIORITY_FEE_GAUGE,
        "Gauge to measure the runtime value of min-priority-fee",
        () -> miningParameters.getMinPriorityFeePerGas().toBigInteger().doubleValue());
  }
}
