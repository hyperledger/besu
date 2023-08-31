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
package org.hyperledger.besu.ethereum.core.encoding;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder.Decoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

public class TypedTransactionDecoder implements Decoder {
  // Map of transaction types to their respective decoders
  private static final ImmutableMap<TransactionType, Decoder> TYPED_TRANSACTION_DECODERS =
      ImmutableMap.of(
          TransactionType.ACCESS_LIST,
          new AccessListTransactionDecoder(),
          TransactionType.EIP1559,
          new EIP1559TransactionDecoder(),
          TransactionType.BLOB,
          new BlobTransactionDecoder());

  /**
   * Decodes the input into a Transaction object.
   *
   * @param input The RLPInput to decode.
   * @return The decoded Transaction.
   */
  @Override
  public Transaction decode(final RLPInput input, final EncodingContext context) {
    final Bytes typedTransactionBytes = input.readBytes();
    final TransactionType transactionType = TransactionType.of(typedTransactionBytes.get(0) & 0xff);
    return decode(transactionType, typedTransactionBytes.slice(1), context);
  }

  /**
   * Decodes the bytes into a Transaction of the specified type.
   *
   * @param transactionType The type of the transaction to decode.
   * @param bytes The bytes to decode.
   * @return The decoded Transaction.
   */
  public Transaction decode(
      final TransactionType transactionType, final Bytes bytes, final EncodingContext context) {
    final Decoder decoder = getDecoder(transactionType);
    return decoder.decode(RLP.input(bytes), context);
  }
  /**
   * Retrieves the decoder for the specified transaction type.
   *
   * @param transactionType The type of the transaction for which to get the decoder.
   * @return The decoder for the specified transaction type.
   */
  private static Decoder getDecoder(final TransactionType transactionType) {
    return checkNotNull(
        TYPED_TRANSACTION_DECODERS.get(transactionType),
        "Developer Error. A supported transaction type %s has no associated decoding logic",
        transactionType);
  }
}
