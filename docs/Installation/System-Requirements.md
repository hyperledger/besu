title: System Requirements
description: System requirements to sync and run Pantheon 
<!--- END of page meta data -->

# System Requirements 

The system requirements for Pantheon depend on a number of factors: 

* Size of the world state for the network
* Number of transactions submitted to network 
* Block gas limit 
* Number and complexity of [JSON-RPC](../Pantheon-API/Using-JSON-RPC-API.md), [PubSub](../Pantheon-API/RPC-PubSub.md), 
or [GraphQL](../Pantheon-API/GraphQL.md) queries being handled by the node 

## Determining System Requirements  

To determine system requirements, monitor CPU and disk space requirements using [Prometheus](https://docs.pantheon.pegasys.tech/en/stable/Monitoring/Monitoring-Performance/#monitor-node-performance-using-prometheus). 
A sample [Grafana dashboard](https://grafana.com/grafana/dashboards/10273) is provided for Pantheon. 

!!! tip
    CPU requirements are highest when syncing to the network and typically reduce once the node is synchronized to the chain head. 

## RAM 

Pantheon requires 4GB of RAM. For Ethereum Mainnet, a minimum of 8GB of RAM is required 

## Disk Space 

Syncing to the Ethereum mainnet requires 3TB for a full sync. 
