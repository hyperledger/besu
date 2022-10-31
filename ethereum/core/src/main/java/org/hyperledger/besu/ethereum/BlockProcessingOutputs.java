/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum;

import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.ArrayList;
import java.util.List;

public class BlockProcessingOutputs {

  private final MutableWorldState worldState;
  private final List<TransactionReceipt> receipts;

  public BlockProcessingOutputs(
      final MutableWorldState worldState, final List<TransactionReceipt> receipts) {
    this.worldState = worldState;
    this.receipts = receipts;
  }

  public static BlockProcessingOutputs empty() {
    return new BlockProcessingOutputs(null, new ArrayList<>());
  }

  public MutableWorldState getWorldState() {
    return worldState;
  }

  public List<TransactionReceipt> getReceipts() {
    return receipts;
  }
}
