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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DebugTraceBlockByHashTest {
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private Blockchain blockchain;
  private DebugTraceBlockByHash debugTraceBlockByHash;
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());

  @BeforeEach
  public void setUp() {
    debugTraceBlockByHash = new DebugTraceBlockByHash(protocolSchedule, blockchainQueries);
  }

  @Test
  public void nameShouldBeDebugTraceBlockByHash() {
    assertThat(debugTraceBlockByHash.getName()).isEqualTo("debug_traceBlockByHash");
  }

  @Test
  public void shouldReturnCorrectResponse() throws IOException {
    final Block block =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockHeaderFunctions(new MainnetBlockHeaderFunctions()));

    final Object[] params = new Object[] {block.getHash()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_traceBlockByHash", params));

    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getBlockByHash(block.getHash())).thenReturn(Optional.of(block));

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    debugTraceBlockByHash.streamResponse(request, out, mapper);
    final String json = out.toString(UTF_8);
    assertThat(json).startsWith("{\"jsonrpc\":\"2.0\"");
    assertThat(json).contains("\"result\":");
  }

  @Test
  public void shouldHandleInvalidParametersGracefully() {
    final Object[] invalidParams = new Object[] {"aaaa"};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "debug_traceBlockByHash", invalidParams));

    assertThatThrownBy(
            () ->
                debugTraceBlockByHash.streamResponse(request, new ByteArrayOutputStream(), mapper))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Invalid block hash parameter");
  }
}
