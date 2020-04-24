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
package org.hyperledger.besu.consensus.ibft.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibft.IbftContextBuilder.setupContextWithValidators;

import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.consensus.ibft.IbftExtraDataFixture;
import org.hyperledger.besu.consensus.ibft.Vote;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class IbftCoinbaseValidationRuleTest {

  public static BlockHeader createProposedBlockHeader(
      final NodeKey proposerNodeKey,
      final List<Address> validators,
      final List<NodeKey> committerNodeKeys) {

    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    builder.coinbase(Util.publicKeyToAddress(proposerNodeKey.getPublicKey()));
    final BlockHeader header = builder.buildHeader();

    final IbftExtraData ibftExtraData =
        IbftExtraDataFixture.createExtraData(
            header,
            Bytes.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            Optional.of(Vote.authVote(Address.fromHexString("1"))),
            validators,
            committerNodeKeys);

    builder.extraData(ibftExtraData.encode());
    return builder.buildHeader();
  }

  @Test
  public void proposerInValidatorListPassesValidation() {
    final NodeKey proposerNodeKey = NodeKeyUtils.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerNodeKey.getPublicKey().getEncodedBytes()));

    final List<Address> validators = Lists.newArrayList(proposerAddress);

    final List<NodeKey> committers = Lists.newArrayList(proposerNodeKey);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final IbftCoinbaseValidationRule coinbaseValidationRule = new IbftCoinbaseValidationRule();

    BlockHeader header = createProposedBlockHeader(proposerNodeKey, validators, committers);

    assertThat(coinbaseValidationRule.validate(header, null, context)).isTrue();
  }

  @Test
  public void proposerNotInValidatorListFailsValidation() {
    final NodeKey proposerNodeKey = NodeKeyUtils.generate();

    final NodeKey otherValidatorNodeKey = NodeKeyUtils.generate();
    final Address otherValidatorNodeAddress =
        Address.extract(Hash.hash(otherValidatorNodeKey.getPublicKey().getEncodedBytes()));

    final List<Address> validators = Lists.newArrayList(otherValidatorNodeAddress);

    final List<NodeKey> committers = Lists.newArrayList(otherValidatorNodeKey);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final IbftCoinbaseValidationRule coinbaseValidationRule = new IbftCoinbaseValidationRule();

    BlockHeader header = createProposedBlockHeader(proposerNodeKey, validators, committers);

    assertThat(coinbaseValidationRule.validate(header, null, context)).isFalse();
  }
}
