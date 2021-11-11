#! /bin/bash
# publish the platform to the registry
jbang catalog_publish@quarkusio --working-directory=. --registry-url=http://localhost:8085 --token=test --all
