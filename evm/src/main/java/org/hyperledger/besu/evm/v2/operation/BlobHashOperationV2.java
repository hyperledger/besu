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
package org.hyperledger.besu.evm.v2.operation;

import static org.hyperledger.besu.evm.v2.operation.StackUtil.pushHash;
import static org.hyperledger.besu.evm.v2.operation.StackUtil.pushZero;

import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.List;

/** The Blob hash operation. */
public class BlobHashOperationV2 extends AbstractOperationV2 {

  /** The BLOBHASH opcode number. */
  public static final int OPCODE = 0x49;

  private final OperationResult successResponse;
  private final OperationResult outOfGasResponse;

  /**
   * Instantiates a new Blob hash operation.
   *
   * @param gasCalculator the gas calculator
   */
  public BlobHashOperationV2(final GasCalculator gasCalculator) {
    super(OPCODE, "BLOBHASH", 1, 1, gasCalculator);
    successResponse = new OperationResult(gasCalculator.getVeryLowTierGasCost(), null);
    outOfGasResponse =
        new OperationResult(
            gasCalculator.getVeryLowTierGasCost(), ExceptionalHaltReason.INSUFFICIENT_GAS);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    // stack before gas, as in v1, so that both halt for the same reason
    if (!frame.stackHasItemsV2(1)) return UNDERFLOW_RESPONSE;
    if (frame.getRemainingGas() < successResponse.getGasCost()) return outOfGasResponse;
    final long[] stack = frame.stackDataV2();
    final int top = frame.stackTopV2();
    final int offset = (top - 1) << 2;
    final long index = stack[offset + 3];
    final List<VersionedHash> versionedHashes = frame.getVersionedHashes().orElse(null);

    if ((stack[offset] | stack[offset + 1] | stack[offset + 2]) == 0
        && index >= 0
        && versionedHashes != null
        && index < versionedHashes.size()) {
      pushHash(versionedHashes.get((int) index), stack, top - 1);
    } else {
      pushZero(stack, top - 1);
    }
    return successResponse;
  }
}
