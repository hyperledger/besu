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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DebugStandardTraceBlockToFileTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
  private final Blockchain blockchain = mock(Blockchain.class);
  private final TransactionTracer transactionTracer = mock(TransactionTracer.class);
  private final DebugStandardTraceBlockToFile debugStandardTraceBlockToFile =
      new DebugStandardTraceBlockToFile(
          () -> transactionTracer, blockchainQueries, folder.getRoot().toPath());

  @Test
  public void nameShouldBeDebugTraceTransaction() {
    assertThat(debugStandardTraceBlockToFile.getName()).isEqualTo("debug_standardTraceBlockToFile");
  }

  @SuppressWarnings("rawtypes")
  @Test
  public void shouldTraceTheTransactionUsingTheTransactionTracer() {

    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block genesis = blockGenerator.genesisBlock();
    final Block block =
        blockGenerator.block(
            new BlockDataGenerator.BlockOptions().setParentHash(genesis.getHeader().getHash()));

    final Object[] params = new Object[] {block.getHash(), new HashMap<>()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "debug_standardTraceBlockToFile", params));

    final List<String> paths = new ArrayList<>();
    paths.add("path-1");

    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);

    when(blockchain.getBlockByHash(block.getHash())).thenReturn(Optional.of(block));

    when(transactionTracer.traceTransactionToFile(eq(block.getHash()), any(), any()))
        .thenReturn(paths);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) debugStandardTraceBlockToFile.response(request);
    final List result = (ArrayList) response.getResult();

    assertThat(result.size()).isEqualTo(1);
  }
}
