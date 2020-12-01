package es.um.asio.service.model.relational;

import data.DataGenerator;
import es.um.asio.service.TestDiscoveryApplication;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestDiscoveryApplication.class)
@ExtendWith(SpringExtension.class)
class AttributeTest {

    Set<Attribute> attributes;
    DataGenerator dg;

    @PostConstruct
    public void init() throws Exception {
        attributes = new HashSet<>();
        dg = new DataGenerator();
        JobRegistry jobRegistry = dg.getJobRegistry();

        for(ObjectResult or : jobRegistry.getObjectResults()) {
            attributes.addAll(or.getAttributes());
        }
    }

    @Test
    void testEquals() {
        for (Attribute att : attributes) {
            for (Attribute attInner : attributes) {
                if (att.hashCode() == attInner.hashCode())
                    Assert.assertTrue(att.equals(attInner));
                else
                    Assert.assertFalse(att.equals(attInner));
            }
        }
    }

    @Test
    void testHashCode() {
        for (Attribute att : attributes) {
            for (Attribute attInner : attributes) {
                if (att.equals(attInner))
                    Assert.assertTrue(att.hashCode() == attInner.hashCode());
                else
                    Assert.assertFalse(att.hashCode() == attInner.hashCode());
            }
        }
    }

    @Test
    void setId() {
        for (Attribute att : attributes) {
            long id = new Random().nextLong();
            att.setId(id);
            Assert.assertTrue(att.getId() == id);
        }
    }

    @Test
    void setKey() {
        for (Attribute att : attributes) {
            att.setKey("key");
            Assert.assertTrue(att.getKey().equals("key"));
        }
    }

    @Test
    void setObjectResult() {
        ObjectResult or = new ArrayList<>(dg.getJobRegistry().getObjectResults()).get(0);
        for (Attribute att : attributes) {
            att.setObjectResult(or);
            Assert.assertTrue(att.getObjectResult().equals(or));
        }
    }

    @Test
    void setValues() {
        int counter = 0;
        for (Attribute att : attributes) {
            Set<Value> values = new HashSet<>();
            values.add(new Value(att,new Integer(++counter)));
            att.setValues(values);
            Assert.assertTrue(att.getValues().equals(values));
        }
    }

    @Test
    void setParentValue() {
        int counter = 0;
        for (Attribute att : attributes) {
            Value v = new Value(att,new Integer(++counter));
            att.setParentValue(v);
            Assert.assertTrue(att.getParentValue().equals(v));
        }
    }

    @Test
    void getId() {
        for (Attribute att : attributes) {
            long id = new Random().nextLong();
            Assert.assertTrue(att.getId());
        }
    }

    @Test
    void getKey() {
    }

    @Test
    void getObjectResult() {
    }

    @Test
    void getValues() {
    }

    @Test
    void getParentValue() {
    }
}