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
package org.hyperledger.besu.ethereum.privacy.storage.migration;

public class PrivateStorageMigrationException extends RuntimeException {

  private static final String MIGRATION_ERROR_MSG =
      "Unexpected error during private database migration. Please re-sync your node to avoid data corruption.";

  public PrivateStorageMigrationException(final String message) {
    super(message);
  }

  public PrivateStorageMigrationException() {
    super(MIGRATION_ERROR_MSG);
  }

  public PrivateStorageMigrationException(final Throwable th) {
    super(MIGRATION_ERROR_MSG, th);
  }
}
