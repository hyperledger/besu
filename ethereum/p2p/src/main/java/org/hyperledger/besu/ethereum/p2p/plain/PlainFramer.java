/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.p2p.plain;

import static io.netty.buffer.Unpooled.wrappedBuffer;

import org.hyperledger.besu.ethereum.p2p.rlpx.framing.Framer;
import org.hyperledger.besu.ethereum.p2p.rlpx.framing.FramingException;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.Arrays;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.FormatMethod;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class PlainFramer extends Framer {
  private static final Logger LOG = LogManager.getLogger();

  protected static final int LENGTH_HEADER_DATA = 16;
  //  protected static final int LENGTH_MAC = 16;
  protected static final int LENGTH_FULL_HEADER = LENGTH_HEADER_DATA; // + LENGTH_MAC;
  protected static final int LENGTH_FRAME_SIZE = 3;
  protected static final int LENGTH_MESSAGE_ID = 1;
  //  protected static final int LENGTH_MAX_MESSAGE_FRAME = 0xFFFFFF;

  protected static final byte[] IV = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  protected static final byte[] PROTOCOL_HEADER =
      RLP.encode(
              out -> {
                out.startList();
                out.writeNull();
                out.writeNull();
                out.endList();
              })
          .toArray();

  private boolean headerProcessed;
  private int frameSize;

  public PlainFramer() {
    LOG.trace("Initialising PlainFramer");
  }

  @Override
  public synchronized MessageData deframe(final ByteBuf buf) throws FramingException {
    LOG.trace("Deframing Message");
    //    if (buf == null || !buf.isReadable()) {
    //      return null;
    //    }
    //    PlainMessage message = MessageHandler.parseMessage(buf);
    //    checkState(
    //        MessageType.DATA.equals(message.getMessageType()), "unexpected message: needs to be
    // data");
    //    return new RawMessage(message.getCode(), message.getData());
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

    final int size = frameSize + padding16(frameSize); // + LENGTH_MAC;
    if (buf.readableBytes() < size) {
      return null;
    }

    final MessageData msg = processFrame(buf.readSlice(size), frameSize);
    buf.discardReadBytes();
    headerProcessed = false;
    return msg;
  }

  @FormatMethod
  private static FramingException error(final String s, final Object... params) {
    return new FramingException(String.format(s, params));
  }

  /**
   * Parses, decrypts and performs MAC verification on a packet header.
   *
   * <p>This method expects a buffer containing the exact number of bytes a well-formed header
   * consists of (32 bytes at this time). Returns the frame size as extracted from the header.
   *
   * @param encryptedHeader The header as seen on the wire.
   * @return The frame size as extracted from the header.
   * @throws FramingException If header parsing or decryption failed.
   */
  private int processHeader(final ByteBuf encryptedHeader) throws FramingException {
    if (encryptedHeader.readableBytes() != LENGTH_FULL_HEADER) {
      throw error(
          "Expected %s bytes in header, got %s",
          LENGTH_FULL_HEADER, encryptedHeader.readableBytes());
    }

    // Decrypt the header.
    final byte[] hCipher = new byte[LENGTH_HEADER_DATA];
    //    final byte[] hMac = new byte[LENGTH_MAC];
    encryptedHeader.readBytes(hCipher); // .readBytes(hMac);

    // Header MAC validation.
    //    byte[] expectedMac = new byte[16];
    //    macEncryptor.processBlock(secrets.getIngressMac(), 0, expectedMac, 0);
    //    expectedMac = secrets.updateIngress(xor(expectedMac, hCipher)).getIngressMac();
    //    expectedMac = Arrays.copyOf(expectedMac, LENGTH_MAC);

    //    validateMac(hMac, expectedMac);

    // Perform the header decryption.
    //    decryptor.processBytes(hCipher, 0, hCipher.length, hCipher, 0);
    final ByteBuf h = wrappedBuffer(hCipher);

    // Read the frame length.
    final byte[] length = new byte[3];
    h.readBytes(length);
    int frameSize = length[0] & 0xff;
    frameSize = (frameSize << 8) + (length[1] & 0xff);
    frameSize = (frameSize << 8) + (length[2] & 0xff);

    // Discard the header data (RLP): being set to fixed value 0xc28080 (list of two null
    // elements) by other clients.
    final int headerDataLength = RLP.calculateSize(Bytes.wrapByteBuffer(h.nioBuffer()));
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
    final int expectedSize = frameSize + pad; // + LENGTH_MAC;
    if (f.readableBytes() != expectedSize) {
      throw error("Expected %s bytes in header, got %s", expectedSize, f.readableBytes());
    }

    final byte[] frameData = new byte[frameSize + pad];
    //    final byte[] fMac = new byte[LENGTH_MAC];
    f.readBytes(frameData); // .readBytes(fMac);

    // Validate the frame's MAC.
    //    final byte[] fMacSeed = secrets.updateIngress(frameData).getIngressMac();
    //    final byte[] fMacSeedEnc = new byte[16];
    //    macEncryptor.processBlock(fMacSeed, 0, fMacSeedEnc, 0);
    //    byte[] expectedMac = secrets.updateIngress(xor(fMacSeedEnc, fMacSeed)).getIngressMac();
    //    expectedMac = Arrays.copyOf(expectedMac, LENGTH_MAC);
    //
    //    validateMac(fMac, expectedMac);

    // Decrypt frame data.
    //    decryptor.processBytes(frameData, 0, frameData.length, frameData, 0);

    // Read the id.
    final Bytes idbv = RLP.decodeOne(Bytes.of(frameData[0]));
    final int id = idbv.isZero() || idbv.size() == 0 ? 0 : idbv.get(0);

    // Write message data to ByteBuf, decompressing as necessary
    final Bytes data;
    //    if (compressionEnabled) {
    //      final byte[] compressedMessageData = Arrays.copyOfRange(frameData, 1, frameData.length -
    // pad);
    //      final int uncompressedLength = compressor.uncompressedLength(compressedMessageData);
    //      if (uncompressedLength >= LENGTH_MAX_MESSAGE_FRAME) {
    //        throw error("Message size %s in excess of maximum length.", uncompressedLength);
    //      }
    //      Bytes _data;
    //      try {
    //        final byte[] decompressedMessageData = compressor.decompress(compressedMessageData);
    //        _data = Bytes.wrap(decompressedMessageData);
    //        compressionSuccessful = true;
    //      } catch (final FramingException fe) {
    //        if (compressionSuccessful) {
    //          throw fe;
    //        } else {
    //          // OpenEthereum/Parity does not implement EIP-706
    //          // If failing on the first packet downgrade to uncompressed
    //          compressionEnabled = false;
    //          LOG.debug("Snappy decompression failed: downgrading to uncompressed");
    //          final int messageLength = frameSize - LENGTH_MESSAGE_ID;
    //          _data = Bytes.wrap(frameData, 1, messageLength);
    //        }
    //      }
    //      data = _data;
    //    } else {
    // Move data to a ByteBuf
    final int messageLength = frameSize - LENGTH_MESSAGE_ID;
    data = Bytes.wrap(frameData, 1, messageLength);
    //    }

    return new RawMessage(id, data);
  }

  @Override
  public synchronized void frame(final MessageData message, final ByteBuf output) {
    LOG.trace("Framing Message");
    frameMessage(message, output);
    //    output.writeBytes(
    //        MessageHandler.buildMessage(MessageType.DATA, message.getCode(), message.getData())
    //            .toArray());
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
    //    encryptor.processBytes(h, 0, LENGTH_HEADER_DATA, h, 0);

    // Generate the header MAC.
    //    byte[] hMac = Arrays.copyOf(secrets.getEgressMac(), LENGTH_MAC);
    //    macEncryptor.processBlock(hMac, 0, hMac, 0);
    //    hMac = secrets.updateEgress(xor(h, hMac)).getEgressMac();
    //    hMac = Arrays.copyOf(hMac, LENGTH_MAC);
    buf.writeBytes(h); // .writeBytes(hMac);

    // Encrypt payload.
    final MutableBytes f = MutableBytes.create(frameSize + pad);

    final Bytes bv = id == 0 ? RLP.NULL : RLP.encodeOne(Bytes.of(id));
    assert bv.size() == 1;
    f.set(0, bv.get(0));

    // Zero-padded to 16-byte boundary.
    message.getData().copyTo(f, 1);
    //    encryptor.processBytes(f.toArrayUnsafe(), 0, f.size(), f.toArrayUnsafe(), 0);

    // Calculate the frame MAC.
    //    final byte[] fMacSeed =
    //        Arrays.copyOf(secrets.updateEgress(f.toArrayUnsafe()).getEgressMac(), LENGTH_MAC);
    //    byte[] fMac = new byte[16];
    //    macEncryptor.processBlock(fMacSeed, 0, fMac, 0);
    //    fMac = Arrays.copyOf(secrets.updateEgress(xor(fMac, fMacSeed)).getEgressMac(),
    // LENGTH_MAC);

    buf.writeBytes(f.toArrayUnsafe()); // .writeBytes(fMac);
  }

  private static int padding16(final int size) {
    final int pad = size % 16;
    return pad == 0 ? 0 : 16 - pad;
  }
}
