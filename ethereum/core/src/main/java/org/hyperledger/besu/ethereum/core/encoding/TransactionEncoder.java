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
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.ssz.TransactionNetworkPayload;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.evm.AccessListEntry;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.ssz.SSZWriter;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;

public class TransactionEncoder {
  private static final Logger LOG = getLogger(Encoder.class);

  @FunctionalInterface
  interface Encoder {

    Bytes encode(final Transaction transaction);

    static Encoder rlpEncoder(final RLPEncoder rlpEncoder) {
      return transaction -> RLP.encode(rlpOutput -> rlpEncoder.encode(transaction, rlpOutput));
    }

    static Encoder sszEncoder(final SSZEncoder sszEncoder) {
      return transaction -> SSZ.encode(sszOutput -> sszEncoder.encode(transaction, sszOutput));
    }
  }

  interface RLPEncoder {
    void encode(final Transaction transaction, final RLPOutput output);
  }

  interface SSZEncoder {
    void encode(final Transaction transaction, final SSZWriter output);
  }

  private static final Map<TransactionType, Encoder> TYPED_TRANSACTION_ENCODERS =
      Map.of(
          TransactionType.ACCESS_LIST, Encoder.rlpEncoder(TransactionEncoder::encodeAccessList),
          TransactionType.EIP1559, Encoder.rlpEncoder(TransactionEncoder::encodeEIP1559),
          TransactionType.BLOB, Encoder.sszEncoder(TransactionEncoder::encodeWithoutBlobs));

  private static final Map<TransactionType, Encoder> TYPED_TRANSACTION_ENCODERS_FOR_NETWORK =
      Map.of(
          TransactionType.ACCESS_LIST, Encoder.rlpEncoder(TransactionEncoder::encodeAccessList),
          TransactionType.EIP1559, Encoder.rlpEncoder(TransactionEncoder::encodeEIP1559),
          TransactionType.BLOB, Encoder.sszEncoder(TransactionEncoder::encodeWithBlobs));

  public static void encodeWithBlobs(final Transaction transaction, final SSZWriter rlpOutput) {
    LOG.trace("Encoding transaction with blobs {}", transaction);
    var payload = new TransactionNetworkPayload();
    var blobsWithCommitments = transaction.getBlobsWithCommitments();
    if (blobsWithCommitments.isPresent()) {
      payload.setBlobs(blobsWithCommitments.get().blobs);
      payload.setKzgProofs(blobsWithCommitments.get().kzgProofs);
      payload.setKzgCommitments(blobsWithCommitments.get().kzgCommitments);
    }

    var signedBlobTransaction = payload.getSignedBlobTransaction();
    populatedSignedBlobTransaction(transaction, signedBlobTransaction);

    payload.writeTo(rlpOutput);
  }

  public static void encodeWithoutBlobs(final Transaction transaction, final SSZWriter rlpOutput) {
    LOG.trace("Encoding transaction without blobs {}", transaction);
    var signedBlobTransaction = new TransactionNetworkPayload.SingedBlobTransaction();
    populatedSignedBlobTransaction(transaction, signedBlobTransaction);
    signedBlobTransaction.writeTo(rlpOutput);
  }

  private static void populatedSignedBlobTransaction(
      final Transaction transaction,
      final TransactionNetworkPayload.SingedBlobTransaction signedBlobTransaction) {
    var signature = signedBlobTransaction.getSignature();
    signature.setR(UInt256.valueOf(transaction.getSignature().getR()));
    signature.setS(UInt256.valueOf(transaction.getSignature().getS()));
    signature.setParity(transaction.getSignature().getRecId() == 1);

    var blobTransaction = signedBlobTransaction.getMessage();

    blobTransaction.setChainId(UInt256.valueOf(transaction.getChainId().orElseThrow()));
    blobTransaction.setNonce(transaction.getNonce());
    blobTransaction.setMaxPriorityFeePerGas(
        transaction.getMaxPriorityFeePerGas().orElseThrow().toUInt256());
    blobTransaction.setMaxFeePerGas(transaction.getMaxFeePerGas().orElseThrow().toUInt256());
    blobTransaction.setGas(transaction.getGasLimit());
    blobTransaction.setAddress(transaction.getTo());
    blobTransaction.setValue(transaction.getValue().toUInt256());
    blobTransaction.setData(transaction.getPayload());
    transaction
        .getAccessList()
        .ifPresent(
            accessListEntries -> {
              var accessList = blobTransaction.getAccessList();
              accessListEntries.forEach(
                  accessListEntry -> {
                    var tuple = new TransactionNetworkPayload.SingedBlobTransaction.AccessTuple();
                    tuple.setAddress(accessListEntry.getAddress());
                    tuple.setStorageKeys(accessListEntry.getStorageKeys());
                    accessList.add(tuple);
                  });
            });
    blobTransaction.setMaxFeePerDataGas(
        transaction.getMaxFeePerDataGas().orElseThrow().toUInt256());
    blobTransaction.setBlobVersionedHashes(transaction.getVersionedHashes().orElseThrow());
  }

  public static void encodeForWire(final Transaction transaction, final RLPOutput rlpOutput) {
    final TransactionType transactionType =
        checkNotNull(
            transaction.getType(), "Transaction type for %s was not specified.", transaction);
    encodeForWire(transactionType, encodeOpaqueBytesForNetwork(transaction), rlpOutput);
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

  public static void encodeOpaqueBytes(final Transaction transaction, final RLPOutput rlpOutput) {
    final TransactionType transactionType =
        checkNotNull(
            transaction.getType(), "Transaction type for %s was not specified.", transaction);
    encodeForWire(transactionType, encodeOpaqueBytes(transaction), rlpOutput);
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
      Bytes encoded = encoder.encode(transaction);
      Bytes appended = Bytes.concatenate(Bytes.of(transactionType.getSerializedType()), encoded);
      return appended;
    }
  }

  public static Bytes encodeOpaqueBytesForNetwork(final Transaction transaction) {
    final TransactionType transactionType =
        checkNotNull(
            transaction.getType(), "Transaction type for %s was not specified.", transaction);
    if (TransactionType.FRONTIER.equals(transactionType)) {
      return RLP.encode(rlpOutput -> encodeFrontier(transaction, rlpOutput));
    } else {
      final Encoder encoder =
          checkNotNull(
              TYPED_TRANSACTION_ENCODERS_FOR_NETWORK.get(transactionType),
              "Developer Error. A supported transaction type %s has no associated encoding logic",
              transactionType);
      return Bytes.concatenate(
          Bytes.of(transactionType.getSerializedType()), encoder.encode(transaction));
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

  public static void writeBlobVersionedHashes(
      final RLPOutput rlpOutput, final List<Hash> versionedHashes) {
    // ToDo 4844: implement
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
