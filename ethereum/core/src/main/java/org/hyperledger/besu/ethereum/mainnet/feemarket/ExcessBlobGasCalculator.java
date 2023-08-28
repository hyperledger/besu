/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet.feemarket;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

/** Calculates the excess blob gas for a parent block header. */
public class ExcessBlobGasCalculator {
  /**
   * public class ExcessBlobGasCalculator { /** Calculates the excess blob gas for a parent block
   * header.
   *
   * @param protocolSpec The protocol specification.
   * @param parentHeader The parent block header.
   * @return The excess blob gas.
   */
  public static BlobGas calculateExcessBlobGasForParent(
      final ProtocolSpec protocolSpec, final BlockHeader parentHeader) {
    // Blob Data Excess
    long headerExcess =
        protocolSpec
            .getGasCalculator()
            .computeExcessBlobGas(
                parentHeader.getExcessBlobGas().map(BlobGas::toLong).orElse(0L),
                parentHeader.getBlobGasUsed().orElse(0L));
    return BlobGas.of(headerExcess);
  }
}
