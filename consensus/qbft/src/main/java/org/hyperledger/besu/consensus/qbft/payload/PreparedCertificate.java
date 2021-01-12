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
package org.hyperledger.besu.consensus.qbft.payload;

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.List;

public class PreparedCertificate {
  private final Block preparedBlock;
  private final List<SignedData<PreparePayload>> prepares;

  public PreparedCertificate(
      final Block preparedBlock, final List<SignedData<PreparePayload>> prepares) {
    this.preparedBlock = preparedBlock;
    this.prepares = prepares;
  }

  public Block getPreparedBlock() {
    return preparedBlock;
  }

  public List<SignedData<PreparePayload>> getPrepares() {
    return prepares;
  }
}
