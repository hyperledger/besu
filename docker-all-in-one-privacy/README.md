# Besu All in One Image

An all in one Besu + Orion image that uses about 0.5 GB of RAM and one vCPU worth of hardware resources idling.
This makes it much easier to pull up for tests that are about functionality rather than running a production grade ledger.


## Build an image locally

```sh
docker build . -t besu-all-in-one:latest
```


## Shell into a running container:

```sh
docker run -it --entrypoint bash besu-all-in-one
```


## Easiest way to run the image with defaults

```sh
docker-compose up
```

The following ports are open on the container:

```yaml
- 8545:8545/tcp # besu RPC - HTTP
- 8546:8546/tcp # besu RPC - WebSocket
- 8888:8888/tcp # orion - HTTP
- 9001:9001/tcp # supervisord - HTTP
- 9545:9545/tcp # besu metrics
```

## Without docker-compose

```sh
docker run -p 0.0.0.0:8545:8545/tcp  -p 0.0.0.0:8546:8546/tcp  -p 0.0.0.0:8888:8888/tcp  -p 0.0.0.0:9001:9001/tcp  -p 0.0.0.0:9545:9545/tcp besu-all-in-one:latest
```

## Logs of Besu and Orion via supervisord web UI:

Navigate your browser to http://localhost:9001

