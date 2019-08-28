/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.config.StubGenesisConfigOptions;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.ChainHead;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class AdminNodeInfoTest {

  @Mock private P2PNetwork p2pNetwork;
  @Mock private Blockchain blockchain;
  @Mock private BlockchainQueries blockchainQueries;

  private AdminNodeInfo method;

  private final BytesValue nodeId =
      BytesValue.fromHexString(
          "0x0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807");
  private final ChainHead testChainHead = new ChainHead(Hash.EMPTY, UInt256.ONE, 1L);
  private final GenesisConfigOptions genesisConfigOptions =
      new StubGenesisConfigOptions().chainId(BigInteger.valueOf(2019));
  private final DefaultPeer defaultPeer =
      DefaultPeer.fromEnodeURL(
          EnodeURL.builder()
              .nodeId(nodeId)
              .ipAddress("1.2.3.4")
              .discoveryPort(7890)
              .listeningPort(30303)
              .build());

  @Before
  public void setup() {
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(Hash.EMPTY));
    when(blockchain.getChainHead()).thenReturn(testChainHead);

    method =
        new AdminNodeInfo(
            "testnet/1.0/this/that",
            BigInteger.valueOf(2018),
            genesisConfigOptions,
            p2pNetwork,
            blockchainQueries);
  }

  @Test
  public void shouldReturnCorrectResult() {
    when(p2pNetwork.isP2pEnabled()).thenReturn(true);
    when(p2pNetwork.getLocalEnode()).thenReturn(Optional.of(defaultPeer.getEnodeURL()));
    final JsonRpcRequest request = adminNodeInfo();

    final Map<String, Object> expected = new HashMap<>();
    expected.put(
        "enode",
        "enode://0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807@1.2.3.4:30303?discport=7890");
    expected.put(
        "id",
        "0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807");
    expected.put("ip", "1.2.3.4");
    expected.put("listenAddr", "1.2.3.4:30303");
    expected.put("name", "testnet/1.0/this/that");
    expected.put("ports", ImmutableMap.of("discovery", 7890, "listener", 30303));
    expected.put(
        "protocols",
        ImmutableMap.of(
            "eth",
            ImmutableMap.of(
                "config",
                genesisConfigOptions.asMap(),
                "difficulty",
                1L,
                "genesis",
                Hash.EMPTY.toString(),
                "head",
                Hash.EMPTY.toString(),
                "network",
                BigInteger.valueOf(2018))));

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final JsonRpcSuccessResponse actual = (JsonRpcSuccessResponse) response;
    assertThat(actual.getResult()).isEqualTo(expected);
  }

  @Test
  public void handlesLocalEnodeWithListeningAndDiscoveryDisabled() {
    final EnodeURL localEnode =
        EnodeURL.builder()
            .nodeId(nodeId)
            .ipAddress("1.2.3.4")
            .discoveryAndListeningPorts(0)
            .build();

    when(p2pNetwork.isP2pEnabled()).thenReturn(true);
    when(p2pNetwork.getLocalEnode()).thenReturn(Optional.of(localEnode));
    final JsonRpcRequest request = adminNodeInfo();

    final Map<String, Object> expected = new HashMap<>();
    expected.put(
        "enode",
        "enode://0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807@1.2.3.4:0");
    expected.put(
        "id",
        "0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807");
    expected.put("ip", "1.2.3.4");
    expected.put("name", "testnet/1.0/this/that");
    expected.put("ports", Collections.emptyMap());
    expected.put(
        "protocols",
        ImmutableMap.of(
            "eth",
            ImmutableMap.of(
                "config",
                genesisConfigOptions.asMap(),
                "difficulty",
                1L,
                "genesis",
                Hash.EMPTY.toString(),
                "head",
                Hash.EMPTY.toString(),
                "network",
                BigInteger.valueOf(2018))));

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final JsonRpcSuccessResponse actual = (JsonRpcSuccessResponse) response;
    assertThat(actual.getResult()).isEqualTo(expected);
  }

  @Test
  public void handlesLocalEnodeWithListeningDisabled() {
    final EnodeURL localEnode =
        EnodeURL.builder()
            .nodeId(nodeId)
            .ipAddress("1.2.3.4")
            .discoveryAndListeningPorts(0)
            .discoveryPort(7890)
            .build();

    when(p2pNetwork.isP2pEnabled()).thenReturn(true);
    when(p2pNetwork.getLocalEnode()).thenReturn(Optional.of(localEnode));
    final JsonRpcRequest request = adminNodeInfo();

    final Map<String, Object> expected = new HashMap<>();
    expected.put(
        "enode",
        "enode://0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807@1.2.3.4:0?discport=7890");
    expected.put(
        "id",
        "0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807");
    expected.put("ip", "1.2.3.4");
    expected.put("name", "testnet/1.0/this/that");
    expected.put("ports", ImmutableMap.of("discovery", 7890));
    expected.put(
        "protocols",
        ImmutableMap.of(
            "eth",
            ImmutableMap.of(
                "config",
                genesisConfigOptions.asMap(),
                "difficulty",
                1L,
                "genesis",
                Hash.EMPTY.toString(),
                "head",
                Hash.EMPTY.toString(),
                "network",
                BigInteger.valueOf(2018))));

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final JsonRpcSuccessResponse actual = (JsonRpcSuccessResponse) response;
    assertThat(actual.getResult()).isEqualTo(expected);
  }

  @Test
  public void handlesLocalEnodeWithDiscoveryDisabled() {
    final EnodeURL localEnode =
        EnodeURL.builder()
            .nodeId(nodeId)
            .ipAddress("1.2.3.4")
            .discoveryAndListeningPorts(0)
            .listeningPort(7890)
            .build();

    when(p2pNetwork.isP2pEnabled()).thenReturn(true);
    when(p2pNetwork.getLocalEnode()).thenReturn(Optional.of(localEnode));
    final JsonRpcRequest request = adminNodeInfo();

    final Map<String, Object> expected = new HashMap<>();
    expected.put(
        "enode",
        "enode://0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807@1.2.3.4:7890?discport=0");
    expected.put(
        "id",
        "0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807");
    expected.put("ip", "1.2.3.4");
    expected.put("listenAddr", "1.2.3.4:7890");
    expected.put("name", "testnet/1.0/this/that");
    expected.put("ports", ImmutableMap.of("listener", 7890));
    expected.put(
        "protocols",
        ImmutableMap.of(
            "eth",
            ImmutableMap.of(
                "config",
                genesisConfigOptions.asMap(),
                "difficulty",
                1L,
                "genesis",
                Hash.EMPTY.toString(),
                "head",
                Hash.EMPTY.toString(),
                "network",
                BigInteger.valueOf(2018))));

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final JsonRpcSuccessResponse actual = (JsonRpcSuccessResponse) response;
    assertThat(actual.getResult()).isEqualTo(expected);
  }

  @Test
  public void returnsErrorWhenP2PDisabled() {
    when(p2pNetwork.isP2pEnabled()).thenReturn(false);
    final JsonRpcRequest request = adminNodeInfo();

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.P2P_DISABLED);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void returnsErrorWhenP2PNotReady() {
    when(p2pNetwork.isP2pEnabled()).thenReturn(true);
    when(p2pNetwork.getLocalEnode()).thenReturn(Optional.empty());
    final JsonRpcRequest request = adminNodeInfo();

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.P2P_NETWORK_NOT_RUNNING);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest adminNodeInfo() {
    return new JsonRpcRequest("2.0", "admin_nodeInfo", new Object[] {});
  }
}
