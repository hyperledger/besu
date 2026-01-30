/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.log.EIP7708TransferLogEmitter.EIP7708_SYSTEM_ADDRESS;
import static org.hyperledger.besu.evm.log.EIP7708TransferLogEmitter.TRANSFER_TOPIC;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class EIP7708TransferLogEmitterTest {

  private static final Address SENDER =
      Address.fromHexString("0x1111111111111111111111111111111111111111");
  private static final Address RECIPIENT =
      Address.fromHexString("0x2222222222222222222222222222222222222222");

  @Test
  void systemAddressIsCorrect() {
    assertThat(EIP7708_SYSTEM_ADDRESS)
        .isEqualTo(Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe"));
  }

  @Test
  void transferTopicIsCorrect() {
    // keccak256('Transfer(address,address,uint256)')
    assertThat(TRANSFER_TOPIC)
        .isEqualTo(
            Bytes32.fromHexString(
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"));
  }

  @Test
  void createTransferLogHasCorrectStructure() {
    final Wei value = Wei.of(1000);
    final Log log = EIP7708TransferLogEmitter.createTransferLog(SENDER, RECIPIENT, value);

    // Logger should be the system address
    assertThat(log.getLogger()).isEqualTo(EIP7708_SYSTEM_ADDRESS);

    // Should have 3 topics
    final List<LogTopic> topics = log.getTopics();
    assertThat(topics).hasSize(3);

    // First topic is the transfer event signature
    assertThat(topics.get(0)).isEqualTo(LogTopic.create(TRANSFER_TOPIC));

    // Second topic is the sender address (zero-padded to 32 bytes)
    assertThat(topics.get(1)).isEqualTo(LogTopic.create(Bytes32.leftPad(SENDER.getBytes())));

    // Third topic is the recipient address (zero-padded to 32 bytes)
    assertThat(topics.get(2)).isEqualTo(LogTopic.create(Bytes32.leftPad(RECIPIENT.getBytes())));

    // Data should be the value as 32-byte big-endian
    assertThat(log.getData()).isEqualTo(Bytes32.leftPad(value));
  }

  @Test
  void createTransferLogWithLargeValue() {
    // Test with a large value (1 ETH = 10^18 Wei)
    final Wei oneEth = Wei.of(new BigInteger("1000000000000000000"));
    final Log log = EIP7708TransferLogEmitter.createTransferLog(SENDER, RECIPIENT, oneEth);

    assertThat(log.getLogger()).isEqualTo(EIP7708_SYSTEM_ADDRESS);
    assertThat(log.getData()).isEqualTo(Bytes32.leftPad(oneEth));
  }

  @Test
  void createTransferLogWithZeroAddress() {
    final Address zeroAddress = Address.ZERO;
    final Wei value = Wei.of(100);
    final Log log = EIP7708TransferLogEmitter.createTransferLog(zeroAddress, RECIPIENT, value);

    assertThat(log.getTopics().get(1)).isEqualTo(LogTopic.create(Bytes32.ZERO));
  }

  @Test
  void createTransferLogWithSmallValue() {
    final Wei smallValue = Wei.ONE;
    final Log log = EIP7708TransferLogEmitter.createTransferLog(SENDER, RECIPIENT, smallValue);

    // Data should be 32 bytes with value 1 in the last byte
    final Bytes32 expectedData =
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
    assertThat(log.getData()).isEqualTo(expectedData);
  }
}
