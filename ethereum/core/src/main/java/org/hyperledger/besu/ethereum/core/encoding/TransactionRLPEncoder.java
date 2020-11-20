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
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.transaction.FrontierlikeSignatureTransaction;
import org.hyperledger.besu.ethereum.core.transaction.EIP1559Transaction;
import org.hyperledger.besu.ethereum.core.transaction.FrontierTransaction;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.TransactionType;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.data.TypedTransaction;

public class TransactionRLPEncoder {

  public static void encode(final TypedTransaction typedTransaction, final RLPOutput output) {
    if (typedTransaction instanceof FrontierTransaction) {
      encode((FrontierTransaction) typedTransaction, output);
    } else if (typedTransaction instanceof EIP1559Transaction) {
      encode((EIP1559Transaction) typedTransaction, output);
    } else {
      throw new IllegalStateException(
          String.format("%s did not have an associated encoder", typedTransaction));
    }
  }

  public static void encode(final FrontierTransaction frontierTransaction, final RLPOutput output) {
    output.startList();
    output.writeLongScalar(frontierTransaction.getNonce());
    output.writeUInt256Scalar(frontierTransaction.getGasPrice());
    output.writeLongScalar(frontierTransaction.getGasLimit());
    output.writeBytes(frontierTransaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    output.writeUInt256Scalar(frontierTransaction.getValue());
    output.writeBytes(frontierTransaction.getPayload());
    writeSignature(frontierTransaction, output);
    output.endList();
  }

  public static void encode(final EIP1559Transaction eip1559Transaction, final RLPOutput output) {
    if (!ExperimentalEIPs.eip1559Enabled
        || !TransactionType.EIP1559.equals(eip1559Transaction.getType())) {
      throw new RuntimeException("Invalid transaction format");
    }

    output.startList();
    output.writeLongScalar(eip1559Transaction.getNonce());
    output.writeNull();
    output.writeLongScalar(eip1559Transaction.getGasLimit());
    output.writeBytes(eip1559Transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    output.writeUInt256Scalar(eip1559Transaction.getValue());
    output.writeBytes(eip1559Transaction.getPayload());
    output.writeUInt256Scalar(Wei.ofNumber(eip1559Transaction.getGasPremium().getValue()));
    output.writeUInt256Scalar(Wei.ofNumber(eip1559Transaction.getFeeCap().getValue()));
    writeSignature(eip1559Transaction, output);
    output.endList();
  }

  private static void writeSignature(
      final FrontierlikeSignatureTransaction frontierlikeSignatureTransaction,
      final RLPOutput out) {
    out.writeBigIntegerScalar(frontierlikeSignatureTransaction.getV());
    out.writeBigIntegerScalar(frontierlikeSignatureTransaction.getSignature().getR());
    out.writeBigIntegerScalar(frontierlikeSignatureTransaction.getSignature().getS());
  }
}
