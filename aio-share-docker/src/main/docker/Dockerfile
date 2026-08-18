FROM ${docker.share.image}:${alfresco.share.docker.version}

USER root

ARG TOMCAT_DIR=/usr/local/tomcat

# =======================================================
# 1. HIGHEST PRIORITY: Remove Google Docs Module
# =======================================================
# We uninstall from the exploded directory, then delete the WAR file 
# so Tomcat does NOT overwrite our modified directory on startup.
RUN echo "=== Uninstalling Google Docs from the exploded directory ===" && \
    java -jar $TOMCAT_DIR/alfresco-mmt/alfresco-mmt*.jar uninstall org.alfresco.integrations.share.google.docs $TOMCAT_DIR/webapps/share || true && \
    echo "=== Deleting share.war to prevent Tomcat auto-deploy reverts ===" && \
    rm -f $TOMCAT_DIR/webapps/share.war

# =======================================================
# 2. Your original customizations
# =======================================================

# Copy Dockerfile to avoid an error if no JARs exist
COPY Dockerfile extensions/*.jar $TOMCAT_DIR/webapps/share/WEB-INF/lib/

# Copy Dockerfile to avoid an error if no AMPs exist
COPY Dockerfile extensions/*.amp $TOMCAT_DIR/amps_share/
RUN java -jar $TOMCAT_DIR/alfresco-mmt/alfresco-mmt*.jar install \
              $TOMCAT_DIR/amps_share $TOMCAT_DIR/webapps/share -directory -nobackup -force

COPY share-config-custom.xml $TOMCAT_DIR/shared/classes/alfresco/web-extension
COPY share-config-custom-dev.xml $TOMCAT_DIR/shared/classes/alfresco/web-extension

COPY log4j2.properties $TOMCAT_DIR/webapps/share/WEB-INF/classes
COPY hotswap-agent.properties $TOMCAT_DIR/webapps/share/WEB-INF/classes

USER ${USERNAME}