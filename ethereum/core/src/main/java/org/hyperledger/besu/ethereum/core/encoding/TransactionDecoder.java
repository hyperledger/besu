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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

public class TransactionDecoder {

  @FunctionalInterface
  interface Decoder {
    Transaction decode(RLPInput input);
  }

  private static final ImmutableMap<TransactionType, Decoder> TYPED_TRANSACTION_DECODERS =
      ImmutableMap.of(
          TransactionType.ACCESS_LIST,
          AccessListTransactionDecoder::decode,
          TransactionType.EIP1559,
          EIP1559TransactionDecoder::decode,
          TransactionType.BLOB,
          BlobTransactionDecoder::decode);

  private static final ImmutableMap<TransactionType, Decoder> NETWORK_TRANSACTION_DECODERS =
      ImmutableMap.of(TransactionType.BLOB, BlobPooledTransactionDecoder::decode);

  /**
   * Decodes an RLP input into a transaction. If the input represents a typed transaction, it uses
   * the appropriate decoder for that type. Otherwise, it uses the frontier decoder.
   *
   * @param rlpInput the RLP input
   * @return the decoded transaction
   */
  public static Transaction decodeRLP(
      final RLPInput rlpInput, final EncodingContext encodingContext) {
    if (isTypedTransaction(rlpInput)) {
      return decodeTypedTransaction(rlpInput, encodingContext);
    } else {
      return FrontierTransactionDecoder.decode(rlpInput);
    }
  }

  /**
   * Decodes a typed transaction from an RLP input. It first reads the transaction type from the
   * input, then uses the appropriate decoder for that type.
   *
   * @param rlpInput the RLP input
   * @return the decoded transaction
   */
  private static Transaction decodeTypedTransaction(
      final RLPInput rlpInput, final EncodingContext context) {
    final Bytes typedTransactionBytes = rlpInput.readBytes();
    TransactionType transactionType = getTransactionType(typedTransactionBytes);
    Decoder decoder = getDecoder(transactionType, context);
    return decoder.decode(RLP.input(typedTransactionBytes.slice(1)));
  }

  /**
   * Decodes a transaction from opaque bytes. It first reads the transaction type from the bytes. If
   * the type is null, it decodes the bytes as an RLP input. Otherwise, it uses the appropriate
   * decoder for the type.
   *
   * @param opaqueBytes the opaque bytes
   * @param context the encoding context
   * @return the decoded transaction
   */
  public static Transaction decodeOpaqueBytes(
      final Bytes opaqueBytes, final EncodingContext context) {
    final TransactionType transactionType;
    try {
      transactionType = getTransactionType(opaqueBytes);
    } catch (IllegalArgumentException ex) {
      return decodeRLP(RLP.input(opaqueBytes), context);
    }
    final Bytes transactionBytes = opaqueBytes.slice(1);
    return getDecoder(transactionType, context).decode(RLP.input(transactionBytes));
  }

  /**
   * Gets the transaction type from opaque bytes. The type is represented by the first byte of the
   * data. If the byte does not represent a valid type, it returns null.
   *
   * @param opaqueBytes the opaque bytes
   * @return the transaction type, or null if the first byte does not represent a valid type
   */
  private static TransactionType getTransactionType(final Bytes opaqueBytes) {
    return TransactionType.of(opaqueBytes.get(0));
  }

  /**
   * Checks if the given RLP input is a typed transaction.
   *
   * <p>See EIP-2718
   *
   * <p>If it starts with a value in the range [0, 0x7f] then it is a new transaction type
   *
   * <p>if it starts with a value in the range [0xc0, 0xfe] then it is a legacy transaction type
   *
   * @param rlpInput the RLP input
   * @return true if the RLP input is a typed transaction, false otherwise
   */
  private static boolean isTypedTransaction(final RLPInput rlpInput) {
    return !rlpInput.nextIsList();
  }

  /**
   * Gets the decoder for a given transaction type and encoding context. If the context is NETWORK,
   * it uses the network decoder for the type. Otherwise, it uses the typed decoder.
   *
   * @param transactionType the transaction type
   * @param context the encoding context
   * @return the decoder
   */
  private static Decoder getDecoder(
      final TransactionType transactionType, final EncodingContext context) {
    if (context.equals(EncodingContext.TRANSACTION_POOL)) {
      return getNetworkDecoder(transactionType);
    }
    return getTypedDecoder(transactionType);
  }

  /**
   * Gets the typed decoder for a given transaction type. If there is no decoder for the type, it
   * throws an exception.
   *
   * @param transactionType the transaction type
   * @return the decoder
   */
  private static Decoder getTypedDecoder(final TransactionType transactionType) {
    return checkNotNull(
        TYPED_TRANSACTION_DECODERS.get(transactionType),
        "Developer Error. A supported transaction type %s has no associated decoding logic",
        transactionType);
  }

  /**
   * Gets the network decoder for a given transaction type. If there is no network decoder for the
   * type, it uses the typed decoder.
   *
   * @param transactionType the transaction type
   * @return the decoder
   */
  private static Decoder getNetworkDecoder(final TransactionType transactionType) {
    return NETWORK_TRANSACTION_DECODERS.getOrDefault(
        transactionType, getTypedDecoder(transactionType));
  }
}
