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
      names = {"--Xsecp-native-enabled", "--Xsecp256k1-native-enabled"},
      description =
          "Per default a native library is used for the signature algorithm. "
              + "If the Java implementation should be used instead, this option must be set to false",
      arity = "1")
  private final Boolean nativeSecp = Boolean.TRUE;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xaltbn128-native-enabled"},
      description =
          "Per default a native library is used for altbn128. "
              + "If the Java implementation should be used instead, this option must be set to false",
      arity = "1")
  private final Boolean nativeAltbn128 = Boolean.TRUE;

  public static NativeLibraryOptions create() {
    return new NativeLibraryOptions();
  }

  public Boolean getNativeSecp() {
    return nativeSecp;
  }

  public Boolean getNativeAltbn128() {
    return nativeAltbn128;
  }
}
