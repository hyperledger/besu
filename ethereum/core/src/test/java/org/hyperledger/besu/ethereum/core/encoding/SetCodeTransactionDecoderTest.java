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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.SetCodeAuthorization;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class SetCodeTransactionDecoderTest {

  @Test
  void shouldDecodeInnerPayloadWithNonce() {
    // "0xd80194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c105"

    final BytesValueRLPInput input =
        new BytesValueRLPInput(
            Bytes.fromHexString(
                "0xf85b0194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c18080a0840798fa67118e034c1eb7e42fe89e28d7cd5006dc813d5729e5f75b0d1a7ec5a03b1dbace38ceb862a65bf2eac0637693b5c3493bcb2a022dd614c0a74cce0b99"),
            true);
    final SetCodeAuthorization authorization = SetCodeTransactionDecoder.decodeInnerPayload(input);

    assertThat(authorization.chainId()).isEqualTo(BigInteger.ONE);
    assertThat(authorization.address())
        .isEqualTo(Address.fromHexStringStrict("0x633688abc3cCf8B0C03088D2d1C6ae4958c2fA56"));
    assertThat(authorization.nonce().get()).isEqualTo(0L);

    final SECPSignature signature = authorization.signature();
    assertThat(signature.getRecId()).isEqualTo((byte) 0);
    assertThat(signature.getR().toString(16))
        .isEqualTo("840798fa67118e034c1eb7e42fe89e28d7cd5006dc813d5729e5f75b0d1a7ec5");
    assertThat(signature.getS().toString(16))
        .isEqualTo("3b1dbace38ceb862a65bf2eac0637693b5c3493bcb2a022dd614c0a74cce0b99");
  }

  @Test
  void shouldDecodeInnerPayloadWithoutNonce() {
    // "0xd70194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c5"

    final BytesValueRLPInput input =
        new BytesValueRLPInput(
            Bytes.fromHexString(
                "0xf85a0194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c001a0dd6b24048be1b7d7fe5bbbb73ffc37eb2ce1997ecb4ae5b6096532ef19363148a025b58a1ff8ad00bddbbfa1d5c2411961cbb6d08dcdc8ae88303db3c6cf983031"),
            true);
    final SetCodeAuthorization authorization = SetCodeTransactionDecoder.decodeInnerPayload(input);

    assertThat(authorization.chainId()).isEqualTo(BigInteger.ONE);
    assertThat(authorization.address())
        .isEqualTo(Address.fromHexStringStrict("0x633688abc3cCf8B0C03088D2d1C6ae4958c2fA56"));
    assertThat(authorization.nonce()).isEmpty();

    final SECPSignature signature = authorization.signature();
    assertThat(signature.getRecId()).isEqualTo((byte) 1);
    assertThat(signature.getR().toString(16))
        .isEqualTo("dd6b24048be1b7d7fe5bbbb73ffc37eb2ce1997ecb4ae5b6096532ef19363148");
    assertThat(signature.getS().toString(16))
        .isEqualTo("25b58a1ff8ad00bddbbfa1d5c2411961cbb6d08dcdc8ae88303db3c6cf983031");
  }

  @Test
  void shouldThrowInnerPayloadWithMultipleNonces() {
    // "d90194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c20107"

    final BytesValueRLPInput input =
        new BytesValueRLPInput(
            Bytes.fromHexString(
                "0xf85c0194633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c2010201a0401b5d4ebe88306448115d1a46a30e5ad1136f2818b4ebb0733d9c4efffd135aa0753ff1dbce6db504ecb9635a64d8c4506ff887e2d2a0d2b7175baf94c849eccc"),
            true);

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          SetCodeTransactionDecoder.decodeInnerPayload(input);
        });
  }

  @Test
  void shouldDecodeInnerPayloadWithoutNonceAndChainIdZero() {
    // "d70094633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c5"

    final BytesValueRLPInput input =
        new BytesValueRLPInput(
            Bytes.fromHexString(
                "0xf85a0094633688abc3ccf8b0c03088d2d1c6ae4958c2fa56c001a0025c1240d7ffec0daeedb752d3357aff2e3cd58468f0c2d43ee0ee999e02ace2a03c8a25b2becd6e666f69803d1ae3322f2e137b7745c2c7f19da80f993ffde4df"),
            true);
    final SetCodeAuthorization authorization = SetCodeTransactionDecoder.decodeInnerPayload(input);

    assertThat(authorization.chainId()).isEqualTo(BigInteger.ZERO);
    assertThat(authorization.address())
        .isEqualTo(Address.fromHexStringStrict("0x633688abc3cCf8B0C03088D2d1C6ae4958c2fA56"));
    assertThat(authorization.nonce().isEmpty()).isTrue();

    final SECPSignature signature = authorization.signature();
    assertThat(signature.getRecId()).isEqualTo((byte) 1);
    assertThat(signature.getR().toString(16))
        .isEqualTo("25c1240d7ffec0daeedb752d3357aff2e3cd58468f0c2d43ee0ee999e02ace2");
    assertThat(signature.getS().toString(16))
        .isEqualTo("3c8a25b2becd6e666f69803d1ae3322f2e137b7745c2c7f19da80f993ffde4df");
  }
}
