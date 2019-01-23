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

  @Test
  public void testWriteTo() {
    BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    VALID_PRIVATE_TRANSACTION.writeTo(bvrlpo);
    assertEquals(VALID_PRIVATE_TRANSACTION_RLP, bvrlpo.encoded().toString());
  }

  @Test
  public void testReadFrom() {
    PrivateTransaction p =
        PrivateTransaction.readFrom(
            new BytesValueRLPInput(BytesValue.fromHexString(VALID_PRIVATE_TRANSACTION_RLP), false));

    assertEquals(VALID_PRIVATE_TRANSACTION, p);
  }

  @Test(expected = RLPException.class)
  public void testReadFromInvalid() {
    PrivateTransaction.readFrom(
        new BytesValueRLPInput(BytesValue.fromHexString(INVALID_RLP), false));
  }
}
