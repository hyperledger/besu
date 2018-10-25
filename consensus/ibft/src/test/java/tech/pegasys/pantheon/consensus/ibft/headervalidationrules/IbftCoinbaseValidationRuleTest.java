/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraDataFixture;
import tech.pegasys.pantheon.consensus.ibft.Vote;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Test;

public class IbftCoinbaseValidationRuleTest {

  public static BlockHeader createProposedBlockHeader(
      final KeyPair proposerKeyPair,
      final List<Address> validators,
      final List<KeyPair> committerKeyPairs) {

    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    builder.coinbase(Util.publicKeyToAddress(proposerKeyPair.getPublicKey()));
    final BlockHeader header = builder.buildHeader();

    final IbftExtraData ibftExtraData =
        IbftExtraDataFixture.createExtraData(
            header,
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            Optional.of(Vote.authVote(Address.fromHexString("1"))),
            validators,
            committerKeyPairs);

    builder.extraData(ibftExtraData.encode());
    return builder.buildHeader();
  }

  @Test
  public void proposerInValidatorListPassesValidation() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = Lists.newArrayList(proposerAddress);

    final List<KeyPair> committers = Lists.newArrayList(proposerKeyPair);

    final VoteTally voteTally = new VoteTally(validators);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, new IbftContext(voteTally, null));

    final IbftCoinbaseValidationRule coinbaseValidationRule = new IbftCoinbaseValidationRule();

    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators, committers);

    assertThat(coinbaseValidationRule.validate(header, null, context)).isTrue();
  }

  @Test
  public void proposerNotInValidatorListFailsValidation() {
    final KeyPair proposerKeyPair = KeyPair.generate();

    final KeyPair otherValidatorKeyPair = KeyPair.generate();
    final Address otherValidatorNodeAddress =
        Address.extract(Hash.hash(otherValidatorKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = Lists.newArrayList(otherValidatorNodeAddress);

    final List<KeyPair> committers = Lists.newArrayList(otherValidatorKeyPair);

    final VoteTally voteTally = new VoteTally(validators);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, new IbftContext(voteTally, null));

    final IbftCoinbaseValidationRule coinbaseValidationRule = new IbftCoinbaseValidationRule();

    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators, committers);

    assertThat(coinbaseValidationRule.validate(header, null, context)).isFalse();
  }
}
