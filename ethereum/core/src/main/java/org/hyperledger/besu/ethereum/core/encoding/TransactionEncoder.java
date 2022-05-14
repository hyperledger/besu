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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.evm.AccessListEntry;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class TransactionEncoder {

  @FunctionalInterface
  interface Encoder {
    void encode(Transaction transaction, RLPOutput output);
  }

  private static final Map<TransactionType, Encoder> TYPED_TRANSACTION_ENCODERS =
      Map.of(
          TransactionType.ACCESS_LIST,
          TransactionEncoder::encodeAccessList,
          TransactionType.EIP1559,
          TransactionEncoder::encodeEIP1559);

  public static void encodeForWire(final Transaction transaction, final RLPOutput rlpOutput) {
    final TransactionType transactionType =
        checkNotNull(
            transaction.getType(), "Transaction type for %s was not specified.", transaction);
    if (TransactionType.FRONTIER.equals(transactionType)) {
      encodeFrontier(transaction, rlpOutput);
    } else {
      rlpOutput.writeBytes(encodeOpaqueBytes(transaction));
    }
  }

  public static Bytes encodeOpaqueBytes(final Transaction transaction) {
    final TransactionType transactionType =
        checkNotNull(
            transaction.getType(), "Transaction type for %s was not specified.", transaction);
    if (TransactionType.FRONTIER.equals(transactionType)) {
      return RLP.encode(rlpOutput -> encodeFrontier(transaction, rlpOutput));
    } else {
      final Encoder encoder =
          checkNotNull(
              TYPED_TRANSACTION_ENCODERS.get(transactionType),
              "Developer Error. A supported transaction type %s has no associated encoding logic",
              transactionType);
      return Bytes.concatenate(
          Bytes.of(transactionType.getSerializedType()),
          RLP.encode(rlpOutput -> encoder.encode(transaction, rlpOutput)));
    }
  }

  static void encodeFrontier(final Transaction transaction, final RLPOutput out) {
    out.startList();
    out.writeLongScalar(transaction.getNonce());
    out.writeUInt256Scalar(transaction.getGasPrice().orElseThrow());
    out.writeLongScalar(transaction.getGasLimit());
    out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    out.writeUInt256Scalar(transaction.getValue());
    out.writeBytes(transaction.getPayload());
    writeSignatureAndV(transaction, out);
    out.endList();
  }

  static void encodeAccessList(final Transaction transaction, final RLPOutput rlpOutput) {
    rlpOutput.startList();
    encodeAccessListInner(
        transaction.getChainId(),
        transaction.getNonce(),
        transaction.getGasPrice().orElseThrow(),
        transaction.getGasLimit(),
        transaction.getTo(),
        transaction.getValue(),
        transaction.getPayload(),
        transaction
            .getAccessList()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Developer error: access list should be guaranteed to be present")),
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
      final List<AccessListEntry> accessList,
      final RLPOutput rlpOutput) {
    rlpOutput.writeBigIntegerScalar(chainId.orElseThrow());
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
    writeAccessList(rlpOutput, Optional.of(accessList));
  }

  static void encodeEIP1559(final Transaction transaction, final RLPOutput out) {
    out.startList();
    out.writeBigIntegerScalar(transaction.getChainId().orElseThrow());
    out.writeLongScalar(transaction.getNonce());
    out.writeUInt256Scalar(transaction.getMaxPriorityFeePerGas().orElseThrow());
    out.writeUInt256Scalar(transaction.getMaxFeePerGas().orElseThrow());
    out.writeLongScalar(transaction.getGasLimit());
    out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    out.writeUInt256Scalar(transaction.getValue());
    out.writeBytes(transaction.getPayload());
    writeAccessList(out, transaction.getAccessList());
    writeSignatureAndRecoveryId(transaction, out);
    out.endList();
  }

  public static void writeAccessList(
      final RLPOutput out, final Optional<List<AccessListEntry>> accessListEntries) {
    if (accessListEntries.isEmpty()) {
      out.writeEmptyList();
    } else {
      out.writeList(
          accessListEntries.get(),
          (accessListEntry, accessListEntryRLPOutput) -> {
            accessListEntryRLPOutput.startList();
            out.writeBytes(accessListEntry.getAddress());
            out.writeList(
                accessListEntry.getStorageKeys(),
                (storageKeyBytes, storageKeyBytesRLPOutput) ->
                    storageKeyBytesRLPOutput.writeBytes(storageKeyBytes));
            accessListEntryRLPOutput.endList();
          });
    }
  }

  private static void writeSignatureAndV(final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getV());
    writeSignature(transaction, out);
  }

  private static void writeSignatureAndRecoveryId(
      final Transaction transaction, final RLPOutput out) {
    out.writeIntScalar(transaction.getSignature().getRecId());
    writeSignature(transaction, out);
  }

  private static void writeSignature(final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getSignature().getR());
    out.writeBigIntegerScalar(transaction.getSignature().getS());
  }
}
