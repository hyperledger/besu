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
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE_PLUS_1;
import static org.hyperledger.besu.ethereum.core.Transaction.TWO;
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.ssz.TransactionNetworkPayload;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.evm.AccessListEntry;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.ssz.SSZReader;
import org.apache.tuweni.units.bigints.UInt32;
import org.slf4j.Logger;

public class TransactionDecoder {

  private static final UInt32 BLOB_TRANSACTION_OFFSET = UInt32.fromHexString("0x3c000000");

  private static final Logger LOG = getLogger(TransactionDecoder.class);

  @FunctionalInterface
  interface Decoder {
    Transaction decode(final Bytes input);

    static Decoder rlpDecoder(final RLPDecoder rlpDecoder) {
      return encoded -> rlpDecoder.decode(RLP.input(encoded));
    }

    static Decoder sszDecoder(final SSZDecoder sszDecoder) {
      return encoded -> {
        UInt32 firstOffset = UInt32.fromBytes(encoded.slice(0, 4));
        return SSZ.decode(encoded, (SSZReader reader) -> sszDecoder.decode(reader, firstOffset));
      };
    }
  }

  interface RLPDecoder {
    Transaction decode(final RLPInput input);
  }

  interface SSZDecoder {
    Transaction decode(final SSZReader reader, UInt32 firstOffset);
  }

  private static final ImmutableMap<TransactionType, Decoder> TYPED_TRANSACTION_DECODERS =
      ImmutableMap.of(
          TransactionType.ACCESS_LIST,
          Decoder.rlpDecoder(TransactionDecoder::decodeAccessList),
          TransactionType.EIP1559,
          Decoder.rlpDecoder(TransactionDecoder::decodeEIP1559),
          TransactionType.BLOB,
          Decoder.sszDecoder(TransactionDecoder::decodeBlob));

  public static Transaction decodeBlob(final SSZReader input, final UInt32 firstOffset) {
    Transaction.Builder builder = Transaction.builder();
    TransactionNetworkPayload.SingedBlobTransaction signedBlobTransaction;

    if (firstOffset.equals(BLOB_TRANSACTION_OFFSET)) {
      LOG.trace("Decoding TransactionNetworkPayload");

      TransactionNetworkPayload payload = new TransactionNetworkPayload();
      payload.populateFromReader(input);
      signedBlobTransaction = payload.getSignedBlobTransaction();

      builder.kzgBlobs(payload.getKzgCommitments(), payload.getBlobs(), payload.getKzgProofs());
    } else {
      LOG.trace("Decoding TransactionNetworkPayload.SingedBlobTransaction");
      signedBlobTransaction = new TransactionNetworkPayload.SingedBlobTransaction();
      signedBlobTransaction.populateFromReader(input);
    }

    var blobTransaction = signedBlobTransaction.getMessage();

    return builder
        .type(TransactionType.BLOB)
        .chainId(blobTransaction.getChainId().toUnsignedBigInteger())
        .nonce(blobTransaction.getNonce())
        .maxPriorityFeePerGas(Wei.of(blobTransaction.getMaxPriorityFeePerGas()))
        .maxFeePerGas(Wei.of(blobTransaction.getMaxFeePerGas()))
        .gasLimit(blobTransaction.getGas())
        .to(blobTransaction.getAddress().orElse(null))
        .value(Wei.of(blobTransaction.getValue()))
        .payload(blobTransaction.getData())
        .accessList(
            blobTransaction.getAccessList().stream()
                .map(
                    accessListEntry ->
                        new AccessListEntry(
                            accessListEntry.getAddress(), accessListEntry.getStorageKeys()))
                .collect(Collectors.toList()))
        .signature(
            SIGNATURE_ALGORITHM
                .get()
                .createSignature(
                    signedBlobTransaction.getSignature().getR().toUnsignedBigInteger(),
                    signedBlobTransaction.getSignature().getS().toUnsignedBigInteger(),
                    signedBlobTransaction.getSignature().isParity() ? (byte) 1 : 0))
        .maxFeePerDataGas(Wei.of(blobTransaction.getMaxFeePerDataGas()))
        .versionedHashes(blobTransaction.getBlobVersionedHashes())
        .build();
  }

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  public static Transaction decodeForWire(final RLPInput rlpInput) {
    if (rlpInput.nextIsList()) {
      return decodeFrontier(rlpInput);
    } else {
      final Bytes typedTransactionBytes = rlpInput.readBytes();
      final TransactionType transactionType =
          TransactionType.of(typedTransactionBytes.get(0) & 0xff);
      return getDecoder(transactionType).decode(typedTransactionBytes.slice(1));
    }
  }

  public static Transaction decodeOpaqueBytes(final Bytes input) {
    final TransactionType transactionType;
    try {
      transactionType = TransactionType.of(input.get(0));
    } catch (final IllegalArgumentException __) {
      return decodeForWire(RLP.input(input));
    }
    return getDecoder(transactionType).decode(input.slice(1));
  }

