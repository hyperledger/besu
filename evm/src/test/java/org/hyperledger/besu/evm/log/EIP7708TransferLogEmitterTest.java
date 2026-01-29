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
import static org.hyperledger.besu.evm.log.EIP7708TransferLogEmitter.SELFDESTRUCT_TOPIC;
import static org.hyperledger.besu.evm.log.EIP7708TransferLogEmitter.TRANSFER_TOPIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

  @Test
  void selfdestructTopicIsCorrect() {
    // keccak256('Selfdestruct(address,uint256)')
    assertThat(SELFDESTRUCT_TOPIC)
        .isEqualTo(
            Bytes32.fromHexString(
                "0x4bfaba3443c1a1836cd362418edc679fc96cae8449cbefccb6457cdf2c943083"));
  }

  @Test
  void createSelfdestructLogHasCorrectStructure() {
    final Wei value = Wei.of(1000);
    final Log log = EIP7708TransferLogEmitter.createSelfdestructLog(SENDER, value);

    // Logger should be the system address
    assertThat(log.getLogger()).isEqualTo(EIP7708_SYSTEM_ADDRESS);

    // Should have 2 topics (LOG2)
    final List<LogTopic> topics = log.getTopics();
    assertThat(topics).hasSize(2);

    // First topic is the selfdestruct event signature
    assertThat(topics.get(0)).isEqualTo(LogTopic.create(SELFDESTRUCT_TOPIC));

    // Second topic is the closed address (zero-padded to 32 bytes)
    assertThat(topics.get(1)).isEqualTo(LogTopic.create(Bytes32.leftPad(SENDER.getBytes())));

    // Data should be the value as 32-byte big-endian
    assertThat(log.getData()).isEqualTo(Bytes32.leftPad(value));
  }

  @Test
  void emitSelfDestructLogEmitsSelfdestructLogWhenOriginatorEqualsBeneficiary() {
    final MessageFrame frame = mock(MessageFrame.class);
    final Wei value = Wei.of(1000);

    EIP7708TransferLogEmitter.INSTANCE.emitSelfDestructLog(frame, SENDER, SENDER, value);

    final ArgumentCaptor<Log> logCaptor = ArgumentCaptor.forClass(Log.class);
    verify(frame).addLog(logCaptor.capture());

    final Log capturedLog = logCaptor.getValue();
    // Should be a selfdestruct log (2 topics)
    assertThat(capturedLog.getTopics()).hasSize(2);
    assertThat(capturedLog.getTopics().get(0)).isEqualTo(LogTopic.create(SELFDESTRUCT_TOPIC));
  }

  @Test
  void emitSelfDestructLogEmitsTransferLogWhenOriginatorDifferentFromBeneficiary() {
    final MessageFrame frame = mock(MessageFrame.class);
    final Wei value = Wei.of(1000);

    EIP7708TransferLogEmitter.INSTANCE.emitSelfDestructLog(frame, SENDER, RECIPIENT, value);

    final ArgumentCaptor<Log> logCaptor = ArgumentCaptor.forClass(Log.class);
    verify(frame).addLog(logCaptor.capture());

    final Log capturedLog = logCaptor.getValue();
    // Should be a transfer log (3 topics)
    assertThat(capturedLog.getTopics()).hasSize(3);
    assertThat(capturedLog.getTopics().get(0)).isEqualTo(LogTopic.create(TRANSFER_TOPIC));
  }

  @Test
  void emitSelfDestructLogDoesNotEmitForZeroValue() {
    final MessageFrame frame = mock(MessageFrame.class);

    EIP7708TransferLogEmitter.INSTANCE.emitSelfDestructLog(frame, SENDER, RECIPIENT, Wei.ZERO);

    verify(frame, never()).addLog(any());
  }

  @Test
  void emitClosureLogsEmitsLogsInLexicographicalOrder() {
    final WorldUpdater worldUpdater = mock(WorldUpdater.class);
    final Account account1 = mock(Account.class);
    final Account account2 = mock(Account.class);
    final Account account3 = mock(Account.class);

    final Address addr1 = Address.fromHexString("0x3333333333333333333333333333333333333333");
    final Address addr2 = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final Address addr3 = Address.fromHexString("0x2222222222222222222222222222222222222222");

    when(worldUpdater.get(addr1)).thenReturn(account1);
    when(worldUpdater.get(addr2)).thenReturn(account2);
    when(worldUpdater.get(addr3)).thenReturn(account3);

    when(account1.getBalance()).thenReturn(Wei.of(300));
    when(account2.getBalance()).thenReturn(Wei.of(100));
    when(account3.getBalance()).thenReturn(Wei.of(200));

    final List<Log> collectedLogs = new ArrayList<>();
    EIP7708TransferLogEmitter.INSTANCE.emitClosureLogs(
        worldUpdater, Set.of(addr1, addr2, addr3), collectedLogs::add);

    // Should have 3 logs in lexicographical order by address
    assertThat(collectedLogs).hasSize(3);

    // addr2 (0x1111...) should be first
    assertThat(collectedLogs.get(0).getTopics().get(1))
        .isEqualTo(LogTopic.create(Bytes32.leftPad(addr2.getBytes())));
    assertThat(collectedLogs.get(0).getData()).isEqualTo(Bytes32.leftPad(Wei.of(100)));

    // addr3 (0x2222...) should be second
    assertThat(collectedLogs.get(1).getTopics().get(1))
        .isEqualTo(LogTopic.create(Bytes32.leftPad(addr3.getBytes())));
    assertThat(collectedLogs.get(1).getData()).isEqualTo(Bytes32.leftPad(Wei.of(200)));

    // addr1 (0x3333...) should be third
    assertThat(collectedLogs.get(2).getTopics().get(1))
        .isEqualTo(LogTopic.create(Bytes32.leftPad(addr1.getBytes())));
    assertThat(collectedLogs.get(2).getData()).isEqualTo(Bytes32.leftPad(Wei.of(300)));
  }

  @Test
  void emitClosureLogsSkipsZeroBalanceAccounts() {
    final WorldUpdater worldUpdater = mock(WorldUpdater.class);
    final Account account1 = mock(Account.class);
    final Account account2 = mock(Account.class);

    final Address addrWithBalance =
        Address.fromHexString("0x1111111111111111111111111111111111111111");
    final Address addrZeroBalance =
        Address.fromHexString("0x2222222222222222222222222222222222222222");

    when(worldUpdater.get(addrWithBalance)).thenReturn(account1);
    when(worldUpdater.get(addrZeroBalance)).thenReturn(account2);

    when(account1.getBalance()).thenReturn(Wei.of(100));
    when(account2.getBalance()).thenReturn(Wei.ZERO);

    final List<Log> collectedLogs = new ArrayList<>();
    EIP7708TransferLogEmitter.INSTANCE.emitClosureLogs(
        worldUpdater, Set.of(addrWithBalance, addrZeroBalance), collectedLogs::add);

    // Should only have 1 log (for the account with balance)
    assertThat(collectedLogs).hasSize(1);
    assertThat(collectedLogs.get(0).getTopics().get(1))
        .isEqualTo(LogTopic.create(Bytes32.leftPad(addrWithBalance.getBytes())));
  }

  @Test
  void emitClosureLogsSkipsNonExistentAccounts() {
    final WorldUpdater worldUpdater = mock(WorldUpdater.class);
    final Account existingAccount = mock(Account.class);

    final Address existingAddr =
        Address.fromHexString("0x1111111111111111111111111111111111111111");
    final Address nonExistentAddr =
        Address.fromHexString("0x2222222222222222222222222222222222222222");

    when(worldUpdater.get(existingAddr)).thenReturn(existingAccount);
    when(worldUpdater.get(nonExistentAddr)).thenReturn(null);

    when(existingAccount.getBalance()).thenReturn(Wei.of(100));

    final List<Log> collectedLogs = new ArrayList<>();
    EIP7708TransferLogEmitter.INSTANCE.emitClosureLogs(
        worldUpdater, Set.of(existingAddr, nonExistentAddr), collectedLogs::add);

    // Should only have 1 log (for the existing account)
    assertThat(collectedLogs).hasSize(1);
    assertThat(collectedLogs.get(0).getTopics().get(1))
        .isEqualTo(LogTopic.create(Bytes32.leftPad(existingAddr.getBytes())));
  }
}
