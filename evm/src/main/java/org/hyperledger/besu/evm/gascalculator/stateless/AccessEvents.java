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

import java.util.function.Supplier;

public final class AccessEvents {

  public static final short NONE = 0;
  public static final short BRANCH_READ = 1;
  public static final short CHUNK_READ = 2;
  public static final short BRANCH_WRITE = 4;
  public static final short CHUNK_WRITE = 16;
  public static final short CHUNK_FILL = 32;

  private AccessEvents() {}

  public static boolean isBranchRead(final short accessEvents) {
    return (accessEvents & BRANCH_READ) != 0;
  }

  public static boolean isChunkRead(final short accessEvents) {
    return (accessEvents & CHUNK_READ) != 0;
  }

  public static boolean isBranchWrite(final short accessEvents) {
    return (accessEvents & BRANCH_WRITE) != 0;
  }

  public static boolean isChunkWrite(final short accessEvents) {
    return (accessEvents & CHUNK_WRITE) != 0;
  }

  public static boolean isChunkFill(final short accessEvents) {
    return (accessEvents & CHUNK_FILL) != 0;
  }
}
