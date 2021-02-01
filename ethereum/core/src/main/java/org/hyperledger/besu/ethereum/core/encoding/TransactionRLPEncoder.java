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

import org.hyperledger.besu.ethereum.core.AccessList;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

public class TransactionRLPEncoder {

  @FunctionalInterface
  interface Encoder {
    void encode(Transaction transaction, RLPOutput output);
  }

  private static final ImmutableMap<TransactionType, Encoder> TYPED_TRANSACTION_ENCODERS =
      ImmutableMap.of(
          TransactionType.ACCESS_LIST,
          TransactionRLPEncoder::encodeAccessList,
          TransactionType.EIP1559,
          TransactionRLPEncoder::encodeEIP1559);

  public static void encode(final Transaction transaction, final RLPOutput rlpOutput) {
    final TransactionType transactionType =
        checkNotNull(
            transaction.getType(), "Transaction type for %s was not specified.", transaction);
    if (TransactionType.FRONTIER.equals(transactionType)) {
      encodeFrontier(transaction, rlpOutput);
    } else {
      rlpOutput.writeBytes(RLP.encode(output -> encodeForTransactionTrie(transaction, output)));
    }
  }

  public static void encodeForTransactionTrie(
      final Transaction transaction, final RLPOutput rlpOutput) {
    final TransactionType transactionType =
        checkNotNull(
            transaction.getType(), "Transaction type for %s was not specified.", transaction);
    if (TransactionType.FRONTIER.equals(transactionType)) {
      encodeFrontier(transaction, rlpOutput);
    } else {
      final Encoder encoder =
          checkNotNull(
              TYPED_TRANSACTION_ENCODERS.get(transactionType),
              "Developer Error. A supported transaction type %s has no associated encoding logic",
              transactionType);
      rlpOutput.writeIntScalar(transactionType.getSerializedType());
      encoder.encode(transaction, rlpOutput);
    }
  }

  static void encodeFrontier(final Transaction transaction, final RLPOutput out) {
    out.startList();
    out.writeLongScalar(transaction.getNonce());
    out.writeUInt256Scalar(transaction.getGasPrice());
    out.writeLongScalar(transaction.getGasLimit());
    out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    out.writeUInt256Scalar(transaction.getValue());
    out.writeBytes(transaction.getPayload());
    writeSignatureAndRecoveryId(transaction, out);
    out.endList();
  }

  static void encodeAccessList(final Transaction transaction, final RLPOutput rlpOutput) {
    rlpOutput.startList();
    encodeAccessListInner(
        transaction.getChainId(),
        transaction.getNonce(),
        transaction.getGasPrice(),
        transaction.getGasLimit(),
        transaction.getTo(),
        transaction.getValue(),
        transaction.getPayload(),
        transaction.getAccessList(),
        rlpOutput);
    rlpOutput.writeIntScalar(transaction.getSignature().getRecId());
    writeSignature(transaction, rlpOutput);
    rlpOutput.endList();
  }

  public static void encodeAccessListInner(
      final Optional<BigInteger> chainId,
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final AccessList accessList,
      final RLPOutput rlpOutput) {
    rlpOutput.writeLongScalar(
        chainId
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "chainId is required for access list transactions"))
            .longValue());
    rlpOutput.writeLongScalar(nonce);
    rlpOutput.writeUInt256Scalar(gasPrice);
    rlpOutput.writeLongScalar(gasLimit);
    rlpOutput.writeBytes(to.map(Bytes::copy).orElse(Bytes.EMPTY));
    rlpOutput.writeUInt256Scalar(value);
    rlpOutput.writeBytes(payload);
    /*
    Access List encoding should look like this
    where hex strings represent raw bytes
    [
      [
        "0xde0b295669a9fd93d5f28d9ec85e40f4cb697bae",
        [
          "0x0000000000000000000000000000000000000000000000000000000000000003",
          "0x0000000000000000000000000000000000000000000000000000000000000007"
        ]
      ],
      [
        "0xbb9bc244d798123fde783fcc1c72d3bb8c189413",
        []
      ]
    ] */
    rlpOutput.writeList(
        accessList,
        (accessListEntry, accessListEntryRLPOutput) -> {
          accessListEntryRLPOutput.startList();
          rlpOutput.writeBytes(accessListEntry.getKey());
          rlpOutput.writeList(
              accessListEntry.getValue(),
              (storageKeyBytes, storageKeyBytesRLPOutput) ->
                  storageKeyBytesRLPOutput.writeBytes(storageKeyBytes));
          accessListEntryRLPOutput.endList();
        });
  }

  static void encodeEIP1559(final Transaction transaction, final RLPOutput out) {
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
    writeSignatureAndRecoveryId(transaction, out);
    out.endList();
  }

  private static void writeSignatureAndRecoveryId(
      final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getV());
    writeSignature(transaction, out);
  }

  private static void writeSignature(final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getSignature().getR());
    out.writeBigIntegerScalar(transaction.getSignature().getS());
  }
}
