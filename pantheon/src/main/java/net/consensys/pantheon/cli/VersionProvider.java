package net.consensys.pantheon.cli;

import net.consensys.pantheon.PantheonInfo;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {
  @Override
  public String[] getVersion() {
    return new String[] {PantheonInfo.version()};
  }
}
