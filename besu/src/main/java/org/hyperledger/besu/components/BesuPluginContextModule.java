package org.hyperledger.besu.components;

import org.hyperledger.besu.plugin.BesuContext;
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

  @Provides
  @Named("besuPluginContext")
  @Singleton
  public BesuContext provideBesuPluginContext() {
    return new BesuPluginContextImpl();
  }
}
