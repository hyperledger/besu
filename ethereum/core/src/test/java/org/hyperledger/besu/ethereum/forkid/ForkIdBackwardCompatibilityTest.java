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
package org.hyperledger.besu.ethereum.forkid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.forkid.ForkIdTestUtil.GenesisHash;
import static org.hyperledger.besu.ethereum.forkid.ForkIdTestUtil.mockBlockchain;

import org.hyperledger.besu.ethereum.chain.Blockchain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForkIdBackwardCompatibilityTest {
  private static final Logger LOG = LoggerFactory.getLogger(ForkIdBackwardCompatibilityTest.class);

  public static Stream<Arguments> data() {
    return Stream.of(
        Arguments.of(
            "with 0 forks and legacyEth64=false",
            GenesisHash.PRIVATE,
            2L,
            Arrays.asList(0L, 0L, 4L, 5L, 6L),
            false,
            new ForkId(Bytes.fromHexString("0x190a55ad"), 4L)),
        Arguments.of(
            "with 0 forks and legacyEth64=true",
            GenesisHash.PRIVATE,
            2L,
            Arrays.asList(0L, 0L, 4L, 5L, 6L),
            true,
            null),
        Arguments.of(
            "with no 0 forks and legacyEth64=false",
            GenesisHash.PRIVATE,
            2L,
            Arrays.asList(4L, 5L, 6L),
            false,
            new ForkId(Bytes.fromHexString("0x190a55ad"), 4L)),
        Arguments.of(
            "with no 0 forks and legacyEth64=true",
            GenesisHash.PRIVATE,
            2L,
            Arrays.asList(4L, 5L, 6L),
            true,
            null),
        Arguments.of(
            "post head with 0 forks and legacyEth64=false",
            GenesisHash.PRIVATE,
            8L,
            Arrays.asList(0L, 0L, 4L, 5L, 6L),
            false,
            new ForkId(Bytes.fromHexString("0x033462fc"), 0L)),
        Arguments.of(
            "post head with 0 forks and legacyEth64=true",
            GenesisHash.PRIVATE,
            8L,
            Arrays.asList(0L, 0L, 4L, 5L, 6L),
            true,
            null),
        Arguments.of(
            "no forks and legacyEth64=true",
            GenesisHash.PRIVATE,
            8L,
            Collections.emptyList(),
            true,
            null));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("data")
  public void assertBackwardCompatibilityWorks(
      final String name,
      final String genesisHash,
      final long head,
      final List<Long> forks,
      final boolean legacyEth64,
      final ForkId wantForkId) {
    LOG.info("Running test case {}", name);
    final Blockchain blockchain = mockBlockchain(genesisHash, head, 0);
    final ForkIdManager forkIdManager =
        new ForkIdManager(blockchain, forks, Collections.emptyList(), legacyEth64);
    final ForkId legacyForkId =
        legacyEth64 ? new LegacyForkIdManager(blockchain, forks).getLatestForkId() : null;
    assertThat(forkIdManager.getForkIdForChainHead())
        .isEqualTo(legacyEth64 ? legacyForkId : wantForkId);
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
