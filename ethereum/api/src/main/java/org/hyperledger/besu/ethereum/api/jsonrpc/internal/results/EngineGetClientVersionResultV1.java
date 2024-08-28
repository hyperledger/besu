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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import com.fasterxml.jackson.annotation.JsonGetter;

public class EngineGetClientVersionResultV1 {
  private final String code;
  private final String name;
  private final String version;
  private final String commit;

  public EngineGetClientVersionResultV1(
      final String code, final String name, final String version, final String commit) {
    this.code = code;
    this.name = name;
    this.version = version;
    this.commit = commit;
  }

  @JsonGetter(value = "code")
  public String getCode() {
    return code;
  }

  @JsonGetter(value = "name")
  public String getName() {
    return name;
  }

  @JsonGetter(value = "version")
  public String getVersion() {
    return version;
  }

  @JsonGetter(value = "commit")
  public String getCommit() {
    return commit;
  }
}
