# Upgrading to Hyperledger Besu v1.5 

## Docker users with volume mounts 

To maintain best security practices, we're changing the `user:group` on the Docker container to `besu`.

What this means for you:

* If you are running Besu as a binary, there is no impact.
* If you are running Besu as a Docker container *and* have a volume mount for data,  ensure that the 
permissions on the directory allow other users and groups to r/w. Ideally this should be set to
`besu:besu` as the owner.

Note that the `besu` user only exists within the container not outside it. The same user ID may match
a different user outside the image.

If you’re mounting local folders, it is best to set the user via the Docker `—user` argument. Use the
UID because the username may not exist inside the docker container. Ensure the directory being mounted
is owned by that user.

## Privacy users 

Besu minor version upgrades require upgrading Orion to the latest minor version. That is, for 
Besu <> Orion node pairs, when upgrading Besu to v1.5, it is required that Orion is upgraded to 
v1.6. Older versions of Orion will no longer work with Besu v1.5.  