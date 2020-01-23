/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crosschain.core.coordination.CoordinationContractWrapper;
import org.hyperledger.besu.crosschain.core.messages.CrosschainTransactionStartMessage;
import org.hyperledger.besu.crosschain.core.messages.ThresholdSignedMessage;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** This class will manage all outward bound connections. For the moment, this is just JSON RPC. */
public class OutwardBoundConnectionManager {
  private static final Logger LOG = LogManager.getLogger();

  final CoordinationContractWrapper wrapper;

  /** @param credentials Credentials to use when interacting with Coordination Contract. */
  public OutwardBoundConnectionManager(final SECP256K1.KeyPair credentials) {
    this.wrapper = new CoordinationContractWrapper(credentials);
  }

  // TODO this should be implemented as a Vertx HTTPS Client. We should probably submit all
  // TODO Subordinate Views together, and wait for them to all return, and submit all
  //  Subordinate Transactions together and wait for them to all return.
  public static String post(final String ipAddressPort, final String method, final String params)
      throws Exception {
    URL url = new URL("http://" + ipAddressPort);
    URLConnection con = url.openConnection();
    HttpURLConnection http = (HttpURLConnection) con;
    http.setRequestMethod("POST");
    http.setDoOutput(true);
    byte[] out =
        ("{\"jsonrpc\":\"2.0\",\"method\":\""
                + method
                + "\",\"params\":[\""
                + params
                + "\"],\"id\":1}")
            .getBytes(UTF_8);
    int length = out.length;
    http.setFixedLengthStreamingMode(length);
    http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    http.connect();
    OutputStream os = http.getOutputStream();
    os.write(out);
    BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream(), UTF_8));
    String line;
    String response = "";
    while ((line = in.readLine()) != null) {
      response += line;
    }
    os.close();
    in.close();
    http.disconnect();
    return response;
  }

  // Send the start message to the coordination contract.
  public boolean coordContractStart(
      final String ipAddressPort,
      final BigInteger blockchainId,
      final Address contractAddress,
      final ThresholdSignedMessage message) {
    if (!(message instanceof CrosschainTransactionStartMessage)) {
      String msg = "Should be called with start message. Called with: " + message.getType();
      LOG.error(msg);
      throw new Error(msg);
    }

    return this.wrapper.start(
        ipAddressPort, blockchainId, contractAddress, (CrosschainTransactionStartMessage) message);
  }
}
