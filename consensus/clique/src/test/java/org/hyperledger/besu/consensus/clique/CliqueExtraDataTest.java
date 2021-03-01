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
package org.hyperledger.besu.consensus.clique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Util;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class CliqueExtraDataTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  @Test
  public void encodeAndDecodingDoNotAlterData() {
    final SECPSignature proposerSeal =
        SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 0);
    final List<Address> validators =
        Arrays.asList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));
    final Bytes vanityData = Bytes.fromHexString("11223344", 32);

    final CliqueExtraData extraData =
        new CliqueExtraData(
            vanityData, proposerSeal, validators, new BlockHeaderTestFixture().buildHeader());

    final Bytes serialisedData = extraData.encode();

    final CliqueExtraData decodedExtraData =
        CliqueExtraData.decodeRaw(createHeaderWithExtraData(serialisedData));

    assertThat(decodedExtraData.getValidators()).isEqualTo(validators);
    assertThat(decodedExtraData.getProposerSeal().get()).isEqualTo(proposerSeal);
    assertThat(decodedExtraData.getVanityData()).isEqualTo(vanityData);
  }

  @Test
  public void parseRinkebyGenesisBlockExtraData() {
    // Rinkeby genesis block extra data text found @ rinkeby.io
    final byte[] genesisBlockExtraData =
        Hex.decode(
            "52657370656374206d7920617574686f7269746168207e452e436172746d616e42eb768f2244c8811c63729a21a3569731535f067ffc57839b00206d1ad20c69a1981b489f772031b279182d99e65703f0076e4812653aab85fca0f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    final Bytes bufferToInject = Bytes.wrap(genesisBlockExtraData);

    final CliqueExtraData extraData =
        CliqueExtraData.decodeRaw(
            new BlockHeaderTestFixture()
                .number(BlockHeader.GENESIS_BLOCK_NUMBER)
                .blockHeaderFunctions(new CliqueBlockHeaderFunctions())
                .extraData(bufferToInject)
                .buildHeader());
    assertThat(extraData.getProposerSeal()).isEmpty();
    assertThat(extraData.getValidators().size()).isEqualTo(3);
  }

  @Test
  public void insufficientDataResultsInAnIllegalArgumentException() {
    final Bytes illegalData =
        Bytes.wrap(
            new byte[SECPSignature.BYTES_REQUIRED + CliqueExtraData.EXTRA_VANITY_LENGTH - 1]);

    assertThatThrownBy(() -> CliqueExtraData.decodeRaw(createHeaderWithExtraData(illegalData)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid Bytes supplied - too short to produce a valid Clique Extra Data object.");
  }

  @Test
  public void sufficientlyLargeButIllegallySizedInputThrowsException() {
    final Bytes illegalData =
        Bytes.wrap(
            new byte
                [SECPSignature.BYTES_REQUIRED
                    + CliqueExtraData.EXTRA_VANITY_LENGTH
                    + Address.SIZE
                    - 1]);

    assertThatThrownBy(() -> CliqueExtraData.decodeRaw(createHeaderWithExtraData(illegalData)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Bytes is of invalid size - i.e. contains unused bytes.");
  }

  @Test
  public void addressToExtraDataString() {
    final List<KeyPair> nodeKeys = Lists.newArrayList();
    for (int i = 0; i < 4; i++) {
      nodeKeys.add(SIGNATURE_ALGORITHM.get().generateKeyPair());
    }

    final List<Address> addresses =
        nodeKeys.stream()
            .map(KeyPair::getPublicKey)
            .map(Util::publicKeyToAddress)
            .collect(Collectors.toList());

    final String hexOutput = CliqueExtraData.createGenesisExtraDataString(addresses);

    final CliqueExtraData extraData =
        CliqueExtraData.decodeRaw(createHeaderWithExtraData(Bytes.fromHexString(hexOutput)));

    assertThat(extraData.getValidators()).containsExactly(addresses.toArray(new Address[0]));
  }

  private BlockHeader createHeaderWithExtraData(final Bytes illegalData) {
    return new BlockHeaderTestFixture()
        .blockHeaderFunctions(new CliqueBlockHeaderFunctions())
        .extraData(illegalData)
        .buildHeader();
  }
}
