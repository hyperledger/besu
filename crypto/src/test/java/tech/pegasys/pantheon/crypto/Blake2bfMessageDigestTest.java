/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.bouncycastle.util.Pack;
import org.junit.Before;
import org.junit.Test;

/**
 * Test vectors adapted from
 * https://github.com/keep-network/blake2b/blob/master/compression/f_test.go
 */
public class Blake2bfMessageDigestTest {

  private Blake2bfMessageDigest messageDigest;

  // output when input is all 0
  private byte[] blake2bfAllZero =
      new byte[] {
        106, 9, -26, 103, -13, -68, -55, 8, -69, 103, -82, -123, -124, -54, -89, 59, 60, 110, -13,
        114, -2, -108, -8, 43, -91, 79, -11, 58, 95, 29, 54, -15, 81, 14, 82, 127, -83, -26, -126,
        -47, -101, 5, 104, -116, 43, 62, 108, 31, 31, -125, -39, -85, -5, 65, -67, 107, 91, -32,
        -51, 25, 19, 126, 33, 121
      };

  // output when input is all 0 for 2147483648 rounds
  private byte[] blake2bfAllZeroNegativeRounds =
      new byte[] {
        118, 127, 109, 29, 115, -124, -99, -111, 81, 112, 35, 60, -89, 75, 21, 18, -97, -73, 19,
        -102, 40, -8, 78, 110, -43, 124, 66, 83, -89, 69, 69, 57, -25, -105, 123, 117, 115, 115, 78,
        -92, 123, 87, 14, -127, -94, -1, -74, 25, -125, 48, 54, -78, -82, -75, 84, -26, -38, -42,
        -93, 120, -61, 7, -58, 38
      };

  @Before
  public void setUp() {
    messageDigest = new Blake2bfMessageDigest();
  }

  @Test
  public void digestIfUpdatedCorrectlyWithBytes() {
    for (int i = 0; i < 213; i++) {
      messageDigest.update((byte) 0);
    }
    assertThat(messageDigest.digest()).isEqualTo(blake2bfAllZero);
  }

  @Test
  public void digestIfUpdatedCorrectlyWithByteArray() {
    byte[] update = new byte[213];
    messageDigest.update(update, 0, 213);
    assertThat(messageDigest.digest()).isEqualTo(blake2bfAllZero);
  }

  @Test
  public void digestIfUpdatedCorrectlyMixed() {
    byte[] update = new byte[213];
    messageDigest.update((byte) 0);
    messageDigest.update(update, 2, 211);
    messageDigest.update((byte) 0);
    assertThat(messageDigest.digest()).isEqualTo(blake2bfAllZero);
  }

  @Test
  public void digestWithNegativeRounds() {
    // equal to Integer.MAX_VALUE + 1 (2147483648) as uint
    byte[] rounds = Pack.intToBigEndian(Integer.MIN_VALUE);
    messageDigest.update(rounds, 0, 4);
    messageDigest.update(new byte[213], 0, 209);
    assertThat(messageDigest.digest()).isEqualTo(blake2bfAllZeroNegativeRounds);
  }

  @Test(expected = IllegalStateException.class)
  public void throwsIfBufferUpdatedWithLessThat213Bytes() {
    for (int i = 0; i < 212; i++) {
      messageDigest.update((byte) 0);
    }
    messageDigest.digest();
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsIfBufferUpdatedWithMoreThat213Bytes() {
    for (int i = 0; i < 214; i++) {
      messageDigest.update((byte) 0);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsIfBufferUpdatedLargeByteArray() {
    byte[] update = new byte[213];
    messageDigest.update((byte) 0);
    messageDigest.update(update, 0, 213);
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsIfEmptyBufferUpdatedLargeByteArray() {
    byte[] update = new byte[214];
    messageDigest.update(update, 0, 214);
  }
}
