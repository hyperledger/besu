/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.eea.Eea;
import org.web3j.protocol.http.HttpService;

public class EthSignerClient {
  private static final Logger LOG = LogManager.getLogger();
  private final Web3jService web3jService;
  private final Eea web3j;
  private final String from;

  public EthSignerClient(final URI ethSignerUri) throws IOException {
    this.web3jService = new HttpService(ethSignerUri.toString());
    this.web3j = Eea.build(web3jService);
    this.from = resolveFrom(ethSignerUri);
  }

  private String resolveFrom(final URI ethSignerUri) throws IOException {
    final List<String> accounts;
    try {
      accounts = ethAccounts();
      return accounts.get(0);
    } catch (IOException e) {
      LOG.info("Failed to connect to EthSigner at {}", ethSignerUri);
      throw e;
    } catch (Exception e) {
      LOG.info("Falling back to signing with node key");
    }
    return null;
  }

  public List<String> ethAccounts() throws IOException {
    return web3j.ethAccounts().send().getAccounts();
  }

  public String ethSendTransaction(
      final String to,
      final BigInteger gas,
      final BigInteger gasPrice,
      final BigInteger value,
      final String data,
      final BigInteger nonce)
      throws IOException {
    return web3j
        .ethSendTransaction(new Transaction(from, nonce, gasPrice, gas, to, value, data))
        .send()
        .getTransactionHash();
  }

  public String eeaSendTransaction(
      final String to,
      final BigInteger gas,
      final BigInteger gasPrice,
      final String data,
      final BigInteger nonce,
      final String privateFrom,
      final List<String> privateFor,
      final String restriction)
      throws IOException {

    final PrivateTransactionRequest transaction =
        new PrivateTransactionRequest(
            from,
            nonce,
            gasPrice,
            gas,
            to,
            BigInteger.ZERO,
            data,
            privateFrom,
            privateFor,
            restriction);

    // temporary until implemented in web3j
    return new Request<>(
            "eea_sendTransaction",
            Collections.singletonList(transaction),
            web3jService,
            EthSendTransaction.class)
        .send()
        .getTransactionHash();
  }
}
