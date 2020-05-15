# Upgrading to Hyperledger Besu v1.5 

## Docker users with volume mounts 

To maintain best security practices, we've changed the `user:group` on the Docker containers to `besu/orion/etc` .

What this means for you:

* If you are running Besu and Orion as binaries, there is no impact.
* If you are running Besu and Orion as Docker containers *and* have volume mounts for data,  ensure that the 
permissions on the directory allow other users and groups to r/w. Ideally this should be set to
`besu:besu` and `orion:orion` as the owners. 

## Privacy users 

Besu minor version upgrades require upgrading Orion to the latest minor version. That is, for 
Besu <> Orion node pairs, when upgrading Besu to v1.5, it is required that Orion is upgraded to 
v1.6. Older versions of Orion will no longer work with Besu v1.5.  