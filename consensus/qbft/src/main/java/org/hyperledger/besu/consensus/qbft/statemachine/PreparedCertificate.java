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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.qbft.statemachine;

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.List;

public class PreparedCertificate {

  private final Block block;
  private final List<SignedData<PreparePayload>> prepares;
  private final int round;

  public PreparedCertificate(
      final Block block, final List<SignedData<PreparePayload>> prepares, final int round) {
    this.block = block;
    this.prepares = prepares;
    this.round = round;
  }

  public int getRound() {
    return round;
  }

  public Block getBlock() {
    return block;
  }

  public List<SignedData<PreparePayload>> getPrepares() {
    return prepares;
  }
}
