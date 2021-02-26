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

import java.io.File;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.tuweni.bytes.Bytes;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SECPPrivateKeyTest {
  public static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;

  @Parameterized.Parameters
  public static Object[][] getKeyParameters() {
    return new Object[][] {
      {
        32,
        "validPrivateKey_256.txt",
        "000000000000000000000000000000000000000000000000000000000000000A"
      },
      {
        48,
        "validPrivateKey_384.txt",
        "db83a36948be2d37cfca81279d30f13912570e1a61e1d3d03d351e68c46327dc9505fe5d1a75edd0e04406b166a23f1b"
      }
    };
  }

  private final int keyLength;
  private final String validPrivateKeyFile;
  private final String expectedPrivateKeyFromFile;

  public SECPPrivateKeyTest(
      final int keyLength,
      final String validPrivateKeyFile,
      final String expectedPrivateKeyFromFile) {
    this.keyLength = keyLength;
    this.validPrivateKeyFile = validPrivateKeyFile;
    this.expectedPrivateKeyFromFile = expectedPrivateKeyFromFile;
  }

  protected static String suiteStartTime = null;
  protected static String suiteName = null;

  @BeforeClass
  public static void setUpSuite() {
    suiteStartTime =
        LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    suiteName(SECPPrivateKeyTest.class);
  }

  public static void suiteName(final Class<?> clazz) {
    suiteName = clazz.getSimpleName() + "-" + suiteStartTime;
  }

  @Test(expected = NullPointerException.class)
  public void createPrivateKey_NullEncoding() {
    SECPPrivateKey.create(null, ALGORITHM);
  }

  @Test
  public void privateKeyEquals() {
    final SECPPrivateKey privateKey1 = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM, keyLength);
    final SECPPrivateKey privateKey2 = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM, keyLength);

    assertThat(privateKey2).isEqualTo(privateKey1);
  }

  @Test
  public void privateHashCode() {
    final SECPPrivateKey privateKey = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM, keyLength);

    assertThat(privateKey.hashCode()).isNotZero();
  }

  @Test
  public void fileContainsValidPrivateKey() throws Exception {
    final File file =
        new File(
            this.getClass()
                .getResource("/org/hyperledger/besu/crypto/" + validPrivateKeyFile)
                .toURI());
    final SECPPrivateKey privateKey = KeyPairUtil.loadPrivateKey(file);
    assertThat(privateKey.getEncodedBytes())
        .isEqualTo(Bytes.fromHexString(expectedPrivateKeyFromFile));
  }
}
