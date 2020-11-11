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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;

public class VersionedPrivateTransaction {
  private final PrivateTransaction privateTransaction;
  private final Bytes32 version;

  public VersionedPrivateTransaction(
      final PrivateTransaction privateTransaction,
      final Optional<TransactionProcessingResult> result) {
    this(
        privateTransaction,
        result
            .map(value -> Bytes32.fromHexStringLenient(value.getOutput().toHexString()))
            .orElse(Bytes32.ZERO));
  }

  public VersionedPrivateTransaction(
      final PrivateTransaction privateTransaction, final Bytes32 version) {
    this.privateTransaction = privateTransaction;
    this.version = version;
  }

  public static VersionedPrivateTransaction readFrom(final RLPInput input) throws RLPException {
    input.enterList();
    final PrivateTransaction privateTransaction = PrivateTransaction.readFrom(input.readAsRlp());
    final Bytes32 version = input.readBytes32();
    input.leaveList();
    return new VersionedPrivateTransaction(privateTransaction, version);
  }

  public PrivateTransaction getPrivateTransaction() {
    return privateTransaction;
  }

  public Bytes32 getVersion() {
    return version;
  }

  public void writeTo(final BytesValueRLPOutput rlpOutput) {
    rlpOutput.startList();
    privateTransaction.writeTo(rlpOutput);
    rlpOutput.writeBytes(version);
    rlpOutput.endList();
  }
}
