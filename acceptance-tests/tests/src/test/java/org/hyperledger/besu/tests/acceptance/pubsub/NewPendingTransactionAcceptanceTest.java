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
package org.hyperledger.besu.tests.acceptance.pubsub;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.pubsub.Subscription;
import org.hyperledger.besu.tests.acceptance.dsl.pubsub.WebSocket;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NewPendingTransactionAcceptanceTest extends AcceptanceTestBase {

  private Vertx vertx;
  private Account accountOne;
  private WebSocket minerWebSocket;
  private WebSocket archiveWebSocket;
  private BesuNode minerNode;
  private BesuNode archiveNode;

  @BeforeEach
  public void setUp() throws Exception {
    vertx = Vertx.vertx();
    minerNode = besu.createMinerNode("miner-node1");
    archiveNode = besu.createArchiveNode("full-node1");
    cluster.start(minerNode, archiveNode);

    // verify nodes are fully connected otherwise tx could not be propagated
    minerNode.verify(net.awaitPeerCount(1));
    archiveNode.verify(net.awaitPeerCount(1));

    accountOne = accounts.createAccount("account-one");
    minerWebSocket = new WebSocket(vertx, minerNode.getConfiguration());
    archiveWebSocket = new WebSocket(vertx, archiveNode.getConfiguration());

    // verify that the miner started producing blocks and all other nodes are syncing from it
    waitForBlockHeight(minerNode, 1);
    final var minerChainHead = minerNode.execute(ethTransactions.block());
    archiveNode.verify(blockchain.minimumHeight(minerChainHead.getNumber().longValue()));
  }

  @AfterEach
  public void tearDown() {
    vertx.close();
  }

  @Test
  public void subscriptionToMinerNodeMustReceivePublishEvent() {
    final Subscription minerSubscription = minerWebSocket.subscribe();

    final Hash event = minerNode.execute(accountTransactions.createTransfer(accountOne, 4));
    cluster.verify(accountOne.balanceEquals(4));

    minerWebSocket.verifyTotalEventsReceived(1);
    minerSubscription.verifyEventReceived(event);

    minerWebSocket.unsubscribe(minerSubscription);
  }

  @Test
  public void subscriptionToArchiveNodeMustReceivePublishEvent() {
    final Subscription archiveSubscription = archiveWebSocket.subscribe();

    final Hash event = minerNode.execute(accountTransactions.createTransfer(accountOne, 23));
    cluster.verify(accountOne.balanceEquals(23));

    archiveWebSocket.verifyTotalEventsReceived(1);
    archiveSubscription.verifyEventReceived(event);

    archiveWebSocket.unsubscribe(archiveSubscription);
  }

  @Test
  public void everySubscriptionMustReceivePublishEvent() {
    final Subscription minerSubscriptionOne = minerWebSocket.subscribe();
    final Subscription minerSubscriptionTwo = minerWebSocket.subscribe();
    final Subscription archiveSubscriptionOne = archiveWebSocket.subscribe();
    final Subscription archiveSubscriptionTwo = archiveWebSocket.subscribe();
    final Subscription archiveSubscriptionThree = archiveWebSocket.subscribe();

    final Hash event = minerNode.execute(accountTransactions.createTransfer(accountOne, 30));
    cluster.verify(accountOne.balanceEquals(30));

    minerWebSocket.verifyTotalEventsReceived(2);
    minerSubscriptionOne.verifyEventReceived(event);
    minerSubscriptionTwo.verifyEventReceived(event);

    archiveWebSocket.verifyTotalEventsReceived(3);
    archiveSubscriptionOne.verifyEventReceived(event);
    archiveSubscriptionTwo.verifyEventReceived(event);
    archiveSubscriptionThree.verifyEventReceived(event);

    minerWebSocket.unsubscribe(minerSubscriptionOne);
    minerWebSocket.unsubscribe(minerSubscriptionTwo);
    archiveWebSocket.unsubscribe(archiveSubscriptionOne);
    archiveWebSocket.unsubscribe(archiveSubscriptionTwo);
    archiveWebSocket.unsubscribe(archiveSubscriptionThree);
  }

  @Test
  @Disabled("test is flaky - sometimes returns more than 3 total events #6909")
  public void subscriptionToMinerNodeMustReceiveEveryPublishEvent() {
    final Subscription minerSubscription = minerWebSocket.subscribe();

    final Hash eventOne = minerNode.execute(accountTransactions.createTransfer(accountOne, 1));
    cluster.verify(accountOne.balanceEquals(1));

    minerWebSocket.verifyTotalEventsReceived(1);
    minerSubscription.verifyEventReceived(eventOne);

    final Hash eventTwo = minerNode.execute(accountTransactions.createTransfer(accountOne, 4));
    final Hash eventThree = minerNode.execute(accountTransactions.createTransfer(accountOne, 5));
    cluster.verify(accountOne.balanceEquals(1 + 4 + 5));

    minerWebSocket.verifyTotalEventsReceived(3);
    minerSubscription.verifyEventReceived(eventTwo);
    minerSubscription.verifyEventReceived(eventThree);

    minerWebSocket.unsubscribe(minerSubscription);
  }

  @Test
  public void subscriptionToArchiveNodeMustReceiveEveryPublishEvent() {
    final Subscription archiveSubscription = archiveWebSocket.subscribe();

    final Hash eventOne = minerNode.execute(accountTransactions.createTransfer(accountOne, 2));
    final Hash eventTwo = minerNode.execute(accountTransactions.createTransfer(accountOne, 5));
    cluster.verify(accountOne.balanceEquals(2 + 5));

    archiveWebSocket.verifyTotalEventsReceived(2);
    archiveSubscription.verifyEventReceived(eventOne);
    archiveSubscription.verifyEventReceived(eventTwo);

    final Hash eventThree = minerNode.execute(accountTransactions.createTransfer(accountOne, 8));
    cluster.verify(accountOne.balanceEquals(2 + 5 + 8));

    archiveWebSocket.verifyTotalEventsReceived(3);
    archiveSubscription.verifyEventReceived(eventThree);

    archiveWebSocket.unsubscribe(archiveSubscription);
  }

  @Test
  @Disabled("This test is flaky and needs to be fixed")
  public void everySubscriptionMustReceiveEveryPublishEvent() {
    final Subscription minerSubscriptionOne = minerWebSocket.subscribe();
    final Subscription minerSubscriptionTwo = minerWebSocket.subscribe();
    final Subscription archiveSubscriptionOne = archiveWebSocket.subscribe();
    final Subscription archiveSubscriptionTwo = archiveWebSocket.subscribe();
    final Subscription archiveSubscriptionThree = archiveWebSocket.subscribe();

    final Hash eventOne = minerNode.execute(accountTransactions.createTransfer(accountOne, 10));
    final Hash eventTwo = minerNode.execute(accountTransactions.createTransfer(accountOne, 5));
    cluster.verify(accountOne.balanceEquals(10 + 5));

    minerWebSocket.verifyTotalEventsReceived(4);
    minerSubscriptionOne.verifyEventReceived(eventOne);
    minerSubscriptionOne.verifyEventReceived(eventTwo);
    minerSubscriptionTwo.verifyEventReceived(eventOne);
    minerSubscriptionTwo.verifyEventReceived(eventTwo);

    archiveWebSocket.verifyTotalEventsReceived(6);
    archiveSubscriptionOne.verifyEventReceived(eventOne);
    archiveSubscriptionOne.verifyEventReceived(eventTwo);
    archiveSubscriptionTwo.verifyEventReceived(eventOne);
    archiveSubscriptionTwo.verifyEventReceived(eventTwo);
    archiveSubscriptionThree.verifyEventReceived(eventOne);
    archiveSubscriptionThree.verifyEventReceived(eventTwo);

    minerWebSocket.unsubscribe(minerSubscriptionOne);
    minerWebSocket.unsubscribe(minerSubscriptionTwo);
    archiveWebSocket.unsubscribe(archiveSubscriptionOne);
    archiveWebSocket.unsubscribe(archiveSubscriptionTwo);
    archiveWebSocket.unsubscribe(archiveSubscriptionThree);
  }
}
