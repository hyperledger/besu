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
package org.hyperledger.besu.cli.options;

import picocli.CommandLine;

/** The Native library CLI options. */
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

  @CommandLine.Option(
      hidden = true,
      names = {"--Xblake2bf-native-enabled"},
      description =
          "Per default a native library is used for blake2bf if present. "
              + "If the Java implementation should be used instead, this option must be set to false",
      arity = "1")
  private final Boolean nativeBlake2bf = Boolean.TRUE;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xmodexp-native-enabled"},
      description =
          "Per default a native library is used for modexp. "
              + "If the Java implementation should be used instead, this option must be set to false",
      arity = "1")
  private final Boolean nativeModExp = Boolean.TRUE;

  /** Default constructor. */
  NativeLibraryOptions() {}

  /**
   * Create native library options.
   *
   * @return the native library options
   */
  public static NativeLibraryOptions create() {
    return new NativeLibraryOptions();
  }

  /**
   * Whether native secp is enabled.
   *
   * @return true if enabled, false otherwise.
   */
  public Boolean getNativeSecp() {
    return nativeSecp;
  }

  /**
   * Whether native Altbn128 is enabled.
   *
   * @return true if enabled, false otherwise.
   */
  public Boolean getNativeAltbn128() {
    return nativeAltbn128;
  }

  /**
   * Whether native blake2bf is enabled.
   *
   * @return true if enabled, false otherwise.
   */
  public Boolean getNativeBlake2bf() {
    return nativeBlake2bf;
  }

  /**
   * Whether native mod exp is enabled.
   *
   * @return true if enabled, false otherwise.
   */
  public Boolean getNativeModExp() {
    return nativeModExp;
  }
}
