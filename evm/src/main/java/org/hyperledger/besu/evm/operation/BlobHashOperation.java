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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

/**
 * The BlobHash operation. As specified in <a
 * href="https://eips.ethereum.org/EIPS/eip-4844">EIP-4844</a>
 *
 * <p>Reads index from the top of the stack as big-endian uint256, and replaces it on the stack with
 * tx.message.blob_versioned_hashes[index] if index &lt; len(tx.message.blob_versioned_hashes), and
 * otherwise with a zeroed bytes32 value.
 */
public class BlobHashOperation extends AbstractOperation {

  /** BLOBHASH opcode number */
  public static final int OPCODE = 0x49;

  /**
   * Instantiates a new BlobHash operation.
   *
   * @param gasCalculator the gas calculator
   */
  public BlobHashOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "BLOBHASH", 1, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    if (!frame.stackHasItems(1)) {
      return new OperationResult(3, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    final org.hyperledger.besu.evm.UInt256 indexParam = frame.popStackItemUnsafe();
    if (frame.getVersionedHashes().isPresent()) {
      List<VersionedHash> versionedHashes = frame.getVersionedHashes().get();
      // If index doesn't fit in a positive int, it's out of range
      if (indexParam.u3() != 0 || indexParam.u2() != 0 || indexParam.u1() != 0
          || indexParam.u0() < 0 || indexParam.u0() > Integer.MAX_VALUE) {
        frame.pushStackItemUnsafe(org.hyperledger.besu.evm.UInt256.ZERO);
        return new OperationResult(3, null);
      }
      int versionedHashIndex = (int) indexParam.u0();
      if (versionedHashIndex < versionedHashes.size() && versionedHashIndex >= 0) {
        VersionedHash requested = versionedHashes.get(versionedHashIndex);
        frame.pushStackItemUnsafe(org.hyperledger.besu.evm.UInt256.fromBytesBE(requested.getBytes().toArrayUnsafe()));
      } else {
        frame.pushStackItemUnsafe(org.hyperledger.besu.evm.UInt256.ZERO);
      }
    } else {
      frame.pushStackItemUnsafe(org.hyperledger.besu.evm.UInt256.ZERO);
    }
    return new OperationResult(3, null);
  }
}
