package org.box1;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class GreetingResourceTest {
    @Test
    void returnsJsonGreetingAndDefault() {
        given().queryParam("name", "Ada").when().get("/api/greetings")
            .then().statusCode(200).body("message", is("Hello, Ada!"))
            .body("service", is("quarkus-reference"));
        given().when().get("/api/greetings").then().statusCode(200)
            .body("message", is("Hello, developer!"));
    }

    @Test
    void rejectsBlankName() {
        given().queryParam("name", " ").when().get("/api/greetings").then().statusCode(400);
    }

    @Test
    void exposesHealthAndJava21() {
        for (String path : new String[]{"/q/health/live", "/q/health/ready", "/q/health/started"}) {
            given().when().get(path).then().statusCode(200).body("status", is("UP"));
        }
        given().when().get("/api/info").then().statusCode(200)
            .body("name", is("quarkus-reference")).body("javaVersion", is(21));
    }

    @Test
    void publishesOpenApiAndPrometheusMetrics() {
        given().accept("application/json").when().get("/q/openapi").then().statusCode(200)
            .body("info.title", is("Box1 Quarkus Reference"))
            .body("paths.keySet()", hasItems("/api/greetings", "/api/info"));
        given().when().get("/api/greetings").then().statusCode(200);
        given().when().get("/q/metrics").then().statusCode(200)
            .body(containsString("box1_greetings_total"));
    }
}
