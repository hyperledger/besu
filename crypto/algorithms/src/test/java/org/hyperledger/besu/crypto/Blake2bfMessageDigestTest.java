/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import org.bouncycastle.util.Pack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Test vectors adapted from
 * https://github.com/keep-network/blake2b/blob/master/compression/f_test.go
 */
public class Blake2bfMessageDigestTest {

  private static final SecureRandom SECURE_RANDOM;

  static {
    try {
      SECURE_RANDOM = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private Blake2bfMessageDigest messageDigest;

  // output when input is all 0
  private static final byte[] BLAKE2F_ALL_ZERO =
      new byte[] {
        8, -55, -68, -13, 103, -26, 9, 106, 59, -89, -54, -124, -123, -82, 103, -69, 43, -8, -108,
        -2, 114, -13, 110, 60, -15, 54, 29, 95, 58, -11, 79, -91, -47, -126, -26, -83, 127, 82, 14,
        81, 31, 108, 62, 43, -116, 104, 5, -101, 107, -67, 65, -5, -85, -39, -125, 31, 121, 33, 126,
        19, 25, -51, -32, 91
      };

  // output when input is all 0 for 4294967295 rounds
  private static final byte[] BLAKE2F_ALL_ZERO_NEGATIVE_ROUNDS =
      new byte[] {
        -111, -99, -124, 115, 29, 109, 127, 118, 18, 21, 75, -89, 60, 35, 112, 81, 110, 78, -8, 40,
        -102, 19, -73, -97, 57, 69, 69, -89, 83, 66, 124, -43, -92, 78, 115, 115, 117, 123, -105,
        -25, 25, -74, -1, -94, -127, 14, 87, 123, -26, 84, -75, -82, -78, 54, 48, -125, 38, -58, 7,
        -61, 120, -93, -42, -38
      };

  @BeforeEach
  public void setUp() {
    messageDigest = new Blake2bfMessageDigest();
  }

  @Test
  public void digestIfUpdatedCorrectlyWithBytes() {
    for (int i = 0; i < 213; i++) {
      messageDigest.update((byte) 0);
    }
    assertThat(messageDigest.digest()).isEqualTo(BLAKE2F_ALL_ZERO);
  }

  @Test
  public void digestIfUpdatedCorrectlyWithByteArray() {
    final byte[] update = new byte[213];
    messageDigest.update(update, 0, 213);
    assertThat(messageDigest.digest()).isEqualTo(BLAKE2F_ALL_ZERO);
  }

  @Test
  public void digestIfUpdatedCorrectlyMixed() {
    final byte[] update = new byte[213];
    messageDigest.update((byte) 0);
    messageDigest.update(update, 2, 211);
    messageDigest.update((byte) 0);
    assertThat(messageDigest.digest()).isEqualTo(BLAKE2F_ALL_ZERO);
  }

  @Test
  public void digestWithMaxRounds() {
    // equal to unsigned int max value (4294967295, or signed -1)
    final byte[] rounds = Pack.intToBigEndian(Integer.MIN_VALUE);
    messageDigest.update(rounds, 0, 4);
    messageDigest.update(new byte[213], 0, 209);
    assertThat(messageDigest.digest()).isEqualTo(BLAKE2F_ALL_ZERO_NEGATIVE_ROUNDS);
  }

  @Test
  public void throwsIfBufferUpdatedWithLessThat213Bytes() {
    for (int i = 0; i < 212; i++) {
      messageDigest.update((byte) 0);
    }
    assertThatThrownBy(() -> messageDigest.digest()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void throwsIfBufferUpdatedWithMoreThat213Bytes() {
    assertThatThrownBy(
            () -> {
              for (int i = 0; i < 214; i++) {
                messageDigest.update((byte) 0);
              }
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void throwsIfBufferUpdatedLargeByteArray() {
    final byte[] update = new byte[213];
    messageDigest.update((byte) 0);
    assertThatThrownBy(() -> messageDigest.update(update, 0, 213))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void throwsIfEmptyBufferUpdatedLargeByteArray() {
    assertThatThrownBy(
            () -> {
              final byte[] update = new byte[214];
              messageDigest.update(update, 0, 214);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testDigestThreadSafety() throws ExecutionException, InterruptedException {
    final byte[] input = new byte[213];
    ;
    SECURE_RANDOM.nextBytes(input);
    int numberOfHashes = 10;

    CompletableFuture<byte[]>[] futures =
        IntStream.range(0, numberOfHashes)
            .mapToObj(
                i ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            MessageDigest clonedDigest = messageDigest.clone();
                            clonedDigest.update(input);
                            byte[] digest = clonedDigest.digest();
                            return digest;
                          } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                          }
                        }))
            .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures).get();

    byte[] expectedHash = futures[0].get();
    for (CompletableFuture<byte[]> future : futures) {
      assertThat(expectedHash).isEqualTo(future.get());
    }
  }

  @ParameterizedTest
  @CsvFileSource(resources = "eip152TestCases.csv", numLinesToSkip = 1)
  public void eip152TestCases(final String hexIn, final String hexExpected) {
    System.out.println("in=" + hexIn);
    byte[] in = hexStringToByteArray(hexIn);
    byte[] expected = hexStringToByteArray(hexExpected);
    messageDigest.update(in, 0, 213);
    assertThat(messageDigest.digest()).isEqualTo(expected);
  }

  private static byte[] hexStringToByteArray(final String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
    }
    return data;
  }
}
