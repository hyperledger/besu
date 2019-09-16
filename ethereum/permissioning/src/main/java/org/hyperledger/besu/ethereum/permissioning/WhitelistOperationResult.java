/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.permissioning;

public enum WhitelistOperationResult {
  SUCCESS,
  ERROR_DUPLICATED_ENTRY,
  ERROR_EMPTY_ENTRY,
  ERROR_EXISTING_ENTRY,
  ERROR_INVALID_ENTRY,
  ERROR_ABSENT_ENTRY,
  ERROR_FIXED_NODE_CANNOT_BE_REMOVED,
  ERROR_WHITELIST_PERSIST_FAIL,
  ERROR_WHITELIST_FILE_SYNC
}
