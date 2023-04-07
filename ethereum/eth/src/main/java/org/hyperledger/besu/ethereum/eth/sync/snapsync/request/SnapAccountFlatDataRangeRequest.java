/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;

/** Returns a list of accounts and the merkle proofs of an entire range */
public class SnapAccountFlatDataRangeRequest extends AccountRangeDataRequest {

  protected SnapAccountFlatDataRangeRequest(
      final Hash rootHash,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final Optional<Bytes32> startStorageRange,
      final Optional<Bytes32> endStorageRange) {
    super(rootHash, startKeyHash, endKeyHash, startStorageRange, endStorageRange);
  }

  protected SnapAccountFlatDataRangeRequest(
      final Hash rootHash, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    super(rootHash, startKeyHash, endKeyHash);
  }

  protected SnapAccountFlatDataRangeRequest(
      final Hash rootHash,
      final Hash accountHash,
      final Bytes32 startStorageRange,
      final Bytes32 endStorageRange) {
    super(rootHash, accountHash, startStorageRange, endStorageRange);
  }
}
