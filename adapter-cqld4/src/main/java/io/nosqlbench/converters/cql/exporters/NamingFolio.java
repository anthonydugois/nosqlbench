/*
 * Copyright (c) 2022 nosqlbench
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

package io.nosqlbench.converters.cql.exporters;

import io.nosqlbench.converters.cql.cqlast.CqlColumnDef;
import io.nosqlbench.converters.cql.cqlast.CqlModel;
import io.nosqlbench.converters.cql.cqlast.CqlTable;
import io.nosqlbench.nb.api.labels.Labeled;

import java.util.*;

/**
 * This will be a pre-built inverted index of all field which need to have bindings assigned.
 * A field reference is presumed to be unique within the scope from which the traversal to
 * the working set has a single path.
 *
 * // name -> type -> table -> keyspace -> namespace
 */
public class NamingFolio {

    private final Map<String, Labeled> graph = new LinkedHashMap<>();
    private final ElementNamer namer;
    public final static String DEFAULT_NAMER_SPEC = "[COLUMN][-TYPEDEF]_[TABLE][-KEYSPACE]";

    public NamingFolio(String namerspec) {
        this.namer = new ElementNamer(
            namerspec,
            List.of(s -> s.toLowerCase().replaceAll("[^a-zA-Z0-9_-]", ""))
        );
    }

    public NamingFolio() {
        this.namer = new ElementNamer(DEFAULT_NAMER_SPEC);
    }

    public void addFieldRef(Map<String, String> labels) {
        String name = namer.apply(labels);
        graph.put(name, Labeled.forMap(labels));
    }

    public void addFieldRef(String column, String typedef, String table, String keyspace) {
        addFieldRef(Map.of("column", column, "typedef", typedef, "table", table, "keyspace", keyspace));
    }

    /**
     * This will eventually elide extraneous fields according to knowledge of all known names
     * by name, type, table, keyspace. For now it just returns everything in fully qualified form.
     */
    public String nameFor(Labeled labeled, String... fields) {
        Map<String, String> labelsPlus = labeled.getLabelsAnd(fields);
        String name = namer.apply(labelsPlus);
        return name;
    }


    public void populate(CqlModel model) {
        for (CqlTable table : model.getAllTables()) {
            for (CqlColumnDef coldef : table.getColumnDefinitions()) {
                addFieldRef(coldef.getLabels());
            }
        }
    }

    public Set<String> getNames() {
        return new LinkedHashSet<>(graph.keySet());
    }

}