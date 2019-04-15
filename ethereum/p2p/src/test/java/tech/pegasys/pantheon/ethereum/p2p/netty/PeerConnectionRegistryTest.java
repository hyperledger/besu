/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.p2p.netty;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Before;
import org.junit.Test;

public class PeerConnectionRegistryTest {

  private static final BytesValue PEER1_ID = BytesValue.wrap(new byte[] {1});
  private static final BytesValue PEER2_ID = BytesValue.wrap(new byte[] {2});
  private final PeerConnection connection1 = mock(PeerConnection.class);
  private final PeerConnection connection2 = mock(PeerConnection.class);

  private final PeerConnectionRegistry registry =
      new PeerConnectionRegistry(new NoOpMetricsSystem());

  @Before
  public void setUp() {
    when(connection1.getPeerInfo())
        .thenReturn(new PeerInfo(5, "client1", emptyList(), 10, PEER1_ID));
    when(connection2.getPeerInfo())
        .thenReturn(new PeerInfo(5, "client2", emptyList(), 10, PEER2_ID));
  }

  @Test
  public void shouldRegisterConnections() {
    registry.registerConnection(connection1);
    assertThat(registry.getPeerConnections()).containsOnly(connection1);
    assertThat(registry.size()).isEqualTo(1);

    registry.registerConnection(connection2);
    assertThat(registry.getPeerConnections()).containsOnly(connection1, connection2);
    assertThat(registry.size()).isEqualTo(2);
  }

  @Test
  public void shouldUnregisterConnections() {
    registry.registerConnection(connection1);
    registry.registerConnection(connection2);
    registry.onDisconnect(connection1, DisconnectReason.TCP_SUBSYSTEM_ERROR, false);
    assertThat(registry.getPeerConnections()).containsOnly(connection2);
    assertThat(registry.size()).isEqualTo(1);
  }

  @Test
  public void shouldReportWhenPeerIsNotConnected() {
    assertThat(registry.isAlreadyConnected(PEER1_ID)).isFalse();
  }

  @Test
  public void shouldReportWhenPeerIsConnected() {
    registry.registerConnection(connection1);
    assertThat(registry.isAlreadyConnected(PEER1_ID)).isTrue();
  }
}
