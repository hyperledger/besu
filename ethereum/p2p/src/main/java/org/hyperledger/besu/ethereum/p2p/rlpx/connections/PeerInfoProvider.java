package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import org.apache.tuweni.bytes.Bytes;

import java.util.stream.Stream;

public interface PeerInfoProvider {

  int getConnectionCount();

  int getMaxPeers();

  Stream<PeerConnection> streamActiveConnections();

  boolean canAcceptMoreConnections();

  boolean canExceedConnectionLimits(Bytes peerId);
}
