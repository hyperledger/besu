/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.p2p.rlpx.framing;

import static io.netty.buffer.ByteBufUtil.hexDump;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.bouncycastle.pqc.math.linearalgebra.ByteUtils.xor;

import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.HandshakeSecrets;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.Handshaker;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Arrays;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * This component is responsible for reading and composing RLPx protocol frames, conformant to the
 * schemes defined in the Ethereum protocols.
 *
 * <p>These frames are encrypted and authenticated using the secrets generated during the
 * cryptographic handshake ({@link Handshaker}.
 *
 * <p>This component is well-versed in TCP streaming complexities: it is capable of processing
 * fragmented frames, as well as streams of multiple messages within the same incoming buffer, as
 * long as the order of incoming bytes matches the underlying TCP sequence.
 *
 * @see <a href="https://github.com/ethereum/devp2p/blob/master/rlpx.md#framing">RLPx framing</a>
 */
public class Framer {
  private static final int LENGTH_HEADER_DATA = 16;
  private static final int LENGTH_MAC = 16;
  private static final int LENGTH_FULL_HEADER = LENGTH_HEADER_DATA + LENGTH_MAC;
  private static final int LENGTH_FRAME_SIZE = 3;
  private static final int LENGTH_MESSAGE_ID = 1;
  private static final int LENGTH_MAX_MESSAGE_FRAME = 0xFFFFFF;

  private static final byte[] IV = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  private static final byte[] PROTOCOL_HEADER =
      RLP.encode(
              out -> {
                out.startList();
                out.writeNull();
                out.writeNull();
                out.endList();
              })
          .extractArray();

  private final HandshakeSecrets secrets;
  private static final SnappyCompressor compressor = new SnappyCompressor();
  private final StreamCipher encryptor;
  private final StreamCipher decryptor;
  private final BlockCipher macEncryptor;
  private boolean headerProcessed;
  private int frameSize;
  private boolean compressionEnabled = false;

  /**
   * Creates a new framer out of the handshake secrets derived during the cryptographic handshake.
   *
   * @param secrets The handshake secrets.
   */
  public Framer(final HandshakeSecrets secrets) {
    this.secrets = secrets;

    final KeyParameter aesKey = new KeyParameter(secrets.getAesSecret());
    final KeyParameter macKey = new KeyParameter(secrets.getMacSecret());

    encryptor = new SICBlockCipher(new AESEngine());
    encryptor.init(true, new ParametersWithIV(aesKey, IV));

    decryptor = new SICBlockCipher(new AESEngine());
    decryptor.init(false, new ParametersWithIV(aesKey, IV));

    macEncryptor = new AESEngine();
    macEncryptor.init(true, macKey);
  }

  public void enableCompression() {
    this.compressionEnabled = true;
  }

  public void disableCompression() {
    this.compressionEnabled = false;
  }

  /**
   * Deframes a full message from the byte buffer, if possible.
   *
   * <p>If the byte buffer contains insufficient bytes to extract a full message, this method
   * returns <code>null</code>.
   *
   * <p>If the buffer contains at least a header, it offloads it and processes it, setting an
   * internal expectation to subsequently receive as many bytes for the frame as the header
   * specified. In this case, this method also returns <code>null</code> to inform the caller that
   * it requires more bytes before it can produce an output.
   *
   * <p>This method can be called repetitively whenever new bytes appear in the buffer. It is worthy
   * to note that the byte buffer is not consumed unless the next expected amount of bytes appears.
   *
   * <p>If there is more than one message in the byte buffer, only the first one is returned,
   * consuming it from the byte buffer. The caller should call this method again with the same byte
   * buffer to continue extracting more messages, if possible.
   *
   * <p>When this method throws an exception, it is recommended that the caller scraps away the RLPx
   * connection, as the digests and stream ciphers could have become corrupted.
   *
   * @param buf The buffer containing no messages, partial messages or multiple messages.
   * @return The first fully extracted message from this buffer, or <code>null</code> if no message
   *     could be extracted yet.
   * @throws FramingException Thrown when a decryption or internal error occurs.
   */
  public synchronized MessageData deframe(final ByteBuf buf) throws FramingException {
    if (buf == null || !buf.isReadable()) {
      return null;
    }

    if (!headerProcessed) {
      // We don't have enough bytes to read the header.
      if (buf.readableBytes() < LENGTH_FULL_HEADER) {
        return null;
      }
      frameSize = processHeader(buf.readSlice(LENGTH_FULL_HEADER));
      headerProcessed = true;
      buf.discardReadBytes();
    }

    final int size = frameSize + padding16(frameSize) + LENGTH_MAC;
    if (buf.readableBytes() < size) {
      return null;
    }

    final MessageData msg = processFrame(buf.readSlice(size), frameSize);
    buf.discardReadBytes();
    headerProcessed = false;
    return msg;
  }

  /**
   * Parses, decrypts and performs MAC verification on a packet header.
   *
   * <p>This method expects a buffer containing the exact number of bytes a well-formed header
   * consists of (32 bytes at this time). Returns the frame size as extracted from the header.
   *
   * @param encryptedHeader The header as seen on the wire.
   * @throws FramingException If header parsing or decryption failed.
   * @return The frame size as extracted from the header.
   */
  private int processHeader(final ByteBuf encryptedHeader) throws FramingException {
    if (encryptedHeader.readableBytes() != LENGTH_FULL_HEADER) {
      throw error(
          "Expected %s bytes in header, got %s",
          LENGTH_FULL_HEADER, encryptedHeader.readableBytes());
    }

    // Decrypt the header.
    final byte[] hCipher = new byte[LENGTH_HEADER_DATA];
    final byte[] hMac = new byte[LENGTH_MAC];
    encryptedHeader.readBytes(hCipher).readBytes(hMac);

    // Header MAC validation.
    byte[] expectedMac = new byte[16];
    macEncryptor.processBlock(secrets.getIngressMac(), 0, expectedMac, 0);
    expectedMac = secrets.updateIngress(xor(expectedMac, hCipher)).getIngressMac();
    expectedMac = Arrays.copyOf(expectedMac, LENGTH_MAC);

    validateMac(hMac, expectedMac);

    // Perform the header decryption.
    decryptor.processBytes(hCipher, 0, hCipher.length, hCipher, 0);
    final ByteBuf h = wrappedBuffer(hCipher);

    // Read the frame length.
    final byte[] length = new byte[3];
    h.readBytes(length);
    int frameSize = length[0] & 0xff;
    frameSize = (frameSize << 8) + (length[1] & 0xff);
    frameSize = (frameSize << 8) + (length[2] & 0xff);

    // Discard the header data (RLP): being set to fixed value 0xc28080 (list of two null
    // elements) by other clients.
    final int headerDataLength = RLP.calculateSize(BytesValue.wrapBuffer(h.nioBuffer()));
    if (h.readableBytes() < headerDataLength) {
      throw error(
          "Expected at least %d readable bytes while processing header, remaining: %s",
          headerDataLength, h.readableBytes());
    }
    h.skipBytes(headerDataLength);

    // Discard padding in header (= zero-fill to 16-byte boundary).
    h.skipBytes(padding16(LENGTH_FRAME_SIZE + headerDataLength));

    if (h.readableBytes() != 0) {
      throw error(
          "Expected no more readable bytes while processing header, remaining: %s",
          h.readableBytes());
    }

    h.discardReadBytes();
    return frameSize;
  }

  /**
   * Parses, decrypts and performs MAC verification on a frame.
   *
   * <p>This method expects a well-formed frame, sized according to the length indicated in this
   * packet's header.
   *
   * @param f The buffer containing
   * @param frameSize The expected
   */
  private MessageData processFrame(final ByteBuf f, final int frameSize) {
    final int pad = padding16(frameSize);
    final int expectedSize = frameSize + pad + LENGTH_MAC;
    if (f.readableBytes() != expectedSize) {
      throw error("Expected %s bytes in header, got %s", expectedSize, f.readableBytes());
    }

    final byte[] frameData = new byte[frameSize + pad];
    final byte[] fMac = new byte[LENGTH_MAC];
    f.readBytes(frameData).readBytes(fMac);

    // Validate the frame's MAC.
    final byte[] fMacSeed = secrets.updateIngress(frameData).getIngressMac();
    final byte[] fMacSeedEnc = new byte[16];
    macEncryptor.processBlock(fMacSeed, 0, fMacSeedEnc, 0);
    byte[] expectedMac = secrets.updateIngress(xor(fMacSeedEnc, fMacSeed)).getIngressMac();
    expectedMac = Arrays.copyOf(expectedMac, LENGTH_MAC);

    validateMac(fMac, expectedMac);

    // Decrypt frame data.
    decryptor.processBytes(frameData, 0, frameData.length, frameData, 0);

    // Read the id.
    final BytesValue idbv = RLP.decodeOne(BytesValue.of(frameData[0]));
    final int id = idbv.isZero() || idbv.size() == 0 ? 0 : idbv.get(0);

    // Write message data to ByteBuf, decompressing as necessary
    final BytesValue data;
    if (compressionEnabled) {
      final byte[] compressedMessageData = Arrays.copyOfRange(frameData, 1, frameData.length - pad);
      final int uncompressedLength = compressor.uncompressedLength(compressedMessageData);
      if (uncompressedLength >= LENGTH_MAX_MESSAGE_FRAME) {
        throw error("Message size %s in excess of maximum length.", uncompressedLength);
      }
      final byte[] decompressedMessageData = compressor.decompress(compressedMessageData);
      data = BytesValue.wrap(decompressedMessageData);
    } else {
      // Move data to a ByteBuf
      final int messageLength = frameSize - LENGTH_MESSAGE_ID;
      data = BytesValue.wrap(frameData, 1, messageLength);
    }

    return new RawMessage(id, data);
  }

  private void validateMac(final byte[] candidateMac, final byte[] expectedMac) {
    if (!Arrays.equals(expectedMac, candidateMac)) {
      throw error(
          "Frame MAC did not match expected MAC; expected: %s, received: %s",
          hexDump(expectedMac), hexDump(candidateMac));
    }
  }

  /**
   * Frames a message for sending to an RLPx peer, encrypting it and calculating the appropriate
   * MACs.
   *
   * @param message The message to frame.
   * @param output The {@link ByteBuf} to write framed data to.
   */
  public synchronized void frame(final MessageData message, final ByteBuf output) {
    Preconditions.checkArgument(
        message.getSize() < LENGTH_MAX_MESSAGE_FRAME, "Message size in excess of maximum length.");
    // Compress message
    if (compressionEnabled) {
      // Extract data from message
      // Compress data
      final byte[] compressed = compressor.compress(message.getData().getArrayUnsafe());
      // Construct new, compressed message
      frameMessage(new RawMessage(message.getCode(), BytesValue.wrap(compressed)), output);
    } else {
      frameMessage(message, output);
    }
  }

  @VisibleForTesting
  void frameMessage(final MessageData message, final ByteBuf buf) {
    final int frameSize = message.getSize() + LENGTH_MESSAGE_ID;
    final int pad = padding16(frameSize);

    final byte id = (byte) message.getCode();

    // Generate the header data.
    final byte[] h = new byte[LENGTH_HEADER_DATA];
    h[0] = (byte) ((frameSize >> 16) & 0xff);
    h[1] = (byte) ((frameSize >> 8) & 0xff);
    h[2] = (byte) (frameSize & 0xff);
    System.arraycopy(PROTOCOL_HEADER, 0, h, LENGTH_FRAME_SIZE, PROTOCOL_HEADER.length);
    Arrays.fill(h, LENGTH_FRAME_SIZE + PROTOCOL_HEADER.length, h.length - 1, (byte) 0x00);
    encryptor.processBytes(h, 0, LENGTH_HEADER_DATA, h, 0);

    // Generate the header MAC.
    byte[] hMac = Arrays.copyOf(secrets.getEgressMac(), LENGTH_MAC);
    macEncryptor.processBlock(hMac, 0, hMac, 0);
    hMac = secrets.updateEgress(xor(h, hMac)).getEgressMac();
    hMac = Arrays.copyOf(hMac, LENGTH_MAC);
    buf.writeBytes(h).writeBytes(hMac);

    // Encrypt payload.
    final byte[] f = new byte[frameSize + pad];

    final BytesValue bv = id == 0 ? RLP.NULL : RLP.encodeOne(BytesValue.of(id));
    assert bv.size() == 1;
    f[0] = bv.get(0);

    // Zero-padded to 16-byte boundary.
    message.getData().copyTo(f, 0, 1);
    encryptor.processBytes(f, 0, f.length, f, 0);

    // Calculate the frame MAC.
    final byte[] fMacSeed = Arrays.copyOf(secrets.updateEgress(f).getEgressMac(), LENGTH_MAC);
    byte[] fMac = new byte[16];
    macEncryptor.processBlock(fMacSeed, 0, fMac, 0);
    fMac = Arrays.copyOf(secrets.updateEgress(xor(fMac, fMacSeed)).getEgressMac(), LENGTH_MAC);

    buf.writeBytes(f).writeBytes(fMac);
  }

  private static int padding16(final int size) {
    final int pad = size % 16;
    return pad == 0 ? 0 : 16 - pad;
  }

  private static FramingException error(final String s, final Object... params) {
    return new FramingException(String.format(s, params));
  }
}
