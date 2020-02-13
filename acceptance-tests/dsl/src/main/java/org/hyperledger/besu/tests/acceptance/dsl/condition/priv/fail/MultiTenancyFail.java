package org.hyperledger.besu.tests.acceptance.dsl.condition.priv.fail;

import org.assertj.core.api.Assertions;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.web3j.protocol.exceptions.ClientConnectionException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class MultiTenancyFail implements Condition {

    private final Transaction transaction;
    private final JsonRpcError error;

    public MultiTenancyFail(
            final Transaction transaction, final JsonRpcError error) {
        this.transaction = transaction;
        this.error = error;
    }

    @Override
    public void verify(final Node node) {
        try {
            node.execute(transaction);
            assertThatExceptionOfType(ClientConnectionException.class);
        } catch (final Exception e) {
            Assertions.assertThat(e)
                    .isInstanceOf(ClientConnectionException.class)
                    .hasMessageContaining("400")
                    .hasMessageContaining(error.getMessage());
        }
    }
}
