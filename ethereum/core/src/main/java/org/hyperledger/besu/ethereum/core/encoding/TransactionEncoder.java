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
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

public class TransactionEncoder {

  @FunctionalInterface
  interface Encoder {
    void encode(Transaction transaction, RLPOutput output, EncodingContext context);
  }

  private static final FrontierTransactionEncoder FRONTIER_ENCODER =
      new FrontierTransactionEncoder();

  private static final Map<TransactionType, Encoder> TYPED_TRANSACTION_ENCODERS =
      Map.of(
          TransactionType.ACCESS_LIST,
          new AccessListTransactionEncoder(),
          TransactionType.EIP1559,
          new EIP1559TransactionEncoder(),
          TransactionType.BLOB,
          BlobTransactionEncoder::encode);

  public static void encodeForWire(final Transaction transaction, final RLPOutput rlpOutput) {
    final TransactionType transactionType =
        checkNotNull(
            transaction.getType(), "Transaction type for %s was not specified.", transaction);
    encodeForWire(transactionType, encodeOpaqueBytes(transaction), rlpOutput);
  }

  public static void encodeForWire(
      final TransactionType transactionType, final Bytes opaqueBytes, final RLPOutput rlpOutput) {
    checkNotNull(transactionType, "Transaction type was not specified.");
    if (TransactionType.FRONTIER.equals(transactionType)) {
      rlpOutput.writeRaw(opaqueBytes);
    } else {
      rlpOutput.writeBytes(opaqueBytes);
    }
  }

  public static Bytes encodeForNetwork(final Transaction transaction) {
    return encodeTransaction(transaction, EncodingContext.NETWORK);
  }

  public static Bytes encodeOpaqueBytes(final Transaction transaction) {
    return encodeTransaction(transaction, EncodingContext.RLP);
  }

  private static Bytes encodeTransaction(
      final Transaction transaction, final EncodingContext encodingContext) {
    final TransactionType transactionType = getTransactionType(transaction);
    if (TransactionType.FRONTIER.equals(transactionType)) {
      return RLP.encode(
          rlpOutput -> FRONTIER_ENCODER.encode(transaction, rlpOutput, EncodingContext.RLP));
    } else {
      final Encoder encoder = getEncoder(transactionType);
      final BytesValueRLPOutput out = new BytesValueRLPOutput();
      out.writeByte(transaction.getType().getSerializedType());
      encoder.encode(transaction, out, encodingContext);
      return out.encoded();
    }
  }

  private static TransactionType getTransactionType(final Transaction transaction) {
    return checkNotNull(
        transaction.getType(), "Transaction type for %s was not specified.", transaction);
  }

  private static Encoder getEncoder(final TransactionType transactionType) {
    return checkNotNull(
        TYPED_TRANSACTION_ENCODERS.get(transactionType),
        "Developer Error. A supported transaction type %s has no associated encoding logic",
        transactionType);
  }

  static void writeSignatureAndV(final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getV());
    writeSignature(transaction, out);
  }

  static void writeSignatureAndRecoveryId(final Transaction transaction, final RLPOutput out) {
    out.writeIntScalar(transaction.getSignature().getRecId());
    writeSignature(transaction, out);
  }

  static void writeSignature(final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getSignature().getR());
    out.writeBigIntegerScalar(transaction.getSignature().getS());
  }
}
