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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea;

import java.util.Collections;
import java.util.List;

import org.assertj.core.util.Lists;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.Log;

public class EeaRequestFactory {

  public static class PrivateTransactionReceiptResponse
      extends Response<PrivateTransactionReceipt> {}

  public static class PrivateTransactionReceipt {
    private String contractAddress;
    private String from;
    private String to;
    private String output;
    private List<Log> logs;

    public PrivateTransactionReceipt() {}

    public String getContractAddress() {
      return contractAddress;
    }

    public void setContractAddress(final String contractAddress) {
      this.contractAddress = contractAddress;
    }

    public String getFrom() {
      return from;
    }

    public void setFrom(final String from) {
      this.from = from;
    }

    public String getTo() {
      return to;
    }

    public void setTo(final String to) {
      this.to = to;
    }

    public List<Log> getLogs() {
      return logs;
    }

    public void setLogs(final List<Log> logs) {
      this.logs = logs;
    }

    public String getOutput() {
      return output;
    }

    public void setOutput(final String output) {
      this.output = output;
    }
  }

  private final Web3jService web3jService;

  public EeaRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  Request<?, org.web3j.protocol.core.methods.response.EthSendTransaction> eeaSendRawTransaction(
      final String signedTransactionData) {
    return new Request<>(
        "eea_sendRawTransaction",
        Collections.singletonList(signedTransactionData),
        web3jService,
        org.web3j.protocol.core.methods.response.EthSendTransaction.class);
  }

  Request<?, PrivateTransactionReceiptResponse> eeaGetTransactionReceipt(final String txHash) {
    return new Request<>(
        "eea_getTransactionReceipt",
        Lists.newArrayList(txHash),
        web3jService,
        PrivateTransactionReceiptResponse.class);
  }
}
