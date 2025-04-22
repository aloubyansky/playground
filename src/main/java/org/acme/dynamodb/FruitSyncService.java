package org.acme.dynamodb;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class FruitSyncService extends AbstractService {

    @Inject
    DynamoDbClient dynamoDB;

    public List<Fruit> findAll() {
        return dynamoDB.scanPaginator(scanRequest()).items().stream()
                .map(Fruit::from)
                .collect(Collectors.toList());
    }

    public void deleteAll() {
        findAll().forEach(fruit -> dynamoDB.deleteItem(deleteRequest(fruit)));
    }

    public void delete(Fruit fruit) {
        dynamoDB.deleteItem(deleteRequest(fruit));
    }

    public void add(Fruit fruit) {
        dynamoDB.putItem(putRequest(fruit));
    }

    public void update(Fruit fruit) {
        dynamoDB.updateItem(updateRequest(fruit));
    }

    public Fruit get(String name) {
        return Fruit.from(dynamoDB.getItem(getRequest(name)).item());
    }
}
