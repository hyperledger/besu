package tech.pegasys.pantheon.tests.acceptance.dsl.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Web3Sha3;

public class Web3 {

  private final Web3j web3j;

  public Web3(final Web3j web3) {
    this.web3j = web3;
  }

  public String web3Sha3(final String input) throws IOException {
    final Web3Sha3 result = web3j.web3Sha3(input).send();
    assertThat(result).isNotNull();
    assertThat(result.hasError()).isFalse();
    return result.getResult();
  }
}
