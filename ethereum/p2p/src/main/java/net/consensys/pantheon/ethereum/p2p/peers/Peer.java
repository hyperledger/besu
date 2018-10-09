package net.consensys.pantheon.ethereum.p2p.peers;

import net.consensys.pantheon.crypto.SecureRandomProvider;
import net.consensys.pantheon.ethereum.rlp.RLPOutput;
import net.consensys.pantheon.util.bytes.BytesValue;

public interface Peer extends PeerId {

  /**
   * A struct-like immutable object encapsulating the peer's network coordinates, namely their
   * hostname (as an IP address in the current implementation), UDP port and optional TCP port for
   * RLPx communications.
   *
   * @return An object encapsulating the peer's network coordinates.
   */
  Endpoint getEndpoint();

  /**
   * Generates a random peer ID in a secure manner.
   *
   * @return The generated peer ID.
   */
  static BytesValue randomId() {
    final byte[] id = new byte[64];
    SecureRandomProvider.publicSecureRandom().nextBytes(id);
    return BytesValue.wrap(id);
  }

  /**
   * Encodes this peer to its RLP representation.
   *
   * @param out The RLP output stream to which to write.
   */
  default void writeTo(final RLPOutput out) {
    out.startList();
    getEndpoint().encodeInline(out);
    out.writeBytesValue(getId());
    out.endList();
  }
}
