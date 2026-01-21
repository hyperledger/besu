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

import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * Decoder for Amsterdam+ transaction receipts (EIP-7778).
 *
 * <p>In Amsterdam and later forks, the gasSpent field is mandatory. This decoder extends the base
 * {@link TransactionReceiptDecoder} and expects gasSpent to always be present after logs.
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
 *
 * <p>Unlike the base decoder which uses a size heuristic to detect gasSpent, this decoder reads
 * gasSpent unconditionally since it's guaranteed to be present in Amsterdam+ receipts.
 */
public class AmsterdamTransactionReceiptDecoder extends TransactionReceiptDecoder {

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
    final ReceiptComponents components = decodeTypedReceiptComponents(rlpInput);
    // EIP-7778: gasSpent is mandatory in Amsterdam+ receipts
    long gasSpent = components.input().readLongScalar();
    Optional<Bytes> revertReason = readMaybeRevertReason(components.input(), revertReasonAllowed);
    components.input().leaveList();
    return createReceipt(components, Optional.of(gasSpent), revertReason);
  }

  private static TransactionReceipt decodeFlatReceipt(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    rlpInput.enterList();
    // Flat receipts: first element is stateRootOrStatus, second is cumulativeGas
    final RLPInput firstElement = rlpInput.readAsRlp();
    final RLPInput secondElement = rlpInput.readAsRlp();
    final boolean isCompacted = isNextNotBloomFilter(rlpInput);
    LogsBloomFilter bloomFilter = null;
    if (!isCompacted) {
      bloomFilter = LogsBloomFilter.readFrom(rlpInput);
    }
    // Decode as legacy receipt with mandatory gasSpent
    final ReceiptComponents components =
        decodeLegacyReceiptComponents(rlpInput, firstElement, secondElement, bloomFilter);
    // EIP-7778: gasSpent is mandatory in Amsterdam+ receipts
    long gasSpent = components.input().readLongScalar();
    Optional<Bytes> revertReason = readMaybeRevertReason(components.input(), revertReasonAllowed);
    rlpInput.leaveList();
    return createReceipt(components, Optional.of(gasSpent), revertReason);
  }

  private static boolean isNextNotBloomFilter(final RLPInput input) {
    return input.nextIsList() || input.nextSize() != LogsBloomFilter.BYTE_SIZE;
  }
}
