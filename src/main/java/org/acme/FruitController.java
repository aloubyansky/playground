package org.acme;

import jakarta.transaction.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fruits")
public class FruitController {

    private final FruitRepository fruitRepository;

    public FruitController(FruitRepository fruitRepository) {
        this.fruitRepository = fruitRepository;
    }


    @GetMapping
    public Iterable<Fruit> findAll() {
        return fruitRepository.findAll();
    }

    @PostMapping
    @Transactional
    public Fruit add(Fruit fruit) {
        fruitRepository.save(fruit);
        return fruit;
    }

    @PatchMapping("/{id}")
    @Transactional
    public Fruit edit(@PathVariable(name = "id") long id, Fruit fruit) {
        var existing = fruitRepository.findById(id).get();
        existing.setName(fruit.getName());
        existing.setColor(fruit.getColor());
        fruitRepository.save(existing);
        return existing;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable(name = "id") long id) {
        var existing = fruitRepository.findById(id).get();
        fruitRepository.delete(existing);
    }
}
