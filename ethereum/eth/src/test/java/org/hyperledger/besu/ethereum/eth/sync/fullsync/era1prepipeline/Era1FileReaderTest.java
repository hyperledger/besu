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
package org.hyperledger.besu.ethereum.eth.sync.fullsync.era1prepipeline;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Era1FileReaderTest {
  private Era1FileReader era1FileReader;

  @BeforeEach
  public void beforeTest() {
    era1FileReader =
        new Era1FileReader(new MainnetBlockHeaderFunctions(), new DeterministicEthScheduler());
  }

  @Test
  public void testApplyForFile()
      throws ExecutionException, InterruptedException, URISyntaxException {
    final URI testFileUri =
        Era1FileSourceTest.class
            .getClassLoader()
            .getResource("mainnet-00000-5ec1ffb8.era1")
            .toURI();
    CompletableFuture<List<Block>> result = era1FileReader.apply(testFileUri);

    Assertions.assertTrue(result.isDone());
    List<Block> blockList = result.get();
    Assertions.assertEquals(8192, blockList.size());
    Assertions.assertEquals(0, blockList.getFirst().getHeader().getNumber());
    Assertions.assertEquals(8191, blockList.getLast().getHeader().getNumber());
  }

  @Test
  public void testApplyForHttpsUrl() throws ExecutionException, InterruptedException {
    final URI testFileUri =
        URI.create("https://mainnet.era1.nimbus.team/mainnet-00000-5ec1ffb8.era1");
    CompletableFuture<List<Block>> result = era1FileReader.apply(testFileUri);

    Assertions.assertTrue(result.isDone());
    List<Block> blockList = result.get();
    Assertions.assertEquals(8192, blockList.size());
    Assertions.assertEquals(0, blockList.getFirst().getHeader().getNumber());
    Assertions.assertEquals(8191, blockList.getLast().getHeader().getNumber());
  }
}
