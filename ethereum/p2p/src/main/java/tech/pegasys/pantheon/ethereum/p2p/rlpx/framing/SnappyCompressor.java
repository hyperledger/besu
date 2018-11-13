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

import static com.google.common.base.Preconditions.checkNotNull;

import org.iq80.snappy.Snappy;

/**
 * A strategy for compressing and decompressing data with the Snappy algorithm.
 *
 * @see <a href="https://google.github.io/snappy/">Snappy algorithm</a>
 */
public class SnappyCompressor implements Compressor {

  @Override
  public byte[] compress(final byte[] uncompressed) {
    checkNotNull(uncompressed, "input data must not be null");
    return Snappy.compress(uncompressed);
  }

  @Override
  public byte[] decompress(final byte[] compressed) {
    checkNotNull(compressed, "input data must not be null");
    return Snappy.uncompress(compressed, 0, compressed.length);
  }

  @Override
  public int uncompressedLength(final byte[] compressed) {
    checkNotNull(compressed, "input data must not be null");
    return Snappy.getUncompressedLength(compressed, 0);
  }
}
