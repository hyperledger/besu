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
package org.hyperledger.besu.ethereum.eth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.ForkIdTestUtil.GenesisHash;
import static org.hyperledger.besu.ethereum.eth.ForkIdTestUtil.mockBlockchain;

import org.hyperledger.besu.ethereum.eth.manager.ForkId;
import org.hyperledger.besu.ethereum.eth.manager.ForkIdManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class ForkIdBackwardCompatibilityTest {
  private static final Logger LOG = LoggerFactory.getLogger(ForkIdBackwardCompatibilityTest.class);

  private final String name;
  private final String genesisHash;
  private final long head;
  private final List<Long> forks;
  private final boolean legacyEth64;
  private final ForkId wantForkId;

  public ForkIdBackwardCompatibilityTest(
      final String name,
      final String genesisHash,
      final long head,
      final List<Long> forks,
      final boolean legacyEth64,
      final ForkId wantForkId) {
    this.name = name;
    this.genesisHash = genesisHash;
    this.head = head;
    this.forks = forks;
    this.legacyEth64 = legacyEth64;
    this.wantForkId = wantForkId;
  }

  @Parameterized.Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "with 0 forks and legacyEth64=false",
            GenesisHash.PRIVATE,
            2L,
            Arrays.asList(0L, 0L, 4L, 5L, 6L),
            false,
            new ForkId(Bytes.fromHexString("0x190a55ad"), 4L)
          },
          {
            "with 0 forks and legacyEth64=true",
            GenesisHash.PRIVATE,
            2L,
            Arrays.asList(0L, 0L, 4L, 5L, 6L),
            true,
            null
          },
          {
            "with no 0 forks and legacyEth64=false",
            GenesisHash.PRIVATE,
            2L,
            Arrays.asList(4L, 5L, 6L),
            false,
            new ForkId(Bytes.fromHexString("0x190a55ad"), 4L)
          },
          {
            "with no 0 forks and legacyEth64=true",
            GenesisHash.PRIVATE,
            2L,
            Arrays.asList(4L, 5L, 6L),
            true,
            null
          },
          {
            "post head with 0 forks and legacyEth64=false",
            GenesisHash.PRIVATE,
            8L,
            Arrays.asList(0L, 0L, 4L, 5L, 6L),
            false,
            new ForkId(Bytes.fromHexString("0x033462fc"), 0L)
          },
          {
            "post head with 0 forks and legacyEth64=true",
            GenesisHash.PRIVATE,
            8L,
            Arrays.asList(0L, 0L, 4L, 5L, 6L),
            true,
            null
          },
        });
  }

  @Test
  public void assertBackwardCompatibilityWorks() {
    LOG.info("Running test case {}", name);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(genesisHash, head), forks, legacyEth64);
    final ForkId legacyForkId =
        legacyEth64
            ? new LegacyForkIdManager(mockBlockchain(genesisHash, head), forks).getLatestForkId()
            : null;
    assertThat(forkIdManager.getForkIdForChainHead())
        .isEqualTo(legacyEth64 ? legacyForkId : wantForkId);
  }
}
