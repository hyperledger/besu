/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft.headervalidationrules;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibft.IbftContextBuilder.setupContextWithValidators;
import static org.hyperledger.besu.consensus.ibft.headervalidationrules.HeaderValidationTestHelpers.createProposedBlockHeader;

import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import org.junit.Test;

public class IbftCommitSealsValidationRuleTest {

  private final IbftCommitSealsValidationRule commitSealsValidationRule =
      new IbftCommitSealsValidationRule();

  @Test
  public void correctlyConstructedHeaderPassesValidation() {
    final List<KeyPair> committerKeyPairs =
        IntStream.range(0, 2).mapToObj(i -> KeyPair.generate()).collect(Collectors.toList());

    final List<Address> committerAddresses =
        committerKeyPairs.stream()
            .map(keyPair -> Util.publicKeyToAddress(keyPair.getPublicKey()))
            .sorted()
            .collect(Collectors.toList());

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(committerAddresses));

    BlockHeader header = createProposedBlockHeader(committerAddresses, committerKeyPairs, false);

    assertThat(commitSealsValidationRule.validate(header, null, context)).isTrue();
  }

  @Test
  public void insufficientCommitSealsFailsValidation() {
    final KeyPair committerKeyPair = KeyPair.generate();
    final Address committerAddress =
        Address.extract(Hash.hash(committerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(committerAddress);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final BlockHeader header = createProposedBlockHeader(validators, emptyList(), false);

    // Note that no committer seals are in the header's IBFT extra data.
    final IbftExtraData headerExtraData = IbftExtraData.decode(header);
    assertThat(headerExtraData.getSeals().size()).isEqualTo(0);

    assertThat(commitSealsValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void committerNotInValidatorListFailsValidation() {
    final KeyPair committerKeyPair = KeyPair.generate();
    final Address committerAddress = Util.publicKeyToAddress(committerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(committerAddress);

    // Insert an extraData block with committer seals.
    final KeyPair nonValidatorKeyPair = KeyPair.generate();

    final BlockHeader header =
        createProposedBlockHeader(validators, singletonList(nonValidatorKeyPair), false);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    assertThat(commitSealsValidationRule.validate(header, null, context)).isFalse();
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

  @Test
  public void headerContainsDuplicateSealsFailsValidation() {
    final KeyPair committerKeyPair = KeyPair.generate();
    final List<Address> validators =
        singletonList(Util.publicKeyToAddress(committerKeyPair.getPublicKey()));
    final BlockHeader header =
        createProposedBlockHeader(
            validators, Lists.newArrayList(committerKeyPair, committerKeyPair), false);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    assertThat(commitSealsValidationRule.validate(header, null, context)).isFalse();
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
    final BlockHeader header =
        createProposedBlockHeader(
            validators,
            committerKeys.subList(0, committerCount),
            useDifferentRoundNumbersForCommittedSeals);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    return commitSealsValidationRule.validate(header, null, context);
  }
}
