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
package org.hyperledger.besu.crosschain.p2p;

import org.hyperledger.besu.crosschain.core.keys.signatures.NodeBlsSigner;
import org.hyperledger.besu.crosschain.core.messages.SubordinateViewResult;
import org.hyperledger.besu.crosschain.crypto.threshold.scheme.BlsPointSecretShare;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.util.bytes.BytesValue;

/**
 * Simulate one or more nodes for the purposes of threshold signing Subordinate View return results.
 *
 * <p>The nodes should be created on application start-up.
 */
public class OtherNodeSimulator {
  private final NodeBlsSigner signer;
  private final SubordinateViewExecutor executor;

  public OtherNodeSimulator(
      final int sidechainId, final int numNodes, final TransactionSimulator transactionSimulator) {
    this.signer = new NodeBlsSigner(sidechainId, numNodes);
    this.executor = new SubordinateViewExecutor(transactionSimulator);
  }

  public BlsPointSecretShare requestSign(final SubordinateViewResult subordinateViewResult) {
    Object resultObj =
        this.executor.getResult(
            subordinateViewResult.getTransaction(), subordinateViewResult.getBlockNumber());
    if (resultObj instanceof TransactionSimulatorResult) {
      TransactionSimulatorResult resultTxSim = (TransactionSimulatorResult) resultObj;
      BytesValue resultBytesValue = resultTxSim.getOutput();

      if (!resultBytesValue.equals(subordinateViewResult.getResult())) {
        // TODO we don't agree with the result!!!!
        // TODO need to log this and return an error.
        throw new Error("Didn't get the same result");
      }

      return this.signer.sign(subordinateViewResult);
    }

    // TODO need to deal with transaction not executing correctly
    throw new Error("Problem executing subordinate view");
  }
}
