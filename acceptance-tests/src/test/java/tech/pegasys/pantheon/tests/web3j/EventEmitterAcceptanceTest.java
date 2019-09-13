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
package tech.pegasys.pantheon.tests.web3j;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.web3j.generated.EventEmitter;
import tech.pegasys.pantheon.tests.web3j.generated.EventEmitter.StoredEventResponse;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/*
 * This class is based around the EventEmitter solidity contract
 */
public class EventEmitterAcceptanceTest extends AcceptanceTestBase {

  private PantheonNode node;

  @Before
  public void setUp() throws Exception {
    node = pantheon.createMinerNode("node1");
    cluster.start(node);
  }

  @Test
  public void shouldDeployContractAndAllowLookupOfValuesAndEmittingEvents() throws Exception {
    final EventEmitter eventEmitter =
        node.execute(contractTransactions.createSmartContract(EventEmitter.class));

    final Flowable<StoredEventResponse> storedEventResponseObservable =
        eventEmitter.storedEventFlowable(new EthFilter());

    final AtomicBoolean subscriptionReceived = new AtomicBoolean(false);

    final Disposable subscription =
        storedEventResponseObservable.subscribe(
            storedEventResponse -> {
              subscriptionReceived.set(true);
              assertThat(storedEventResponse._amount).isEqualTo(BigInteger.valueOf(12));
            });

    assertThat(subscription.isDisposed()).isFalse();

    final TransactionReceipt receipt = eventEmitter.store(BigInteger.valueOf(12)).send();

    assertThat(receipt).isNotNull();
    assertThat(eventEmitter.value().send()).isEqualTo(BigInteger.valueOf(12));
    assertThat(subscriptionReceived.get()).isTrue();
  }
}
