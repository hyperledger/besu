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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validation rule to check if the block header's excess data gas matches the calculated value. */
public class DataGasValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(DataGasValidationRule.class);

  private final GasCalculator gasCalculator;

  public DataGasValidationRule(final GasCalculator gasCalculator) {
    this.gasCalculator = gasCalculator;
  }

  /**
   * Validates the block header by checking if the header's excess data gas matches the calculated
   * value based on the parent header.
   */
  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    long headerExcessDataGas = header.getExcessDataGas().map(DataGas::toLong).orElse(0L);
    long parentExcessDataGas = parent.getExcessDataGas().map(DataGas::toLong).orElse(0L);
    long parentDataGasUsed = parent.getDataGasUsed().orElse(0L);

    long calculatedExcessDataGas =
        gasCalculator.computeExcessDataGas(parentExcessDataGas, parentDataGasUsed);

    if (headerExcessDataGas != calculatedExcessDataGas) {
      LOG.info(
          "Invalid block header: header excessDataGas {} and calculated excessDataGas {} do not match",
          headerExcessDataGas,
          calculatedExcessDataGas);
      return false;
    }
    return true;
  }
}
