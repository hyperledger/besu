#!/usr/bin/env groovy

import hudson.model.Result
import hudson.model.Run
import jenkins.model.CauseOfInterruption.UserInterruption

if (env.BRANCH_NAME == "master") {
    properties([
        buildDiscarder(
            logRotator(
                daysToKeepStr: '90'
            )
        )
    ])
} else {
    properties([
        buildDiscarder(
            logRotator(
                numToKeepStr: '10'
            )
        )
    ])
}

def docker_image = 'docker:18.06.0-ce-dind'
def build_image = 'pegasyseng/pantheon-build:0.0.5-jdk11'

def abortPreviousBuilds() {
    Run previousBuild = currentBuild.rawBuild.getPreviousBuildInProgress()

    while (previousBuild != null) {
        if (previousBuild.isInProgress()) {
            def executor = previousBuild.getExecutor()
            if (executor != null) {
                echo ">> Aborting older build #${previousBuild.number}"
                executor.interrupt(Result.ABORTED, new UserInterruption(
                    "Aborted by newer build #${currentBuild.number}"
                ))
            }
        }

        previousBuild = previousBuild.getPreviousBuildInProgress()
    }
}

abortPreviousBuilds()

try {
    parallel UnitTests: {
        def stage_name = "Unit tests node: "
        node {
            checkout scm
            docker.image(docker_image).withRun('--privileged') { d ->
                docker.image(build_image).inside("--link ${d.id}:docker") {
                    try {
                        stage(stage_name + 'Prepare') {
                            sh './gradlew --no-daemon --parallel clean compileJava compileTestJava assemble'
                        }
                        stage(stage_name + 'Unit tests') {
                            sh './gradlew --no-daemon --parallel build'
                        }
                    } finally {
                        archiveArtifacts '**/build/reports/**'
                        archiveArtifacts '**/build/test-results/**'
                        archiveArtifacts 'build/reports/**'
                        archiveArtifacts 'build/distributions/**'

                        junit '**/build/test-results/**/*.xml'
                    }
                }
            }
        }
    }, ReferenceTests: {
        def stage_name = "Reference tests node: "
        node {
            checkout scm
            docker.image(docker_image).withRun('--privileged') { d ->
                docker.image(build_image).inside("--link ${d.id}:docker") {
                    try {
                        stage(stage_name + 'Prepare') {
                            sh './gradlew --no-daemon --parallel clean compileJava compileTestJava assemble'
                        }
                        stage(stage_name + 'Reference tests') {
                            sh './gradlew --no-daemon --parallel referenceTest'
                        }
                    } finally {
                        archiveArtifacts '**/build/reports/**'
                        archiveArtifacts '**/build/test-results/**'
                        archiveArtifacts 'build/reports/**'
                        archiveArtifacts 'build/distributions/**'

                        junit '**/build/test-results/**/*.xml'
                    }
                }
            }
        }
    }, IntegrationTests: {
        def stage_name = "Integration tests node: "
        node {
            checkout scm
            docker.image(docker_image).withRun('--privileged') { d ->
                docker.image(build_image).inside("--link ${d.id}:docker") {
                    try {
                        stage(stage_name + 'Prepare') {
                            sh './gradlew --no-daemon --parallel clean compileJava compileTestJava assemble'
                        }
                        stage(stage_name + 'Integration Tests') {
                            sh './gradlew --no-daemon --parallel integrationTest'
                        }
                        stage(stage_name + 'Check Licenses') {
                            sh './gradlew --no-daemon --parallel checkLicenses'
                        }
                        stage(stage_name + 'Check javadoc') {
                            sh './gradlew --no-daemon --parallel javadoc'
                        }
                        stage(stage_name + 'Compile Benchmarks') {
                            sh './gradlew --no-daemon --parallel compileJmh'
                        }
                    } finally {
                        archiveArtifacts '**/build/reports/**'
                        archiveArtifacts '**/build/test-results/**'
                        archiveArtifacts 'build/reports/**'
                        archiveArtifacts 'build/distributions/**'

                        junit '**/build/test-results/**/*.xml'
                    }
                }
            }
        }
    }, AcceptanceTests: {
        def stage_name = "Acceptance tests node: "
        node {
            checkout scm
            docker.image(docker_image).withRun('--privileged') { d ->
                docker.image(build_image).inside("--link ${d.id}:docker") {
                    try {
                        stage(stage_name + 'Prepare') {
                            sh './gradlew --no-daemon --parallel clean compileJava compileTestJava assemble'
                        }
                        stage(stage_name + 'Acceptance Tests') {
                            sh './gradlew --no-daemon --parallel acceptanceTest'
                        }
                    } finally {
                        archiveArtifacts '**/build/reports/**'
                        archiveArtifacts '**/build/test-results/**'
                        archiveArtifacts 'build/reports/**'
                        archiveArtifacts 'build/distributions/**'

                        junit '**/build/test-results/**/*.xml'
                    }
                }
            }
        }
    }
} catch (e) {
    currentBuild.result = 'FAILURE'
} finally {
    // If we're on master and it failed, notify slack
    if (env.BRANCH_NAME == "master") {
        def currentResult = currentBuild.result ?: 'SUCCESS'
        def channel = '#priv-pegasys-prod-dev'
        if (currentResult == 'SUCCESS') {
            def previousResult = currentBuild.previousBuild?.result
            if (previousResult != null && (previousResult == 'FAILURE' || previousResult == 'UNSTABLE')) {
                slackSend(
                    color: 'good',
                    message: "Pantheon branch ${env.BRANCH_NAME} build is back to HEALTHY.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                    channel: channel
                )
            }
        } else if (currentBuild.result == 'FAILURE') {
            slackSend(
                color: 'danger',
                message: "Pantheon branch ${env.BRANCH_NAME} build is FAILING.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                channel: channel
            )
        } else if (currentBuild.result == 'UNSTABLE') {
            slackSend(
                color: 'warning',
                message: "Pantheon branch ${env.BRANCH_NAME} build is UNSTABLE.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                channel: channel
            )
        }
    }
}
