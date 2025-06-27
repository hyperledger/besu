/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.internal.CodeCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for persisting and loading the code cache to and from disk.
 *
 * <p>The code cache is stored in RLP format, with each entry containing the code hash, code bytes,
 * code size, and an optional jump destination bitmask.
 */
public class CodeCacheStorage {
  private static final Logger LOG = LoggerFactory.getLogger(CodeCacheStorage.class);

  /** Private constructor to prevent instantiation. */
  private CodeCacheStorage() {}

  /**
   * Writes the code cache to disk in RLP format.
   *
   * @param dataDir the directory where the code cache will be stored
   * @param codeCache the code cache to persist
   */
  public static void writeToDisk(final Path dataDir, final CodeCache codeCache) {
    LOG.info("Writing code cache to disk...");

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    Map<Hash, Code> entries = codeCache.asMap();

    out.startList();
    for (Map.Entry<Hash, Code> entry : entries.entrySet()) {
      Code code = entry.getValue();

      out.startList();
      out.writeBytes(entry.getKey()); // codeHash
      out.writeBytes(code.getBytes()); // code bytes
      out.writeIntScalar(code.getSize()); // code size

      long[] bitmask = code.getJumpDestBitMask();
      if (bitmask == null || bitmask.length == 0) {
        out.writeEmptyList();
      } else {
        out.startList();
        for (long l : bitmask) {
          out.writeLong(l);
        }
        out.endList();
      }

      out.endList();
    }
    out.endList();

    Bytes rlpEncoded = out.encoded();
    Bytes32 rlpHash = Hash.hash(rlpEncoded);

    Path outFile = dataDir.resolve("codecache-" + rlpHash.toUnprefixedHexString() + ".rlp");
    try {
      Files.write(outFile, rlpEncoded.toArrayUnsafe());
    } catch (IOException e) {
      LOG.warn("Failed to persist code cache to disk", e);
      return;
    }

    LOG.info("Successfully persisted code cache to {}", outFile.toAbsolutePath());
  }

  /**
   * Loads the code cache from disk.
   *
   * @param dataDir the directory where the code cache is stored
   * @param cache the code cache to populate with loaded entries
   */
  public static void loadFromDisk(final Path dataDir, final CodeCache cache) {
    LOG.info("Trying to load code cache from disk...");

    try (Stream<Path> files = Files.list(dataDir)) {
      List<Path> candidates =
          files
              .filter(
                  p ->
                      p.getFileName().toString().startsWith("codecache-")
                          && p.getFileName().toString().endsWith(".rlp"))
              .toList();

      if (candidates.isEmpty()) {
        LOG.info("No code cache file found in {}", dataDir.toAbsolutePath());
        return;
      }

      if (candidates.size() != 1) {
        LOG.warn("Expected one code cache file, found {}. Skipping restore.", candidates.size());
        return;
      }

      final Path file = candidates.getFirst();
      final Bytes data = Bytes.wrap(Files.readAllBytes(file));

      // Verify hash
      final Bytes32 actualHash = Hash.hash(data);
      final String expectedHex =
          file.getFileName().toString().replace("codecache-", "").replace(".rlp", "");

      if (!actualHash.toUnprefixedHexString().equals(expectedHex)) {
        LOG.warn(
            "Code cache file failed hash check: expected {}, found {}",
            expectedHex,
            actualHash.toUnprefixedHexString());
        return;
      }

      final Map<Hash, Code> codeCacheMap = cache.asMap();

      try {
        // Decode RLP
        final BytesValueRLPInput in = new BytesValueRLPInput(data, false);
        in.enterList();
        while (!in.isEndOfCurrentList()) {
          in.enterList();

          final Hash codeHash = Hash.wrap((Bytes32) in.readBytes());
          final Bytes byteCode = in.readBytes();
          final int size = in.readIntScalar();

          long[] jumpDestBitMask = null;
          if (in.nextIsList()) {
            in.enterList();
            List<Long> bits = new ArrayList<>();

            while (!in.isEndOfCurrentList()) {
              bits.add(in.readLong());
            }
            in.leaveList();
            jumpDestBitMask = bits.stream().mapToLong(Long::longValue).toArray();
          }

          if (jumpDestBitMask == null || jumpDestBitMask.length == 0) {
            jumpDestBitMask = null;
          }

          codeCacheMap.put(codeHash, new CodeV0(byteCode, codeHash, size, jumpDestBitMask));

          in.leaveList();
        }
        in.leaveList();

      } catch (final RLPException e) {
        LOG.warn("Failed to decode code cache from disk: {}", e.getMessage());
        return;
      }

      cache.putAll(codeCacheMap);
      LOG.info("Successfully loaded {} code cache entries from disk", codeCacheMap.size());
    } catch (final IOException e) {
      LOG.warn("Failed to load code cache from disk", e);
    } catch (final Exception e) {
      LOG.warn("Unexpected error while loading code cache from disk: {}", e.getMessage(), e);
    }
  }

  /**
   * Deletes all persisted code cache files in the specified directory.
   *
   * @param dataDir the directory where code cache files are stored
   * @throws IOException if an I/O error occurs
   */
  public static void deleteAllPersistedCodeCaches(final Path dataDir) throws IOException {
    try (Stream<Path> files = Files.list(dataDir)) {
      files
          .filter(
              p -> {
                String name = p.getFileName().toString();
                return name.startsWith("codecache-") && name.endsWith(".rlp");
              })
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  LOG.info("Deletion of old code cache file {} failed: {}", p, e.getMessage());
                }
              });
    }
  }
}
