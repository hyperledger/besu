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

import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_MAX_OMMERS_DEPTH;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_POS_BLOCK_CREATION_MAX_TIME;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_POS_BLOCK_CREATION_REPETITION_MIN_DURATION;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_POW_JOB_TTL;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_REMOTE_SEALERS_LIMIT;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_REMOTE_SEALERS_TTL;

import picocli.CommandLine;

/** The Mining CLI options. */
public class MiningOptions {

  @CommandLine.Option(
      hidden = true,
      names = {"--Xminer-remote-sealers-limit"},
      description =
          "Limits the number of remote sealers that can submit their hashrates (default: ${DEFAULT-VALUE})")
  private final Integer remoteSealersLimit = DEFAULT_REMOTE_SEALERS_LIMIT;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xminer-remote-sealers-hashrate-ttl"},
      description =
          "Specifies the lifetime of each entry in the cache. An entry will be automatically deleted if no update has been received before the deadline (default: ${DEFAULT-VALUE} minutes)")
  private final Long remoteSealersTimeToLive = DEFAULT_REMOTE_SEALERS_TTL;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xminer-pow-job-ttl"},
      description =
          "Specifies the time PoW jobs are kept in cache and will accept a solution from miners (default: ${DEFAULT-VALUE} milliseconds)")
  private final Long powJobTimeToLive = DEFAULT_POW_JOB_TTL;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xmax-ommers-depth"},
      description =
          "Specifies the depth of ommer blocks to accept when receiving solutions (default: ${DEFAULT-VALUE})")
  private final Integer maxOmmersDepth = DEFAULT_MAX_OMMERS_DEPTH;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      hidden = true,
      names = {"--Xminer-stratum-extranonce"},
      description = "Extranonce for Stratum network miners (default: ${DEFAULT-VALUE})")
  private String stratumExtranonce = "080c";

  @CommandLine.Option(
      hidden = true,
      names = {"--Xpos-block-creation-max-time"},
      description =
          "Specifies the maximum time, in milliseconds, a PoS block creation jobs is allowed to run. Must be positive and ≤ 12000 (default: ${DEFAULT-VALUE} milliseconds)")
  private final Long posBlockCreationMaxTime = DEFAULT_POS_BLOCK_CREATION_MAX_TIME;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xpos-block-creation-repetition-min-duration"},
      description =
          "If a PoS block creation repetition takes less than this duration, in milliseconds,"
              + " then it waits before next repetition. Must be positive and ≤ 2000 (default: ${DEFAULT-VALUE} milliseconds)")
  private final Long posBlockCreationRepetitionMinDuration =
      DEFAULT_POS_BLOCK_CREATION_REPETITION_MIN_DURATION;

  /**
   * Create mining options.
   *
   * @return the mining options
   */
  public static MiningOptions create() {
    return new MiningOptions();
  }

  /**
   * Gets remote sealers limit.
   *
   * @return the remote sealers limit
   */
  public Integer getRemoteSealersLimit() {
    return remoteSealersLimit;
  }

  /**
   * Gets remote sealers time to live.
   *
   * @return the remote sealers time to live
   */
  public Long getRemoteSealersTimeToLive() {
    return remoteSealersTimeToLive;
  }

  /**
   * Gets stratum extra nonce.
   *
   * @return the stratum extra nonce
   */
  public String getStratumExtranonce() {
    return stratumExtranonce;
  }

  /**
   * Gets pow job time to live.
   *
   * @return the pow job time to live
   */
  public Long getPowJobTimeToLive() {
    return powJobTimeToLive;
  }

  /**
   * Gets max ommers depth.
   *
   * @return the max ommers depth
   */
  public int getMaxOmmersDepth() {
    return maxOmmersDepth;
  }

  /**
   * Gets pos block creation max time.
   *
   * @return the pos block creation max time
   */
  public Long getPosBlockCreationMaxTime() {
    return posBlockCreationMaxTime;
  }

  /**
   * Gets pos block creation repetition min duration.
   *
   * @return the pos block creation repetition min duration.
   */
  public Long getPosBlockCreationRepetitionMinDuration() {
    return posBlockCreationRepetitionMinDuration;
  }
}
