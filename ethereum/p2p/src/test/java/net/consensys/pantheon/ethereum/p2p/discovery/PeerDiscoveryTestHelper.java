package net.consensys.pantheon.ethereum.p2p.discovery;

import net.consensys.pantheon.crypto.SECP256K1;
import net.consensys.pantheon.ethereum.p2p.peers.Endpoint;

import java.util.OptionalInt;
import java.util.stream.Stream;

public class PeerDiscoveryTestHelper {

  public static SECP256K1.KeyPair[] generateKeyPairs(final int count) {
    return Stream.generate(SECP256K1.KeyPair::generate)
        .limit(count)
        .toArray(SECP256K1.KeyPair[]::new);
  }

  public static DiscoveryPeer[] generatePeers(final SECP256K1.KeyPair... keypairs) {
    return Stream.of(keypairs)
        .map(kp -> kp.getPublicKey().getEncodedBytes())
        .map(bytes -> new DiscoveryPeer(bytes, new Endpoint("127.0.0.1", 1, OptionalInt.empty())))
        .toArray(DiscoveryPeer[]::new);
  }

  public static DiscoveryPeer[] generateDiscoveryPeers(final SECP256K1.KeyPair... keypairs) {
    return Stream.of(generatePeers(keypairs)).map(DiscoveryPeer::new).toArray(DiscoveryPeer[]::new);
  }
}
