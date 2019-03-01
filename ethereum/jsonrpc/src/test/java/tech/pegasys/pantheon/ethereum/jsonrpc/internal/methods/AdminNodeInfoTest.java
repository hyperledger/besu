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
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.net.InetSocketAddress;
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

  private final PeerInfo localPeer =
      new PeerInfo(5, "0x0", Collections.emptyList(), 30303, BytesValue.EMPTY);
  private final InetSocketAddress discoverySocketAddress = new InetSocketAddress("1.2.3.4", 7890);
  private final ChainHead testChainHead = new ChainHead(Hash.EMPTY, UInt256.ONE);
  private final GenesisConfigOptions genesisConfigOptions =
      new StubGenesisConfigOptions().chainId(2019);

  @Before
  public void setup() {
    when(p2pNetwork.getLocalPeerInfo()).thenReturn(localPeer);
    when(p2pNetwork.getDiscoverySocketAddress()).thenReturn(discoverySocketAddress);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(Hash.EMPTY));
    when(blockchain.getChainHead()).thenReturn(testChainHead);

    method =
        new AdminNodeInfo(
            "testnet/1.0/this/that", 2018, genesisConfigOptions, p2pNetwork, blockchainQueries);
  }

  @Test
  public void shouldReturnCorrectResult() {
    final JsonRpcRequest request = adminNodeInfo();

    final JsonRpcSuccessResponse actual = (JsonRpcSuccessResponse) method.response(request);
    final Map<String, Object> expected = new HashMap<>();
    expected.put("enode", "enode://@1.2.3.4:30303");
    expected.put("id", "");
    expected.put("listenAddr", "1.2.3.4:30303");
    expected.put("name", "testnet/1.0/this/that");
    expected.put("ports", ImmutableMap.of("discovery", 30303, "listener", 30303));
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
                2018)));

    assertThat(actual.getResult()).isEqualTo(expected);
  }

  private JsonRpcRequest adminNodeInfo() {
    return new JsonRpcRequest("2.0", "admin_nodeInfo", new Object[] {});
  }
}
