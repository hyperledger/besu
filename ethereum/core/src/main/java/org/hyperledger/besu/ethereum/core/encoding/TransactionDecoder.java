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

import com.google.common.annotations.VisibleForTesting;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

public class TransactionDecoder {

  @FunctionalInterface
  protected interface Decoder {
    Transaction decode(RLPInput input);
  }

  private static final ImmutableMap<TransactionType, Decoder> TYPED_TRANSACTION_DECODERS =
      ImmutableMap.of(
          TransactionType.ACCESS_LIST,
          AccessListTransactionDecoder::decode,
          TransactionType.EIP1559,
          EIP1559TransactionDecoder::decode,
          TransactionType.BLOB,
          BlobTransactionDecoder::decode,
          TransactionType.DELEGATE_CODE,
          CodeDelegationTransactionDecoder::decode);

  /**
   * Decodes an RLP input into a transaction. If the input represents a typed transaction, it uses
   * the appropriate decoder for that type. Otherwise, it uses the frontier decoder.
   *
   * @param rlpInput the RLP input
   * @return the decoded transaction
   */
  public static Transaction readFrom(
      final RLPInput rlpInput) {
    if (isTypedTransaction(rlpInput)) {
      return decodeTypedTransaction(rlpInput);
    } else {
      return FrontierTransactionDecoder.decode(rlpInput);
    }
  }

  public static Transaction readFrom(final Bytes rlpBytes) {
    return readFrom(RLP.input(rlpBytes));
  }

  /**
   * Decodes a typed transaction from an RLP input. It first reads the transaction type from the
   * input, then uses the appropriate decoder for that type.
   *
   * @param rlpInput the RLP input
   * @return the decoded transaction
   */
  private static Transaction decodeTypedTransaction(
      final RLPInput rlpInput) {
    // Read the typed transaction bytes from the RLP input
    final Bytes typedTransactionBytes = rlpInput.readBytes();

    // Determine the transaction type from the typed transaction bytes
    TransactionType transactionType =
        getTransactionType(typedTransactionBytes)
            .orElseThrow((() -> new IllegalArgumentException("Unsupported transaction type")));
    return decodeTypedTransaction(typedTransactionBytes, transactionType);
  }

  /**
   * Decodes a typed transaction. The method first slices the transaction bytes to exclude the
   * transaction type, then uses the appropriate decoder for the transaction type to decode the
   * remaining bytes.
   *
   * @param transactionBytes the transaction bytes
   * @param transactionType the type of the transaction
   * @return the decoded transaction
   */
  private static Transaction decodeTypedTransaction(
      final Bytes transactionBytes,
      final TransactionType transactionType) {
    // Slice the transaction bytes to exclude the transaction type and prepare for decoding
    final RLPInput transactionInput = RLP.input(transactionBytes.slice(1));
    // Use the appropriate decoder for the transaction type to decode the remaining bytes
    return getDecoder(transactionType).decode(transactionInput);
  }

  /**
   * Decodes a transaction from opaque bytes. The method first determines the transaction type from
   * the bytes. If the type is present, it delegates the decoding process to the appropriate decoder
   * for that type. If the type is not present, it decodes the bytes as an RLP input.
   *
   * @param opaqueBytes the opaque bytes
   * @return the decoded transaction
   */
  public static Transaction decodeOpaqueBytes(
      final Bytes opaqueBytes) {
    var transactionType = getTransactionType(opaqueBytes);
    if (transactionType.isPresent()) {
      return decodeTypedTransaction(opaqueBytes, transactionType.get());
    } else {
      // If the transaction type is not present, decode the opaque bytes as RLP
      return readFrom(RLP.input(opaqueBytes));
    }
  }

  /**
   * Retrieves the transaction type from the provided bytes. The method attempts to extract the
   * first byte from the input bytes and interpret it as a transaction type. If the byte does not
   * correspond to a valid transaction type, the method returns an empty Optional.
   *
   * @param opaqueBytes the bytes from which to extract the transaction type
   * @return an Optional containing the TransactionType if the first byte of the input corresponds
   *     to a valid transaction type, or an empty Optional if it does not
   */
  private static Optional<TransactionType> getTransactionType(final Bytes opaqueBytes) {
    try {
      byte transactionTypeByte = opaqueBytes.get(0);
      return Optional.of(TransactionType.of(transactionTypeByte));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
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
   * Gets the decoder for a given transaction type
   *
   * @param transactionType the transaction type
   * @return the decoder
   */
  @VisibleForTesting
  protected static Decoder getDecoder(
      final TransactionType transactionType) {
    return checkNotNull(
        TYPED_TRANSACTION_DECODERS.get(transactionType),
        "Developer Error. A supported transaction type %s has no associated decoding logic",
        transactionType);
  }
}
