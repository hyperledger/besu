import requests
import argparse


def create_artifact_paths(version):
    artifacts = []
    artifacts_base_path = "https://hyperledger.jfrog.io/hyperledger/besu-maven/org/hyperledger/besu"
    # add to this list here to update the list of artifacts to check 
    besu_paths = [
        f"evm/{version}/evm-{version}",
        f"plugin-api/{version}/plugin-api-{version}",
        f"internal/metrics-core/{version}/metrics-core-{version}",
        f"internal/core/{version}/core-{version}",
        f"internal/config/{version}/config-{version}"
    ]
    for path in besu_paths:
        artifacts.append(f"{artifacts_base_path}/{path}.module")
        artifacts.append(f"{artifacts_base_path}/{path}.pom")
        artifacts.append(f"{artifacts_base_path}/{path}.jar")
    return artifacts

def check_url(url):
    print(f"Checking artifact at: {url}")
    r = requests.head(url)
    if (r.status_code != 200):
        raise Exception(f"Sorry, No artifact found at '{url}' !!!") 

def main():
    parser = argparse.ArgumentParser(description='Check besu artifacts')
    parser.add_argument('--besu_version', action="store", dest='besu_version', default="")
    args = parser.parse_args()
    print(args.besu_version)

    artifacts = create_artifact_paths(args.besu_version)
    print(artifacts)
    for url in artifacts:
        check_url(url)

if __name__ == "__main__":
    main()