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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import org.junit.Test;

public class IbftExtraDataValidationRuleTest {

  public static BlockHeader createProposedBlockHeader(
      final List<Address> validators,
      final List<KeyPair> committerKeyPairs,
      final boolean useDifferentRoundNumbersForCommittedSeals) {
    final int BASE_ROUND_NUMBER = 5;
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all

    final BlockHeader header = builder.buildHeader();

    final IbftExtraData ibftExtraData =
        IbftExtraDataFixture.createExtraData(
            header,
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            Optional.of(Vote.authVote(Address.fromHexString("1"))),
            validators,
            committerKeyPairs,
            BASE_ROUND_NUMBER,
            useDifferentRoundNumbersForCommittedSeals);

    builder.extraData(ibftExtraData.encode());
    return builder.buildHeader();
  }

  @Test
  public void correctlyConstructedHeaderPassesValidation() {
    final List<KeyPair> committerKeyPairs =
        IntStream.range(0, 2).mapToObj(i -> KeyPair.generate()).collect(Collectors.toList());

    final List<Address> committerAddresses =
        committerKeyPairs
            .stream()
            .map(keyPair -> Util.publicKeyToAddress(keyPair.getPublicKey()))
            .sorted()
            .collect(Collectors.toList());

    final VoteTally voteTally = new VoteTally(committerAddresses);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, new IbftContext(voteTally, null));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    BlockHeader header = createProposedBlockHeader(committerAddresses, committerKeyPairs, false);

    assertThat(extraDataValidationRule.validate(header, null, context)).isTrue();
  }

  @Test
  public void insufficientCommitSealsFailsValidation() {
    final KeyPair committerKeyPair = KeyPair.generate();
    final Address committerAddress =
        Address.extract(Hash.hash(committerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(committerAddress);
    final VoteTally voteTally = new VoteTally(validators);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, new IbftContext(voteTally, null));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    final BlockHeader header = createProposedBlockHeader(validators, emptyList(), false);

    // Note that no committer seals are in the header's IBFT extra data.
    final IbftExtraData headerExtraData = IbftExtraData.decode(header.getExtraData());
    assertThat(headerExtraData.getSeals().size()).isEqualTo(0);

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void outOfOrderValidatorListFailsValidation() {
    final List<KeyPair> committerKeyPairs =
        IntStream.range(0, 2).mapToObj(i -> KeyPair.generate()).collect(Collectors.toList());

    final List<Address> committerAddresses =
        committerKeyPairs
            .stream()
            .map(keyPair -> Util.publicKeyToAddress(keyPair.getPublicKey()))
            .sorted()
            .collect(Collectors.toList());

    final List<Address> validators = Lists.reverse(committerAddresses);

    final VoteTally voteTally = new VoteTally(validators);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, new IbftContext(voteTally, null));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    BlockHeader header = createProposedBlockHeader(validators, committerKeyPairs, false);

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void mismatchingReportedValidatorsVsLocallyStoredListFailsValidation() {
    final List<KeyPair> committerKeyPairs =
        IntStream.range(0, 2).mapToObj(i -> KeyPair.generate()).collect(Collectors.toList());

    final List<Address> validators =
        IntStream.range(0, 2)
            .mapToObj(i -> Util.publicKeyToAddress(KeyPair.generate().getPublicKey()))
            .collect(Collectors.toList());

    final VoteTally voteTally = new VoteTally(validators);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, new IbftContext(voteTally, null));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    BlockHeader header = createProposedBlockHeader(validators, committerKeyPairs, false);

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void committerNotInValidatorListFailsValidation() {
    final KeyPair committerKeyPair = KeyPair.generate();
    final Address committerAddress = Util.publicKeyToAddress(committerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(committerAddress);
    final VoteTally voteTally = new VoteTally(validators);

    // Insert an extraData block with committer seals.
    final KeyPair nonValidatorKeyPair = KeyPair.generate();

    BlockHeader header =
        createProposedBlockHeader(validators, singletonList(nonValidatorKeyPair), false);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, new IbftContext(voteTally, null));
    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void ratioOfCommittersToValidatorsAffectValidation() {
    assertThat(subExecution(4, 4, false)).isEqualTo(true);
    assertThat(subExecution(4, 3, false)).isEqualTo(true);
    assertThat(subExecution(4, 2, false)).isEqualTo(false);

    assertThat(subExecution(5, 4, false)).isEqualTo(true);
    assertThat(subExecution(5, 3, false)).isEqualTo(false);
    assertThat(subExecution(5, 2, false)).isEqualTo(false);

    assertThat(subExecution(6, 4, false)).isEqualTo(true);
    assertThat(subExecution(6, 3, false)).isEqualTo(false);
    assertThat(subExecution(6, 2, false)).isEqualTo(false);

    assertThat(subExecution(7, 5, false)).isEqualTo(true);
    assertThat(subExecution(7, 4, false)).isEqualTo(false);

    assertThat(subExecution(8, 6, false)).isEqualTo(true);
    assertThat(subExecution(8, 5, false)).isEqualTo(false);
    assertThat(subExecution(8, 4, false)).isEqualTo(false);

    assertThat(subExecution(9, 6, false)).isEqualTo(true);
    assertThat(subExecution(9, 5, false)).isEqualTo(false);
    assertThat(subExecution(9, 4, false)).isEqualTo(false);

    assertThat(subExecution(10, 7, false)).isEqualTo(true);
    assertThat(subExecution(10, 6, false)).isEqualTo(false);

    assertThat(subExecution(12, 8, false)).isEqualTo(true);
    assertThat(subExecution(12, 7, false)).isEqualTo(false);
    assertThat(subExecution(12, 6, false)).isEqualTo(false);
  }

  @Test
  public void validationFailsIfCommittedSealsAreForDifferentRounds() {
    assertThat(subExecution(2, 2, true)).isEqualTo(false);
    assertThat(subExecution(4, 4, true)).isEqualTo(false);
  }

  private boolean subExecution(
      final int validatorCount,
      final int committerCount,
      final boolean useDifferentRoundNumbersForCommittedSeals) {

    final List<Address> validators = Lists.newArrayList();
    final List<KeyPair> committerKeys = Lists.newArrayList();

    for (int i = 0; i < validatorCount; i++) { // need -1 to account for proposer
      final KeyPair committerKeyPair = KeyPair.generate();
      committerKeys.add(committerKeyPair);
      validators.add(Address.extract(Hash.hash(committerKeyPair.getPublicKey().getEncodedBytes())));
    }

    Collections.sort(validators);
    final VoteTally voteTally = new VoteTally(validators);
    BlockHeader header =
        createProposedBlockHeader(
            validators,
            committerKeys.subList(0, committerCount),
            useDifferentRoundNumbersForCommittedSeals);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, new IbftContext(voteTally, null));
    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    return extraDataValidationRule.validate(header, null, context);
  }
}
