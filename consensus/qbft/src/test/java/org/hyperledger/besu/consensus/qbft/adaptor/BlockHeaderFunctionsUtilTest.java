/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.adaptor;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.core.types.QbftHashMode;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class BlockHeaderFunctionsUtilTest {
  private final QbftExtraDataCodec qbftExtraDataCodec = new QbftExtraDataCodec();
  private final BftExtraData bftExtraData =
      new BftExtraData(
          Bytes.wrap(new byte[32]),
          List.of(new SECPSignature(BigInteger.ONE, BigInteger.ONE, (byte) 0)),
          Optional.empty(),
          2,
          emptyList());
  private final BlockHeader header =
      new BlockHeaderTestFixture()
          .number(1)
          .extraData(new QbftExtraDataCodec().encode(bftExtraData))
          .buildHeader();

  @Test
  void returnBlockHeaderFunctionThatHashesForCommitedSeal() {
    Hash expectedHash = BftBlockHeaderFunctions.forCommittedSeal(qbftExtraDataCodec).hash(header);
    Hash hash =
        BlockHeaderFunctionsUtil.getBlockHeaderFunctions(
                qbftExtraDataCodec, QbftHashMode.COMMITTED_SEAL)
            .hash(header);
    assertThat(hash).isEqualTo(expectedHash);
  }

  @Test
  void returnBlockHeaderFunctionThatHashesForOnChain() {
    Hash expectedHash = BftBlockHeaderFunctions.forOnchainBlock(qbftExtraDataCodec).hash(header);
    Hash hash =
        BlockHeaderFunctionsUtil.getBlockHeaderFunctions(qbftExtraDataCodec, QbftHashMode.ONCHAIN)
            .hash(header);
    assertThat(hash).isEqualTo(expectedHash);
  }
}
