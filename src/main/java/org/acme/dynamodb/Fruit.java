package org.acme.dynamodb;

import io.quarkus.runtime.annotations.RegisterForReflection;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Objects;

@RegisterForReflection
public class Fruit {

    public static Fruit from(Map<String, AttributeValue> item) {
        Fruit fruit = new Fruit();
        if (item != null && !item.isEmpty()) {
            fruit.setName(item.get(AbstractService.FRUIT_NAME_COL).s());
            fruit.setDescription(item.get(AbstractService.FRUIT_DESC_COL).s());
        }
        return fruit;
    }

    private String name;
    private String description;

    public Fruit() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Fruit fruit = (Fruit) o;
        return Objects.equals(name, fruit.name) && Objects.equals(description, fruit.description);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(description);
        return result;
    }
}
