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
package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.SetCodeAuthorization;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetCodeTransactionEncoderTest {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  BytesValueRLPOutput output;

  @BeforeEach
  void setUp() {
    output = new BytesValueRLPOutput();
  }

  @Test
  void shouldEncodeSingleSetCodeWithNonce() {
    // "0xd80194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c105"

    final SetCodeAuthorization authorization =
        new SetCodeAuthorization(
            BigInteger.ONE,
            Address.fromHexString("0x633688abc3cCf8B0C03088D2d1C6ae4958c2fA56"),
            Optional.of(0L),
            SIGNATURE_ALGORITHM
                .get()
                .createSignature(
                    new BigInteger(
                        "840798fa67118e034c1eb7e42fe89e28d7cd5006dc813d5729e5f75b0d1a7ec5", 16),
                    new BigInteger(
                        "3b1dbace38ceb862a65bf2eac0637693b5c3493bcb2a022dd614c0a74cce0b99", 16),
                    (byte) 0));

    SetCodeTransactionEncoder.encodeSingleSetCode(authorization, output);

    assertThat(output.encoded())
        .isEqualTo(
            Bytes.fromHexString(
                "0xf85b0194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c18080a0840798fa67118e034c1eb7e42fe89e28d7cd5006dc813d5729e5f75b0d1a7ec5a03b1dbace38ceb862a65bf2eac0637693b5c3493bcb2a022dd614c0a74cce0b99"));
  }

  @Test
  void shouldEncodeSingleSetCodeWithoutNonce() {
    // "0xd70194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c5"

    final SetCodeAuthorization authorization =
        new SetCodeAuthorization(
            BigInteger.ONE,
            Address.fromHexString("0x633688abc3cCf8B0C03088D2d1C6ae4958c2fA56"),
            Optional.empty(),
            SIGNATURE_ALGORITHM
                .get()
                .createSignature(
                    new BigInteger(
                        "dd6b24048be1b7d7fe5bbbb73ffc37eb2ce1997ecb4ae5b6096532ef19363148", 16),
                    new BigInteger(
                        "25b58a1ff8ad00bddbbfa1d5c2411961cbb6d08dcdc8ae88303db3c6cf983031", 16),
                    (byte) 1));

    SetCodeTransactionEncoder.encodeSingleSetCode(authorization, output);

    assertThat(output.encoded())
        .isEqualTo(
            Bytes.fromHexString(
                "0xf85a0194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c001a0dd6b24048be1b7d7fe5bbbb73ffc37eb2ce1997ecb4ae5b6096532ef19363148a025b58a1ff8ad00bddbbfa1d5c2411961cbb6d08dcdc8ae88303db3c6cf983031"));
  }

  @Test
  void shouldEncodeSingleSetCodeWithoutNonceAndChainIdZero() {
    // "d70094633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c5"

    final SetCodeAuthorization authorization =
        new SetCodeAuthorization(
            BigInteger.ZERO,
            Address.fromHexString("0x633688abc3cCf8B0C03088D2d1C6ae4958c2fA56"),
            Optional.empty(),
            SIGNATURE_ALGORITHM
                .get()
                .createSignature(
                    new BigInteger(
                        "25c1240d7ffec0daeedb752d3357aff2e3cd58468f0c2d43ee0ee999e02ace2", 16),
                    new BigInteger(
                        "3c8a25b2becd6e666f69803d1ae3322f2e137b7745c2c7f19da80f993ffde4df", 16),
                    (byte) 1));

    SetCodeTransactionEncoder.encodeSingleSetCode(authorization, output);

    assertThat(output.encoded())
        .isEqualTo(
            Bytes.fromHexString(
                "0xf85a8094633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c001a0025c1240d7ffec0daeedb752d3357aff2e3cd58468f0c2d43ee0ee999e02ace2a03c8a25b2becd6e666f69803d1ae3322f2e137b7745c2c7f19da80f993ffde4df"));
  }
}
