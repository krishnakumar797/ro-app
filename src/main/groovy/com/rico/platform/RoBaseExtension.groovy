package com.rico.platform

import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

class RoBaseExtension {
    String security
    String dataBase
    String persistence
    String multitenancy
    String keyvaluestore
    String cache
    String queue
    String grpc
    String rest
    String web
    String search
    String logging
    String unitTest
    String modelMapper
    String beanValidation
    String javaModule

    IdentityManager identityManager

    Project project

    RoBaseExtension(Instantiator instantiator,
                      Project project) {
        this.project = project
    }

    static class IdentityManager {

        enum IdmName {
            UAA, KEYCLOAK
        }

        IdmName UAA = IdmName.UAA
        IdmName KEYCLOAK = IdmName.KEYCLOAK
        IdmName idmName
        String uaaClient, uaaResourceServer
        Project project

        IdentityManager(Project project) {
            this.project = project
        }
    }

    IdentityManager identityManager(Closure closure) {
        identityManager = new IdentityManager(this.project)
        this.project.configure(identityManager, closure)
        return identityManager
    }

}
