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
package org.hyperledger.besu.ethereum.crosschain;

import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crypto.crosschain.threshold.scheme.BlsPointSecretShare;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Subordinate View Coordinators coordinate the execution subordinate views and the signing of the
 * view results across validator nodes.
 *
 * <p>This implementation simulates the communication by coordinating with simulated nodes. The
 * final implementation will need to communicate and coordinate with real nodes.
 */
public class SubordinateViewCoordinator {
  private static final Logger LOG = LogManager.getLogger();

  SubordinateViewExecutor executor;
  private NodeBlsSigner signer;
  private ArrayList<OtherNodeSimulator> otherNodes;

  /**
   * This method should be called at application start-up to set-up the subordinate view coordinator
   * and the other simulated nodes.
   *
   * @param sidechainId Sidechain ID of this node.
   * @param numNodes number of other simulated nodes to be created.
   * @param transactionSimulator executor to be supplied to simulated nodes.
   * @return a subordinate view coordinator which simulates other nodes.
   */
  public static SubordinateViewCoordinator createSubordinateViewCoordinatorAndOtherNodes(
      final int sidechainId,
      final int numNodes,
      final int nodeNum,
      final TransactionSimulator transactionSimulator) {
    ArrayList<OtherNodeSimulator> otherNodes = new ArrayList<>();
    for (int i = 0; i < numNodes; i++) {
      if (i == nodeNum) continue;
      otherNodes.add(new OtherNodeSimulator(sidechainId, i, transactionSimulator));
    }
    return new SubordinateViewCoordinator(sidechainId, nodeNum, otherNodes, transactionSimulator);
  }

  private SubordinateViewCoordinator(
      final int sidechainId,
      final int nodeNumber,
      final ArrayList<OtherNodeSimulator> otherNodes,
      final TransactionSimulator transactionSimulator) {
    this.signer = new NodeBlsSigner(sidechainId, nodeNumber);
    this.otherNodes = otherNodes;
    this.executor = new SubordinateViewExecutor(transactionSimulator);
  }

  // TODO this method would be called from the JSON RPC call.
  // TODO the JSON RPC call should be similar to EthCall, and get the blocknumber using similar code
  // to EthBlockNumber
  public Object getSignedResult(
      final CrosschainTransaction subordinateView, final long blockNumber) {

    Object resultObj = executor.getResult(subordinateView, blockNumber);
    if (resultObj instanceof TransactionSimulatorResult) {
      TransactionSimulatorResult resultTxSim = (TransactionSimulatorResult) resultObj;
      BytesValue resultBytesValue = resultTxSim.getOutput();
      LOG.info("Transaction Simulator Result: " + resultBytesValue.toString());
      SubordinateViewResult result =
          new SubordinateViewResult(subordinateView, resultBytesValue, blockNumber);

      ArrayList<BlsPointSecretShare> partialSignatures = new ArrayList<>();
      for (OtherNodeSimulator otherNode : this.otherNodes) {
        BlsPointSecretShare partialSignature = otherNode.requestSign(result);
        // TODO check for other node indicating an error
        partialSignatures.add(partialSignature);
      }

      // Sign the result at the local node.
      BlsPointSecretShare localPartialSignature = this.signer.sign(result);
      // Use threshold partial signatures to combine to produce signature.
      BlsPoint signature =
          this.signer.combineSignatureShares(localPartialSignature, partialSignatures, result);
      if (signature == null) {
        // TODO should log this error and return an error via JSON RPC
        // TODO should also determine which node produced an invalid partial signature
        throw new Error("Partial signatures could not be combined to create a valid signature");
      }

      // TODO Use RLP to combine the resultBytesValue with the serialized signature,
      //  the blocknumber and either the transaction or the hash or the transaction

      BytesValue signatureAndResult = resultBytesValue;

      // Replace the output with the output and signature in the result object.
      TransactionProcessor.Result txResult =
          MainnetTransactionProcessor.Result.successful(
              resultTxSim.getResult().getLogs(),
              resultTxSim.getResult().getGasRemaining(),
              signatureAndResult,
              resultTxSim.getValidationResult());

      return new TransactionSimulatorResult(subordinateView, txResult);
    } else {
      // An error occurred - propagate the error.
      LOG.info("Transaction Simulator returned an error");
      return resultObj;
    }
  }
}
