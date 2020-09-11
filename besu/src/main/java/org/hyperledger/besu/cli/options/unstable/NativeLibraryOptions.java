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
package org.hyperledger.besu.cli.options.unstable;

import picocli.CommandLine;

public class NativeLibraryOptions {

  @CommandLine.Option(
      hidden = true,
      names = {"--Xsecp256k1-native-enabled"},
      description = "Path to PID file (optional)",
      arity = "1")
  private final Boolean nativeSecp256k1 = Boolean.TRUE;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xaltbn128-native-enabled"},
      description = "Path to PID file (optional)",
      arity = "1")
  private final Boolean nativeAltbn128 = Boolean.TRUE;

  public static NativeLibraryOptions create() {
    return new NativeLibraryOptions();
  }

  public Boolean getNativeSecp256k1() {
    return nativeSecp256k1;
  }

  public Boolean getNativeAltbn128() {
    return nativeAltbn128;
  }
}
