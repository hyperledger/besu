package tech.pegasys.pantheon.ethereum.p2p.peers;

import tech.pegasys.pantheon.crypto.Hash;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Objects;

public class DefaultPeerId implements PeerId {
  protected final BytesValue id;
  private Bytes32 keccak256;

  public DefaultPeerId(final BytesValue id) {
    this.id = id;
  }

  @Override
  public BytesValue getId() {
    return id;
  }

  @Override
  public Bytes32 keccak256() {
    if (keccak256 == null) {
      keccak256 = Hash.keccak256(getId());
    }
    return keccak256;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || o.getClass().isAssignableFrom(this.getClass())) return false;
    final DefaultPeerId that = (DefaultPeerId) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
