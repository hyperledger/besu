package net.consensys.pantheon.tests.web3j;

import static net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonMinerNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.consensys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import net.consensys.pantheon.tests.web3j.generated.EventEmitter;
import net.consensys.pantheon.tests.web3j.generated.EventEmitter.StoredEventResponse;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import rx.Observable;

/*
 * This class is based around the EventEmitter solidity contract
 *
 */
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
