/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.RegistrationContext;
import org.hyperledger.besu.plugin.StartContext;

/**
 * A plugin in the default package, so that its id is also its simple class name and equals the
 * simple class name of the nested {@code Plugin} test classes of {@code BesuPluginContextImplTest},
 * which selects it by id through {@code Class.forName} since a named package cannot import it.
 */
public class Plugin implements BesuPlugin {
  @Override
  public void register(final RegistrationContext context) {}

  @Override
  public void start(final StartContext context) {}

  @Override
  public void stop() {}
}
