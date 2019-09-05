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
package tech.pegasys.pantheon.plugin.services;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/** Generally useful configuration provided by Pantheon. */
public interface PantheonConfiguration {

  /**
   * Location of the working directory of the storage in the file system running the client.
   *
   * @return location of the storage in the file system of the client.
   */
  Path getStoragePath();

  /**
   * Url of the enclave that stores private transaction data.
   *
   * @return an optional containing the url of the enclave Pantheon is connected to, or empty if
   *     privacy is not enabled.
   */
  Optional<URI> getEnclaveUrl();
}
