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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.mainnet.requests.InvalidDepositLogLayoutException;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class DepositLogDecoderTest {

  static final Bytes VALID_DEPOSIT_LOG =
      Bytes.fromHexString(
          "0x00000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000018000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030b10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200017a7fcf06faf493d30bbe2632ea7c2383cd86825e12797165de7aa35589483000000000000000000000000000000000000000000000000000000000000000800405973070000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000060a889db8300194050a2636c92a95bc7160515867614b7971a9500cdb62f9c0890217d2901c3241f86fac029428fc106930606154bd9e406d7588934a5f15b837180b17194d6e44bd6de23e43b163dfe12e369dcc75a3852cd997963f158217eb500000000000000000000000000000000000000000000000000000000000000083f3d080000000000000000000000000000000000000000000000000000000000");
  public static final Address LOGGER_ADDRESS =
      Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");
  public static final LogTopic LOG_TOPIC =
      LogTopic.fromHexString("0x649bbc62d0e31342afea4e5cd82d4049e7e1ee912fc0889aa790803be39038c5");

  @Test
  void shouldDecodeDepositFromLog() {
    final List<LogTopic> topics = List.of(LOG_TOPIC);

    final Log log = new Log(LOGGER_ADDRESS, VALID_DEPOSIT_LOG, topics);
    final Bytes requestData = DepositLogDecoder.decodeFromLog(log);

    final Bytes expectedDepositRequestData =
        Bytes.fromHexString(
            "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e0017a7fcf06faf493d30bbe2632ea7c2383cd86825e12797165de7aa355894830040597307000000a889db8300194050a2636c92a95bc7160515867614b7971a9500cdb62f9c0890217d2901c3241f86fac029428fc106930606154bd9e406d7588934a5f15b837180b17194d6e44bd6de23e43b163dfe12e369dcc75a3852cd997963f158217eb53f3d080000000000");

    assertThat(requestData).isEqualTo(expectedDepositRequestData);
  }

  @Test
  void shouldDecodeSepoliaDepositFromLog() {
    final List<LogTopic> topics = List.of(LOG_TOPIC);
    final Bytes data =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000180000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000800000c3d5d53aa01000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000");

    final Log log = new Log(LOGGER_ADDRESS, data, topics);
    final Bytes requestData = DepositLogDecoder.decodeFromLog(log);

    final Bytes expectedDepositRequestData =
        Bytes.fromHexString(
            "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000200000c3d5d53aa010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000030000000000000000");

    assertThat(requestData).isEqualTo(expectedDepositRequestData);
  }

  @Test
  void shouldThrowExceptionIfLogLengthIsTooBig() {
    final Bytes data = Bytes.fromHexString("0x" + "00".repeat(577)); // Incorrect length
    assertException(data, "Invalid deposit log length. Must be 576 bytes, but is 577 bytes");
  }

  @Test
  void shouldThrowExceptionIfLogLengthIsTooSmall() {
    final Bytes data = Bytes.fromHexString("0x00"); // Incorrect length
    assertException(data, "Invalid deposit log length. Must be 576 bytes, but is 1 bytes");
  }

  @Test
  void shouldThrowExceptionIfPubKeyOffsetIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(0);
    assertException(data, "Invalid pubKey offset: expected 160, but got 1");
  }

  @Test
  void shouldThrowExceptionIfWithdrawalCredOffsetIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(32);
    assertException(data, "Invalid withdrawalCred offset: expected 256, but got 1");
  }

  @Test
  void shouldThrowExceptionIfAmountOffsetIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(64);
    assertException(data, "Invalid amount offset: expected 320, but got 1");
  }

  @Test
  void shouldThrowExceptionIfSignatureOffsetIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(96);
    assertException(data, "Invalid signature offset: expected 384, but got 1");
  }

  @Test
  void shouldThrowExceptionIfIndexOffsetIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(128);
    assertException(data, "Invalid index offset: expected 512, but got 1");
  }

  @Test
  void shouldThrowExceptionIfPubKeySizeIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(160);
    assertException(data, "Invalid pubKey size: expected 48, but got 1");
  }

  @Test
  void shouldThrowExceptionIfWithdrawalCredSizeIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(256);
    assertException(data, "Invalid withdrawalCred size: expected 32, but got 1");
  }

  @Test
  void shouldThrowExceptionIfAmountSizeIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(320);
    assertException(data, "Invalid amount size: expected 8, but got 1");
  }

  @Test
  void shouldThrowExceptionIfSignatureSizeIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(384);
    assertException(data, "Invalid signature size: expected 96, but got 1");
  }

  @Test
  void shouldThrowExceptionIfIndexSizeIsIncorrect() {
    final Bytes data = createDataWithIncorrectData(512);
    assertException(data, "Invalid index size: expected 8, but got 1");
  }

  private Bytes createDataWithIncorrectData(final int position) {
    return Bytes.concatenate(
        VALID_DEPOSIT_LOG.slice(0, position),
        Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001"),
        VALID_DEPOSIT_LOG.slice(position + 32));
  }

  private void assertException(final Bytes data, final String message) {
    final List<LogTopic> topics = List.of(LOG_TOPIC);
    final Log log = new Log(LOGGER_ADDRESS, data, topics);
    assertThatThrownBy(() -> DepositLogDecoder.decodeFromLog(log))
        .isInstanceOf(InvalidDepositLogLayoutException.class)
        .hasMessageContaining(message);
  }
}
