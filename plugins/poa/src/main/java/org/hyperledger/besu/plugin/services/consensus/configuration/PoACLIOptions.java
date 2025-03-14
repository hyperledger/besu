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
package org.hyperledger.besu.plugin.services.consensus.configuration;

import com.google.common.base.MoreObjects;
import picocli.CommandLine;

/** The PoA cli options. */
public class PoACLIOptions {

  /** The constant DEFAULT_IS_QBFT_EARLY_ROUND_CHANGE. */
  public static final boolean DEFAULT_IS_QBFT_EARLY_ROUND_CHANGE = false;

  /** Flag for enabling early QBFT round change */
  public static final String QBFT_EARLY_ROUND_CHANGE_FLAG =
      "--plugin-poa-qbft-enable-early-round-change";

  /** Is QBFT early round change enabled. */
  @CommandLine.Option(
      names = {QBFT_EARLY_ROUND_CHANGE_FLAG},
      paramLabel = "<BOOLEAN>",
      description =
          "Use this flag to enable early round change when f+1 validators have agreed on a higher round (default: ${DEFAULT-VALUE})")
  private boolean qbftEnableEarlyRoundChange;

  private PoACLIOptions() {}

  /**
   * Create PoA cli options.
   *
   * @return the PoA cli options
   */
  public static PoACLIOptions create() {
    return new PoACLIOptions();
  }

  /**
   * RocksDb cli options from config.
   *
   * @param config the config
   * @return the RocksDb cli options
   */
  public static PoACLIOptions fromConfig(final PoACLIOptions config) {
    final PoACLIOptions options = create();
    options.qbftEnableEarlyRoundChange = config.isQbftEarlyRoundChange();
    return options;
  }

  /**
   * To domain object PoA factory configuration.
   *
   * @return the PoA factory configuration
   */
  public PoAFactoryConfiguration toDomainObject() {
    return new PoAFactoryConfiguration(qbftEnableEarlyRoundChange);
  }

  /**
   * Is QBFT early round change.
   *
   * @return the boolean
   */
  public boolean isQbftEarlyRoundChange() {
    return qbftEnableEarlyRoundChange;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("qbftEnableEarlyRoundChange", qbftEnableEarlyRoundChange)
        .toString();
  }
}
