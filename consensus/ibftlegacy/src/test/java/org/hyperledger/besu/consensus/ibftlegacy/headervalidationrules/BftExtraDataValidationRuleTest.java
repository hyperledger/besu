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
package org.hyperledger.besu.consensus.ibftlegacy.headervalidationrules;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibftlegacy.IbftLegacyContextBuilder.setupContextWithValidators;

import org.hyperledger.besu.consensus.ibftlegacy.IbftBlockHashing;
import org.hyperledger.besu.consensus.ibftlegacy.IbftExtraData;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class BftExtraDataValidationRuleTest {

  private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

  private BlockHeader createProposedBlockHeader(
      final KeyPair proposerKeyPair, final List<Address> validators) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all

    // Construct an extraData block and add to a header
    final IbftExtraData initialIbftExtraData =
        new IbftExtraData(
            Bytes.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]), emptyList(), null, validators);
    builder.extraData(initialIbftExtraData.encode());
    final BlockHeader header = builder.buildHeader();

    // Hash the header (ignoring committer and proposer seals), and create signature
    final Hash proposerSealHash =
        IbftBlockHashing.calculateDataHashForProposerSeal(header, initialIbftExtraData);
    final SECPSignature proposerSignature =
        signatureAlgorithm.sign(proposerSealHash, proposerKeyPair);

    // Construct a new extraData block, containing the constructed proposer signature
    final IbftExtraData proposedData =
        new IbftExtraData(
            Bytes.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
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

    final List<SECPSignature> commitSeals =
        committerKeyPairs.stream()
            .map(keys -> signatureAlgorithm.sign(headerHashForCommitters, keys))
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
    final KeyPair proposerKeyPair = signatureAlgorithm.generateKeyPair();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);
    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true, 0);

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
    final KeyPair proposerKeyPair = signatureAlgorithm.generateKeyPair();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);
    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true, 0);

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
    final KeyPair proposerKeyPair = signatureAlgorithm.generateKeyPair();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.calculateAddressWithRespectTo(proposerAddress, 1), proposerAddress);

    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true, 0);

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
    final KeyPair proposerKeyPair = signatureAlgorithm.generateKeyPair();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.calculateAddressWithRespectTo(proposerAddress, 1), proposerAddress);

    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true, 0);

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
    final KeyPair proposerKeyPair = signatureAlgorithm.generateKeyPair();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = Lists.newArrayList(proposerAddress);

    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithValidators(validators));

    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true, 0);

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
    final KeyPair proposerKeyPair = signatureAlgorithm.generateKeyPair();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);

    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators);

    // Insert an extraData block with committer seals.
    final KeyPair nonValidatorKeyPair = signatureAlgorithm.generateKeyPair();
    final IbftExtraData commitedExtraData =
        createExtraDataWithCommitSeals(header, singletonList(nonValidatorKeyPair));
    builder.extraData(commitedExtraData.encode());
    header = builder.buildHeader();

    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithValidators(validators));
    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true, 0);

    assertThat(extraDataValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void ratioOfCommittersToValidatorsAffectValidation() {
    assertThat(subExecution(4, 4, false)).isEqualTo(true);
    assertThat(subExecution(4, 3, false)).isEqualTo(true);
    assertThat(subExecution(4, 2, false)).isEqualTo(false);

    assertThat(subExecution(5, 3, false)).isEqualTo(true);
    assertThat(subExecution(5, 2, false)).isEqualTo(false);

    assertThat(subExecution(6, 4, false)).isEqualTo(true);
    assertThat(subExecution(6, 3, false)).isEqualTo(true);
    assertThat(subExecution(6, 2, false)).isEqualTo(false);

    assertThat(subExecution(7, 5, false)).isEqualTo(true);
    assertThat(subExecution(7, 4, false)).isEqualTo(false);

    assertThat(subExecution(9, 5, false)).isEqualTo(true);
    assertThat(subExecution(9, 4, false)).isEqualTo(false);

    assertThat(subExecution(10, 7, false)).isEqualTo(true);
    assertThat(subExecution(10, 6, false)).isEqualTo(false);

    assertThat(subExecution(12, 7, false)).isEqualTo(true);
    assertThat(subExecution(12, 6, false)).isEqualTo(false);

    // The concern in the above is that when using 6 validators, only 1/2 the validators are
    // required to seal a block. All other combinations appear to be safe they always have >50%
    // validators sealing the block.

  }

  private boolean subExecution(
      final int validatorCount, final int committerCount, final boolean useTwoThirds) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all
    final KeyPair proposerKeyPair = signatureAlgorithm.generateKeyPair();

    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = Lists.newArrayList();
    final List<KeyPair> committerKeys = Lists.newArrayList();
    validators.add(proposerAddress);
    committerKeys.add(proposerKeyPair);
    for (int i = 0; i < validatorCount - 1; i++) { // need -1 to account for proposer
      final KeyPair committerKeyPair = signatureAlgorithm.generateKeyPair();
      committerKeys.add(committerKeyPair);
      validators.add(Address.extract(Hash.hash(committerKeyPair.getPublicKey().getEncodedBytes())));
    }

    Collections.sort(validators);
    BlockHeader header = createProposedBlockHeader(proposerKeyPair, validators);
    final IbftExtraData commitedExtraData =
        createExtraDataWithCommitSeals(header, committerKeys.subList(0, committerCount));

    builder.extraData(commitedExtraData.encode());
    header = builder.buildHeader();

    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithValidators(validators));
    final IbftExtraDataValidationRule extraDataValidationRule =
        new IbftExtraDataValidationRule(true, useTwoThirds ? 0 : 2);

    return extraDataValidationRule.validate(header, null, context);
  }
}
