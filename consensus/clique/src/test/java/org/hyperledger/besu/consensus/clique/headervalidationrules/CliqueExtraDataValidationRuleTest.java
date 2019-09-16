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
package org.hyperledger.besu.consensus.clique.headervalidationrules;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.CliqueExtraData;
import org.hyperledger.besu.consensus.clique.TestHelpers;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class CliqueExtraDataValidationRuleTest {

  private final KeyPair proposerKeyPair = KeyPair.generate();
  private Address localAddr;

  private final List<Address> validatorList = Lists.newArrayList();
  private ProtocolContext<CliqueContext> cliqueProtocolContext;

  @Before
  public void setup() {
    localAddr = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    validatorList.add(localAddr);
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddr, 1));

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAfterBlock(any())).thenReturn(new VoteTally(validatorList));

    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, null, null);
    cliqueProtocolContext = new ProtocolContext<>(null, null, cliqueContext);
  }

  @Test
  public void missingSignerFailsValidation() {
    final BytesValue extraData =
        CliqueExtraData.createWithoutProposerSeal(
            BytesValue.wrap(new byte[32]), Lists.newArrayList());

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parent = headerBuilder.number(1).buildHeader();
    final BlockHeader child = headerBuilder.number(2).extraData(extraData).buildHeader();

    final CliqueExtraDataValidationRule rule =
        new CliqueExtraDataValidationRule(new EpochManager(10));

    assertThat(rule.validate(child, parent, cliqueProtocolContext)).isFalse();
  }

  @Test
  public void signerNotInExpectedValidatorsFailsValidation() {
    final KeyPair otherSigner = KeyPair.generate();

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parent = headerBuilder.number(1).buildHeader();
    headerBuilder.number(2);
    final BlockHeader badlySignedChild =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, otherSigner, Lists.newArrayList());

    final CliqueExtraDataValidationRule rule =
        new CliqueExtraDataValidationRule(new EpochManager(10));
    assertThat(rule.validate(badlySignedChild, parent, cliqueProtocolContext)).isFalse();
  }

  @Test
  public void signerIsInValidatorsAndValidatorsNotPresentWhenNotEpochIsSuccessful() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parent = headerBuilder.number(1).buildHeader();
    headerBuilder.number(2);
    final BlockHeader correctlySignedChild =
        TestHelpers.createCliqueSignedBlockHeader(
            headerBuilder, proposerKeyPair, Lists.newArrayList());

    final CliqueExtraDataValidationRule rule =
        new CliqueExtraDataValidationRule(new EpochManager(10));
    assertThat(rule.validate(correctlySignedChild, parent, cliqueProtocolContext)).isTrue();
  }

  @Test
  public void epochBlockContainsSameValidatorsAsContextIsSuccessful() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parent = headerBuilder.number(9).buildHeader();
    headerBuilder.number(10);
    final BlockHeader correctlySignedChild =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);

    final CliqueExtraDataValidationRule rule =
        new CliqueExtraDataValidationRule(new EpochManager(10));
    assertThat(rule.validate(correctlySignedChild, parent, cliqueProtocolContext)).isTrue();
  }

  @Test
  public void epochBlockWithMisMatchingListOfValidatorsFailsValidation() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parent = headerBuilder.number(9).buildHeader();
    headerBuilder.number(10);
    final BlockHeader correctlySignedChild =
        TestHelpers.createCliqueSignedBlockHeader(
            headerBuilder,
            proposerKeyPair,
            Lists.newArrayList(AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), localAddr));

    final CliqueExtraDataValidationRule rule =
        new CliqueExtraDataValidationRule(new EpochManager(10));
    assertThat(rule.validate(correctlySignedChild, parent, cliqueProtocolContext)).isFalse();
  }

  @Test
  public void nonEpochBlockContainingValidatorsFailsValidation() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parent = headerBuilder.number(8).buildHeader();
    headerBuilder.number(9);
    final BlockHeader correctlySignedChild =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);

    final CliqueExtraDataValidationRule rule =
        new CliqueExtraDataValidationRule(new EpochManager(10));
    assertThat(rule.validate(correctlySignedChild, parent, cliqueProtocolContext)).isFalse();
  }
}
