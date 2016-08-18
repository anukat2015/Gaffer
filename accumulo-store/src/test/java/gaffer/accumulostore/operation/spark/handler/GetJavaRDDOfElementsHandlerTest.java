/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gaffer.accumulostore.operation.spark.handler;

import gaffer.data.element.Edge;
import gaffer.data.element.Element;
import gaffer.data.element.Entity;
import gaffer.data.elementdefinition.view.View;
import gaffer.graph.Graph;
import gaffer.operation.OperationException;
import gaffer.operation.data.EntitySeed;
import gaffer.operation.impl.add.AddElements;
import gaffer.operation.simple.spark.GetJavaRDDOfElementsOperation;
import gaffer.user.User;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GetJavaRDDOfElementsHandlerTest {

    private final static String ENTITY_GROUP = "BasicEntity";
    private final static String EDGE_GROUP = "BasicEdge";

    @Test
    public void checkGetCorrectElementsInJavaRDD() throws OperationException {
        final Graph graph1 = new Graph.Builder()
                .addSchema(getClass().getResourceAsStream("/schema/dataSchema.json"))
                .addSchema(getClass().getResourceAsStream("/schema/dataTypes.json"))
                .addSchema(getClass().getResourceAsStream("/schema/storeTypes.json"))
                .storeProperties(getClass().getResourceAsStream("/store.properties"))
                .build();

        final List<Element> elements = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final Entity entity = new Entity(ENTITY_GROUP);
            entity.setVertex("" + i);

            final Edge edge1 = new Edge(EDGE_GROUP);
            edge1.setSource("" + i);
            edge1.setDestination("B");
            edge1.setDirected(false);
            edge1.putProperty("count", 2);

            final Edge edge2 = new Edge(EDGE_GROUP);
            edge2.setSource("" + i);
            edge2.setDestination("C");
            edge2.setDirected(false);
            edge2.putProperty("count", 4);

            elements.add(edge1);
            elements.add(edge2);
            elements.add(entity);
        }
        User user = new User();
        try {
            graph1.execute(new AddElements(elements), user);
        } catch (OperationException e) {
            System.out.println("Couldn't add element: " + e);
        }

        final SparkConf sparkConf = new SparkConf()
                .setMaster("local")
                .setAppName("testCheckGetCorrectElementsInJavaRDD")
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                .set("spark.kryo.registrator", "gaffer.serialisation.kryo.Registrator")
                .set("spark.driver.allowMultipleContexts", "true");
        final JavaSparkContext sparkContext = new JavaSparkContext(sparkConf);

        // Check get correct edges for "1"
        GetJavaRDDOfElementsOperation rddQuery = new GetJavaRDDOfElementsOperation(sparkContext, Collections.singleton(new EntitySeed("1")));
        Iterable<JavaRDD<Element>> rdds = graph1.execute(rddQuery, user);
        if (rdds == null || !rdds.iterator().hasNext()) {
            fail("No RDD returned");
        }
        JavaRDD<Element> rdd = rdds.iterator().next();
        Set<Element> results = new HashSet<>(rdd.collect());
        final Set<Element> expectedElements = new HashSet<>();
        final Entity entity1 = new Entity(ENTITY_GROUP);
        entity1.setVertex("1");
        final Edge edge1B = new Edge(EDGE_GROUP);
        edge1B.setSource("1");
        edge1B.setDestination("B");
        edge1B.setDirected(false);
        edge1B.putProperty("count", 2);
        final Edge edge1C = new Edge(EDGE_GROUP);
        edge1C.setSource("1");
        edge1C.setDestination("C");
        edge1C.setDirected(false);
        edge1C.putProperty("count", 4);
        expectedElements.add(entity1);
        expectedElements.add(edge1B);
        expectedElements.add(edge1C);
        assertEquals(expectedElements, results);

        // Check get correct edges for "1" when specify entities only
        rddQuery.setView(new View.Builder()
                .entity(ENTITY_GROUP)
                .build());
        rdds = graph1.execute(rddQuery, user);
        if (rdds == null || !rdds.iterator().hasNext()) {
            fail("No RDD returned");
        }
        rdd = rdds.iterator().next();
        results = new HashSet<>(rdd.collect());
        expectedElements.clear();
        expectedElements.add(entity1);
        assertEquals(expectedElements, results);

        // Check get correct edges for "1" when specify edges only
        rddQuery.setView(new View.Builder()
                .edge(EDGE_GROUP)
                .build());
        rdds = graph1.execute(rddQuery, user);
        if (rdds == null || !rdds.iterator().hasNext()) {
            fail("No RDD returned");
        }
        rdd = rdds.iterator().next();
        results = new HashSet<>(rdd.collect());
        expectedElements.clear();
        expectedElements.add(edge1B);
        expectedElements.add(edge1C);
        assertEquals(expectedElements, results);

        // Check get correct edges for "1" and "5"
        Set<EntitySeed> seeds = new HashSet<>();
        seeds.add(new EntitySeed("1"));
        seeds.add(new EntitySeed("5"));
        rddQuery = new GetJavaRDDOfElementsOperation(sparkContext, seeds);
        rdds = graph1.execute(rddQuery, user);
        if (rdds == null || !rdds.iterator().hasNext()) {
            fail("No RDD returned");
        }
        rdd = rdds.iterator().next();
        results = new HashSet<>(rdd.collect());
        final Entity entity5 = new Entity(ENTITY_GROUP);
        entity5.setVertex("5");
        final Edge edge5B = new Edge(EDGE_GROUP);
        edge5B.setSource("5");
        edge5B.setDestination("B");
        edge5B.setDirected(false);
        edge5B.putProperty("count", 2);
        final Edge edge5C = new Edge(EDGE_GROUP);
        edge5C.setSource("5");
        edge5C.setDestination("C");
        edge5C.setDirected(false);
        edge5C.putProperty("count", 4);
        expectedElements.clear();
        expectedElements.add(entity1);
        expectedElements.add(edge1B);
        expectedElements.add(edge1C);
        expectedElements.add(entity5);
        expectedElements.add(edge5B);
        expectedElements.add(edge5C);
        assertEquals(expectedElements, results);

        sparkContext.stop();
    }

}