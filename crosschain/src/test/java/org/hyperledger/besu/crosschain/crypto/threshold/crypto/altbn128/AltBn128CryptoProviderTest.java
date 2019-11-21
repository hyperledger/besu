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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;

import java.math.BigInteger;

import org.junit.Test;

public class AltBn128CryptoProviderTest {

  @Test
  public void signVerifyHappyCase() {
    byte[] dataToBeSigned = new byte[] {0x01, 0x02, 0x03};
    BigInteger privateKey = BigInteger.TEN;

    BlsCryptoProvider cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);
    BlsPoint pubKey = cryptoProvider.createPointE2(privateKey);

    BlsPoint signature = cryptoProvider.sign(privateKey, dataToBeSigned);
    boolean verified = cryptoProvider.verify(pubKey, dataToBeSigned, signature);
    assertThat(verified).isTrue();
  }

  @Test
  public void signVerifyBadVerifyData() {
    byte[] dataToBeSigned = new byte[] {0x01, 0x02, 0x03};
    byte[] dataToBeVerified = new byte[] {0x01, 0x02, 0x04};
    BigInteger privateKey = BigInteger.TEN;

    BlsCryptoProvider cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);
    BlsPoint pubKey = cryptoProvider.createPointE2(privateKey);

    BlsPoint signature = cryptoProvider.sign(privateKey, dataToBeSigned);
    boolean verified = cryptoProvider.verify(pubKey, dataToBeVerified, signature);
    assertThat(verified).isFalse();
  }

  @Test
  public void signVerifyBadPublicKey() {
    byte[] dataToBeSigned = new byte[] {0x01, 0x02, 0x03};
    BigInteger privateKey = BigInteger.TEN;

    BlsCryptoProvider cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);
    BlsPoint pubKey = cryptoProvider.getBasePointE2();

    BlsPoint signature = cryptoProvider.sign(privateKey, dataToBeSigned);
    boolean verified = cryptoProvider.verify(pubKey, dataToBeSigned, signature);
    assertThat(verified).isFalse();
  }
}
