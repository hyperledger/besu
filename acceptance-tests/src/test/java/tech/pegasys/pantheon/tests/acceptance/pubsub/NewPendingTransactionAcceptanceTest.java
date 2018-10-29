/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.pubsub;

import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonMinerNode;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonNode;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.pubsub.Subscription;
import tech.pegasys.pantheon.tests.acceptance.dsl.pubsub.WebSocket;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class NewPendingTransactionAcceptanceTest extends AcceptanceTestBase {

  private Vertx vertx;
  private Account accountOne;
  private WebSocket minerWebSocket;
  private WebSocket archiveWebSocket;
  private PantheonNode minerNode;
  private PantheonNode archiveNode;

  @Before
  public void setUp() throws Exception {
    vertx = Vertx.vertx();
    minerNode = cluster.create(pantheonMinerNode("miner-node1"));
    archiveNode = cluster.create(pantheonNode("full-node1"));
    cluster.start(minerNode, archiveNode);
    accountOne = accounts.createAccount("account-one");
    minerWebSocket = new WebSocket(vertx, minerNode);
    archiveWebSocket = new WebSocket(vertx, archiveNode);
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  @Test
  public void transactionRemovedByChainReorganisationMustPublishEvent() throws Exception {

    // Create the light fork
    final Subscription lightForkSubscription = minerWebSocket.subscribe();

    final Hash lightForkEvent = minerNode.execute(transactions.createTransfer(accountOne, 5));
    cluster.verify(accountOne.balanceEquals(5));

    minerWebSocket.verifyTotalEventsReceived(1);
    lightForkSubscription.verifyEventReceived(lightForkEvent);

    final Condition atLeastLighterForkBlockNumber = blockchain.blockNumberMustBeLatest(minerNode);

    cluster.stop();

    // Create the heavy fork
    final PantheonNode minerNodeTwo = cluster.create(pantheonMinerNode("miner-node2"));
    cluster.start(minerNodeTwo);

    final WebSocket heavyForkWebSocket = new WebSocket(vertx, minerNodeTwo);
    final Subscription heavyForkSubscription = heavyForkWebSocket.subscribe();

    final Account accountTwo = accounts.createAccount("account-two");

    // Keep both forks transactions valid by using a different benefactor
    final Account heavyForkBenefactor = accounts.getSecondaryBenefactor();

    final Hash heavyForkEventOne =
        minerNodeTwo.execute(transactions.createTransfer(heavyForkBenefactor, accountTwo, 1));
    cluster.verify(accountTwo.balanceEquals(1));
    final Hash heavyForkEventTwo =
        minerNodeTwo.execute(transactions.createTransfer(heavyForkBenefactor, accountTwo, 2));
    cluster.verify(accountTwo.balanceEquals(1 + 2));
    final Hash heavyForkEventThree =
        minerNodeTwo.execute(transactions.createTransfer(heavyForkBenefactor, accountTwo, 3));
    cluster.verify(accountTwo.balanceEquals(1 + 2 + 3));

    heavyForkWebSocket.verifyTotalEventsReceived(3);
    heavyForkSubscription.verifyEventReceived(heavyForkEventOne);
    heavyForkSubscription.verifyEventReceived(heavyForkEventTwo);
    heavyForkSubscription.verifyEventReceived(heavyForkEventThree);

    final Condition atLeastHeavierForkBlockNumber =
        blockchain.blockNumberMustBeLatest(minerNodeTwo);

    cluster.stop();

    // Restart the two nodes on the light fork with the additional node from the heavy fork
    cluster.start(minerNode, archiveNode, minerNodeTwo);

    final WebSocket minerMergedForksWebSocket = new WebSocket(vertx, minerNode);
    final WebSocket minerTwoMergedForksWebSocket = new WebSocket(vertx, minerNodeTwo);
    final WebSocket archiveMergedForksWebSocket = new WebSocket(vertx, archiveNode);
    final Subscription minerMergedForksSubscription = minerMergedForksWebSocket.subscribe();
    final Subscription minerTwoMergedForksSubscription = minerTwoMergedForksWebSocket.subscribe();
    final Subscription archiveMergedForksSubscription = archiveMergedForksWebSocket.subscribe();

    // Check that all node have loaded their respective forks, i.e. not begin new chains
    minerNode.verify(atLeastLighterForkBlockNumber);
    archiveNode.verify(atLeastLighterForkBlockNumber);
    minerNodeTwo.verify(atLeastHeavierForkBlockNumber);

    // This publish give time needed for heavy fork to be chosen
    final Hash mergedForksEventOne =
        minerNodeTwo.execute(
            transactions.createTransfer(accounts.getSecondaryBenefactor(), accountTwo, 3));
    cluster.verify(accountTwo.balanceEquals(9));

    minerMergedForksWebSocket.verifyTotalEventsReceived(1);
    minerMergedForksSubscription.verifyEventReceived(lightForkEvent);
    archiveMergedForksWebSocket.verifyTotalEventsReceived(1);
    archiveMergedForksSubscription.verifyEventReceived(lightForkEvent);
    minerTwoMergedForksWebSocket.verifyTotalEventsReceived(2);
    minerTwoMergedForksSubscription.verifyEventReceived(lightForkEvent);
    minerTwoMergedForksSubscription.verifyEventReceived(mergedForksEventOne);

    // Check that account two (funded in heavier chain) can be mined on miner one (from lighter
    // chain)
    final Hash mergedForksEventTwo = minerNode.execute(transactions.createTransfer(accountTwo, 3));
    cluster.verify(accountTwo.balanceEquals(9 + 3));

    // Check that account one (funded in lighter chain) can be mined on miner two (from heavier
    // chain)
    final Hash mergedForksEventThree =
        minerNodeTwo.execute(transactions.createTransfer(accountOne, 2));
    cluster.verify(accountOne.balanceEquals(5 + 2));

    minerMergedForksWebSocket.verifyTotalEventsReceived(1 + 1 + 1);
    minerMergedForksSubscription.verifyEventReceived(mergedForksEventTwo);
    minerMergedForksSubscription.verifyEventReceived(mergedForksEventThree);
    archiveMergedForksWebSocket.verifyTotalEventsReceived(1 + 1 + 1);
    archiveMergedForksSubscription.verifyEventReceived(mergedForksEventTwo);
    archiveMergedForksSubscription.verifyEventReceived(mergedForksEventThree);
    minerTwoMergedForksWebSocket.verifyTotalEventsReceived(2 + 1 + 1);
    minerTwoMergedForksSubscription.verifyEventReceived(mergedForksEventTwo);
    minerTwoMergedForksSubscription.verifyEventReceived(mergedForksEventThree);
  }

  @Test
  public void subscriptionToMinerNodeMustReceivePublishEvent() {
    final Subscription minerSubscription = minerWebSocket.subscribe();

    final Hash event = minerNode.execute(transactions.createTransfer(accountOne, 4));
    cluster.verify(accountOne.balanceEquals(4));

    minerWebSocket.verifyTotalEventsReceived(1);
    minerSubscription.verifyEventReceived(event);

    minerWebSocket.unsubscribe(minerSubscription);
  }

  @Test
  public void subscriptionToArchiveNodeMustReceivePublishEvent() {
    final Subscription archiveSubscription = archiveWebSocket.subscribe();

    final Hash event = minerNode.execute(transactions.createTransfer(accountOne, 23));
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

    final Hash event = minerNode.execute(transactions.createTransfer(accountOne, 30));
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
  public void subscriptionToMinerNodeMustReceiveEveryPublishEvent() {
    final Subscription minerSubscription = minerWebSocket.subscribe();

    final Hash eventOne = minerNode.execute(transactions.createTransfer(accountOne, 1));
    cluster.verify(accountOne.balanceEquals(1));

    minerWebSocket.verifyTotalEventsReceived(1);
    minerSubscription.verifyEventReceived(eventOne);

    final Hash eventTwo = minerNode.execute(transactions.createTransfer(accountOne, 4));
    final Hash eventThree = minerNode.execute(transactions.createTransfer(accountOne, 5));
    cluster.verify(accountOne.balanceEquals(1 + 4 + 5));

    minerWebSocket.verifyTotalEventsReceived(3);
    minerSubscription.verifyEventReceived(eventTwo);
    minerSubscription.verifyEventReceived(eventThree);

    minerWebSocket.unsubscribe(minerSubscription);
  }

  @Test
  public void subscriptionToArchiveNodeMustReceiveEveryPublishEvent() {
    final Subscription archiveSubscription = archiveWebSocket.subscribe();

    final Hash eventOne = minerNode.execute(transactions.createTransfer(accountOne, 2));
    final Hash eventTwo = minerNode.execute(transactions.createTransfer(accountOne, 5));
    cluster.verify(accountOne.balanceEquals(2 + 5));

    archiveWebSocket.verifyTotalEventsReceived(2);
    archiveSubscription.verifyEventReceived(eventOne);
    archiveSubscription.verifyEventReceived(eventTwo);

    final Hash eventThree = minerNode.execute(transactions.createTransfer(accountOne, 8));
    cluster.verify(accountOne.balanceEquals(2 + 5 + 8));

    archiveWebSocket.verifyTotalEventsReceived(3);
    archiveSubscription.verifyEventReceived(eventThree);

    archiveWebSocket.unsubscribe(archiveSubscription);
  }

  @Test
  public void everySubscriptionMustReceiveEveryPublishEvent() {
    final Subscription minerSubscriptionOne = minerWebSocket.subscribe();
    final Subscription minerSubscriptionTwo = minerWebSocket.subscribe();
    final Subscription archiveSubscriptionOne = archiveWebSocket.subscribe();
    final Subscription archiveSubscriptionTwo = archiveWebSocket.subscribe();
    final Subscription archiveSubscriptionThree = archiveWebSocket.subscribe();

    final Hash eventOne = minerNode.execute(transactions.createTransfer(accountOne, 10));
    final Hash eventTwo = minerNode.execute(transactions.createTransfer(accountOne, 5));
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
