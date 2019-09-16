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
package org.hyperledger.besu.consensus.ibftlegacy.headervalidationrules;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibft.IbftContextBuilder.setupContextWithValidators;

import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibftlegacy.IbftBlockHashing;
import org.hyperledger.besu.consensus.ibftlegacy.IbftExtraData;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class IbftExtraDataValidationRuleTest {

  private BlockHeader createProposedBlockHeader(
      final KeyPair proposerKeyPair, final List<Address> validators) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all

    // Construct an extraData block and add to a header
    final IbftExtraData initialIbftExtraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            emptyList(),
            null,
            validators);
    builder.extraData(initialIbftExtraData.encode());
    final BlockHeader header = builder.buildHeader();

    // Hash the header (ignoring committer and proposer seals), and create signature
    final Hash proposerSealHash =
        IbftBlockHashing.calculateDataHashForProposerSeal(header, initialIbftExtraData);
    final Signature proposerSignature = SECP256K1.sign(proposerSealHash, proposerKeyPair);

    // Construct a new extraData block, containing the constructed proposer signature
    final IbftExtraData proposedData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            emptyList(),
            proposerSignature,
            validators);

    // insert the signed ExtraData into the block
    builder.extraData(proposedData.encode());
    return builder.buildHeader();
  }

  private IbftExtraData createExtraDataWithCommitSeals(
      final BlockHeader header, final Collection<KeyPair> committerKeyPairs) {
    final IbftExtraData extraDataInHeader = IbftExtraData.decode(header);

    final Hash headerHashForCommitters =
        IbftBlockHashing.calculateDataHashForCommittedSeal(header, extraDataInHeader);

    final List<Signature> commitSeals =
        committerKeyPairs.stream()
            .map(keys -> SECP256K1.sign(headerHashForCommitters, keys))
            .collect(Collectors.toList());

    return new IbftExtraData(
        extraDataInHeader.getVanityData(),
        commitSeals,
        extraDataInHeader.getProposerSeal(),
        extraDataInHeader.getValidators());
  }

  @Test
  public void correctlyConstructedHeaderPassesValidation() {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators);

    // Insert an extraData block with committer seals.
    final IbftExtraData commitedExtraData =
        createExtraDataWithCommitSeals(header, singletonList(proposerKeyPair));
    builder.extraData(commitedExtraData.encode());
    header = builder.buildHeader();

    assertThat(extraDataValidationRule.validate(header, null, context)).isTrue();
  }

  @Test
  public void insufficientCommitSealsFailsValidation() {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);
    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    final BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators);

    // Note that no committer seals are in the header's IBFT extra data.
    final IbftExtraData headerExtraData = IbftExtraData.decode(header);
    Assertions.assertThat(headerExtraData.getSeals().size()).isEqualTo(0);

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void outOfOrderValidatorListFailsValidation() {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.calculateAddressWithRespectTo(proposerAddress, 1), proposerAddress);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators);

    // Insert an extraData block with committer seals.
    final IbftExtraData commitedExtraData =
        createExtraDataWithCommitSeals(header, singletonList(proposerKeyPair));
    builder.extraData(commitedExtraData.encode());
    header = builder.buildHeader();

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void proposerNotInValidatorListFailsValidation() {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.calculateAddressWithRespectTo(proposerAddress, 1), proposerAddress);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators);

    // Insert an extraData block with committer seals.
    final IbftExtraData commitedExtraData =
        createExtraDataWithCommitSeals(header, singletonList(proposerKeyPair));
    builder.extraData(commitedExtraData.encode());
    header = builder.buildHeader();

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void mismatchingReportedValidatorsVsLocallyStoredListFailsValidation() {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = Lists.newArrayList(proposerAddress);

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    // Add another validator to the list reported in the IbftExtraData (note, as the
    final List<Address> extraDataValidators =
        Lists.newArrayList(
            proposerAddress, AddressHelpers.calculateAddressWithRespectTo(proposerAddress, 1));
    BlockHeader header = createProposedBlockHeader(proposerKeyPair, extraDataValidators);

    // Insert an extraData block with committer seals.
    final IbftExtraData commitedExtraData =
        createExtraDataWithCommitSeals(header, singletonList(proposerKeyPair));
    builder.extraData(commitedExtraData.encode());
    header = builder.buildHeader();

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void committerNotInValidatorListFailsValidation() {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);

    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators);

    // Insert an extraData block with committer seals.
    final KeyPair nonValidatorKeyPair = KeyPair.generate();
    final IbftExtraData commitedExtraData =
        createExtraDataWithCommitSeals(header, singletonList(nonValidatorKeyPair));
    builder.extraData(commitedExtraData.encode());
    header = builder.buildHeader();

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));
    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void ratioOfCommittersToValidatorsAffectValidation() {
    assertThat(subExecution(4, 4)).isEqualTo(true);
    assertThat(subExecution(4, 3)).isEqualTo(true);
    assertThat(subExecution(4, 2)).isEqualTo(false);

    assertThat(subExecution(5, 3)).isEqualTo(true);
    assertThat(subExecution(5, 2)).isEqualTo(false);

    assertThat(subExecution(6, 4)).isEqualTo(true);
    assertThat(subExecution(6, 3)).isEqualTo(true);
    assertThat(subExecution(6, 2)).isEqualTo(false);

    assertThat(subExecution(7, 5)).isEqualTo(true);
    assertThat(subExecution(7, 4)).isEqualTo(false);

    assertThat(subExecution(9, 5)).isEqualTo(true);
    assertThat(subExecution(9, 4)).isEqualTo(false);

    assertThat(subExecution(10, 7)).isEqualTo(true);
    assertThat(subExecution(10, 6)).isEqualTo(false);

    assertThat(subExecution(12, 7)).isEqualTo(true);
    assertThat(subExecution(12, 6)).isEqualTo(false);

    // The concern in the above is that when using 6 validators, only 1/2 the validators are
    // required to seal a block. All other combinations appear to be safe they always have >50%
    // validators sealing the block.

  }

  private boolean subExecution(final int validatorCount, final int committerCount) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    final KeyPair proposerKeyPair = KeyPair.generate();

    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = Lists.newArrayList();
    final List<KeyPair> committerKeys = Lists.newArrayList();
    validators.add(proposerAddress);
    committerKeys.add(proposerKeyPair);
    for (int i = 0; i < validatorCount - 1; i++) { // need -1 to account for proposer
      final KeyPair committerKeyPair = KeyPair.generate();
      committerKeys.add(committerKeyPair);
      validators.add(Address.extract(Hash.hash(committerKeyPair.getPublicKey().getEncodedBytes())));
    }

    Collections.sort(validators);
    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators);
    final IbftExtraData commitedExtraData =
        createExtraDataWithCommitSeals(header, committerKeys.subList(0, committerCount));

    builder.extraData(commitedExtraData.encode());
    header = builder.buildHeader();

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));
    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true);

    return extraDataValidationRule.validate(header, null, context);
  }
}
