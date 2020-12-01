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

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

public class TransactionRLPEncoder {

  private static final ImmutableMap<TransactionType, Encoder> TYPED_TRANSACTION_ENCODERS =
      ImmutableMap.of(TransactionType.EIP1559, eip1559Encoder());

  public static void encode(final Transaction transaction, final RLPOutput output) {
    if (transaction.getType().equals(TransactionType.FRONTIER)) {
      frontierEncoder().encode(transaction, output);
    } else {
      final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
      Optional.ofNullable(TYPED_TRANSACTION_ENCODERS.get(transaction.getType()))
          // TODO message in this or else throw
          .orElseThrow()
          .encode(transaction, bytesValueRLPOutput);
      output.writeRaw(
          Bytes.concatenate(
              Bytes.of((byte) transaction.getType().getSerializedType()),
              bytesValueRLPOutput.encoded()));
    }
  }

  static Encoder frontierEncoder() {
    return (transaction, out) -> {
      out.startList();
      out.writeLongScalar(transaction.getNonce());
      out.writeUInt256Scalar(transaction.getGasPrice());
      out.writeLongScalar(transaction.getGasLimit());
      out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
      out.writeUInt256Scalar(transaction.getValue());
      out.writeBytes(transaction.getPayload());
      writeSignature(transaction, out);
      out.endList();
    };
  }

  static Encoder eip1559Encoder() {
    return (transaction, out) -> {
      if (!ExperimentalEIPs.eip1559Enabled
          || !TransactionType.EIP1559.equals(transaction.getType())) {
        throw new RuntimeException("Invalid transaction format");
      }

      out.startList();
      out.writeLongScalar(transaction.getNonce());
      out.writeNull();
      out.writeLongScalar(transaction.getGasLimit());
      out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
      out.writeUInt256Scalar(transaction.getValue());
      out.writeBytes(transaction.getPayload());
      out.writeUInt256Scalar(
          transaction.getGasPremium().map(Quantity::getValue).map(Wei::ofNumber).orElseThrow());
      out.writeUInt256Scalar(
          transaction.getFeeCap().map(Quantity::getValue).map(Wei::ofNumber).orElseThrow());
      writeSignature(transaction, out);
      out.endList();
    };
  }

  private static void writeSignature(final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getV());
    out.writeBigIntegerScalar(transaction.getSignature().getR());
    out.writeBigIntegerScalar(transaction.getSignature().getS());
  }

  @FunctionalInterface
  interface Encoder {
    void encode(Transaction transaction, RLPOutput output);
  }
}
