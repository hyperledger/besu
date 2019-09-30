package org.hyperledger.besu.tests.acceptance.crosschain;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.junit.Test;

import java.io.IOException;

public class CrosschainIsLockableTest extends AcceptanceTestBase {

  @Test
  public void shouldDiscardVotes() throws IOException {
    final String[] validators = {"validator1", "validator3"};
    final BesuNode validator1 = besu.createIbft2NodeWithValidators("validator1", validators);
    final BesuNode validator2 = besu.createIbft2NodeWithValidators("validator2", validators);
    final BesuNode validator3 = besu.createIbft2NodeWithValidators("validator3", validators);
    cluster.start(validator1, validator2, validator3);



    validator1.execute(ibftTwoTransactions.createAddProposal(validator2));
    validator1.execute(ibftTwoTransactions.createRemoveProposal(validator3));
    validator1.verify(
        ibftTwo.pendingVotesEqual().addProposal(validator2).removeProposal(validator3).build());

    validator1.execute(ibftTwoTransactions.createDiscardProposal(validator2));
    validator1.verify(ibftTwo.pendingVotesEqual().removeProposal(validator3).build());

    validator1.execute(ibftTwoTransactions.createDiscardProposal(validator3));
    cluster.verify(ibftTwo.noProposals());
  }
}
