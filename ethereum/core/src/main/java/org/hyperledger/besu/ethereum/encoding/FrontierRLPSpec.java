/*
 *
 *  * Copyright ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.encoding;

import org.hyperledger.besu.config.GoQuorumOptions;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class FrontierRLPSpec implements ProtocolRLPSpec {

  @Override
  public Transaction decodeTransaction(final RLPInput rlpInput) {
    if (GoQuorumOptions.goquorumCompatibilityMode) {
      return decodeGoQuorum(rlpInput);
    }
    rlpInput.enterList();
    final Transaction.Builder builder =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .nonce(rlpInput.readLongScalar())
            .gasPrice(Wei.of(rlpInput.readUInt256Scalar()))
            .gasLimit(rlpInput.readLongScalar())
            .to(rlpInput.readBytes(v -> v.size() == 0 ? null : Address.wrap(v)))
            .value(Wei.of(rlpInput.readUInt256Scalar()))
            .payload(rlpInput.readBytes());

    final BigInteger v = rlpInput.readBigIntegerScalar();
    final byte recId;
    Optional<BigInteger> chainId = Optional.empty();
    if (v.equals(Transaction.REPLAY_UNPROTECTED_V_BASE)
        || v.equals(Transaction.REPLAY_UNPROTECTED_V_BASE_PLUS_1)) {
      recId = v.subtract(Transaction.REPLAY_UNPROTECTED_V_BASE).byteValueExact();
    } else if (v.compareTo(Transaction.REPLAY_PROTECTED_V_MIN) > 0) {
      chainId =
          Optional.of(v.subtract(Transaction.REPLAY_PROTECTED_V_BASE).divide(Transaction.TWO));
      recId =
          v.subtract(
                  Transaction.TWO.multiply(chainId.get()).add(Transaction.REPLAY_PROTECTED_V_BASE))
              .byteValueExact();
    } else {
      throw new RuntimeException(
          String.format("An unsupported encoded `v` value of %s was found", v));
    }
    final BigInteger r = rlpInput.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final BigInteger s = rlpInput.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);

    rlpInput.leaveList();

    chainId.ifPresent(builder::chainId);
    return builder.signature(signature).build();
  }

  static Transaction decodeGoQuorum(final RLPInput input) {
    input.enterList();

    final Transaction.Builder builder =
        Transaction.builder()
            .type(TransactionType.EIP1559)
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
      recId = v.subtract(Transaction.GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN).byteValueExact();
    } else if (v.equals(Transaction.REPLAY_UNPROTECTED_V_BASE)
        || v.equals(Transaction.REPLAY_UNPROTECTED_V_BASE_PLUS_1)) {
      recId = v.subtract(Transaction.REPLAY_UNPROTECTED_V_BASE).byteValueExact();
    } else if (v.compareTo(Transaction.REPLAY_PROTECTED_V_MIN) > 0) {
      chainId =
          Optional.of(v.subtract(Transaction.REPLAY_PROTECTED_V_BASE).divide(Transaction.TWO));
      recId =
          v.subtract(
                  Transaction.TWO.multiply(chainId.get()).add(Transaction.REPLAY_PROTECTED_V_BASE))
              .byteValueExact();
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
    return v.equals(Transaction.GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MAX)
        || v.equals(Transaction.GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN);
  }

  @Override
  public BlockBody decodeBlockBody(
      final RLPInput input, final BlockHeaderFunctions blockHeaderFunctions) {
    input.enterList();
    final BlockBody body =
        new BlockBody(
            input.readList(this::decodeTransaction),
            input.readList(rlpInput -> decodeBlockHeader(rlpInput, blockHeaderFunctions)));
    input.leaveList();
    return body;
  }

  @Override
  public BlockHeader decodeBlockHeader(
      final RLPInput input, final BlockHeaderFunctions blockHeaderFunctions) {
    input.enterList();
    final Hash parentHash = Hash.wrap(input.readBytes32());
    final Hash ommersHash = Hash.wrap(input.readBytes32());
    final Address coinbase = Address.readFrom(input);
    final Hash stateRoot = Hash.wrap(input.readBytes32());
    final Hash transactionsRoot = Hash.wrap(input.readBytes32());
    final Hash receiptsRoot = Hash.wrap(input.readBytes32());
    final LogsBloomFilter logsBloom = LogsBloomFilter.readFrom(input);
    final Difficulty difficulty = Difficulty.of(input.readUInt256Scalar());
    final long number = input.readLongScalar();
    final long gasLimit = input.readLongScalar();
    final long gasUsed = input.readLongScalar();
    final long timestamp = input.readLongScalar();
    final Bytes extraData = input.readBytes();
    final Hash mixHash = Hash.wrap(input.readBytes32());
    final long nonce = input.readLong();
    input.leaveList();
    return new BlockHeader(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        null,
        mixHash,
        nonce,
        blockHeaderFunctions);
  }
}
