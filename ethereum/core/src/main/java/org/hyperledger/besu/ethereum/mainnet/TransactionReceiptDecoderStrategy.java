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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.AmsterdamTransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.FrontierTransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

/**
 * Strategy interface for decoding transaction receipts from RLP.
 *
 * <p>Different hard forks may have different receipt formats. For example, EIP-7778 (Amsterdam+)
 * introduces a mandatory {@code gasSpent} field in receipts. This strategy allows the appropriate
 * decoder to be selected based on the protocol spec for a given block.
 *
 * @see FrontierTransactionReceiptDecoder
 * @see AmsterdamTransactionReceiptDecoder
 */
@FunctionalInterface
public interface TransactionReceiptDecoderStrategy {

  /** Pre-Amsterdam strategy: decodes receipts without mandatory gasSpent field. */
  TransactionReceiptDecoderStrategy FRONTIER = FrontierTransactionReceiptDecoder::readFrom;

  /** Amsterdam+ strategy: decodes receipts with mandatory gasSpent field (EIP-7778). */
  TransactionReceiptDecoderStrategy AMSTERDAM = AmsterdamTransactionReceiptDecoder::readFrom;

  /**
   * Decodes a transaction receipt from RLP.
   *
   * @param input the RLP-encoded transaction receipt
   * @param revertReasonAllowed whether the receipt is allowed to contain a revert reason
   * @return the decoded transaction receipt
   */
  TransactionReceipt decode(RLPInput input, boolean revertReasonAllowed);
}
