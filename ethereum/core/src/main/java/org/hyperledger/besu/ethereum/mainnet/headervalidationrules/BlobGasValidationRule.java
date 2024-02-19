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

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validation rule to check if the block header's excess blob gas matches the calculated value. */
public class BlobGasValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(BlobGasValidationRule.class);

  private final GasCalculator gasCalculator;

  public BlobGasValidationRule(final GasCalculator gasCalculator) {
    this.gasCalculator = gasCalculator;
  }

  /**
   * Validates the block header by checking if the header's excess blob gas matches the calculated
   * value based on the parent header, as well that the used blobGas is a multiple of GAS_PER_BLOB.
   */
  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    long headerExcessBlobGas = header.getExcessBlobGas().map(BlobGas::toLong).orElse(0L);
    long parentExcessBlobGas = parent.getExcessBlobGas().map(BlobGas::toLong).orElse(0L);
    long parentBlobGasUsed = parent.getBlobGasUsed().orElse(0L);

    long calculatedExcessBlobGas =
        gasCalculator.computeExcessBlobGas(parentExcessBlobGas, parentBlobGasUsed);

    if (headerExcessBlobGas != calculatedExcessBlobGas) {
      LOG.info(
          "Invalid block header: header excessBlobGas {} and calculated excessBlobGas {} do not match",
          headerExcessBlobGas,
          calculatedExcessBlobGas);
      return false;
    }
    long headerBlobGasUsed = header.getBlobGasUsed().orElse(0L);
    if (headerBlobGasUsed % CancunGasCalculator.BLOB_GAS_PER_BLOB != 0) {
      LOG.info(
          "blob gas used must be multiple of GAS_PER_BLOB ({})",
          CancunGasCalculator.BLOB_GAS_PER_BLOB);
      return false;
    }
    return true;
  }
}
