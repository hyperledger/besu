package org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

import org.web3j.protocol.core.methods.response.EthSyncing;

public class PrivSyncingTransactions implements Transaction<Boolean> {

  PrivSyncingTransactions() {}

  @Override
  public Boolean execute(final NodeRequests node) {
    try {
      EthSyncing response = node.eth().ethSyncing().send();
      assertThat(response).isNotNull();
      return response.isSyncing();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
