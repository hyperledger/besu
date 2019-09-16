/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.WorldState;

import java.util.function.Supplier;

public class GeneralStateTestCaseEipSpec {

  private final String eip;

  // Creating the actual transaction is expensive because the json test file does not give us the
  // transaction but rather the private key to sign, and so we have to do the signing. And we don't
  // want to do this for 22k general state tests up-front (during
  // GeneralStateReferenceTest.getTestParametersForConfig) because 1) that makes the parameters
  // generation of
  // GeneralStateReferenceTest take more than a minute, which means that much time waiting before
  // anything
  // is run, which isn't friendly and 2) this makes it harder to parallelize this step. Anyway, this
  // is why this is a supplier: calling get() actually does the signing.
  private final Supplier<Transaction> transactionSupplier;

  private final WorldState initialWorldState;

  private final Hash expectedRootHash;

  // The keccak256 hash of the RLP encoding of the log series
  private final Hash expectedLogsHash;

  private final BlockHeader blockHeader;

  GeneralStateTestCaseEipSpec(
      final String eip,
      final Supplier<Transaction> transactionSupplier,
      final WorldState initialWorldState,
      final Hash expectedRootHash,
      final Hash expectedLogsHash,
      final BlockHeader blockHeader) {
    this.eip = eip;
    this.transactionSupplier = transactionSupplier;
    this.initialWorldState = initialWorldState;
    this.expectedRootHash = expectedRootHash;
    this.expectedLogsHash = expectedLogsHash;
    this.blockHeader = blockHeader;
  }

  String eip() {
    return eip;
  }

  WorldState initialWorldState() {
    return initialWorldState;
  }

  Hash expectedRootHash() {
    return expectedRootHash;
  }

  Hash expectedLogsHash() {
    return expectedLogsHash;
  }

  Transaction transaction() {
    return transactionSupplier.get();
  }

  BlockHeader blockHeader() {
    return blockHeader;
  }
}
