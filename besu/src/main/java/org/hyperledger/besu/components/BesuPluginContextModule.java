package org.hyperledger.besu.components;

import org.hyperledger.besu.services.BesuPluginContextImpl;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * A dagger module that know how to create the BesuCommand, which collects all configuration
 * settings.
 */
@Module
public class BesuPluginContextModule {

  /**
   * Creates a BesuPluginContextImpl, used for plugin service discovery.
   *
   * @return the BesuPluginContext
   */
  @Provides
  @Named("besuPluginContext")
  @Singleton
  public BesuPluginContextImpl provideBesuPluginContext() {
    return new BesuPluginContextImpl();
  }
}
