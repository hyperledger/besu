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
package org.hyperledger.besu.ethereum.core.encoding.receipt;

import org.hyperledger.besu.datatypes.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.FrontierTransactionReceiptDecoder.ReceiptComponents;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * Decoder for Amsterdam+ transaction receipts (EIP-7778).
 *
 * <p>In Amsterdam and later forks, the gasSpent field is mandatory. This decoder reuses utility
 * methods from {@link FrontierTransactionReceiptDecoder} but adds gasSpent reading.
 *
 * <pre>
 * amsterdam-receipt = [
 *   post-state-or-status: {B_32, {0, 1}},
 *   cumulative-gas: P,        (pre-refund gas for block accounting)
 *   bloom: B_256,
 *   logs: [log₁, log₂, ...],
 *   gas-spent: P,             (mandatory, post-refund gas - what user pays)
 *   revert-reason?: B         (optional, Besu-specific extension)
 * ]
 * </pre>
 */
public class AmsterdamTransactionReceiptDecoder {

  /**
   * Creates a transaction receipt for the given RLP, expecting mandatory gasSpent (EIP-7778).
   *
   * @param rlpInput the RLP-encoded transaction receipt
   * @param revertReasonAllowed whether the rlp input is allowed to have a revert reason
   * @return the transaction receipt with gasSpent field populated
   */
  public static TransactionReceipt readFrom(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    // The first byte indicates whether the receipt is typed (eth/68) or flat (eth/69).
    if (!rlpInput.nextIsList()) {
      return decodeTypedReceipt(rlpInput, revertReasonAllowed);
    } else {
      return decodeFlatReceipt(rlpInput, revertReasonAllowed);
    }
  }

  private static TransactionReceipt decodeTypedReceipt(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    final ReceiptComponents components =
        FrontierTransactionReceiptDecoder.decodeTypedReceiptComponents(rlpInput);
    // Read mandatory gasSpent (EIP-7778)
    final long gasSpent = components.input().readLongScalar();
    Optional<Bytes> revertReason =
        FrontierTransactionReceiptDecoder.readMaybeRevertReason(
            components.input(), revertReasonAllowed);
    components.input().leaveList();
    return FrontierTransactionReceiptDecoder.createReceipt(
        components, Optional.of(gasSpent), revertReason);
  }

  private static TransactionReceipt decodeFlatReceipt(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    rlpInput.enterList();
    // Flat receipts can be either legacy or eth/69 receipts.
    final RLPInput firstElement = rlpInput.readAsRlp();
    final RLPInput secondElement = rlpInput.readAsRlp();
    final boolean isCompacted = FrontierTransactionReceiptDecoder.isNextNotBloomFilter(rlpInput);
    LogsBloomFilter bloomFilter = null;
    if (!isCompacted) {
      bloomFilter = LogsBloomFilter.readFrom(rlpInput);
    }
    // eth/69 receipts don't have gasSpent in the same format - for now, only handle legacy
    boolean isEth69Receipt = isCompacted && !rlpInput.nextIsList();
    TransactionReceipt receipt;
    if (isEth69Receipt) {
      // eth/69 format doesn't include gasSpent - throw or handle appropriately
      throw new IllegalStateException(
          "eth/69 receipt format is not supported for Amsterdam+ receipts with mandatory gasSpent");
    } else {
      receipt =
          decodeLegacyReceipt(
              rlpInput, firstElement, secondElement, bloomFilter, revertReasonAllowed);
    }
    rlpInput.leaveList();
    return receipt;
  }

  private static TransactionReceipt decodeLegacyReceipt(
      final RLPInput input,
      final RLPInput statusOrStateRootRlpInput,
      final RLPInput cumulativeGasRlpInput,
      final LogsBloomFilter bloomFilter,
      final boolean revertReasonAllowed) {
    final ReceiptComponents components =
        FrontierTransactionReceiptDecoder.decodeLegacyReceiptComponents(
            input, statusOrStateRootRlpInput, cumulativeGasRlpInput, bloomFilter);
    // Read mandatory gasSpent (EIP-7778)
    final long gasSpent = components.input().readLongScalar();
    Optional<Bytes> revertReason =
        FrontierTransactionReceiptDecoder.readMaybeRevertReason(
            components.input(), revertReasonAllowed);
    return FrontierTransactionReceiptDecoder.createReceipt(
        components, Optional.of(gasSpent), revertReason);
  }
}
