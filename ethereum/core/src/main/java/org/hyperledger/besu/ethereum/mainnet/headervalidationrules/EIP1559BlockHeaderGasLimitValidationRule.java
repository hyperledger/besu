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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EIP1559BlockHeaderGasLimitValidationRule implements DetachedBlockHeaderValidationRule {
  private static final Logger LOG = LogManager.getLogger();
  private final EIP1559 eip1559;
  private final FeeMarket feeMarket = FeeMarket.eip1559();

  public EIP1559BlockHeaderGasLimitValidationRule(final EIP1559 eip1559) {
    this.eip1559 = eip1559;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    if (!eip1559.isEIP1559(header.getNumber())) {
      return true;
    }
    if (eip1559.isEIP1559Finalized(header.getNumber())) {
      if (header.getGasLimit() != feeMarket.getMaxGas()) {
        LOG.trace(
            "Invalid block header: gas limit {} does not equal expected gas limit {}",
            header.getGasLimit(),
            feeMarket.getMaxGas());
        return false;
      } else {
        return true;
      }
    }

    final long numberOfIncrements = header.getNumber() - eip1559.getForkBlock();
    final long expectedGasLimit =
        (feeMarket.getMaxGas() / 2) + (numberOfIncrements * feeMarket.getGasIncrementAmount());
    if (header.getGasLimit() != expectedGasLimit) {
      LOG.trace(
          "Invalid block header: gas limit {} does not equal expected gas limit {}",
          header.getGasLimit(),
          expectedGasLimit);
      return false;
    }

    return true;
  }
}
