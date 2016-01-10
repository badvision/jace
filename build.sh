#!/bin/sh

# Building Jace requies:
#
#   * maven
#   * Java 1.8
#
# On OSX the easiest way to install Maven is to use brew
#
#   brew install maven
#
#
# Troubleshooting:
# ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.3:compile (default-compile) on project jace: Fatal error compiling: invalid target release: 1.8 -> [Help 1]
# org.apache.maven.lifecycle.LifecycleExecutionException: Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.3:compile (default-compile) on project jace: Fatal error compiling
#
# Cause: You probably have Java 1.8 installed but Maven is (trying to) use Java 1.7
#  OR  : You probably have Java 1.7 installed but Maven is (trying to) use Java 1.8
# Reference: http://stackoverflow.com/questions/24705877/cant-get-maven-to-recognize-java-1-8
#
# Solution:
#
# 1. Install Java 1.8
#
# 2. Check JAVA_HOME is set:
#
# First, check which version of Java that Maven is using:
#
#   mvn -version
#
# Next, check which version of Java is:
#
#   java -version
#
# Verify what JAVA_HOME is:
#
#   echo ${JAVA_HOME}
#
# If it is blank (or not set), set it via:
#
#   export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
#
# Then you can build JACE
#
# Note: Changing the maven project file 'pom.xml' to use Java 1.7 *won't* work:
#            <plugin>
#                <groupId>org.apache.maven.plugins</groupId>
#                <artifactId>maven-compiler-plugin</artifactId>
#                <version>3.3</version>
#                <configuration>
#                    <source>1.7</source>
#                    <target>1.7</target>
#
# As the source code is using Java 1.8 langauge features.

if [[ -z "$JAVA_HOME" ]]; then
    echo "WARNING: JAVA_HOME was not set"
    echo "... Defaulting to Java 1.8..."
    export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
    echo "... JAVA_HOME=${JAVA_HOME}"
fi

#mvn clean install -X

mvn install -DskipTests=true -Dmaven.javadoc.skip=true -B -V

