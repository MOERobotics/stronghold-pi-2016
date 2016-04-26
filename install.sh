#!/bin/bash
sudo apt-get install -y openjdk-8-jdk ant wiringpi & git clone https://github.com/mailmindlin/v4l4j.git v4l4j
pushd v4l4j
#Install v4l4j
sudo ant uninstall clean compile all install
popd
echo Finished installing.