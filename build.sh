#!/bin/sh

# Building Jace requies:
#
#   * Maven
#   * Java 1.8 JDK
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
# Cause: You probably have the Java 1.8 RUNTIME installed but Maven is (trying to) use the Java 1.7 COMPILER.
#  OR  : You probably have Java 1.7 installed but Maven is (trying to) use Java 1.8
# Reference: http://stackoverflow.com/questions/24705877/cant-get-maven-to-recognize-java-1-8
#
# 0. Here is some information to clear up the confusion about Java:
#
#    The JRE (runtime) is needed to RUN Java programs.
#    The JDK (compiler) is needed to COMPILTE Java programs.
#
# Solution:
#
# 1. Check which verison of Java that Maven is using
#
#   mvn -version
#
# 2. Check which version of the Java JRE is installed:
#
#   java -version
#
# You should see something like this:
#
#       java version "1.8.0_66"
#       Java(TM) SE Runtime Environment (build 1.8.0_66-b17)
#
# 3. Check which version of the Java JDK is installed:
#
#   javac -version
#
# If you see something like this:
#
#      javac 1.7.0_75
#
# Then you will need to proceed to the next step, else you can skip it.
#
# 4. Install Java 1.8 JDK (if necessary)
#
# You can download the JDK either via the GUI or the command line:
#   http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
#
# To download from the command line:
#
#   For OSX
#     curl -L -O -H "Cookie: oraclelicense=accept-securebackup-cookie" -k "https://edelivery.oracle.com/otn-pub/java/jdk/8u66-b17/jdk-8u66-macosx-x64.dmg"
#     open jdk-8u66-macosx-x64.dmg
#     Double-click on the .pkg
#
#   For Linux
#     curl -L -O -H "Cookie: oraclelicense=accept-securebackup-cookie" -k "https://edelivery.oracle.com/otn-pub/java/jdk/8u20-b26/jdk-8u20-linux-i586.tar.gz"
#
#   Reference:
#     Commands / shell script to download JDK / JRE / Java binaries from Oracle website from terminal / shell / command line / command prompt.
#     https://gist.github.com/P7h/9741922
#
# 5. Lastly, verify that JAVA_HOME is set:
#
#   echo ${JAVA_HOME}
#
# If it is blank (or not set), set it via:
#
#   export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
#
# Then you can (finally!) build JACE. Whew!
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

