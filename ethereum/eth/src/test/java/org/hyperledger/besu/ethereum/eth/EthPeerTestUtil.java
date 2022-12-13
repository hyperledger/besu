package org.hyperledger.besu.ethereum.eth;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;

public class EthPeerTestUtil {


    public static EnodeURLImpl.Builder enodeBuilder() {
        return EnodeURLImpl.builder().ipAddress("127.0.0.1").useDefaultPorts().nodeId(Peer.randomId());
    }

    public static Peer createPeer(final Bytes nodeId) {
        return DefaultPeer.fromEnodeURL(enodeBuilder().nodeId(nodeId).build());
    }
}
