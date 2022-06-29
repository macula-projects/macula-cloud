package org.macula.cloud.tinyid;

import org.junit.jupiter.api.Test;
import org.macula.boot.starter.tinyid.base.factory.IdGeneratorFactory;
import org.macula.boot.starter.tinyid.base.generator.IdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ServerTest {

    @Autowired
    IdGeneratorFactory idGeneratorFactory;

    @Test
    public void testNextId() {
        IdGenerator idGenerator = idGeneratorFactory.getIdGenerator("test");
        Long id = idGenerator.nextId();
        System.out.println("current id is: " + id);
    }
}
