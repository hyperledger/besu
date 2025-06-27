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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeV0;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeCacheStorageTest {

  @TempDir Path tempDir;

  private CodeCache cache;

  @BeforeEach
  void setUp() {
    cache = new CodeCache();
  }

  @Test
  void shouldWriteAndReadSimpleCodeCacheSuccessfully() {
    // given
    final Bytes bytecode = Bytes.fromHexString("0x6001600055");
    final Hash codeHash = Hash.hash(bytecode);
    final int size = bytecode.size();
    final long[] jumpDests = new long[] {0b10L};

    final CodeV0 code = new CodeV0(bytecode, codeHash, size, jumpDests);
    cache.put(codeHash, code);

    // when
    CodeCacheStorage.writeToDisk(tempDir, cache);

    final CodeCache emptyCache = new CodeCache();
    CodeCacheStorage.loadFromDisk(tempDir, emptyCache);

    // then
    Map<Hash, Code> result = emptyCache.asMap();
    assertThat(result.containsKey(codeHash)).isTrue();

    Code loaded = result.get(codeHash);
    assertThat(loaded.getBytes()).isEqualTo(bytecode);
    assertThat(loaded.getSize()).isEqualTo(size);
    assertThat(loaded.getCodeHash()).isEqualTo(codeHash);
    assertThat(loaded.getJumpDestBitMask()).containsExactly(jumpDests);
  }

  @Test
  void shouldWriteAndReadComplexCodeCacheSuccessfully() {
    // given
    final Bytes bytecode1 = Bytes.fromHexString("0x6001600055");
    final Bytes bytecode2 = Bytes.fromHexString("0x6002600155600355");
    final Bytes bytecode3 = Bytes.fromHexString("0x60FF");
    final Bytes bytecode4 = Bytes.fromHexString("0x6001600160016001");

    final Hash hash1 = Hash.hash(bytecode1);
    final Hash hash2 = Hash.hash(bytecode2);
    final Hash hash3 = Hash.hash(bytecode3);
    final Hash hash4 = Hash.hash(bytecode4);

    final long[] bitmask1 = null;
    final long[] bitmask2 = new long[0];
    final long[] bitmask3 = new long[] {0b101L};
    final long[] bitmask4 = new long[] {0xFFFFFFFFFFFFFFFFL, 0x0FL};

    cache.put(hash1, new CodeV0(bytecode1, hash1, bytecode1.size(), bitmask1));
    cache.put(hash2, new CodeV0(bytecode2, hash2, bytecode2.size(), bitmask2));
    cache.put(hash3, new CodeV0(bytecode3, hash3, bytecode3.size(), bitmask3));
    cache.put(hash4, new CodeV0(bytecode4, hash4, bytecode4.size(), bitmask4));

    // when
    CodeCacheStorage.writeToDisk(tempDir, cache);

    final CodeCache reloadedCache = new CodeCache();
    CodeCacheStorage.loadFromDisk(tempDir, reloadedCache);

    // then
    Map<Hash, Code> result = reloadedCache.asMap();

    assertThat(result.size()).isEqualTo(4);
    assertThat(result.containsKey(hash1)).isTrue();
    assertThat(result.containsKey(hash2)).isTrue();
    assertThat(result.containsKey(hash3)).isTrue();
    assertThat(result.containsKey(hash4)).isTrue();

    Code c1 = result.get(hash1);
    assertThat(c1.getBytes()).isEqualTo(bytecode1);
    assertThat(c1.getJumpDestBitMask()).isNull();

    Code c2 = result.get(hash2);
    assertThat(c2.getBytes()).isEqualTo(bytecode2);
    assertThat(c2.getJumpDestBitMask())
        .isNull(); // empty array is encoded as empty list -> null on read

    Code c3 = result.get(hash3);
    assertThat(c3.getBytes()).isEqualTo(bytecode3);
    assertThat(c3.getJumpDestBitMask()).containsExactly(bitmask3);

    Code c4 = result.get(hash4);
    assertThat(c4.getBytes()).isEqualTo(bytecode4);
    assertThat(c4.getJumpDestBitMask()).containsExactly(bitmask4);
  }

  @Test
  @SuppressWarnings("StreamResourceLeak")
  void shouldDeletePersistedCacheFiles() throws IOException {
    // given
    final Bytes bytecode = Bytes.fromHexString("0x6001600055");
    final Hash codeHash = Hash.hash(bytecode);
    final CodeV0 code = new CodeV0(bytecode, codeHash, bytecode.size(), new long[] {0b1L});
    cache.put(codeHash, code);
    CodeCacheStorage.writeToDisk(tempDir, cache);

    // ensure file is written
    assertThat(Files.list(tempDir).count()).isEqualTo(1);

    // when
    CodeCacheStorage.deleteAllPersistedCodeCaches(tempDir);

    // then
    assertThat(Files.list(tempDir).count()).isEqualTo(0);
  }

  @Test
  void shouldSkipLoadIfMultipleCacheFilesExist() throws IOException {
    // write two bogus files
    Files.write(tempDir.resolve("codecache-foo.rlp"), Bytes.of(1, 2, 3).toArrayUnsafe());
    Files.write(tempDir.resolve("codecache-bar.rlp"), Bytes.of(4, 5, 6).toArrayUnsafe());

    CodeCacheStorage.loadFromDisk(tempDir, cache);

    // should not crash or load anything
    assertThat(cache.asMap().isEmpty()).isTrue();
  }

  @Test
  void shouldSkipLoadIfFileIsCorrupted() throws IOException {
    // write invalid RLP
    final Path file = tempDir.resolve("codecache-deadbeef.rlp");
    Files.write(file, Bytes.fromHexString("0xdeadbeef").toArrayUnsafe());

    CodeCacheStorage.loadFromDisk(tempDir, cache);

    assertThat(cache.asMap().isEmpty()).isTrue();
  }
}
