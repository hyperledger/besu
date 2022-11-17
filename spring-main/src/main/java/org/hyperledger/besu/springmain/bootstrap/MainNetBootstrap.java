/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.springmain.bootstrap;

import org.hyperledger.besu.ethereum.p2p.network.NetworkRunner;
import org.hyperledger.besu.nat.NatService;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class MainNetBootstrap {
    private static final Logger LOG = getLogger(MainNetBootstrap.class);

    final NatService natService;

    final NetworkRunner networkRunner;

    public MainNetBootstrap(final NatService natService, final NetworkRunner networkRunner) {
        this.natService = natService;
        this.networkRunner = networkRunner;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void doSomethingAfterStartup() {
        LOG.info("starting NAT service");
        natService.start();
        LOG.info("starting network runner");
        networkRunner.start();
    }
}
