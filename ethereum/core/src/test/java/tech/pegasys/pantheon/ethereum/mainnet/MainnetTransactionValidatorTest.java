package net.consensys.pantheon.ethereum.mainnet;

import static net.consensys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INCORRECT_NONCE;
import static net.consensys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT;
import static net.consensys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.NONCE_TOO_LOW;
import static net.consensys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
import static net.consensys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE;
import static net.consensys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.WRONG_CHAIN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionTestFixture;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.vm.GasCalculator;

import java.util.OptionalLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetTransactionValidatorTest {

  private static final KeyPair senderKeys = KeyPair.generate();

  @Mock private GasCalculator gasCalculator;

  private final Transaction basicTransaction =
      new TransactionTestFixture().chainId(1).createTransaction(senderKeys);

  @Test
  public void shouldRejectTransactionIfIntrinsicGasExceedsGasLimit() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false);
    final Transaction transaction =
        new TransactionTestFixture().gasLimit(10).chainId(0).createTransaction(senderKeys);
    when(gasCalculator.transactionIntrinsicGasCost(transaction)).thenReturn(Gas.of(50));

    assertThat(validator.validate(transaction))
        .isEqualTo(ValidationResult.invalid(INTRINSIC_GAS_EXCEEDS_GAS_LIMIT));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasChainIdAndValidatorDoesNot() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false);
    assertThat(validator.validate(basicTransaction))
        .isEqualTo(ValidationResult.invalid(REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionHasIncorrectChainId() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 2);
    assertThat(validator.validate(basicTransaction))
        .isEqualTo(ValidationResult.invalid(WRONG_CHAIN_ID));
  }

  @Test
  public void shouldRejectTransactionWhenSenderAccountDoesNotExist() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);
    assertThat(validator.validateForSender(basicTransaction, null, OptionalLong.empty()))
        .isEqualTo(ValidationResult.invalid(UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  public void shouldRejectTransactionWhenSenderAccountHasInsufficentBalance() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Account account = accountWithBalance(basicTransaction.getUpfrontCost().minus(Wei.of(1)));
    assertThat(validator.validateForSender(basicTransaction, account, OptionalLong.empty()))
        .isEqualTo(ValidationResult.invalid(UPFRONT_COST_EXCEEDS_BALANCE));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionNonceBelowAccountNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Account account = accountWithNonce(basicTransaction.getNonce() + 1);
    assertThat(validator.validateForSender(basicTransaction, account, OptionalLong.empty()))
        .isEqualTo(ValidationResult.invalid(NONCE_TOO_LOW));
  }

  @Test
  public void shouldRejectTransactionWhenTransactionNonceAboveAccountNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Account account = accountWithNonce(basicTransaction.getNonce() - 1);
    assertThat(validator.validateForSender(basicTransaction, account, OptionalLong.empty()))
        .isEqualTo(ValidationResult.invalid(INCORRECT_NONCE));
  }

  @Test
  public void shouldAcceptTransactionWhenNonceBetweenAccountNonceAndMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Transaction transaction =
        new TransactionTestFixture().nonce(10).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, OptionalLong.of(15)))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldAcceptTransactionWhenNonceEqualsMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Transaction transaction =
        new TransactionTestFixture().nonce(10).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, OptionalLong.of(10)))
        .isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectTransactionWhenNonceExceedsMaximumAllowedNonce() {
    final MainnetTransactionValidator validator =
        new MainnetTransactionValidator(gasCalculator, false, 1);

    final Transaction transaction =
        new TransactionTestFixture().nonce(11).createTransaction(senderKeys);
    final Account account = accountWithNonce(5);

    assertThat(validator.validateForSender(transaction, account, OptionalLong.of(10)))
        .isEqualTo(ValidationResult.invalid(INCORRECT_NONCE));
  }

  private Account accountWithNonce(final long nonce) {
    return account(basicTransaction.getUpfrontCost(), nonce);
  }

  private Account accountWithBalance(final Wei balance) {
    return account(balance, basicTransaction.getNonce());
  }

  private Account account(final Wei balance, final long nonce) {
    final Account account = mock(Account.class);
    when(account.getBalance()).thenReturn(balance);
    when(account.getNonce()).thenReturn(nonce);
    return account;
  }
}
