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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.crosschain;

import java.math.BigInteger;

public class CrossTransactions {

  public CrossAddMultichainNode getAddMultichainNode(
      final BigInteger blockchainId, final String ipAddressAndPort) {
    return new CrossAddMultichainNode(blockchainId, ipAddressAndPort);
  }

  public CrossIsLockableTransaction getIsLockable(final String address) {
    return new CrossIsLockableTransaction(address);
  }

  public CrossListMultichainNodes getListMultichainNodes() {
    return new CrossListMultichainNodes();
  }

  public CrossRemoveMultichainNode getRemoveMultichainNode(final BigInteger blockchainId) {
    return new CrossRemoveMultichainNode(blockchainId);
  }
}
