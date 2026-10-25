package org.box1;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/api/info")
@Produces(MediaType.APPLICATION_JSON)
public class InfoResource {
    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @GET
    @Operation(summary = "Return application and Java runtime versions")
    public ApplicationInfo info() {
        return new ApplicationInfo("quarkus-reference", version, Runtime.version().feature());
    }

    public record ApplicationInfo(String name, String version, int javaVersion) {}
}
