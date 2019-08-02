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
package tech.pegasys.pantheon.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class HashTest {

  private static final String cowKeccak256 =
      "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
  private static final String horseKeccak256 =
      "c87f65ff3f271bf5dc8643484f66b200109caffe4bf98c4cb393dc35740b28c0";

  private static final String inputBlake2bf =
      "000000016a09e667f2bd8948bb67ae8584caa73b3c6ef372fe94f82ba54ff53a5f1d36f1510e527fade682d19b05688c2b3e6c1f1f83d9abfb41bd6b5be0cd19137e217907060504030201000f0e0d0c0b0a090817161514131211101f1e1d1c1b1a191827262524232221202f2e2d2c2b2a292837363534333231303f3e3d3c3b3a3938000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000080000000000000000001";

  private static final String outputBlake2bf =
      "62305ad4d48dade8269ef60a1bcb8b7bef6e479e643b5ac1f8017e6422ce89fb62d09ecaa81d095e855540dcbc07bd0feb3d4f5e5e50541260ed930f027cfd8d";

  /** Validate keccak256 hash. */
  @Test
  public void keccak256Hash() {
    final BytesValue resultHorse = Hash.keccak256(BytesValue.wrap("horse".getBytes(UTF_8)));
    assertEquals(BytesValue.fromHexString(horseKeccak256), resultHorse);

    final BytesValue resultCow = Hash.keccak256(BytesValue.wrap("cow".getBytes(UTF_8)));
    assertEquals(BytesValue.fromHexString(cowKeccak256), resultCow);
  }

  /** Validate blake2f compression digest. */
  @Test
  public void blake2bfCompression() {
    final BytesValue result = Hash.blake2bf(BytesValue.wrap(Hex.decode(inputBlake2bf)));
    assertEquals(BytesValue.fromHexString(outputBlake2bf), result);
  }
}
