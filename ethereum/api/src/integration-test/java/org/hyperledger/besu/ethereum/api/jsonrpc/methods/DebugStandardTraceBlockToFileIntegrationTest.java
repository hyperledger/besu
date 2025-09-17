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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceTransactionStepFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.ibm.icu.impl.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

public class DebugStandardTraceBlockToFileIntegrationTest {
  private static final String RPC_ENDPOINT = "debug_standardTraceBlockToFile";
  private static JsonRpcTestMethodsFactory blockchain;
  private JsonRpcMethod method;

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  private static Path dataDir;

  @BeforeAll
  public static void setUpOnce() throws Exception {
    // experimental tracers are meant to be enabled in tests
    DebugTraceTransactionStepFactory.enableExtraTracers = true;

    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), StandardCharsets.UTF_8);

    blockchain =
        new JsonRpcTestMethodsFactory(
            new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson), dataDir);
  }

  @BeforeEach
  public void setUp() {
    final Map<String, JsonRpcMethod> methods = blockchain.methods();
    method = methods.get(RPC_ENDPOINT);
  }

  @Test
  public void defaultFieldsMemory() throws IOException {
    final Hash blockHash =
        Hash.fromHexString("0x10aaf14a53caf27552325374429d3558398a36d3682ede6603c2c6511896e9f9");
    final Object[] params = new Object[] {blockHash};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", RPC_ENDPOINT, params));

    final JsonRpcResponse response = method.response(request);
    assertThat(response.getType()).isEqualTo(RpcResponseType.SUCCESS);
    List<?> files = (List<?>) ((JsonRpcSuccessResponse) response).getResult();

    ObjectMapper jsonMapper = new ObjectMapper();
    List<JsonNode> json =
        Files.readAllLines(Path.of((String) files.getFirst())).stream()
            .map(
                line -> {
                  try {
                    return jsonMapper.readTree(line);
                  } catch (JsonProcessingException e) {
                    Assert.fail("encountered invalid json from RPC response");
                    throw new RuntimeException(e);
                  }
                })
            .toList();

    assertThat(json).anyMatch(node -> node.has("memory"));
    assertThat(json).anyMatch(node -> node.has("stack"));
  }

  @Test
  public void defaultFieldsStorage() throws IOException {
    final Hash blockHash =
        Hash.fromHexString("0x0362d0ee919714b702cb31d2f4fe6b5c834f36cc19558acb81a4832f86738e39");
    final Object[] params = new Object[] {blockHash};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", RPC_ENDPOINT, params));

    final JsonRpcResponse response = method.response(request);
    assertThat(response.getType()).isEqualTo(RpcResponseType.SUCCESS);
    List<?> files = (List<?>) ((JsonRpcSuccessResponse) response).getResult();

    ObjectMapper jsonMapper = new ObjectMapper();
    List<JsonNode> json =
        Files.readAllLines(Path.of((String) files.getFirst())).stream()
            .map(
                line -> {
                  try {
                    return jsonMapper.readTree(line);
                  } catch (JsonProcessingException e) {
                    Assert.fail("encountered invalid json from RPC response");
                    throw new RuntimeException(e);
                  }
                })
            .toList();

    assertThat(json).anyMatch(node -> node.has("stack"));
    assertThat(json).noneMatch(node -> node.has("storage"));
  }

  @Test
  public void disableMemory() throws IOException {
    final Map<String, Boolean> map = Map.of("disableMemory", true);
    final Hash blockHash =
        Hash.fromHexString("0x10aaf14a53caf27552325374429d3558398a36d3682ede6603c2c6511896e9f9");
    final Object[] params = new Object[] {blockHash, map};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", RPC_ENDPOINT, params));

    final JsonRpcResponse response = method.response(request);
    assertThat(response.getType()).isEqualTo(RpcResponseType.SUCCESS);
    List<?> files = (List<?>) ((JsonRpcSuccessResponse) response).getResult();

    ObjectMapper jsonMapper = new ObjectMapper();
    List<JsonNode> json =
        Files.readAllLines(Path.of((String) files.getFirst())).stream()
            .map(
                line -> {
                  try {
                    return jsonMapper.readTree(line);
                  } catch (JsonProcessingException e) {
                    Assert.fail("encountered invalid json from RPC response");
                    throw new RuntimeException(e);
                  }
                })
            .toList();

    assertThat(json).noneMatch(node -> node.has("memory"));
  }

  @Test
  public void disableStack() throws IOException {
    final Map<String, Boolean> map = Map.of("disableStack", true);
    final Hash blockHash =
        Hash.fromHexString("0x10aaf14a53caf27552325374429d3558398a36d3682ede6603c2c6511896e9f9");
    final Object[] params = new Object[] {blockHash, map};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", RPC_ENDPOINT, params));

    final JsonRpcResponse response = method.response(request);
    assertThat(response.getType()).isEqualTo(RpcResponseType.SUCCESS);
    List<?> files = (List<?>) ((JsonRpcSuccessResponse) response).getResult();

    ObjectMapper jsonMapper = new ObjectMapper();
    List<JsonNode> json =
        Files.readAllLines(Path.of((String) files.getFirst())).stream()
            .map(
                line -> {
                  try {
                    return jsonMapper.readTree(line);
                  } catch (JsonProcessingException e) {
                    Assert.fail("encountered invalid json from RPC response");
                    throw new RuntimeException(e);
                  }
                })
            .toList();

    assertThat(json).noneMatch(node -> node.has("stack"));
  }

  @Test
  public void enableStorage() throws IOException {
    final Map<String, Boolean> map = Map.of("disableStorage", false);
    final Hash blockHash =
        Hash.fromHexString("0x0362d0ee919714b702cb31d2f4fe6b5c834f36cc19558acb81a4832f86738e39");
    final Object[] params = new Object[] {blockHash, map};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", RPC_ENDPOINT, params));

    final JsonRpcResponse response = method.response(request);
    assertThat(response.getType()).isEqualTo(RpcResponseType.SUCCESS);
    List<?> files = (List<?>) ((JsonRpcSuccessResponse) response).getResult();

    ObjectMapper jsonMapper = new ObjectMapper();
    List<JsonNode> json =
        Files.readAllLines(Path.of((String) files.getFirst())).stream()
            .map(
                line -> {
                  try {
                    return jsonMapper.readTree(line);
                  } catch (JsonProcessingException e) {
                    Assert.fail("encountered invalid json from RPC response");
                    throw new RuntimeException(e);
                  }
                })
            .toList();

    assertThat(json).anyMatch(node -> node.has("storage"));
  }
}
