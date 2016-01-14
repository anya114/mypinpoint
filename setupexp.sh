agentFile=pinpoint-agent-1.5.1-SNAPSHOT.tar.gz
tomcatPath=~/Desktop/apache-tomcat-8.0.21
cp agent/target/$agentFile $tomcatPath/bin
cd $tomcatPath/bin
tar zxf $agentFile --exclude=pinpoint.config --overwrite -C pinpoint-agent-1.5.1-SNAPSHOT 
