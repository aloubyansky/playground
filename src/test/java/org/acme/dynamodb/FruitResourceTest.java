package org.acme.dynamodb;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestHTTPEndpoint(FruitResource.class)
class FruitResourceTest {

    @Inject
    FruitSyncService fruitSync;

    String initialFruitName = "Apple";

    @BeforeEach
    @Transactional
    public void setup() {
        fruitSync.deleteAll();
        var fruit = new Fruit();
        fruit.setName(initialFruitName);
        fruit.setDescription("An apple");
        fruitSync.add(fruit);
    }

    @Test
    void testHelloEndpoint() {
        given()
          .when().get()
          .then()
             .statusCode(200)
             .body(is("Hello AWS Community from Quarkus!"));
    }

    @Test
    void testAll() {
        given()
                .when().get("/all")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", is(1))
                .body("[0].name", is(initialFruitName));
    }

    @Test
    void testAdd() {
        given()
                .body(Map.of("name", "Peach", "description", "a peach"))
                .contentType(ContentType.JSON)
                .when().post()
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("name", is("Peach"));

        given()
                .when().get("/all")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].name", is("Apple"))
                .body("[1].name", is("Peach"));
    }

    @Test
    void edit() {
        given()
                .body(Map.of("name", initialFruitName, "description", "green apple"))
                .contentType(ContentType.JSON)
                .when().patch("/update")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("name", is("Apple"));

        given()
                .when().get("/all")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].name", is("Apple"))
                .body("[0].description", is("green apple"));
    }

    @Test
    void delete() {
        given()
                .body(Map.of("name", initialFruitName))
                .contentType(ContentType.JSON)
                .when().delete("/delete")
                .then()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        given()
                .when().get("/all")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", is(0));
    }
}