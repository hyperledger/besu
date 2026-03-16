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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.eth.encoding.TransactionAnnouncementDecoder.getDecoder;
import static org.hyperledger.besu.ethereum.eth.encoding.TransactionAnnouncementEncoder.getEncoder;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.encoding.TransactionAnnouncementDecoder;
import org.hyperledger.besu.ethereum.eth.encoding.TransactionAnnouncementEncoder;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.eth.transactions.NewPooledTransactionHashesMessageProcessor.FetcherCreatorTask;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewPooledTransactionHashesMessageProcessorTest {

  @Mock private TransactionPool transactionPool;

  @Mock(answer = RETURNS_DEEP_STUBS)
  private TransactionPoolConfiguration transactionPoolConfiguration;

  @Mock private PeerTransactionTracker transactionTracker;
  @Mock private EthPeer peer1;
  @Mock private EthContext ethContext;
  @Mock private EthScheduler ethScheduler;

  private final BlockDataGenerator generator = new BlockDataGenerator();

  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();
  private final Hash hash1 = transaction1.getHash();
  private final Hash hash2 = transaction2.getHash();
  private final Hash hash3 = transaction3.getHash();
  private final List<Transaction> transactionList =
      List.of(transaction1, transaction2, transaction3);
  private NewPooledTransactionHashesMessageProcessor messageHandler;
  private StubMetricsSystem metricsSystem;

  @BeforeEach
  public void setup() {
    metricsSystem = new StubMetricsSystem();
    when(transactionPoolConfiguration.getUnstable().getEth65TrxAnnouncedBufferingPeriod())
        .thenReturn(Duration.ofMillis(500));
    messageHandler =
        new NewPooledTransactionHashesMessageProcessor(
            transactionTracker,
            transactionPool,
            transactionPoolConfiguration,
            ethContext,
            new TransactionPoolMetrics(metricsSystem),
            EthProtocolConfiguration.DEFAULT_MAX_TRANSACTIONS_MESSAGE_SIZE);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
  }

  @Test
  void shouldAddInitiatedRequestingTransactions() {

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(transactionList, EthProtocol.LATEST),
        now(),
        ofMinutes(1));

    verify(transactionPool).getTransactionByHash(hash1);
    verify(transactionPool).getTransactionByHash(hash2);
    verify(transactionPool).getTransactionByHash(hash3);
    verifyNoMoreInteractions(transactionPool);
  }

  @Test
  void shouldNotAddAlreadyPresentTransactions() {

    when(transactionPool.getTransactionByHash(hash1)).thenReturn(Optional.of(transaction1));
    when(transactionPool.getTransactionByHash(hash2)).thenReturn(Optional.of(transaction2));

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(transactionList, EthProtocol.LATEST),
        now(),
        ofMinutes(1));

    verify(transactionPool).getTransactionByHash(hash1);
    verify(transactionPool).getTransactionByHash(hash2);
    verify(transactionPool).getTransactionByHash(hash3);
    verifyNoMoreInteractions(transactionPool);
  }

  @Test
  void shouldAddInitiatedRequestingTransactionsWhenOutOfSync() {

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(transactionList, EthProtocol.LATEST),
        now(),
        ofMinutes(1));
    verify(transactionPool, times(3)).getTransactionByHash(any());
  }

  @Test
  void shouldNotMarkReceivedExpiredTransactionsAsSeen() {
    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(transactionList, EthProtocol.LATEST),
        now().minus(ofMinutes(1)),
        ofMillis(1));
    verifyNoInteractions(transactionTracker);
    assertThat(
            metricsSystem.getCounterValue(
                TransactionPoolMetrics.EXPIRED_MESSAGES_COUNTER_NAME,
                NewPooledTransactionHashesMessageProcessor.METRIC_LABEL))
        .isEqualTo(1);
  }

  @Test
  void shouldNotAddReceivedTransactionsToTransactionPoolIfExpired() {
    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(transactionList, EthProtocol.LATEST),
        now().minus(ofMinutes(1)),
        ofMillis(1));
    verifyNoInteractions(transactionPool);
    assertThat(
            metricsSystem.getCounterValue(
                TransactionPoolMetrics.EXPIRED_MESSAGES_COUNTER_NAME,
                NewPooledTransactionHashesMessageProcessor.METRIC_LABEL))
        .isEqualTo(1);
  }

  @Test
  void shouldScheduleGetPooledTransactionsTaskWhenNewTransactionAddedFromPeerForTheFirstTime() {

    final EthScheduler ethScheduler = mock(EthScheduler.class);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(
            List.of(transaction1, transaction2), EthProtocol.LATEST),
        now(),
        ofMinutes(1));

    verify(ethScheduler, times(1))
        .scheduleFutureTaskWithFixedDelay(
            any(FetcherCreatorTask.class), any(Duration.class), any(Duration.class));
  }

  @Test
  void shouldNotScheduleGetPooledTransactionsTaskTwice() {

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(
            Collections.singletonList(transaction1), EthProtocol.LATEST),
        now(),
        ofMinutes(1));

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(
            Collections.singletonList(transaction2), EthProtocol.LATEST),
        now(),
        ofMinutes(1));

    verify(ethScheduler, times(1))
        .scheduleFutureTaskWithFixedDelay(
            any(FetcherCreatorTask.class), any(Duration.class), any(Duration.class));
  }

  @Test
  void shouldCreateAndDecodeForEth68() {
    final List<TransactionAnnouncement> expectedTransactions =
        transactionList.stream().map(TransactionAnnouncement::new).collect(Collectors.toList());

    final NewPooledTransactionHashesMessage message =
        NewPooledTransactionHashesMessage.create(transactionList, EthProtocol.ETH68);

    final List<TransactionAnnouncement> announcementList =
        message.pendingTransactionAnnouncements();
    assertThat(announcementList).containsExactlyElementsOf(expectedTransactions);
  }

  @Test
  void shouldEncodeTransactionsCorrectly_Eth68() {

    final String expected =
        "0xf86d83000102c3010203f863a00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002a00000000000000000000000000000000000000000000000000000000000000003";
    final List<Hash> hashes =
        List.of(
            Hash.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            Hash.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"),
            Hash.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000003"));
    final List<Integer> sizes = List.of(1, 2, 3);
    final List<TransactionType> types =
        List.of(TransactionType.FRONTIER, TransactionType.ACCESS_LIST, TransactionType.EIP1559);

    final Bytes bytes = TransactionAnnouncementEncoder.encodeForEth68(types, sizes, hashes);
    assertThat(expected).isEqualTo(bytes.toHexString());
  }

  @Test
  void shouldDecodeBytesCorrectly_Eth68() {
    /*
     * [
     * "0x0000102"]
     * ["0x01","0x02","0x03"],
     * ["0x0000000000000000000000000000000000000000000000000000000000000001",
     *  "0x0000000000000000000000000000000000000000000000000000000000000002",
     *  "0x0000000000000000000000000000000000000000000000000000000000000003"]
     * ]
     */

    final Bytes bytes =
        Bytes.fromHexString(
            "0xf86d83000102c3010203f863a00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002a00000000000000000000000000000000000000000000000000000000000000003");

    final List<TransactionAnnouncement> announcementList =
        getDecoder(EthProtocol.ETH68).decode(RLP.input(bytes));

    final TransactionAnnouncement frontier = announcementList.get(0);
    assertThat(frontier.hash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"));
    assertThat(frontier.type()).isEqualTo(TransactionType.FRONTIER);
    assertThat(frontier.size()).isEqualTo(1L);

    final TransactionAnnouncement accessList = announcementList.get(1);
    assertThat(accessList.hash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"));
    assertThat(accessList.type()).isEqualTo(TransactionType.ACCESS_LIST);
    assertThat(accessList.size()).isEqualTo(2L);

    final TransactionAnnouncement eip1559 = announcementList.get(2);
    assertThat(eip1559.hash())
        .isEqualTo(
            Hash.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000003"));
    assertThat(eip1559.type()).isEqualTo(TransactionType.EIP1559);
    assertThat(eip1559.size()).isEqualTo(3L);
  }

  @Test
  void shouldEncodeAndDecodeTransactionAnnouncement_Eth68() {
    final Transaction t1 = generator.transaction(TransactionType.FRONTIER);
    final Transaction t2 = generator.transaction(TransactionType.ACCESS_LIST);
    final Transaction t3 = generator.transaction(TransactionType.EIP1559);

    final List<Transaction> list = List.of(t1, t2, t3);
    final Bytes bytes = getEncoder(EthProtocol.ETH68).encode(list);

    final List<TransactionAnnouncement> announcementList =
        getDecoder(EthProtocol.ETH68).decode(RLP.input(bytes));

    assertThat(announcementList).hasSameSizeAs(list);

    for (final Transaction transaction : list) {
      final TransactionAnnouncement announcement = announcementList.get(list.indexOf(transaction));
      assertThat(announcement.hash()).isEqualTo(transaction.getHash());
      assertThat(announcement.type()).isEqualTo(transaction.getType());
      assertThat(announcement.size()).isEqualTo(transaction.getSizeForAnnouncement());
    }
  }

  @Test
  void shouldThrowInvalidArgumentExceptionWhenCreatingListsWithDifferentSizes() {
    assertThatThrownBy(
            () -> TransactionAnnouncement.create(new ArrayList<>(), List.of(1L), new ArrayList<>()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Hashes, sizes and types must have the same number of elements");
  }

  @Test
  void shouldThrowInvalidArgumentExceptionWhenEncodingListsWithDifferentSizes() {
    assertThatThrownBy(
            () ->
                TransactionAnnouncementEncoder.encodeForEth68(
                    new ArrayList<>(), List.of(1), new ArrayList<>()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Hashes, sizes and types must have the same number of elements");
  }

  @Test
  @SuppressWarnings("UnusedVariable")
  void shouldThrowRLPExceptionWhenDecodingListsWithDifferentSizes() {

    // ["0x000102",[],["0x881699519a25b0e32db9b1ba9981f3fbec93fbc0726c3e096af89e5ada2b1351"]]
    final Bytes invalidMessageBytes =
        Bytes.fromHexString(
            "0xe783000102c0e1a0881699519a25b0e32db9b1ba9981f3fbec93fbc0726c3e096af89e5ada2b1351");

    assertThatThrownBy(
            () ->
                TransactionAnnouncementDecoder.getDecoder(EthProtocol.ETH68)
                    .decode(RLP.input(invalidMessageBytes)))
        .isInstanceOf(RLPException.class)
        .hasMessage("Hashes, sizes and types must have the same number of elements");
  }

  @Test
  void shouldThrowRLPExceptionWhenTypeIsInvalid() {
    final Bytes invalidMessageBytes =
        Bytes.fromHexString(
            // ["0x07",["0x00000002"],["0x881699519a25b0e32db9b1ba9981f3fbec93fbc0726c3e096af89e5ada2b1351"]]
            "0xe907c58400000002e1a0881699519a25b0e32db9b1ba9981f3fbec93fbc0726c3e096af89e5ada2b1351");

    assertThatThrownBy(
            () ->
                TransactionAnnouncementDecoder.getDecoder(EthProtocol.ETH68)
                    .decode(RLP.input(invalidMessageBytes)))
        .isInstanceOf(RLPException.class)
        .hasMessageContaining("Invalid transaction type 0x07");
  }

  @Test
  void shouldThrowRLPExceptionWhenSizeSizeGreaterThanFourBytes() {
    final Bytes invalidMessageBytes =
        Bytes.fromHexString(
            // ["0x02",["0xffffffff01"],["0x881699519a25b0e32db9b1ba9981f3fbec93fbc0726c3e096af89e5ada2b1351"]]
            "0xea02c685ffffffff00e1a0881699519a25b0e32db9b1ba9981f3fbec93fbc0726c3e096af89e5ada2b1351");

    assertThatThrownBy(
            () ->
                TransactionAnnouncementDecoder.getDecoder(EthProtocol.ETH68)
                    .decode(RLP.input(invalidMessageBytes)))
        .isInstanceOf(RLPException.class)
        .hasMessageContaining(
            "Cannot read a unsigned int scalar, expecting a maximum of 4 bytes but current element is 5 bytes long");
  }

  @Test
  void shouldThrowNullPointerIfArgumentsAreNull() {
    final Hash hash = Hash.hash(Bytes.random(32));

    assertThatThrownBy(() -> new TransactionAnnouncement(null, TransactionType.EIP1559, 0L))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Hash cannot be null");

    assertThatThrownBy(() -> new TransactionAnnouncement(hash, null, 0L))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Type cannot be null");

    assertThatThrownBy(() -> new TransactionAnnouncement(hash, TransactionType.EIP1559, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Size cannot be null");

    assertThatThrownBy(() -> new TransactionAnnouncement(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Transaction cannot be null");
  }

  @Test
  void shouldDisconnectPeerWhenInvalidTransactionType() {
    final Bytes invalidMessageBytes =
        Bytes.fromHexString(
            // ["0x07",["0x00000002"],["0x881699519a25b0e32db9b1ba9981f3fbec93fbc0726c3e096af89e5ada2b1351"]]
            "0xe907c58400000002e1a0881699519a25b0e32db9b1ba9981f3fbec93fbc0726c3e096af89e5ada2b1351");

    final NewPooledTransactionHashesMessage message =
        new NewPooledTransactionHashesMessage(invalidMessageBytes, EthProtocol.ETH68);

    messageHandler.processNewPooledTransactionHashesMessage(peer1, message, now(), ofMinutes(1));

    verify(peer1).disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
  }
}
