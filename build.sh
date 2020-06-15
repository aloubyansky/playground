mvn -f acme-common-1.0/pom.xml clean install
mvn -f acme-common-2.0/pom.xml clean install
mvn -f acme-app-lib/pom.xml clean install
mvn -f acme-quarkus-bom/pom.xml clean install
mvn -f acme-quarkus-bom-deployment/pom.xml clean install
mvn -f acme-quarkus-extension/pom.xml clean install
mvn -f acme-app/pom.xml clean package