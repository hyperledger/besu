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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;

public class SyncBlockBody {

  private static final String EMPTY_BODY_RLP = "0xc2c0c0";
  private static final String EMPTY_BODY_WITH_EMPTY_WITHDRAWALS_RLP = "0xc3c0c0c0";

  private final Bytes bytesOfWrappedRlpInput;
  private final List<Bytes> transactionBytes;
  private final Bytes ommersListBytes;
  private final Optional<List<Bytes>> withdrawalBytes;
  private final BlockHeaderFunctions blockHeaderFunctions;

  public SyncBlockBody(
      final Bytes bytesOfWrappedRlpInput,
      final List<Bytes> transactionBytes,
      final Bytes ommersListBytes,
      final List<Bytes> withdrawalBytes,
      final ProtocolSchedule protocolSchedule) {
    this.bytesOfWrappedRlpInput = bytesOfWrappedRlpInput;
    this.transactionBytes = transactionBytes;
    this.ommersListBytes = ommersListBytes;
    this.withdrawalBytes = Optional.ofNullable(withdrawalBytes);
    this.blockHeaderFunctions = ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
  }

  public static SyncBlockBody emptyWithNullWithdrawals(final ProtocolSchedule protocolSchedule) {
    return new SyncBlockBody(
        Bytes.fromHexString(EMPTY_BODY_RLP),
        Collections.emptyList(),
        Bytes.EMPTY,
        null,
        protocolSchedule);
  }

  public static SyncBlockBody emptyWithEmptyWithdrawals(final ProtocolSchedule protocolSchedule) {
    return new SyncBlockBody(
        Bytes.fromHexString(EMPTY_BODY_WITH_EMPTY_WITHDRAWALS_RLP),
        Collections.emptyList(),
        Bytes.EMPTY,
        Collections.emptyList(),
        protocolSchedule);
  }

  /**
   * Read all fields from the block body expecting a list wrapping them An example of valid body
   * structure that this method would be able to read is: [[txs],[ommers],[withdrawals]] This is
   * used for decoding list of bodies
   *
   * @param input The RLP-encoded input
   * @param allowEmptyBody A flag indicating whether an empty body is allowed
   * @return the decoded BlockBody from the RLP
   */
  public static SyncBlockBody readWrappedBodyFrom(
      final RLPInput input, final boolean allowEmptyBody, final ProtocolSchedule protocolSchedule) {
    final Bytes bytesCurrentBody = input.currentListAsBytesNoCopy(false);
    input.enterList();
    if (input.isEndOfCurrentList() && allowEmptyBody) {
      // empty block [] -> Return empty body.
      input.leaveList();
      return emptyWithNullWithdrawals(protocolSchedule);
    }
    // get a list of Bytes for the transactions
    final ArrayList<Bytes> transactionBytes = new ArrayList<>();
    input.enterList();
    while (!input.isEndOfCurrentList()) {
      if (input.nextIsList()) {
        // This is for Transactions before Berlin fork
        transactionBytes.add(input.currentListAsBytesNoCopy(true));
      } else {
        // This is for Transactions after Berlin fork (typed transactions, EIP-2718)
        transactionBytes.add(input.readBytes());
      }
    }
    input.leaveList();
    // get the Bytes for the ommers
    Bytes ommersListBytes = input.currentListAsBytesNoCopy(true);
    // get a list of Bytes for the withdrawals
    ArrayList<Bytes> withdrawalBytes = null;
    if (!input.isEndOfCurrentList()) {
      withdrawalBytes = new ArrayList<>();
      input.enterList();
      while (!input.isEndOfCurrentList()) {
        withdrawalBytes.add(input.currentListAsBytesNoCopy(true));
      }
      input.leaveList();
    }
    input.leaveList();
    return new SyncBlockBody(
        bytesCurrentBody, transactionBytes, ommersListBytes, withdrawalBytes, protocolSchedule);
  }

  public List<Bytes> getEncodedTransactions() {
    return transactionBytes;
  }

  public int getTransactionCount() {
    return transactionBytes.size();
  }

  public Hash getTransactionsRoot() {
    return Util.getRootFromListOfBytes(transactionBytes);
  }

  public Hash getOmmersHash() {
    return Hash.wrap(org.hyperledger.besu.crypto.Hash.keccak256(ommersListBytes));
  }

  public Hash getWithdrawalsRoot() {
    return withdrawalBytes.map(Util::getRootFromListOfBytes).orElse(null);
  }

  public Bytes getRlp() {
    return bytesOfWrappedRlpInput;
  }

  public Supplier<BlockBody> getBodySupplier() {
    return () ->
        BlockBody.readWrappedBodyFrom(
            new BytesValueRLPInput(bytesOfWrappedRlpInput, false), blockHeaderFunctions, false);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SyncBlockBody blockBody = (SyncBlockBody) o;
    return Objects.equals(bytesOfWrappedRlpInput, blockBody.bytesOfWrappedRlpInput);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytesOfWrappedRlpInput);
  }

  @Override
  public String toString() {
    return "SyncBlockBody{"
        + "bytesOfWrappedRlpInput="
        + bytesOfWrappedRlpInput
        + ", transactionBytes="
        + transactionBytes
        + ", ommersListBytes="
        + ommersListBytes
        + ", withdrawalBytes="
        + withdrawalBytes
        + '}';
  }
}
