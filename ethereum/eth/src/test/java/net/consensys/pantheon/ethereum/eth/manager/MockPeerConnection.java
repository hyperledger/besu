package net.consensys.pantheon.ethereum.eth.manager;

import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.p2p.wire.PeerInfo;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import net.consensys.pantheon.util.bytes.Bytes32;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Strings;

class MockPeerConnection implements PeerConnection {

  private static final PeerSendHandler NOOP_ON_SEND = (cap, msg, conn) -> {};
  private static final AtomicLong ID_GENERATOR = new AtomicLong();
  private final PeerSendHandler onSend;
  private final Set<Capability> caps;
  private volatile boolean disconnected = false;
  private final Bytes32 nodeId;

  public MockPeerConnection(final Set<Capability> caps, final PeerSendHandler onSend) {
    this.caps = caps;
    this.onSend = onSend;
    this.nodeId = generateUsefulNodeId();
  }

  private Bytes32 generateUsefulNodeId() {
    // EthPeer only shows the first 20 characters of the node ID so add some padding.
    return Bytes32.fromHexStringLenient(
        "0x" + ID_GENERATOR.incrementAndGet() + Strings.repeat("0", 46));
  }

  public MockPeerConnection(final Set<Capability> caps) {
    this(caps, NOOP_ON_SEND);
  }

  @Override
  public void send(final Capability capability, final MessageData message) throws PeerNotConnected {
    if (disconnected) {
      message.release();
      throw new PeerNotConnected("MockPeerConnection disconnected");
    }
    onSend.exec(capability, message, this);
  }

  @Override
  public Set<Capability> getAgreedCapabilities() {
    return caps;
  }

  @Override
  public PeerInfo getPeer() {
    return new PeerInfo(5, "Mock", new ArrayList<>(caps), 0, nodeId);
  }

  @Override
  public void terminateConnection(final DisconnectReason reason, final boolean peerInitiated) {
    disconnect(reason);
  }

  @Override
  public void disconnect(final DisconnectReason reason) {
    disconnected = true;
  }

  @Override
  public SocketAddress getLocalAddress() {
    throw new UnsupportedOperationException();
  }

  @Override
  public SocketAddress getRemoteAddress() {
    throw new UnsupportedOperationException();
  }

  public boolean isDisconnected() {
    return disconnected;
  }

  @FunctionalInterface
  public interface PeerSendHandler {
    void exec(Capability cap, MessageData msg, PeerConnection connection);
  }
}
