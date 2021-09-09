/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.manager;

public class EthContext {

  private final EthPeers ethPeers;
  private final ProtocolMessages ethMessages;
  final ProtocolMessages snapMessages;
  private final EthScheduler scheduler;

  public EthContext(
      final EthPeers ethPeers,
      final ProtocolMessages ethMessages,
      final ProtocolMessages snapMessages,
      final EthScheduler scheduler) {
    this.ethPeers = ethPeers;
    this.ethMessages = ethMessages;
    this.snapMessages = snapMessages;
    this.scheduler = scheduler;
  }

  public EthPeers getEthPeers() {
    return ethPeers;
  }

  public ProtocolMessages getEthMessages() {
    return ethMessages;
  }

  public ProtocolMessages getSnapMessages() {
    return snapMessages;
  }

  public EthScheduler getScheduler() {
    return scheduler;
  }
}
