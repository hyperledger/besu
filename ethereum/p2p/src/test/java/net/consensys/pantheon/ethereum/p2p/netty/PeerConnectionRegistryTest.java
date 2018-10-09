package net.consensys.pantheon.ethereum.p2p.netty;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.p2p.api.PeerConnection;
import net.consensys.pantheon.ethereum.p2p.wire.PeerInfo;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import net.consensys.pantheon.util.bytes.BytesValue;

import org.junit.Before;
import org.junit.Test;

public class PeerConnectionRegistryTest {

  private static final BytesValue PEER1_ID = BytesValue.wrap(new byte[] {1});
  private static final BytesValue PEER2_ID = BytesValue.wrap(new byte[] {2});
  private final PeerConnection connection1 = mock(PeerConnection.class);
  private final PeerConnection connection2 = mock(PeerConnection.class);

  private final PeerConnectionRegistry registry = new PeerConnectionRegistry();

  @Before
  public void setUp() {
    when(connection1.getPeer()).thenReturn(new PeerInfo(5, "client1", emptyList(), 10, PEER1_ID));
    when(connection2.getPeer()).thenReturn(new PeerInfo(5, "client2", emptyList(), 10, PEER2_ID));
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
