package net.consensys.pantheon.ethereum.p2p.rlpx.framing;

/** A strategy for compressing and decompressing devp2p subprotocol messages. */
public interface Compressor {

  /**
   * Compresses the provided payload.
   *
   * @param decompressed The original payload.
   * @throws CompressionException Thrown if an error occurs during compression; expect to find the
   *     root cause inside.
   * @return The compressed payload.
   */
  byte[] compress(byte[] decompressed) throws CompressionException;

  /**
   * Decompresses the provided payload.
   *
   * @param compressed The compressed payload.
   * @throws CompressionException Thrown if an error occurs during decompression; expect to find the
   *     root cause inside.
   * @return The original payload.
   */
  byte[] decompress(byte[] compressed) throws CompressionException;

  /**
   * Return the length when uncompressed
   *
   * @param compressed The compressed payload.
   * @return The length of the payload when uncompressed.
   * @throws CompressionException Thrown if the size cannot be calculated from the available data;
   *     expect to find the root cause inside;
   */
  int uncompressedLength(byte[] compressed) throws CompressionException;
}
