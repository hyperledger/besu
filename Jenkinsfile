#!/usr/bin/env groovy

import hudson.model.Result
import hudson.model.Run
import jenkins.model.CauseOfInterruption.UserInterruption

def shouldPublish() {
    return env.BRANCH_NAME == 'master' || env.BRANCH_NAME ==~ /^release-\d+\.\d+/
}

if (shouldPublish()) {
    properties([
        buildDiscarder(
            logRotator(
                daysToKeepStr: '30', artifactDaysToKeepStr: '7'
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

def docker_image_dind = 'docker:18.06.0-ce-dind'
def docker_image = 'docker:18.06.0-ce'
def build_image = 'pegasyseng/pantheon-build:0.0.7-jdk11'

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

if (!shouldPublish()) {
    abortPreviousBuilds()
}

try {
    timeout(time: 1, unit: 'HOURS') {
        parallel UnitTests: {
            def stage_name = "Unit tests node: "
            node {
                checkout scm
                docker.image(docker_image_dind).withRun('--privileged') { d ->
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

                            stash allowEmpty: true, includes: 'build/distributions/pantheon-*.tar.gz', name: 'distTarBall'

                            junit '**/build/test-results/**/*.xml'
                        }
                    }
                }
            }
        }, ReferenceTests: {
            def stage_name = "Reference tests node: "
            node {
                checkout scm
                docker.image(docker_image_dind).withRun('--privileged') { d ->
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
                docker.image(docker_image_dind).withRun('--privileged') { d ->
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
                docker.image(docker_image_dind).withRun('--privileged') { d ->
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

        parallel DockerImage: {
                def stage_name = 'Docker image node: '
                def docker_folder = 'docker'
                def reports_folder = docker_folder + '/reports'
                def dockerfile = docker_folder + '/Dockerfile'
                def version = ''
                def image = ''
                node {
                    checkout scm
                    docker.image(build_image).inside() {
                        stage(stage_name + 'Dockerfile lint') {
                            sh "docker run --rm -i hadolint/hadolint < ${dockerfile}"
                        }

                        stage(stage_name + 'Build image') {
                            sh './gradlew distDocker'
                        }

                        stage(stage_name + 'Calculate variables') {
                            def gradleProperties = readProperties file: 'gradle.properties'
                            version = gradleProperties.version
                            def imageRepos = 'pegasyseng'
                            image = "${imageRepos}/pantheon:${version}"
                        }

                        stage(stage_name + "Test image labels") {
                            shortCommit = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
                            sh "docker image inspect \
    --format='{{index .Config.Labels \"org.label-schema.vcs-ref\"}}' \
    ${image} \
    | grep ${shortCommit}"
                            sh "docker image inspect \
    --format='{{index .Config.Labels \"org.label-schema.version\"}}' \
    ${image} \
    | grep ${version}"
                        }

                        try {
                            stage(stage_name + 'Test image') {
                                sh "mkdir -p ${reports_folder}"
                                sh "cd ${docker_folder} && bash test.sh ${image}"
                            }
                        } finally {
                            archiveArtifacts "${reports_folder}/**"
                            junit "${reports_folder}/*.xml"
                            sh "rm -rf ${reports_folder}"
                        }

                        if (shouldPublish()) {
                            def registry = 'https://registry.hub.docker.com'
                            def userAccount = 'dockerhub-pegasysengci'
                            stage(stage_name + 'Push image') {
                                docker.withRegistry(registry, userAccount) {
                                    docker.image(image).push()

                                    def additionalTags = []
                                    if (env.BRANCH_NAME == 'master') {
                                        additionalTags.add('develop')
                                    }

                                    if (! version ==~ /.*-SNAPSHOT/) {
                                        additionalTags.add('latest')
                                        additionalTags.add(version.split(/\./)[0..1].join('.'))
                                    }

                                    additionalTags.each { tag ->
                                        docker.image(image).push tag.trim()
                                    }
                                }
                            }
                        }
                    }
                }
        }, BintrayPublish: {
            def stage_name = "Bintray publish node: "
            node {
                if (shouldPublish()) {
                    checkout scm

                    docker.image(docker_image_dind).withRun('--privileged') { d ->
                        docker.image(build_image).inside("--link ${d.id}:docker") {
                            stage(stage_name + 'Prepare') {
                                sh './gradlew --no-daemon --parallel clean assemble'
                            }
                            stage(stage_name + 'Publish') {
                                withCredentials([
                                usernamePassword(
                                    credentialsId: 'pegasys-bintray',
                                    usernameVariable: 'BINTRAY_USER',
                                    passwordVariable: 'BINTRAY_KEY'
                                )
                                ]) {
                                    sh './gradlew --no-daemon --parallel bintrayUpload'
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} catch (e) {
    currentBuild.result = 'FAILURE'
} finally {
    // If we're on master and it failed, notify slack
    if (shouldPublish()) {
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
