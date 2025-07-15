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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Era1FileReaderTest {
  private static Path testFilePath;

  private Era1FileReader era1FileReader;

  @BeforeAll
  public static void setupClass() throws URISyntaxException {
    testFilePath =
        Path.of(
            Era1FileSourceTest.class
                .getClassLoader()
                .getResource("mainnet-00000-5ec1ffb8.era1")
                .toURI());
  }

  @BeforeEach
  public void beforeTest() {
    era1FileReader = new Era1FileReader(new MainnetBlockHeaderFunctions());
  }

  @Test
  public void testApply() throws ExecutionException, InterruptedException {
    CompletableFuture<List<Block>> result = era1FileReader.apply(testFilePath);

    Assertions.assertTrue(result.isDone());
    List<Block> blockList = result.get();
    Assertions.assertEquals(8192, blockList.size());
    Assertions.assertEquals(0, blockList.getFirst().getHeader().getNumber());
    Assertions.assertEquals(8191, blockList.getLast().getHeader().getNumber());
  }
}
