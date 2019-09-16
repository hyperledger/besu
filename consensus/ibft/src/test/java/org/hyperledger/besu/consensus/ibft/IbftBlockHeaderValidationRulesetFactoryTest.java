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
package org.hyperledger.besu.consensus.ibft;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibft.IbftContextBuilder.setupContextWithValidators;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class IbftBlockHeaderValidationRulesetFactoryTest {

  private final ProtocolContext<IbftContext> protocolContext(final Collection<Address> validators) {
    return new ProtocolContext<>(null, null, setupContextWithValidators(validators));
  }

  @Test
  public void ibftValidateHeaderPasses() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader).buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isTrue();
  }

  @Test
  public void ibftValidateHeaderFailsOnExtraData() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, emptyList(), parentHeader).buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnCoinbaseData() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final Address nonProposerAddress = Util.publicKeyToAddress(KeyPair.generate().getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader)
            .coinbase(nonProposerAddress)
            .buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnNonce() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader).nonce(3).buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnTimestamp() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader)
            .timestamp(100)
            .buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnMixHash() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader)
            .mixHash(Hash.EMPTY_TRIE_HASH)
            .buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnOmmers() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader)
            .ommersHash(Hash.EMPTY_TRIE_HASH)
            .buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnDifficulty() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader)
            .difficulty(UInt256.of(5))
            .buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnAncestor() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, null).buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnGasUsage() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader)
            .gasLimit(5_000)
            .gasUsed(6_000)
            .buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void ibftValidateHeaderFailsOnGasLimitRange() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader =
        getPresetHeaderBuilder(1, proposerKeyPair, validators, null).buildHeader();
    final BlockHeader blockHeader =
        getPresetHeaderBuilder(2, proposerKeyPair, validators, parentHeader)
            .gasLimit(4999)
            .buildHeader();

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  private BlockHeaderTestFixture getPresetHeaderBuilder(
      final long number,
      final KeyPair proposerKeyPair,
      final List<Address> validators,
      final BlockHeader parent) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();

    if (parent != null) {
      builder.parentHash(parent.getHash());
    }
    builder.number(number);
    builder.gasLimit(5000);
    builder.timestamp(6000 * number);
    builder.mixHash(
        Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"));
    builder.ommersHash(Hash.EMPTY_LIST_HASH);
    builder.nonce(0);
    builder.difficulty(UInt256.ONE);
    builder.coinbase(Util.publicKeyToAddress(proposerKeyPair.getPublicKey()));

    final IbftExtraData ibftExtraData =
        IbftExtraDataFixture.createExtraData(
            builder.buildHeader(),
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            Optional.of(Vote.authVote(Address.fromHexString("1"))),
            validators,
            singletonList(proposerKeyPair),
            0xDEADBEEF);

    builder.extraData(ibftExtraData.encode());
    return builder;
  }
}
