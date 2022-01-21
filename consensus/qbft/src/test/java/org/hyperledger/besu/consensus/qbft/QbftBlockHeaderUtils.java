/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.qbft;

import static java.util.Collections.singletonList;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataFixture;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class QbftBlockHeaderUtils {

  private static final int ROUND_NUMBER = 0x2A;

  @FunctionalInterface
  public interface HeaderModifier {

    void update(BlockHeaderTestFixture blockHeaderTestFixture);
  }

  public static BlockHeaderTestFixture createPresetHeaderBuilder(
      final long number,
      final NodeKey proposerNodeKey,
      final List<Address> validators,
      final BlockHeader parent,
      final Optional<Wei> baseFee) {
    return createPresetHeaderBuilder(number, proposerNodeKey, validators, parent, null, baseFee);
  }

  public static BlockHeaderTestFixture createPresetHeaderBuilderForContractMode(
      final long number,
      final NodeKey proposerNodeKey,
      final BlockHeader parent,
      final HeaderModifier modifier,
      final Optional<Wei> baseFee) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    final QbftExtraDataCodec qbftExtraDataEncoder = new QbftExtraDataCodec();
    populateDefaultBlockHeader(
        number, proposerNodeKey, parent, modifier, builder, qbftExtraDataEncoder, baseFee);

    final BftExtraData bftExtraData =
        BftExtraDataFixture.createExtraData(
            builder.buildHeader(),
            Bytes.wrap(new byte[BftExtraDataCodec.EXTRA_VANITY_LENGTH]),
            Optional.empty(),
            Collections.emptyList(),
            singletonList(proposerNodeKey),
            ROUND_NUMBER,
            qbftExtraDataEncoder);

    builder.extraData(qbftExtraDataEncoder.encode(bftExtraData));
    return builder;
  }

  public static BlockHeaderTestFixture createPresetHeaderBuilder(
      final long number,
      final NodeKey proposerNodeKey,
      final List<Address> validators,
      final BlockHeader parent,
      final HeaderModifier modifier,
      final Optional<Wei> baseFee) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    final QbftExtraDataCodec qbftExtraDataEncoder = new QbftExtraDataCodec();
    populateDefaultBlockHeader(
        number, proposerNodeKey, parent, modifier, builder, qbftExtraDataEncoder, baseFee);

    final BftExtraData bftExtraData =
        BftExtraDataFixture.createExtraData(
            builder.buildHeader(),
            Bytes.wrap(new byte[BftExtraDataCodec.EXTRA_VANITY_LENGTH]),
            Optional.of(Vote.authVote(Address.fromHexString("1"))),
            validators,
            singletonList(proposerNodeKey),
            ROUND_NUMBER,
            qbftExtraDataEncoder);

    builder.extraData(qbftExtraDataEncoder.encode(bftExtraData));
    return builder;
  }

  private static void populateDefaultBlockHeader(
      final long number,
      final NodeKey proposerNodeKey,
      final BlockHeader parent,
      final HeaderModifier modifier,
      final BlockHeaderTestFixture builder,
      final QbftExtraDataCodec qbftExtraDataEncoder,
      final Optional<Wei> baseFee) {
    if (parent != null) {
      builder.parentHash(parent.getHash());
    }
    builder.number(number);
    builder.gasLimit(5000);
    builder.timestamp(6 * number);
    builder.mixHash(
        Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"));
    builder.difficulty(Difficulty.ONE);
    builder.coinbase(Util.publicKeyToAddress(proposerNodeKey.getPublicKey()));
    builder.blockHeaderFunctions(BftBlockHeaderFunctions.forCommittedSeal(qbftExtraDataEncoder));
    baseFee.ifPresent(fee -> builder.baseFeePerGas(fee));

    if (modifier != null) {
      modifier.update(builder);
    }
  }
}
