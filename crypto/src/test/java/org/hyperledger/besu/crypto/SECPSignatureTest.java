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

import java.math.BigInteger;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SECPSignatureTest {

  @Parameterized.Parameters
  public static Object[][] getKeyParameters() {
    return new Object[][] {
      {32, "secp256k1"},
      {32, "secp256r1"},
      {48, "secp384r1"}
    };
  }

  public int keyLength;
  public BigInteger curveOrder;

  public SECPSignatureTest(final int keyLength, final String curveName) {
    this.keyLength = keyLength;

    final X9ECParameters params = SECNamedCurves.getByName(curveName);
    final ECDomainParameters curve =
        new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
    curveOrder = curve.getN();
  }

  @Test
  public void createSignature() {
    final SECPSignature signature =
        SECPSignature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0, curveOrder, keyLength);
    assertThat(signature.getR()).isEqualTo(BigInteger.ONE);
    assertThat(signature.getS()).isEqualTo(BigInteger.TEN);
    assertThat(signature.getRecId()).isEqualTo((byte) 0);
  }

  @Test(expected = NullPointerException.class)
  public void createSignature_NoR() {
    SECPSignature.create(null, BigInteger.ZERO, (byte) 27, curveOrder, keyLength);
  }

  @Test(expected = NullPointerException.class)
  public void createSignature_NoS() {
    SECPSignature.create(BigInteger.ZERO, null, (byte) 27, curveOrder, keyLength);
  }
}
