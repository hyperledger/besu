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
package tech.pegasys.pantheon.ethereum.eth.manager;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.EthereumWireProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.messages.GetNodeDataMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.NodeDataMessage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class EthServerTest {

  private static final BytesValue VALUE1 = BytesValue.of(1);
  private static final BytesValue VALUE2 = BytesValue.of(2);
  private static final BytesValue VALUE3 = BytesValue.of(3);
  private static final Hash HASH1 = Hash.hash(VALUE1);
  private static final Hash HASH2 = Hash.hash(VALUE2);
  private static final Hash HASH3 = Hash.hash(VALUE3);
  private final Blockchain blockchain = mock(Blockchain.class);
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final EthPeer ethPeer = mock(EthPeer.class);
  private final EthMessages ethMessages = new EthMessages();

  @Before
  public void setUp() {
    new EthServer(
        blockchain,
        worldStateArchive,
        ethMessages,
        new EthereumWireProtocolConfiguration(2, 2, 2, 2));
  }

  @Test
  public void shouldRespondToNodeDataRequests() throws Exception {
    when(worldStateArchive.getNodeData(HASH1)).thenReturn(Optional.of(VALUE1));
    when(worldStateArchive.getNodeData(HASH2)).thenReturn(Optional.of(VALUE2));
    ethMessages.dispatch(new EthMessage(ethPeer, GetNodeDataMessage.create(asList(HASH1, HASH2))));

    verify(ethPeer).send(NodeDataMessage.create(asList(VALUE1, VALUE2)));
  }

  @Test
  public void shouldHandleDataBeingUnavailableWhenRespondingToNodeDataRequests() throws Exception {
    when(worldStateArchive.getNodeData(HASH1)).thenReturn(Optional.of(VALUE1));
    when(worldStateArchive.getNodeData(HASH2)).thenReturn(Optional.empty());
    ethMessages.dispatch(new EthMessage(ethPeer, GetNodeDataMessage.create(asList(HASH1, HASH2))));

    verify(ethPeer).send(NodeDataMessage.create(singletonList(VALUE1)));
  }

  @Test
  public void shouldLimitNumberOfResponsesToNodeDataRequests() throws Exception {
    when(worldStateArchive.getNodeData(HASH1)).thenReturn(Optional.of(VALUE1));
    when(worldStateArchive.getNodeData(HASH2)).thenReturn(Optional.of(VALUE2));
    ethMessages.dispatch(
        new EthMessage(ethPeer, GetNodeDataMessage.create(asList(HASH1, HASH2, HASH3))));

    verify(ethPeer).send(NodeDataMessage.create(asList(VALUE1, VALUE2)));
  }

  @Test
  public void shouldLimitTheNumberOfNodeDataResponsesLookedUpNotTheNumberReturned()
      throws Exception {
    when(worldStateArchive.getNodeData(HASH1)).thenReturn(Optional.of(VALUE1));
    when(worldStateArchive.getNodeData(HASH2)).thenReturn(Optional.empty());
    when(worldStateArchive.getNodeData(HASH3)).thenReturn(Optional.of(VALUE3));
    ethMessages.dispatch(
        new EthMessage(ethPeer, GetNodeDataMessage.create(asList(HASH1, HASH2, HASH3))));

    verify(ethPeer).send(NodeDataMessage.create(singletonList(VALUE1)));
  }
}
