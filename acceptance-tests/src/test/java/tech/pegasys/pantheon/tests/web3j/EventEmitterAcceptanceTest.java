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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonMinerNode;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.web3j.generated.EventEmitter;
import tech.pegasys.pantheon.tests.web3j.generated.EventEmitter.StoredEventResponse;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import rx.Observable;

/*
 * This class is based around the EventEmitter solidity contract
 *
 */
@Ignore
public class EventEmitterAcceptanceTest extends AcceptanceTestBase {

  public static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(1000);
  public static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(3000000);
  Credentials MAIN_CREDENTIALS =
      Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");

  private PantheonNode node;

  @Before
  public void setUp() throws Exception {
    node = cluster.create(pantheonMinerNode("node1"));
    cluster.start(node);
  }

  @Test
  public void shouldDeployContractAndAllowLookupOfValuesAndEmittingEvents() throws Exception {
    System.out.println("Sending Create Contract Transaction");
    final EventEmitter eventEmitter =
        EventEmitter.deploy(node.web3j(), MAIN_CREDENTIALS, DEFAULT_GAS_PRICE, DEFAULT_GAS_LIMIT)
            .send();
    final Observable<StoredEventResponse> storedEventResponseObservable =
        eventEmitter.storedEventObservable(new EthFilter());
    final AtomicBoolean subscriptionReceived = new AtomicBoolean(false);
    storedEventResponseObservable.subscribe(
        storedEventResponse -> {
          subscriptionReceived.set(true);
          assertEquals(BigInteger.valueOf(12), storedEventResponse._amount);
        });

    final TransactionReceipt send = eventEmitter.store(BigInteger.valueOf(12)).send();

    assertEquals(BigInteger.valueOf(12), eventEmitter.value().send());
    assertTrue(subscriptionReceived.get());
  }
}
