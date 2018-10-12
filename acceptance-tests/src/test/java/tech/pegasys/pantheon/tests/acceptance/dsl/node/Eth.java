package tech.pegasys.pantheon.tests.acceptance.dsl.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

public class Eth {

  private final Web3j web3j;

  public Eth(final Web3j web3) {
    this.web3j = web3;
  }

  public BigInteger blockNumber() throws IOException {
    final EthBlockNumber result = web3j.ethBlockNumber().send();
    assertThat(result).isNotNull();
    assertThat(result.hasError()).isFalse();
    return result.getBlockNumber();
  }
}
