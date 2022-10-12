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
import static org.hyperledger.besu.ethereum.core.Transaction.GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE_PLUS_1;
import static org.hyperledger.besu.ethereum.core.Transaction.TWO;

import org.hyperledger.besu.config.GoQuorumOptions;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.transaction.GoQuorumPrivateTransactionDetector;
import org.hyperledger.besu.evm.AccessListEntry;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
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
          TransactionDecoder::decodeAccessList,
          TransactionType.EIP1559,
          TransactionDecoder::decodeEIP1559);

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  public static Transaction decodeForWire(final RLPInput rlpInput) {
    return decodeForWire(rlpInput, GoQuorumOptions.getGoQuorumCompatibilityMode());
  }

  public static Transaction decodeForWire(
      final RLPInput rlpInput, final boolean goQuorumCompatibilityMode) {
    if (rlpInput.nextIsList()) {
      return decodeFrontier(rlpInput, goQuorumCompatibilityMode);
    } else {
      final Bytes typedTransactionBytes = rlpInput.readBytes();
      final TransactionType transactionType =
          TransactionType.of(typedTransactionBytes.get(0) & 0xff);
      return getDecoder(transactionType).decode(RLP.input(typedTransactionBytes.slice(1)));
    }
  }

  public static Transaction decodeOpaqueBytes(final Bytes input) {
    return decodeOpaqueBytes(input, GoQuorumOptions.getGoQuorumCompatibilityMode());
  }

  public static Transaction decodeOpaqueBytes(
      final Bytes input, final boolean goQuorumCompatibilityMode) {
    final TransactionType transactionType;
    try {
      transactionType = TransactionType.of(input.get(0));
    } catch (final IllegalArgumentException __) {
      return decodeForWire(RLP.input(input), goQuorumCompatibilityMode);
    }
    return getDecoder(transactionType).decode(RLP.input(input.slice(1)));
  }

  private static Decoder getDecoder(final TransactionType transactionType) {
    return checkNotNull(
        TYPED_TRANSACTION_DECODERS.get(transactionType),
        "Developer Error. A supported transaction type %s has no associated decoding logic",
        transactionType);
  }

  static Transaction decodeFrontier(final RLPInput input, final boolean goQuorumCompatibilityMode) {
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
    if (goQuorumCompatibilityMode
        && GoQuorumPrivateTransactionDetector.isGoQuorumPrivateTransactionV(v)) {
      builder.v(v);
      recId = v.subtract(GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN).byteValueExact();
    } else if (v.equals(REPLAY_UNPROTECTED_V_BASE) || v.equals(REPLAY_UNPROTECTED_V_BASE_PLUS_1)) {
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
