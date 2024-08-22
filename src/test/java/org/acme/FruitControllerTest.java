package org.acme;

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
@TestHTTPEndpoint(FruitController.class)
class FruitControllerTest {

    @Inject
    FruitRepository fruitRepository;
    long fruitId;

    @BeforeEach
    @Transactional
    public void setup() {
        fruitRepository.deleteAll();
        var fruit = new Fruit();
        fruit.setName("Apple");
        fruit.setColor("Green");
        fruitRepository.save(fruit);
        fruitId = fruit.getId();
    }

    @Test
    void findAll() {
        given()
          .when().get()
          .then()
             .statusCode(HttpStatus.SC_OK)
             .body("[0].name", is("Apple"));
    }

    @Test
    void add() {
        given()
                .body(Map.of("name", "Peach", "color", "orange"))
                .contentType(ContentType.JSON)
                .when().post()
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("name", is("Peach"));

        given()
                .when().get()
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].name", is("Apple"))
                .body("[1].name", is("Peach"));
    }

    @Test
    void edit() {
        given()
                .body(Map.of("name", "Apple", "color", "brown"))
                .contentType(ContentType.JSON)
                .when().patch("/" + fruitId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("name", is("Apple"));

        given()
                .when().get()
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].name", is("Apple"))
                .body("[0].color", is("brown"));
    }

    @Test
    void delete() {
        given()
                .when().delete(String.valueOf(fruitId))
                .then()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        given()
                .when().get()
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", is(0));
    }

}