  private static Decoder getDecoder(final TransactionType transactionType) {
    return checkNotNull(
        TYPED_TRANSACTION_DECODERS.get(transactionType),
        "Developer Error. A supported transaction type %s has no associated decoding logic",
        transactionType);
  }

  static Transaction decodeFrontier(final RLPInput input) {
    input.enterList();
    final Transaction.Builder builder =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .nonce(input.readLongScalar())
            .gasPrice(Wei.of(input.readUInt256Scalar()))
            .gasLimit(input.readLongScalar())
            .to(input.readBytes(v -> v.size() == 0 ? null : Address.wrap(v)))
            .value(Wei.of(input.readUInt256Scalar()))
            .payload(input.readBytes());

    final BigInteger v = input.readBigIntegerScalar();
    final byte recId;
    Optional<BigInteger> chainId = Optional.empty();
    if (v.equals(REPLAY_UNPROTECTED_V_BASE) || v.equals(REPLAY_UNPROTECTED_V_BASE_PLUS_1)) {
      recId = v.subtract(REPLAY_UNPROTECTED_V_BASE).byteValueExact();
    } else if (v.compareTo(REPLAY_PROTECTED_V_MIN) > 0) {
      chainId = Optional.of(v.subtract(REPLAY_PROTECTED_V_BASE).divide(TWO));
      recId = v.subtract(TWO.multiply(chainId.get()).add(REPLAY_PROTECTED_V_BASE)).byteValueExact();
    } else {
      throw new RuntimeException(
          String.format("An unsupported encoded `v` value of %s was found", v));
    }
    final BigInteger r = input.readUInt256Scalar().toUnsignedBigInteger();
    final BigInteger s = input.readUInt256Scalar().toUnsignedBigInteger();
    final SECPSignature signature = SIGNATURE_ALGORITHM.get().createSignature(r, s, recId);

    input.leaveList();

    chainId.ifPresent(builder::chainId);
    return builder.signature(signature).build();
  }

  private static Transaction decodeAccessList(final RLPInput rlpInput) {
    rlpInput.enterList();
    final Transaction.Builder preSignatureTransactionBuilder =
        Transaction.builder()
            .type(TransactionType.ACCESS_LIST)
            .chainId(BigInteger.valueOf(rlpInput.readLongScalar()))
            .nonce(rlpInput.readLongScalar())
            .gasPrice(Wei.of(rlpInput.readUInt256Scalar()))
            .gasLimit(rlpInput.readLongScalar())
            .to(
                rlpInput.readBytes(
                    addressBytes -> addressBytes.size() == 0 ? null : Address.wrap(addressBytes)))
            .value(Wei.of(rlpInput.readUInt256Scalar()))
            .payload(rlpInput.readBytes())
            .accessList(
                rlpInput.readList(
                    accessListEntryRLPInput -> {
                      accessListEntryRLPInput.enterList();
                      final AccessListEntry accessListEntry =
                          new AccessListEntry(
                              Address.wrap(accessListEntryRLPInput.readBytes()),
                              accessListEntryRLPInput.readList(RLPInput::readBytes32));
                      accessListEntryRLPInput.leaveList();
                      return accessListEntry;
                    }));
    final byte recId = (byte) rlpInput.readIntScalar();
    final Transaction transaction =
        preSignatureTransactionBuilder
            .signature(
                SIGNATURE_ALGORITHM
                    .get()
                    .createSignature(
                        rlpInput.readUInt256Scalar().toUnsignedBigInteger(),
                        rlpInput.readUInt256Scalar().toUnsignedBigInteger(),
                        recId))
            .build();
    rlpInput.leaveList();
    return transaction;
  }

  static Transaction decodeEIP1559(final RLPInput input) {
    input.enterList();
    final BigInteger chainId = input.readBigIntegerScalar();
    final Transaction.Builder builder =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(chainId)
            .nonce(input.readLongScalar())
            .maxPriorityFeePerGas(Wei.of(input.readUInt256Scalar()))
            .maxFeePerGas(Wei.of(input.readUInt256Scalar()))
            .gasLimit(input.readLongScalar())
            .to(input.readBytes(v -> v.size() == 0 ? null : Address.wrap(v)))
            .value(Wei.of(input.readUInt256Scalar()))
            .payload(input.readBytes())
            .accessList(
                input.readList(
                    accessListEntryRLPInput -> {
                      accessListEntryRLPInput.enterList();
                      final AccessListEntry accessListEntry =
                          new AccessListEntry(
                              Address.wrap(accessListEntryRLPInput.readBytes()),
                              accessListEntryRLPInput.readList(RLPInput::readBytes32));
                      accessListEntryRLPInput.leaveList();
                      return accessListEntry;
                    }));
    final byte recId = (byte) input.readIntScalar();
    final Transaction transaction =
        builder
            .signature(
                SIGNATURE_ALGORITHM
                    .get()
                    .createSignature(
                        input.readUInt256Scalar().toUnsignedBigInteger(),
                        input.readUInt256Scalar().toUnsignedBigInteger(),
                        recId))
            .build();
    input.leaveList();
    return transaction;
  }
}
