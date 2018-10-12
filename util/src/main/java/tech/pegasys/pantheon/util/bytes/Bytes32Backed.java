package tech.pegasys.pantheon.util.bytes;

/** Base interface for a value whose content is stored with exactly 32 bytes. */
public interface Bytes32Backed extends BytesBacked {
  @Override
  Bytes32 getBytes();
}
