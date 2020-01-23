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
package org.hyperledger.besu.crosschain.core.coordination;

import org.hyperledger.besu.crosschain.core.coordination.generated.CrosschainCoordinationV1;
import org.hyperledger.besu.crosschain.core.messages.CrosschainTransactionStartMessage;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

public class CoordinationContractWrapper {
  private static final Logger LOG = LogManager.getLogger();

  public static final int RETRY = 5;
  // TODO this should be parameter and not hard coded.
  public static final int COORDINATION_BLOCK_PERIOD_IN_MS = 2000;
  // TODO this is a gas provider which indicates no gas is charged for transactions.
  // The gas provide is going to be Coordination Blockchain specific.
  private ContractGasProvider freeGasProvider =
      new StaticGasProvider(BigInteger.ZERO, DefaultGasProvider.GAS_LIMIT);

  Credentials credentials;

  public CoordinationContractWrapper(final SECP256K1.KeyPair creds) {
    this.credentials = Credentials.create(creds.getPrivateKey().getD().toString(16));
  }

  public boolean start(
      final String ipAddressPort,
      final BigInteger blockchainId,
      final Address contractAddress,
      final CrosschainTransactionStartMessage message) {

    String uri = "http://" + ipAddressPort + "/";
    Besu web3j = Besu.build(new HttpService(uri), COORDINATION_BLOCK_PERIOD_IN_MS);
    // Even though the coordination contract isn't doing crosschain operations, we need to use
    // it as if it was so the start function can get the current blockchain id using the precompile.
    RawTransactionManager tm =
        new RawTransactionManager(
            web3j,
            this.credentials,
            blockchainId.longValue(),
            RETRY,
            COORDINATION_BLOCK_PERIOD_IN_MS);
    CrosschainCoordinationV1 contractWrapper =
        CrosschainCoordinationV1.load(
            contractAddress.getHexString(), web3j, tm, this.freeGasProvider);

    BigInteger originatingBlockchainId = message.getOriginatingBlockchainId();
    BigInteger crosschainTransactionId = message.getCrosschainTransactionId();
    BigInteger hashOfMessage =
        new BigInteger(1, message.getCrosschainTransactionHash().getByteArray());
    BigInteger transactionTimeoutBlock = message.getTransactionTimeoutBlockNumber();
    BigInteger keyVersion = BigInteger.valueOf(message.getKeyVersion());
    byte[] signature = message.getSignature().extractArray();
    LOG.info(
        "Crosschain Coordination Contract start message: signature: {}",
        message.getSignature().getHexString());
    RemoteFunctionCall<TransactionReceipt> tx =
        contractWrapper.start(
            originatingBlockchainId,
            crosschainTransactionId,
            hashOfMessage,
            transactionTimeoutBlock,
            keyVersion,
            signature);
    TransactionReceipt receipt;
    try {
      receipt = tx.send();
    } catch (Exception ex) {
      LOG.error(
          "Exception thrown while submitting start message transaction to Crosschain Coordination Contract: {}",
          ex.toString());
      return false;
    }
    LOG.info(
        "Crosschain Coordination Contract start message transaction receipt: {}",
        receipt.toString());
    return true;
  }
}
