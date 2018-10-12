package tech.pegasys.pantheon.ethereum.p2p.peers;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public interface PeerId {
  /**
   * The ID of the peer, equivalent to its public key. In public Ethereum, the public key is derived
   * from the signatures the peer attaches to certain messages.
   *
   * @return The peer's ID.
   */
  BytesValue getId();

  /**
   * The Keccak-256 hash value of the peer's ID. The value may be memoized to avoid recomputation
   * overhead.
   *
   * @return The Keccak-256 hash of the peer's ID.
   */
  Bytes32 keccak256();
}
