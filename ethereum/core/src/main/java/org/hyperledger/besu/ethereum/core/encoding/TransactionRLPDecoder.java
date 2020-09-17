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

import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE_PLUS_1;
import static org.hyperledger.besu.ethereum.core.Transaction.TWO;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

@FunctionalInterface
public interface TransactionRLPDecoder {

  TransactionRLPDecoder FRONTIER = frontierDecoder();
  TransactionRLPDecoder EIP1559 = eip1559Decoder();

  static Transaction decodeTransaction(final RLPInput input) {
    return (ExperimentalEIPs.eip1559Enabled ? EIP1559 : FRONTIER).decode(input);
  }

  Transaction decode(RLPInput input);

  static TransactionRLPDecoder frontierDecoder() {
    return input -> {
      input.enterList();
      final Transaction.Builder builder =
          Transaction.builder()
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
        recId =
            v.subtract(TWO.multiply(chainId.get()).add(REPLAY_PROTECTED_V_BASE)).byteValueExact();
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
    };
  }

  static TransactionRLPDecoder eip1559Decoder() {
    return input -> {
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
        v = maybeGasPremiumOrV.toUnsignedBigInteger();
        r = maybeFeeCapOrR.toUnsignedBigInteger();
        s = maybeVOrS.toUnsignedBigInteger();
      } else {
        // otherwise this is an EIP-1559 transaction
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
        recId =
            v.subtract(TWO.multiply(chainId.get()).add(REPLAY_PROTECTED_V_BASE)).byteValueExact();
      } else {
        throw new RuntimeException(
            String.format("An unsupported encoded `v` value of %s was found", v));
      }
      final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);
      input.leaveList();
      chainId.ifPresent(builder::chainId);
      return builder.signature(signature).build();
    };
  }
}
