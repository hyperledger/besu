#!/usr/bin/env groovy

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

stage('Pantheon tests') {
    parallel javaTests: {
        node {
            checkout scm
            docker.image('docker:18.06.0-ce-dind').withRun('--privileged') { d ->
                docker.image('pegasyseng/pantheon-build:0.0.1').inside("--link ${d.id}:docker") {
                    try {
                        stage('Compile') {
                            sh './gradlew --no-daemon --parallel clean compileJava'
                        }
                        stage('compile tests') {
                            sh './gradlew --no-daemon --parallel compileTestJava'
                        }
                        stage('assemble') {
                            sh './gradlew --no-daemon --parallel assemble'
                        }
                        stage('Build') {
                            sh './gradlew --no-daemon --parallel build'
                        }
                        stage('Reference tests') {
                            sh './gradlew --no-daemon --parallel referenceTest'
                        }
                        stage('Integration Tests') {
                            sh './gradlew --no-daemon --parallel integrationTest'
                        }
                        stage('Acceptance Tests') {
                            sh './gradlew --no-daemon --parallel acceptanceTest'
                        }
                        stage('Check Licenses') {
                            sh './gradlew --no-daemon --parallel checkLicenses'
                        }
                        stage('Check javadoc') {
                            sh './gradlew --no-daemon --parallel javadoc'
                        }
                        stage('Jacoco root report') {
                            sh './gradlew --no-daemon jacocoRootReport'
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
    }, quickstartTests: {
        node {
            checkout scm
            docker.image('docker:18.06.0-ce-dind').withRun('--privileged') { d ->
                docker.image('pegasyseng/pantheon-build:0.0.1').inside("--link ${d.id}:docker") {
                    try {
                        stage('Docker quickstart Tests') {
                            sh 'DOCKER_HOST=tcp://docker:2375 ./gradlew --no-daemon --parallel clean dockerQuickstartTest'
                        }
                    } finally {
                        archiveArtifacts '**/build/test-results/**'
                        archiveArtifacts '**/build/reports/**'

                        junit '**/build/test-results/**/*.xml'
                    }
                }
            }
        }
    }
}

// If we're on master and it failed, notify slack
if (env.BRANCH_NAME == "master") {
    if (currentBuild.result != 'SUCCESSFUL') {
        def channel = '#priv-pegasys-prod-dev'
        if (currentBuild.result == 'FAILURE') {
            slackSend(
                color: 'danger',
                msg: "Pantheon master branch build is FAILING.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                channel: channel
            )
        } else if (currentBuild.result == 'UNSTABLE') {
            slackSend(
                color: 'warning',
                msg: "Pantheon master branch build is UNSTABLE.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                channel: channel
            )
        }
    }
}
