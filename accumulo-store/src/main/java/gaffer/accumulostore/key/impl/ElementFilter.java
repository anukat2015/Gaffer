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

package gaffer.accumulostore.key.impl;

import gaffer.accumulostore.key.AccumuloElementConverter;
import gaffer.accumulostore.key.exception.AccumuloElementConversionException;
import gaffer.accumulostore.key.exception.ElementFilterException;
import gaffer.accumulostore.utils.AccumuloStoreConstants;
import gaffer.accumulostore.utils.IteratorOptionsBuilder;
import gaffer.commonutil.CommonConstants;
import gaffer.data.element.Element;
import gaffer.data.elementdefinition.exception.SchemaException;
import gaffer.data.elementdefinition.view.View;
import gaffer.store.ElementValidator;
import gaffer.store.schema.Schema;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.Filter;
import org.apache.accumulo.core.iterators.IteratorEnvironment;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * The ElementFilter will filter out {@link Element}s based on the filtering
 * instructions given in the {@link View} that is passed to this iterator
 */
public class ElementFilter extends Filter {
    private ElementValidator validator;
    private AccumuloElementConverter elementConverter;

    @Override
    public boolean accept(final Key key, final Value value) {
        final Element element;
        try {
            element = elementConverter.getFullElement(key, value);
        } catch (final AccumuloElementConversionException e) {
            throw new ElementFilterException(
                    "Element filter iterator failed to create an element from an accumulo key value pair", e);
        }
        return validator.validate(element);
    }

    @Override
    public void init(final SortedKeyValueIterator<Key, Value> source, final Map<String, String> options,
                     final IteratorEnvironment env) throws IOException {
        super.init(source, options, env);
        validateOptions(options);
    }

    @Override
    public boolean validateOptions(final Map<String, String> options) {
        if (!super.validateOptions(options)) {
            return false;
        }
        if (!options.containsKey(AccumuloStoreConstants.ACCUMULO_ELEMENT_CONVERTER_CLASS)) {
            throw new IllegalArgumentException("Must specify the " + AccumuloStoreConstants.ACCUMULO_ELEMENT_CONVERTER_CLASS);
        }
        if (!options.containsKey(AccumuloStoreConstants.SCHEMA)) {
            throw new IllegalArgumentException("Must specify the " + AccumuloStoreConstants.SCHEMA);
        }

        validator = getElementValidator(options);

        final Schema schema;
        try {
            schema = Schema.fromJson(options.get(AccumuloStoreConstants.SCHEMA).getBytes(CommonConstants.UTF_8));
        } catch (final UnsupportedEncodingException e) {
            throw new SchemaException("Unable to deserialise the schema from JSON", e);
        }

        try {
            final Class<?> elementConverterClass = Class
                    .forName(options.get(AccumuloStoreConstants.ACCUMULO_ELEMENT_CONVERTER_CLASS));
            elementConverter = (AccumuloElementConverter) elementConverterClass.getConstructor(Schema.class)
                    .newInstance(schema);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            throw new ElementFilterException("Failed to load element converter from class name provided : "
                    + options.get(AccumuloStoreConstants.ACCUMULO_ELEMENT_CONVERTER_CLASS));
        }
        return true;
    }

    @Override
    public IteratorOptions describeOptions() {
        return new IteratorOptionsBuilder(super.describeOptions()).addViewNamedOption().addSchemaNamedOption()
                .addElementConverterClassNamedOption().setIteratorName(AccumuloStoreConstants.ELEMENT_FILTER_ITERATOR_NAME)
                .setIteratorDescription("Only returns elements that pass validation against the given view").build();
    }

    protected ElementValidator getElementValidator(final Map<String, String> options) {
        if (!options.containsKey(AccumuloStoreConstants.VIEW)) {
            throw new IllegalArgumentException("Must specify the " + AccumuloStoreConstants.VIEW);
        }

        try {
            return new ElementValidator(View.fromJson(options.get(AccumuloStoreConstants.VIEW).getBytes(CommonConstants.UTF_8)));
        } catch (final UnsupportedEncodingException e) {
            throw new SchemaException("Unable to deserialise view from JSON", e);
        }
    }
}
