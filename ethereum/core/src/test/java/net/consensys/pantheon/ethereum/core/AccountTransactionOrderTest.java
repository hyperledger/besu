package net.consensys.pantheon.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.crypto.SECP256K1.KeyPair;

import java.util.stream.Stream;

import org.junit.Test;

public class AccountTransactionOrderTest {

  private static final KeyPair KEYS = KeyPair.generate();

  private final Transaction transaction1 = transaction(1);
  private final Transaction transaction2 = transaction(2);
  private final Transaction transaction3 = transaction(3);
  private final Transaction transaction4 = transaction(4);
  private final AccountTransactionOrder accountTransactionOrder =
      new AccountTransactionOrder(
          Stream.of(transaction1, transaction2, transaction3, transaction4));

  @Test
  public void shouldProcessATransactionImmediatelyIfItsTheLowestNonce() {
    assertThat(accountTransactionOrder.transactionsToProcess(transaction1))
        .containsExactly(transaction1);
  }

  @Test
  public void shouldDeferProcessingATransactionIfItIsNotTheLowestNonce() {
    assertThat(accountTransactionOrder.transactionsToProcess(transaction2)).isEmpty();
  }

  @Test
  public void shouldProcessDeferredTransactionsAfterPrerequisiteIsProcessed() {
    assertThat(accountTransactionOrder.transactionsToProcess(transaction2)).isEmpty();
    assertThat(accountTransactionOrder.transactionsToProcess(transaction3)).isEmpty();

    assertThat(accountTransactionOrder.transactionsToProcess(transaction1))
        .containsExactly(transaction1, transaction2, transaction3);
  }

  @Test
  public void shouldNotProcessDeferredTransactionsThatAreNotYetDue() {
    assertThat(accountTransactionOrder.transactionsToProcess(transaction2)).isEmpty();
    assertThat(accountTransactionOrder.transactionsToProcess(transaction4)).isEmpty();

    assertThat(accountTransactionOrder.transactionsToProcess(transaction1))
        .containsExactly(transaction1, transaction2);

    assertThat(accountTransactionOrder.transactionsToProcess(transaction3))
        .containsExactly(transaction3, transaction4);
  }

  private Transaction transaction(final int nonce) {
    return new TransactionTestFixture().nonce(nonce).createTransaction(KEYS);
  }
}
