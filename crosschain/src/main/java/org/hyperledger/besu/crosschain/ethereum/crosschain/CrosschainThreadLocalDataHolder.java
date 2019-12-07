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
package org.hyperledger.besu.crosschain.ethereum.crosschain;

import org.hyperledger.besu.ethereum.core.CrosschainTransaction;

/**
 * Holds the Crosschain Transaction context during the execution of a Crosschain Transaction. This
 * gives the precompile contracts access to the Crosschain Transaction's list of Subordinate
 * Transactions and Views.
 */
public class CrosschainThreadLocalDataHolder {
  private static final ThreadLocal<CrosschainTransaction> data =
      new ThreadLocal<CrosschainTransaction>();

  public static void setCrosschainTransaction(final CrosschainTransaction tx) {
    data.set(tx);
  }

  public static CrosschainTransaction getCrosschainTransaction() {
    return data.get();
  }

  public static void removeCrosschainTransaction() {
    data.remove();
  }
}
