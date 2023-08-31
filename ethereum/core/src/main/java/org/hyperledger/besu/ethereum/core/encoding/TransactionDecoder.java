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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;

public class TransactionDecoder {

  @FunctionalInterface
  interface Decoder {
    Transaction decode(RLPInput input, EncodingContext context);
  }

  private static final TypedTransactionDecoder TYPED_DECODER = new TypedTransactionDecoder();
  private static final FrontierTransactionDecoder FRONTIER_DECODER =
      new FrontierTransactionDecoder();

  /**
   * Decodes the given RLP input into a transaction.
   *
   * @param rlpInput the RLP input
   * @return the decoded transaction
   */
  public static Transaction decode(final RLPInput rlpInput) {
    return getDecoder(rlpInput).decode(rlpInput, EncodingContext.INTERNAL);
  }

  /**
   * Decodes the given input opaque bytes into a transaction.
   *
   * @param input the input bytes
   * @return the decoded transaction
   */
  public static Transaction decodeOpaqueBytes(final Bytes input) {
    return decodeOpaqueBytes(input, EncodingContext.INTERNAL);
  }

  /**
   * Decodes the input into a Transaction object, considering network specifics.
   *
   * <p>This method is particularly important for certain types of transactions that need to be
   * wrapped into a different format when they are broadcast. An example of this is blob
   * transactions as per EIP-4844.
   *
   * @param input The RLPInput to decode.
   * @return The decoded Transaction.
   */
  public static Transaction decodeForNetwork(final Bytes input) {
    return decodeOpaqueBytes(input, EncodingContext.NETWORK);
  }

  private static Transaction decodeOpaqueBytes(final Bytes input, final EncodingContext context) {
    try {
      final TransactionType transactionType = TransactionType.of(input.get(0));
      final Bytes transactionBytes = input.slice(1);
      return TYPED_DECODER.decode(transactionType, transactionBytes, context);
    } catch (final IllegalArgumentException __) {
      return decode(RLP.input(input));
    }
  }
  /**
   * Returns the appropriate decoder for the given RLP input.
   *
   * @param rlpInput the RLP input
   * @return the appropriate decoder
   */
  private static Decoder getDecoder(final RLPInput rlpInput) {
    return isTypedTransaction(rlpInput) ? TYPED_DECODER : FRONTIER_DECODER;
  }

  /**
   * Checks if the given RLP input is a typed transaction.
   *
   * <p>EIP-2718 Clients can differentiate between the legacy transactions and typed transactions by
   * looking at the first byte. If it starts with a value in the range [0, 0x7f] then it is a new
   * transaction type, if it starts with a value in the range [0xc0, 0xfe] then it is a legacy
   * transaction type. 0xff is not realistic for an RLP encoded transaction, so it is reserved for
   * future use as an extension sentinel value.
   *
   * @param rlpInput the RLP input
   * @return true if the RLP input is a typed transaction, false otherwise
   */
  private static boolean isTypedTransaction(final RLPInput rlpInput) {
    return !rlpInput.nextIsList();
  }
}
