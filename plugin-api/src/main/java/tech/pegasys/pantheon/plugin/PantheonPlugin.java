/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.plugin;

/**
 * Base interface for Pantheon plugins.
 *
 * <p>Plugins are discovered and loaded using {@link java.util.ServiceLoader} from jar files within
 * Pantheon's plugin directory. See the {@link java.util.ServiceLoader} documentation for how to
 * register plugins.
 */
public interface PantheonPlugin {

  /**
   * Called when the plugin is first registered with Pantheon. Plugins are registered very early in
   * the Pantheon life-cycle and should use this callback to register any command line options
   * required via the PicoCLIOptions service.
   *
   * <p>The <code>context</code> parameter should be stored in a field in the plugin. This is the
   * only time it will be provided to the plugin and is how the plugin will interact with Pantheon.
   *
   * <p>Typically the plugin will not begin operation until the {@link #start()} method is called.
   *
   * @param context the context that provides access to Pantheon services.
   */
  void register(PantheonContext context);

  /**
   * Called once Pantheon has loaded configuration and is starting up. The plugin should begin
   * operation, including registering any event listener with Pantheon services and starting any
   * background threads the plugin requires.
   */
  void start();

  /**
   * Called when the plugin is being stopped. This method will be called as part of Pantheon
   * shutting down but may also be called at other times to disable the plugin.
   *
   * <p>The plugin should remove any registered listeners and stop any background threads it
   * started.
   */
  void stop();
}
