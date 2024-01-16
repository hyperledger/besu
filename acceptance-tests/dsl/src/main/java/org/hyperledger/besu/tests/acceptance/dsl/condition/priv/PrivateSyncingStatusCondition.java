package org.hyperledger.besu.tests.acceptance.dsl.condition.priv;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.condition.PrivateCondition;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivSyncingTransactions;

public class PrivateSyncingStatusCondition implements PrivateCondition {

  private final PrivSyncingTransactions transaction;
  private final boolean syncingMiningStatus;

  public PrivateSyncingStatusCondition(
      final PrivSyncingTransactions transaction, final boolean syncingStatus) {
    this.transaction = transaction;
    this.syncingMiningStatus = syncingStatus;
  }

  @Override
  public void verify(final PrivacyNode node) {
    WaitUtils.waitFor(
        10, () -> assertThat(node.execute(transaction)).isEqualTo(syncingMiningStatus));
  }
}
