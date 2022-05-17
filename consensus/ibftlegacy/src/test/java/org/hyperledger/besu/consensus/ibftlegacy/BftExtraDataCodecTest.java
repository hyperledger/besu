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
package org.hyperledger.besu.consensus.ibftlegacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class BftExtraDataCodecTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  @Test
  public void emptyListsConstituteValidContent() {
    final SECPSignature proposerSeal =
        SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 0);
    final List<Address> validators = Lists.newArrayList();
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));
    encoder.writeBytes(proposerSeal.encodedBytes());
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);
    final Bytes bufferToInject = Bytes.wrap(vanity_data, encoder.encoded());

    final IbftExtraData extraData = IbftExtraData.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getProposerSeal()).isEqualTo(proposerSeal);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
  }

  @Test
  public void fullyPopulatedDataProducesCorrectlyFormedExtraDataObject() {
    final List<Address> validators = Arrays.asList(Address.ECREC, Address.SHA256);
    final SECPSignature proposerSeal =
        SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 0);
    final List<SECPSignature> committerSeals =
        Arrays.asList(
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList(); // This is required to create a "root node" for all RLP'd data
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));
    encoder.writeBytes(proposerSeal.encodedBytes());
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    // Create randomised vanity data.
    final byte[] vanity_bytes = new byte[32];
    new Random().nextBytes(vanity_bytes);
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);
    final Bytes bufferToInject = Bytes.wrap(vanity_data, encoder.encoded());

    final IbftExtraData extraData = IbftExtraData.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getProposerSeal()).isEqualTo(proposerSeal);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
  }

  @Test
  public void incorrectlyStructuredRlpThrowsException() {
    final SECPSignature proposerSeal =
        SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 0);
    final List<Address> validators = Lists.newArrayList();
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));
    encoder.writeBytes(proposerSeal.encodedBytes());
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.writeLong(1);
    encoder.endList();

    final Bytes bufferToInject = Bytes.wrap(Bytes.wrap(new byte[32]), encoder.encoded());

    assertThatThrownBy(() -> IbftExtraData.decodeRaw(bufferToInject))
        .isInstanceOf(RLPException.class);
  }

  @Test
  public void incorrectlySizedVanityDataThrowsException() {
    final List<Address> validators = Arrays.asList(Address.ECREC, Address.SHA256);
    final SECPSignature proposerSeal =
        SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 0);
    final List<SECPSignature> committerSeals =
        Arrays.asList(
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));
    encoder.writeBytes(proposerSeal.encodedBytes());
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    final Bytes bufferToInject = Bytes.wrap(Bytes.wrap(new byte[31]), encoder.encoded());

    assertThatThrownBy(() -> IbftExtraData.decodeRaw(bufferToInject))
        .isInstanceOf(RLPException.class);
  }

  @Test
  public void parseGenesisBlockWithZeroProposerSeal() {
    final byte[] genesisBlockExtraData =
        Hex.decode(
            "0000000000000000000000000000000000000000000000000000000000000000f89af85494c332d0db1704d18f89a590e7586811e36d37ce049424defc2d149861d3d245749b81fe0e6b28e04f31943814f17bd4b7ce47ab8146684b3443c0a4b2fc2c942a813d7db3de19b07f92268b6d4125ed295cbe00b8410000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0");

    final Bytes bufferToInject = Bytes.wrap(genesisBlockExtraData);

    final IbftExtraData extraData = IbftExtraData.decodeRaw(bufferToInject);
    assertThat(extraData.getProposerSeal()).isNull();
  }
}
