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
package org.hyperledger.besu.evm.gascalculator.stateless;

public class AccessEvents {
  private boolean branchRead;
  private boolean chunkRead;
  private boolean branchWrite;
  private boolean chunkWrite;
  private boolean chunkFill;

  public AccessEvents() {
    this(false, false, false, false, false);
  }

  public AccessEvents(
      final boolean branchRead,
      final boolean chunkRead,
      final boolean branchWrite,
      final boolean chunkWrite,
      final boolean chunkFill) {
    this.branchRead = branchRead;
    this.chunkRead = chunkRead;
    this.branchWrite = branchWrite;
    this.chunkWrite = chunkWrite;
    this.chunkFill = chunkFill;
  }

  public boolean isBranchRead() {
    return branchRead;
  }

  public boolean isChunkRead() {
    return chunkRead;
  }

  public boolean isBranchWrite() {
    return branchWrite;
  }

  public boolean isChunkWrite() {
    return chunkWrite;
  }

  public boolean isChunkFill() {
    return chunkFill;
  }

  public void setBranchRead(final boolean branchRead) {
    this.branchRead = branchRead;
  }

  public void setChunkRead(final boolean chunkRead) {
    this.chunkRead = chunkRead;
  }

  public void setBranchWrite(final boolean branchWrite) {
    this.branchWrite = branchWrite;
  }

  public void setChunkWrite(final boolean chunkWrite) {
    this.chunkWrite = chunkWrite;
  }

  public void setChunkFill(final boolean chunkFill) {
    this.chunkFill = chunkFill;
  }
}
