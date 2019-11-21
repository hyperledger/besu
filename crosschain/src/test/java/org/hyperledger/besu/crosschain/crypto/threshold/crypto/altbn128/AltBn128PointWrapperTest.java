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
package org.hyperledger.besu.crosschain.crypto.threshold.crypto.altbn128;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;

import java.math.BigInteger;

import org.junit.Test;

public class AltBn128PointWrapperTest {

  @Test
  public void loadStore() {
    BigInteger privateKey = BigInteger.TEN;
    BlsCryptoProvider cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);
    BlsPoint point = cryptoProvider.createPointE1(privateKey);

    byte[] data = point.store();
    BlsPoint newPoint = BlsPoint.load(data);

    assertThat(newPoint).isEqualTo(point);
  }
}
