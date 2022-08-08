FROM quay.io/keycloak/keycloak:18.0.2 as builder

COPY target/scim-user-spi-0.0.1-SNAPSHOT.jar /opt/keycloak/providers/scim-user-spi-0.0.1-SNAPSHOT.jar
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:18.0.2
COPY --from=builder /opt/keycloak/ /opt/keycloak/
WORKDIR /opt/keycloak

ENV KC_HOSTNAME=localhost
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]