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
package org.hyperledger.besu.ethereum.blockcreation.sealer;

public class SealerInfo implements Comparable<SealerInfo> {
  private final String id;
  private final Long lastUpdated;
  private final Long hashRate;

  public SealerInfo(final String id, final Long hashRate) {
    this(id, System.currentTimeMillis(), hashRate);
  }

  public SealerInfo(final String id, final Long lastUpdated, final Long hashRate) {
    this.id = id;
    this.lastUpdated = lastUpdated;
    this.hashRate = hashRate;
  }

  public Long getLastUpdated() {
    return lastUpdated;
  }

  public Long getHashRate() {
    return hashRate;
  }

  public String getId() {
    return id;
  }

  @Override
  public int compareTo(final SealerInfo other) {
    return lastUpdated.compareTo(other.lastUpdated);
  }
}
