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
package tech.pegasys.pantheon.ethereum.p2p.rlpx.framing;

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
