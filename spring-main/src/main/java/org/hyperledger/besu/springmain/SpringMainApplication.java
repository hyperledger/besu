/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.springmain;

import org.hyperledger.besu.springmain.config.properties.BesuProperties;
import org.hyperledger.besu.springmain.config.properties.EngineRpcProperties;
import org.hyperledger.besu.springmain.config.properties.GenesisProperties;
import org.hyperledger.besu.springmain.config.properties.JsonRPCHttpProperties;
import org.hyperledger.besu.springmain.config.properties.MetricsProperties;
import org.hyperledger.besu.springmain.config.properties.MinerOptionProperties;
import org.hyperledger.besu.springmain.config.properties.MiningOptionsProperties;
import org.hyperledger.besu.springmain.config.properties.P2PProperties;
import org.hyperledger.besu.springmain.config.properties.TXPoolProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        BesuProperties.class,
        EngineRpcProperties.class,
        GenesisProperties.class,
        JsonRPCHttpProperties.class,
        MetricsProperties.class,
        MinerOptionProperties.class,
        MiningOptionsProperties.class,
        P2PProperties.class,
        TXPoolProperties.class
})
public class SpringMainApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SpringMainApplication.class, args);
    }

}
