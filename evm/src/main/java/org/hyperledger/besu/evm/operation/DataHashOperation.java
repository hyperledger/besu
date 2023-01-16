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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class DataHashOperation extends AbstractOperation {

  public static final int OPCODE = 0x49;

  public DataHashOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "DATAHASH", 1, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    int blobIndex = frame.popStackItem().toInt();
    final Optional<List<Hash>> maybeHashes = frame.getVersionedHashes();
    if (frame.getVersionedHashes().isPresent()) {
      List<Hash> versionedHashes = maybeHashes.get();
      if (blobIndex < versionedHashes.size()) {
        Hash requested = versionedHashes.get(blobIndex);
        frame.pushStackItem(requested);
      } else {
        frame.pushStackItem(Bytes.EMPTY);
      }
    } else {
      return new OperationResult(3, ExceptionalHaltReason.INVALID_OPERATION);
    }
    return new OperationResult(3, ExceptionalHaltReason.NONE);
  }

  @Override
  public boolean isVirtualOperation() {
    return super.isVirtualOperation();
  }
}
