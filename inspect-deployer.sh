#!/bin/bash
set -e
docker exec docker-aio-acs-1 sh -c '
JAR=/usr/local/tomcat/webapps/alfresco/WEB-INF/lib/alfresco-repository-26.1.0.61.jar
command -v unzip >/dev/null && echo "unzip: yes" || echo "unzip: no"
command -v jar >/dev/null && echo "jar: yes" || echo "jar: no"
command -v python3 >/dev/null && echo "python3: yes" || echo "python3: no"
command -v python >/dev/null && echo "python: yes" || echo "python: no"
'