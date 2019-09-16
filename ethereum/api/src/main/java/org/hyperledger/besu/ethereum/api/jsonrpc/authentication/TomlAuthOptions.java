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
package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.AuthOptions;
import io.vertx.ext.auth.AuthProvider;

public class TomlAuthOptions implements AuthOptions {

  private Path tomlPath;

  public TomlAuthOptions() {}

  public TomlAuthOptions(final TomlAuthOptions that) {
    tomlPath = that.tomlPath;
  }

  @Override
  public AuthProvider createProvider(final Vertx vertx) {
    return new TomlAuth(vertx, this);
  }

  @Override
  public AuthOptions clone() {
    return new TomlAuthOptions(this);
  }

  public TomlAuthOptions setTomlPath(final String tomlPath) {
    this.tomlPath = Paths.get(tomlPath);
    return this;
  }

  public Path getTomlPath() {
    return tomlPath;
  }
}
