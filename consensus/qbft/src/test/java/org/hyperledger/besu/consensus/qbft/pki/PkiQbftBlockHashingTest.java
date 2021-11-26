/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.consensus.qbft.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataFixture;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class PkiQbftBlockHashingTest {

  private PkiQbftExtraDataCodec pkiExtraDataCodec = new PkiQbftExtraDataCodec();
  private PkiQbftBlockHashing pkiQbftBlockHashing;

  @Before
  public void before() {
    pkiExtraDataCodec = spy(new PkiQbftExtraDataCodec());
    pkiQbftBlockHashing = new PkiQbftBlockHashing(pkiExtraDataCodec);
  }

  @Test
  public void blockHashingUsesCorrectEncodingWithoutCmsMethodInCodec() {
    final PkiQbftExtraData pkiQbftExtraData = createPkiQbftExtraData();
    final BlockHeader headerWithExtraData =
        new BlockHeaderTestFixture()
            .number(1L)
            .extraData(pkiExtraDataCodec.encode(pkiQbftExtraData))
            .buildHeader();

    // Expected hash using the extraData encoded by the encodeWithoutCms method of the codec
    final Hash expectedHash =
        Hash.hash(
            BftBlockHashing.serializeHeader(
                headerWithExtraData,
                () -> pkiExtraDataCodec.encodeWithoutCms(pkiQbftExtraData),
                pkiExtraDataCodec));

    final Hash hash =
        pkiQbftBlockHashing.calculateHashOfBftBlockForCmsSignature(headerWithExtraData);

    assertThat(hash).isEqualTo(expectedHash);

    /*
     Verify that the encodeWithoutCms method was called twice, once when calculating the
     expected hash and a second time as part of the hash calculation on
     calculateHashOfBftBlockForCmsSignature
    */
    verify(pkiExtraDataCodec, times(2)).encodeWithoutCms(any(PkiQbftExtraData.class));
  }

  private PkiQbftExtraData createPkiQbftExtraData() {
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    final BftExtraData extraData =
        BftExtraDataFixture.createExtraData(blockHeader, pkiExtraDataCodec);
    return new PkiQbftExtraData(extraData, Bytes.random(32));
  }
}
