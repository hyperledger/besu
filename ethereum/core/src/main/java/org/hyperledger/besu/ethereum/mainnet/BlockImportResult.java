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

public class BlockImportResult {

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

  public boolean isImported() {
    return status == BlockImportStatus.IMPORTED || status == BlockImportStatus.ALREADY_IMPORTED;
  }

  private final BlockImportStatus status;

  public BlockImportStatus getStatus() {
    return status;
  }
}
