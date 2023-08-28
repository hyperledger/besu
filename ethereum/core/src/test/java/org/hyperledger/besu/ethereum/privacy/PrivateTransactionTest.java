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
package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.plugin.data.Restriction.RESTRICTED;
import static org.hyperledger.besu.plugin.data.Restriction.UNRESTRICTED;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.math.BigInteger;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class PrivateTransactionTest {

  private static final String INVALID_RLP =
      "0xf87f800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "a0fffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
          + "fffffff801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff75"
          + "9b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43"
          + "601b4ab949f53faa07bd2c804";

  private static final String VALID_PRIVATE_TRANSACTION_RLP =
      "0xf8ef800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87a0f"
          + "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
          + "801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a3"
          + "6649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53fa"
          + "a07bd2c804a0035695b4cc4b0941e60551d7a19cf30603db5bfc23e5ac43a56"
          + "f57f25f75486af842a0035695b4cc4b0941e60551d7a19cf30603db5bfc23e5"
          + "ac43a56f57f25f75486aa02a8d9b56a0fe9cd94d60be4413bcb721d3a7be27e"
          + "d8e28b3a6346df874ee141b8a72657374726963746564";

  private static final String VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP =
      "0xf8cc800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87a0f"
          + "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
          + "801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a3"
          + "6649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53fa"
          + "a07bd2c804a0035695b4cc4b0941e60551d7a19cf30603db5bfc23e5ac43a56"
          + "f57f25f75486aa00f200e885ff29e973e2576b6600181d1b0a2b5294e30d9be"
          + "4a1981ffb33a0b8c8a72657374726963746564";

  private static final String VALID_SIGNED_PRIVATE_TRANSACTION_RLP =
      "0xf9018c808203e8832dc6c08080b8ef60806040523480156100105760008"
          + "0fd5b5060d08061001f6000396000f3fe60806040526004361060485763ffff"
          + "ffff7c010000000000000000000000000000000000000000000000000000000"
          + "060003504166360fe47b18114604d5780636d4ce63c146075575b600080fd5b"
          + "348015605857600080fd5b50607360048036036020811015606d57600080fd5"
          + "b50356099565b005b348015608057600080fd5b506087609e565b6040805191"
          + "8252519081900360200190f35b600055565b6000549056fea165627a7a72305"
          + "820cb1d0935d14b589300b12fcd0ab849a7e9019c81da24d6daa4f6b2f003d1"
          + "b01800292ca0bab7ae7f68afad5ade9e26177e044079bbcfd7857cf911b7a4c"
          + "97027dd92e41ba06e92e391ced52de53725e3054af3908a9f2f36374d727ebc"
          + "616a96a1ed2ca548a0035695b4cc4b0941e60551d7a19cf30603db5bfc23e5a"
          + "c43a56f57f25f75486ae1a02a8d9b56a0fe9cd94d60be4413bcb721d3a7be27"
          + "ed8e28b3a6346df874ee141b8a72657374726963746564";

  private static final String VALID_SIGNED_PRIVATE_TRANSACTION_LARGE_CHAINID_RLP =
      "0xf90191808203e8832dc6c08080b8ef60806040523480156100105760008"
          + "0fd5b5060d08061001f6000396000f3fe60806040526004361060485763ffff"
          + "ffff7c010000000000000000000000000000000000000000000000000000000"
          + "060003504166360fe47b18114604d5780636d4ce63c146075575b600080fd5b"
          + "348015605857600080fd5b50607360048036036020811015606d57600080fd5"
          + "b50356099565b005b348015608057600080fd5b506087609e565b6040805191"
          + "8252519081900360200190f35b600055565b6000549056fea165627a7a72305"
          + "820cb1d0935d14b589300b12fcd0ab849a7e9019c81da24d6daa4f6b2f003d1"
          + "b0180029850100000021a0bfa05529e85cdaadda498b6ab4ded157a70356ad7"
          + "6df24c18fa12b9d49be6ef4a03a54b8bc0c4e41e08d4cf0e94da98c5e02ef57"
          + "524cf2b53deecd56e3ba184927a0035695b4cc4b0941e60551d7a19cf30603d"
          + "b5bfc23e5ac43a56f57f25f75486ae1a02a8d9b56a0fe9cd94d60be4413bcb7"
          + "21d3a7be27ed8e28b3a6346df874ee141b8a72657374726963746564";

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private static final PrivateTransaction VALID_PRIVATE_TRANSACTION =
      new PrivateTransaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.of(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129639935")),
          SIGNATURE_ALGORITHM
              .get()
              .createSignature(
                  new BigInteger(
                      "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
                  new BigInteger(
                      "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
                  Byte.valueOf("0")),
          Bytes.fromHexString("0x"),
          Address.wrap(Bytes.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty(),
          Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="),
          Optional.of(
              Lists.newArrayList(
                  Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="),
                  Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs="))),
          Optional.empty(),
          RESTRICTED);

  private static final PrivateTransaction VALID_PRIVATE_TRANSACTION_PRIVACY_GROUP =
      new PrivateTransaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.of(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129639935")),
          SIGNATURE_ALGORITHM
              .get()
              .createSignature(
                  new BigInteger(
                      "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
                  new BigInteger(
                      "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
                  Byte.valueOf("0")),
          Bytes.fromHexString("0x"),
          Address.wrap(Bytes.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty(),
          Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="),
          Optional.empty(),
          Optional.of(Bytes.fromBase64String("DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w=")),
          RESTRICTED);

  private static final PrivateTransaction VALID_UNRESTRICTED_PRIVATE_TRANSACTION_PRIVACY_GROUP =
      new PrivateTransaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.of(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129639935")),
          SIGNATURE_ALGORITHM
              .get()
              .createSignature(
                  new BigInteger(
                      "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
                  new BigInteger(
                      "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
                  Byte.valueOf("0")),
          Bytes.fromHexString("0x"),
          Address.wrap(Bytes.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty(),
          Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="),
          Optional.empty(),
          Optional.of(Bytes.fromBase64String("DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w=")),
          UNRESTRICTED);

  private static final PrivateTransaction VALID_SIGNED_PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              Bytes.fromHexString(
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
          .sender(Address.wrap(Bytes.fromHexString("0x1c9a6e1ee3b7ac6028e786d9519ae3d24ee31e79")))
          .chainId(BigInteger.valueOf(4))
          .privateFrom(Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
          .privateFor(
              Lists.newArrayList(
                  Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")))
          .restriction(RESTRICTED)
          .signAndBuild(
              SIGNATURE_ALGORITHM
                  .get()
                  .createKeyPair(
                      SIGNATURE_ALGORITHM
                          .get()
                          .createPrivateKey(
                              new BigInteger(
                                  "853d7f0010fd86d0d7811c1f9d968ea89a24484a8127b4a483ddf5d2cfec766d",
                                  16))));

  private static final PrivateTransaction VALID_SIGNED_PRIVATE_TRANSACTION_LARGE_CHAINID =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              Bytes.fromHexString(
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
          .sender(Address.wrap(Bytes.fromHexString("0x1c9a6e1ee3b7ac6028e786d9519ae3d24ee31e79")))
          .chainId(BigInteger.valueOf(2147483647))
          .privateFrom(Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
          .privateFor(
              Lists.newArrayList(
                  Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")))
          .restriction(RESTRICTED)
          .signAndBuild(
              SIGNATURE_ALGORITHM
                  .get()
                  .createKeyPair(
                      SIGNATURE_ALGORITHM
                          .get()
                          .createPrivateKey(
                              new BigInteger(
                                  "853d7f0010fd86d0d7811c1f9d968ea89a24484a8127b4a483ddf5d2cfec766d",
                                  16))));

  @Test
  public void testWriteTo() {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    VALID_PRIVATE_TRANSACTION.writeTo(bvrlpo);
    assertThat(bvrlpo.encoded().toString()).isEqualTo(VALID_PRIVATE_TRANSACTION_RLP);
  }

  @Test
  public void testWriteTo_privacyGroup() {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    VALID_PRIVATE_TRANSACTION_PRIVACY_GROUP.writeTo(bvrlpo);
    assertThat(bvrlpo.encoded().toString()).isEqualTo(VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP);
  }

  @Test
  public void testWriteToWithLargeChainId() {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    VALID_SIGNED_PRIVATE_TRANSACTION_LARGE_CHAINID.writeTo(bvrlpo);
    assertThat(bvrlpo.encoded().toString())
        .isEqualTo(VALID_SIGNED_PRIVATE_TRANSACTION_LARGE_CHAINID_RLP);
  }

  @Test
  public void testSignedWriteTo() {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    VALID_SIGNED_PRIVATE_TRANSACTION.writeTo(bvrlpo);
    assertThat(bvrlpo.encoded().toString()).isEqualTo(VALID_SIGNED_PRIVATE_TRANSACTION_RLP);
  }

  @Test
  public void testReadFrom() {
    final PrivateTransaction p =
        PrivateTransaction.readFrom(
            new BytesValueRLPInput(Bytes.fromHexString(VALID_PRIVATE_TRANSACTION_RLP), false));

    assertThat(p).isEqualTo(VALID_PRIVATE_TRANSACTION);
  }

  @Test
  public void testReadFrom_privacyGroup() {
    final PrivateTransaction p =
        PrivateTransaction.readFrom(
            new BytesValueRLPInput(
                Bytes.fromHexString(VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP), false));

    assertThat(p).isEqualTo(VALID_PRIVATE_TRANSACTION_PRIVACY_GROUP);
  }

  @Test
  public void testSignedReadFrom() {
    final PrivateTransaction p =
        PrivateTransaction.readFrom(
            new BytesValueRLPInput(
                Bytes.fromHexString(VALID_SIGNED_PRIVATE_TRANSACTION_RLP), false));

    assertThat(p).isEqualTo(VALID_SIGNED_PRIVATE_TRANSACTION);
  }

  @Test
  public void testReadFromInvalid() {
    final BytesValueRLPInput input =
        new BytesValueRLPInput(Bytes.fromHexString(INVALID_RLP), false);
    assertThatThrownBy(() -> PrivateTransaction.readFrom(input)).isInstanceOf(RLPException.class);
  }

  @Test
  public void testReadFromWithLargeChainId() {
    final PrivateTransaction p =
        PrivateTransaction.readFrom(
            new BytesValueRLPInput(
                Bytes.fromHexString(VALID_SIGNED_PRIVATE_TRANSACTION_LARGE_CHAINID_RLP), false));

    assertThat(p).isEqualTo(VALID_SIGNED_PRIVATE_TRANSACTION_LARGE_CHAINID);
  }

  @Test
  public void testBuildInvalidPrivateTransactionThrowsException() {
    final PrivateTransaction.Builder privateTransactionBuilder =
        PrivateTransaction.builder()
            .nonce(0)
            .gasPrice(Wei.of(1000))
            .gasLimit(3000000)
            .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
            .value(Wei.ZERO)
            .payload(Bytes.fromHexString("0x"))
            .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
            .chainId(BigInteger.valueOf(1337))
            .privacyGroupId(Bytes.fromBase64String("DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w="))
            .privateFrom(Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
            .privateFor(
                Lists.newArrayList(
                    Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="),
                    Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")))
            .restriction(RESTRICTED);
    assertThatThrownBy(privateTransactionBuilder::build)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testRestrictionSerialization() {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    VALID_UNRESTRICTED_PRIVATE_TRANSACTION_PRIVACY_GROUP.writeTo(bvrlpo);

    final BytesValueRLPInput rlp = new BytesValueRLPInput(bvrlpo.encoded(), false);
    assertThat(PrivateTransaction.readFrom(rlp).getRestriction()).isEqualTo(UNRESTRICTED);
  }
}
