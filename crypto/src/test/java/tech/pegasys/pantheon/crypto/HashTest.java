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

import org.junit.Test;

public class HashTest {

  private static final String cowKeccak256 =
      "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
  private static final String horseKeccak256 =
      "c87f65ff3f271bf5dc8643484f66b200109caffe4bf98c4cb393dc35740b28c0";

  /** Validate keccak256 hash. */
  @Test
  public void keccak256Hash() {
    final BytesValue resultHorse = Hash.keccak256(BytesValue.wrap("horse".getBytes(UTF_8)));
    assertEquals(BytesValue.fromHexString(horseKeccak256), resultHorse);

    final BytesValue resultCow = Hash.keccak256(BytesValue.wrap("cow".getBytes(UTF_8)));
    assertEquals(BytesValue.fromHexString(cowKeccak256), resultCow);
  }
}
