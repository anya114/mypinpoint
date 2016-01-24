cd bootstrap-core
mvn -o clean install -Dmaven.test.skip=true
cd ../profiler
mvn -o clean install  -Dmaven.test.skip=true
cd ../plugins
mvn -o clean install -Dmaven.test.skip=true
cd ../agent
mvn -o clean install -Dmaven.test.skip=true
