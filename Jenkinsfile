#!/usr/bin/env groovy

pipeline {
    agent {
        label 'java8'
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'build/test-results/**/*.xml'
        }

        success {
            ircSendSuccess()
        }

        failure {
            ircSendFailure()
        }
    }

    environment {
        GRADLE_OPTIONS = "--no-daemon --rerun-tasks -PBUILD_NUMBER=${env.BUILD_NUMBER} -PBRANCH=\"${env.BRANCH_NAME}\""
    }

    stages {
        stage('Checkout') {
            steps {
                ircSendStarted()

                checkout scm
                sh "rm -Rv build || true"
            }
        }

        stage('Build & Test') {
            steps {
                sh "./gradlew ${env.GRADLE_OPTIONS} clean build test"
                sh "./gradlew ${env.GRADLE_OPTIONS} generatePomFileForMavenJavaPublication"
            }
        }

        stage('Coverage') {
            environment {
                CODECOV_TOKEN = credentials('chat.willow.burrow.codecov')
            }

            steps {
                sh "./gradlew ${env.GRADLE_OPTIONS} jacocoTestReport"
                step([$class: 'JacocoPublisher'])

                sh "./codecov.sh -B ${env.BRANCH_NAME}"
            }
        }

        stage('Archive') {
            steps {
                archive includes: 'build/libs/*.jar'
            }
        }

        stage('Deploy') {
            steps {
                sh "find build/libs -name Burrow\\*${env.BUILD_NUMBER}.jar | head -n 1 | xargs -I '{}' mvn install:install-file -Dfile={} -DpomFile=build/publications/mavenJava/pom-default.xml -DlocalRepositoryPath=/var/www/maven.hopper.bunnies.io"
                sh "find build/libs -name Burrow\\*sources.jar | head -n 1 | xargs -I '{}' mvn install:install-file -Dfile={} -Dclassifier=sources -DpomFile=build/publications/mavenJava/pom-default.xml -DlocalRepositoryPath=/var/www/maven.hopper.bunnies.io"
                sh "find build/libs -name Burrow\\*all.jar | head -n 1 | xargs -I '{}' docker build -t carrot/burrow-testnet:latest --build-arg jar={} --build-arg config=\"burrow.yaml\" ."
                sh "docker rm --force burrow-testnet || true"
                sh "docker run --name burrow-testnet --detach --publish 6660:6660 --publish 6661:6661 carrot/burrow-testnet:latest || true"
            }
        }
    }
}
