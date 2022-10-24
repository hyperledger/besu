/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet;

/** The result of a block import. */
public class BlockImportResult {
  private final BlockImportStatus status;

  public enum BlockImportStatus {
    IMPORTED,
    NOT_IMPORTED,
    ALREADY_IMPORTED
  }

  public BlockImportResult(final boolean status) {
    this.status = status ? BlockImportStatus.IMPORTED : BlockImportStatus.NOT_IMPORTED;
  }

  public BlockImportResult(final BlockImportStatus status) {
    this.status = status;
  }

  /**
   * The result of the block import call
   *
   * @return {@code true} if the block was added somewhere in the blockchain; otherwise {@code
   *     false}
   */
  public boolean isImported() {
    return status == BlockImportStatus.IMPORTED || status == BlockImportStatus.ALREADY_IMPORTED;
  }

  public BlockImportStatus getStatus() {
    return status;
  }
}
