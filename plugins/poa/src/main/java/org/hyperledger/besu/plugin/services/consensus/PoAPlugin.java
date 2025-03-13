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
package org.hyperledger.besu.plugin.services.consensus;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.consensus.configuration.PoACLIOptions;
import org.hyperledger.besu.plugin.services.consensus.jsonrpc.BftService;
import org.hyperledger.besu.plugin.services.consensus.jsonrpc.QbftGetValidatorsByBlockNumber;
import org.hyperledger.besu.plugin.services.query.BftQueryService;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The PoA plugin. */
public class PoAPlugin implements BesuPlugin, BesuEvents.BlockAddedListener {

  private static final Logger LOG = LoggerFactory.getLogger(PoAPlugin.class);
  private final PoACLIOptions options;
  private static final String NAME = "poa";
  private ServiceManager context;
  private BlockchainService blockchain;
  private BftService bftService;
  private RpcEndpointService rpcService;

  /** Instantiates a new PoA plugin. */
  public PoAPlugin() {
    this.options = PoACLIOptions.create();
  }

  @Override
  public void register(final ServiceManager context) {
    LOG.info("Registering PoA plugin");
    this.context = context;
    this.bftService = new BftService();

    final Optional<PicoCLIOptions> cmdlineOptions = context.getService(PicoCLIOptions.class);

    if (cmdlineOptions.isEmpty()) {
      throw new IllegalStateException(
          "Expecting a PicoCLI options to register CLI options with, but none found.");
    }

    cmdlineOptions.get().addPicoCLIOptions(NAME, options);

    final Optional<BlockchainService> blockchain = context.getService(BlockchainService.class);
    this.blockchain = blockchain.get();
    rpcService = context.getService(RpcEndpointService.class).get();
    QbftGetValidatorsByBlockNumber thing =
        new QbftGetValidatorsByBlockNumber(this.blockchain, bftService);
    rpcService.registerRPCEndpoint("qbft", "getValidatorsByBlockNumberV2", thing::response);

    LOG.debug("Plugin registered.");
  }

  @Override
  public void start() {
    LOG.info("Starting PoA plugin.");
    final Optional<BesuEvents> events = context.getService(BesuEvents.class);
    if (events.isPresent()) {
      events.get().addBlockAddedListener(this);
    }

    bftService.setBftService(context.getService(BftQueryService.class).get());
  }

  @Override
  public void stop() {
    LOG.info("Stopping PoA plugin.");
  }

  @Override
  public void onBlockAdded(final AddedBlockContext addedBlockContext) {
    LOG.debug("PoA plugin - block {} added", addedBlockContext.getBlockHeader().getNumber());
    LOG.debug(
        "PoA plugin - block requested: {}",
        blockchain
            .getBlockByNumber(addedBlockContext.getBlockHeader().getNumber())
            .get()
            .getBlockHeader()
            .getBlockHash());
    LOG.debug(
        "Block signers: {}",
        bftService.getBftService().getSignersFrom(addedBlockContext.getBlockHeader()));
  }
}
