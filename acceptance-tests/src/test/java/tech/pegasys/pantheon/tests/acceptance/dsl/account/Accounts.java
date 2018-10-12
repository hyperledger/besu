package tech.pegasys.pantheon.tests.acceptance.dsl.account;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.utils.Convert.Unit.ETHER;
import static org.web3j.utils.Convert.toWei;
import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.web3j.utils.Convert.Unit;

public class Accounts {

  private static final Logger LOG = getLogger();

  private final Account richBenefactorOne;
  private final Account richBenefactorTwo;

  public Accounts() {
    richBenefactorOne =
        Account.fromPrivateKey(
            "Rich Benefactor One",
            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");
    richBenefactorTwo =
        Account.fromPrivateKey(
            "Rich Benefactor Two",
            "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3");
  }

  public Account createAccount(
      final String accountName,
      final String initialBalance,
      final Unit initialBalanceUnit,
      final PantheonNode createOnNode) {
    final Account account = Account.create(accountName);
    createOnNode.transferFunds(richBenefactorOne, account, initialBalance, initialBalanceUnit);

    return account;
  }

  public Account createAccount(final String accountName) {
    return Account.create(accountName);
  }

  public void waitForAccountBalance(
      final Account account,
      final String expectedBalance,
      final Unit balanceUnit,
      final PantheonNode node) {
    LOG.info(
        "Waiting for {} to have a balance of {} {} on node {}",
        account.getName(),
        expectedBalance,
        balanceUnit,
        node.getName());

    waitFor(
        () ->
            assertThat(node.getAccountBalance(account))
                .isEqualTo(toWei(expectedBalance, balanceUnit).toBigIntegerExact()));
  }

  public void waitForAccountBalance(
      final Account account, final int etherAmount, final PantheonNode node) {
    waitForAccountBalance(account, String.valueOf(etherAmount), ETHER, node);
  }

  public Hash transfer(final Account recipient, final int amount, final PantheonNode node) {
    return transfer(richBenefactorOne, recipient, amount, node);
  }

  public Hash transfer(
      final Account sender, final Account recipient, final int amount, final PantheonNode node) {
    return node.transferFunds(sender, recipient, String.valueOf(amount), Unit.ETHER);
  }

  /**
   * Transfer funds in separate transactions (1 eth increments). This is a strategy to increase the
   * total of transactions.
   *
   * @param fromAccount account sending the ether value
   * @param toAccount account receiving the ether value
   * @param etherAmount amount of ether to transfer
   * @return a list with the hashes of each transaction
   */
  public List<Hash> incrementalTransfer(
      final Account fromAccount,
      final Account toAccount,
      final int etherAmount,
      final PantheonNode node) {
    final List<Hash> txHashes = new ArrayList<>();
    for (int i = 1; i <= etherAmount; i++) {
      final Hash hash = node.transferFunds(fromAccount, toAccount, String.valueOf(1), Unit.ETHER);
      txHashes.add(hash);
    }
    return txHashes;
  }

  public Account getSecondaryBenefactor() {
    return richBenefactorTwo;
  }
}
