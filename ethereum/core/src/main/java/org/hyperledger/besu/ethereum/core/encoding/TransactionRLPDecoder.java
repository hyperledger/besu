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

import static org.hyperledger.besu.ethereum.core.Transaction.GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MAX;
import static org.hyperledger.besu.ethereum.core.Transaction.GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE_PLUS_1;
import static org.hyperledger.besu.ethereum.core.Transaction.TWO;

import org.hyperledger.besu.config.GoQuorumOptions;
import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

public class TransactionRLPDecoder {

  @FunctionalInterface
  interface Decoder {
    Transaction decode(RLPInput input);
  }

  private static final ImmutableMap<TransactionType, TransactionRLPDecoder.Decoder>
      TYPED_TRANSACTION_DECODERS =
          ImmutableMap.of(TransactionType.EIP1559, TransactionRLPDecoder::decodeEIP1559);

  public static Transaction decode(final RLPInput rlpInput) {
    if (GoQuorumOptions.goquorumCompatibilityMode) {
      return decodeGoQuorum(rlpInput);
    }
    if (rlpInput.nextIsList()) {
      return decodeFrontierOrEip1559(rlpInput);
    } else {
      final Bytes typedTransactionBytes = rlpInput.raw();
      final int firstByte = typedTransactionBytes.get(0) & 0xff;
      final TransactionType transactionType = TransactionType.of(firstByte);
      rlpInput.skipNext(); // throw away the type byte
      final Decoder decoder =
          Optional.ofNullable(TYPED_TRANSACTION_DECODERS.get(transactionType))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "Developer Error. A supported transaction type %s has no associated decoding logic",
                              transactionType)));
      return decoder.decode(rlpInput);
    }
  }

  static Transaction decodeFrontierOrEip1559(final RLPInput input) {
    input.enterList();

    final Transaction.Builder builder =
        Transaction.builder()
            .nonce(input.readLongScalar())
            .gasPrice(Wei.of(input.readUInt256Scalar()))
            .gasLimit(input.readLongScalar())
            .to(input.readBytes(v -> v.size() == 0 ? null : Address.wrap(v)))
            .value(Wei.of(input.readUInt256Scalar()))
            .payload(input.readBytes());

    final Bytes maybeGasPremiumOrV = input.readBytes();
    final Bytes maybeFeeCapOrR = input.readBytes();
    final Bytes maybeVOrS = input.readBytes();
    final BigInteger v, r, s;
    // if this is the end of the list we are processing a legacy transaction
    if (input.isEndOfCurrentList()) {
      builder.type(TransactionType.FRONTIER);
      v = maybeGasPremiumOrV.toUnsignedBigInteger();
      r = maybeFeeCapOrR.toUnsignedBigInteger();
      s = maybeVOrS.toUnsignedBigInteger();
    } else {
      // otherwise this is an EIP-1559 transaction
      builder.type(TransactionType.EIP1559);
      builder
          .gasPremium(Wei.of(maybeGasPremiumOrV.toBigInteger()))
          .feeCap(Wei.of(maybeFeeCapOrR.toBigInteger()));
      v = maybeVOrS.toBigInteger();
      r = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
      s = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    }
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
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);
    input.leaveList();
    chainId.ifPresent(builder::chainId);
    return builder.signature(signature).build();
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
    final BigInteger r = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final BigInteger s = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);

    input.leaveList();

    chainId.ifPresent(builder::chainId);
    return builder.signature(signature).build();
  }

  static Transaction decodeEIP1559(final RLPInput input) {
    ExperimentalEIPs.eip1559MustBeEnabled();
    input.enterList();

    final Transaction.Builder builder =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .nonce(input.readLongScalar())
            .gasPrice(Wei.of(input.readUInt256Scalar()))
            .gasLimit(input.readLongScalar())
            .to(input.readBytes(v -> v.size() == 0 ? null : Address.wrap(v)))
            .value(Wei.of(input.readUInt256Scalar()))
            .payload(input.readBytes())
            .gasPremium(Wei.of(input.readBytes().toBigInteger()))
            .feeCap(Wei.of(input.readBytes().toBigInteger()));

    final BigInteger v = input.readBytes().toBigInteger();
    final BigInteger r = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final BigInteger s = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
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
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);
    input.leaveList();
    chainId.ifPresent(builder::chainId);
    return builder.signature(signature).build();
  }

  static Transaction decodeGoQuorum(final RLPInput input) {
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
    if (isGoQuorumPrivateTransaction(v)) {
      // GoQuorum private TX. No chain ID. Preserve the v value as provided.
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
    final BigInteger r = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final BigInteger s = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);

    input.leaveList();
    chainId.ifPresent(builder::chainId);
    return builder.signature(signature).build();
  }

  private static boolean isGoQuorumPrivateTransaction(final BigInteger v) {
    return v.equals(GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MAX)
        || v.equals(GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN);
  }
}
