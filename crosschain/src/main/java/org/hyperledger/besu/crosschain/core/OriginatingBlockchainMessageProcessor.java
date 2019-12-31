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
package org.hyperledger.besu.crosschain.core;

import org.hyperledger.besu.crosschain.core.keys.CrosschainKeyManager;
import org.hyperledger.besu.crosschain.core.messages.CrosschainTransactionStartMessage;
import org.hyperledger.besu.crosschain.core.messages.ThresholdSignedMessage;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Does the coordination of the processing required for the Crosschain Transaction Start, Commit,
 * and Ignore messages.
 */
public class OriginatingBlockchainMessageProcessor {
  private static final Logger LOG = LogManager.getLogger();

  CrosschainKeyManager keyManager;
  CoordContractManager coordContractManager;

  public OriginatingBlockchainMessageProcessor(
      final CrosschainKeyManager keyManager, final CoordContractManager coordContractManager) {
    this.keyManager = keyManager;
    this.coordContractManager = coordContractManager;
  }

  /**
   * Start message processing: - Get the current Coordination Contract block number. - Validate that
   * the number in the transaction is greater than what the current block number is. - Create the
   * message. - Threshold sign it with this node and other validators. - Upload it to the
   * appropriate Crosschain Coordination Contract.
   *
   * @param transaction Originating (with enclosed subordinates) Transaction to kick off the start.
   */
  public void doStartMessageMagic(final CrosschainTransaction transaction) {
    // If this is an originating transaction, then we are sure the optional fields will exist.
    BigInteger coordBcId = transaction.getCrosschainCoordinationBlockchainId().get();
    Address coordContractAddress = transaction.getCrosschainCoordinationContractAddress().get();
    // BigInteger timeoutBlockNumber =
    // transaction.getCrosschainTransactionTimeoutBlockNumber().get();

    String ipAndPort = this.coordContractManager.getIpAndPort(coordBcId, coordContractAddress);
    if (ipAndPort == null) {
      String msg =
          "Crosschain Transaction uses unknown Coordination Blockchain and Address combination "
              + "Blockchain: 0x"
              + coordBcId.toString(16)
              + ", Address: "
              + coordContractAddress.getHexString();
      LOG.error(msg);
      return;
      // TODO throw error to stop the JSON RPC call
      //      throw new RuntimeException(msg);
    }

    // TODO get block number from Coordination blockchain and check.

    // Create message to be signed.
    ThresholdSignedMessage message = new CrosschainTransactionStartMessage(transaction);

    // Have the message threshold signed.
    // TODO more to do here.
    this.keyManager.thresholdSign(message);
  }
}
