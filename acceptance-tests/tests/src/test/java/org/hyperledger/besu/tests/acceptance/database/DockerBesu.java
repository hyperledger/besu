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

package org.hyperledger.besu.tests.acceptance.database;

import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class DockerBesu {
  private static final Logger LOG = LogManager.getLogger();

  static final String containerDataPath = "/home/besu";
  private final DockerClient dockerClient;

  private DockerBesu(final DockerClient dockerClient) {
    this.dockerClient = dockerClient;
  }

  static DockerBesu createDockerClient() {
    return new DockerBesu(
        DockerClientBuilder.getInstance(
                DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("unix:///var/run/docker.sock")
                    .build())
            .build());
  }

  void pull(final String image, final String tag) throws Exception {
    dockerClient
        .pullImageCmd(image)
        .withTag(tag)
        .exec(new PullImageResultCallback())
        .awaitCompletion(2, TimeUnit.MINUTES);
  }

  void runBesu(final String dockerImage, final String hostDataPath, final String... cmds)
      throws Exception {
    CreateContainerResponse container =
        dockerClient
            .createContainerCmd(dockerImage)
            .withBinds(Bind.parse(String.format("%s:%s", hostDataPath, containerDataPath)))
            .withCmd(cmds)
            .exec();
    dockerClient.startContainerCmd(container.getId()).exec();
    // showLog(container.getId());
    final WaitContainerResultCallback callback =
        dockerClient.waitContainerCmd(container.getId()).exec(new WaitContainerResultCallback());
    callback.awaitCompletion(1, TimeUnit.MINUTES);
    // callback.awaitCompletion().close();
    try {
      dockerClient.stopContainerCmd(container.getId()).exec();
      dockerClient.removeContainerCmd(container.getId()).exec();
    } catch (Exception e) {
      LOG.warn("Cannot stop and remove container: {}", e.getClass().getName());
    }
  }

  /*private void showLog(final String containerId) {
    dockerClient
        .logContainerCmd(containerId)
        .withStdOut(true)
        .withStdErr(true)
        .withFollowStream(true)
        .withTail(1000)
        .exec(
            new LogContainerResultCallback() {
              @Override
              public void onNext(final Frame item) {
                System.out.println(new String(item.getPayload(), UTF_8));
              }
            });
  }*/

  DockerClient getDockerClient() {
    return dockerClient;
  }
}
