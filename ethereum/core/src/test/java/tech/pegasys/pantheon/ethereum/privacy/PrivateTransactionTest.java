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
package tech.pegasys.pantheon.ethereum.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Test;

public class PrivateTransactionTest {

  private static final String INVALID_RLP =
      "0xf87f800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "a0fffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
          + "fffffff801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff75"
          + "9b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43"
          + "601b4ab949f53faa07bd2c804";

  private static final String VALID_PRIVATE_TRANSACTION_RLP =
      "0xf90113800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
          + "ffff801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d"
          + "495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab94"
          + "9f53faa07bd2c804ac41316156744d784c4355486d425648586f5a7a7a4267"
          + "5062572f776a3561784470573958386c393153476f3df85aac41316156744d"
          + "784c4355486d425648586f5a7a7a42675062572f776a356178447057395838"
          + "6c393153476f3dac4b6f32625671442b6e4e6c4e594c35454537793349644f"
          + "6e766966746a69697a706a52742b4854754642733d8a726573747269637465"
          + "64";

  private static final String VALID_SIGNED_PRIVATE_TRANSACTION_RLP =
      "0xf901a4808203e8832dc6c08080b8ef60806040523480156100105760008"
          + "0fd5b5060d08061001f6000396000f3fe60806040526004361060485763f"
          + "fffffff7c010000000000000000000000000000000000000000000000000"
          + "000000060003504166360fe47b18114604d5780636d4ce63c146075575b6"
          + "00080fd5b348015605857600080fd5b50607360048036036020811015606"
          + "d57600080fd5b50356099565b005b348015608057600080fd5b506087609"
          + "e565b60408051918252519081900360200190f35b600055565b600054905"
          + "6fea165627a7a72305820cb1d0935d14b589300b12fcd0ab849a7e9019c8"
          + "1da24d6daa4f6b2f003d1b01800292ca0a6dc7319bd355ce9d8e0928d29d"
          + "9b8110bcba9168fad68498e49526420fe65dea06c4c12c2ae518c5130353"
          + "eb6c2893b1c36b7fd1497c156b1e158b716f482601fac41316156744d784"
          + "c4355486d425648586f5a7a7a42675062572f776a3561784470573958386"
          + "c393153476f3dedac4b6f32625671442b6e4e6c4e594c354545377933496"
          + "44f6e766966746a69697a706a52742b4854754642733d8a7265737472696"
          + "3746564";

  private static final PrivateTransaction VALID_PRIVATE_TRANSACTION =
      new PrivateTransaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(BytesValue.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.of(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129639935")),
          SECP256K1.Signature.create(
              new BigInteger(
                  "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
              new BigInteger(
                  "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
              Byte.valueOf("0")),
          BytesValue.fromHexString("0x"),
          Address.wrap(BytesValue.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          0,
          BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)),
          Lists.newArrayList(
              BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)),
              BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8))),
          BytesValue.wrap("restricted".getBytes(UTF_8)));

  private static final PrivateTransaction VALID_SIGNED_PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              BytesValue.fromHexString(
                  "0x608060405234801561001057600080fd5b5060d08061001f6000396000"
                      + "f3fe60806040526004361060485763ffffffff7c010000000000"
                      + "0000000000000000000000000000000000000000000000600035"
                      + "04166360fe47b18114604d5780636d4ce63c146075575b600080"
                      + "fd5b348015605857600080fd5b50607360048036036020811015"
                      + "606d57600080fd5b50356099565b005b348015608057600080fd"
                      + "5b506087609e565b60408051918252519081900360200190f35b"
                      + "600055565b6000549056fea165627a7a72305820cb1d0935d14b"
                      + "589300b12fcd0ab849a7e9019c81da24d6daa4f6b2f003d1b018"
                      + "0029"))
          .sender(
              Address.wrap(BytesValue.fromHexString("0x1c9a6e1ee3b7ac6028e786d9519ae3d24ee31e79")))
          .chainId(4)
          .privateFrom(
              BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)))
          .privateFor(
              Lists.newArrayList(
                  BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8))))
          .restriction(BytesValue.wrap("restricted".getBytes(UTF_8)))
          .signAndBuild(
              SECP256K1.KeyPair.create(
                  SECP256K1.PrivateKey.create(
                      new BigInteger(
                          "853d7f0010fd86d0d7811c1f9d968ea89a24484a8127b4a483ddf5d2cfec766d",
                          16))));

  @Test
  public void testWriteTo() {
    BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    VALID_PRIVATE_TRANSACTION.writeTo(bvrlpo);
    assertEquals(VALID_PRIVATE_TRANSACTION_RLP, bvrlpo.encoded().toString());
  }

  @Test
  public void testSignedWriteTo() {
    BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    VALID_SIGNED_PRIVATE_TRANSACTION.writeTo(bvrlpo);
    assertEquals(VALID_SIGNED_PRIVATE_TRANSACTION_RLP, bvrlpo.encoded().toString());
  }

  @Test
  public void testReadFrom() {
    PrivateTransaction p =
        PrivateTransaction.readFrom(
            new BytesValueRLPInput(BytesValue.fromHexString(VALID_PRIVATE_TRANSACTION_RLP), false));

    assertEquals(VALID_PRIVATE_TRANSACTION, p);
  }

  @Test
  public void testSignedReadFrom() {
    PrivateTransaction p =
        PrivateTransaction.readFrom(
            new BytesValueRLPInput(
                BytesValue.fromHexString(VALID_SIGNED_PRIVATE_TRANSACTION_RLP), false));

    assertEquals(VALID_SIGNED_PRIVATE_TRANSACTION, p);
  }

  @Test(expected = RLPException.class)
  public void testReadFromInvalid() {
    PrivateTransaction.readFrom(
        new BytesValueRLPInput(BytesValue.fromHexString(INVALID_RLP), false));
  }
}
