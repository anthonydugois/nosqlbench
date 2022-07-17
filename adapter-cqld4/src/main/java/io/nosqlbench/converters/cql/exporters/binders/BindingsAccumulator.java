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

package io.nosqlbench.converters.cql.exporters.binders;

import io.nosqlbench.converters.cql.cqlast.CqlColumnDef;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BindingsAccumulator {
    private final static Logger logger = LogManager.getLogger("CQL-GENERATOR");

    private final NamingFolio namer;
    private final List<BindingsLibrary> libraries;
    private final Map<String, String> accumulated = new LinkedHashMap<>();
    private final Map<String,String> accumulatedByRecipe = new LinkedHashMap<>();
    private final NamingStyle namingStyle = NamingStyle.SymbolicType;
    private final LinkedHashMap<String,Integer> counts = new LinkedHashMap<>();
    private long enumeration=0L;

    public BindingsAccumulator(NamingFolio namer, List<BindingsLibrary> libraries) {
        this.namer = namer;
        this.libraries = libraries;
    }

    public Binding forColumn(CqlColumnDef def, String... prefixes) {
        return forColumn(def,Map.of(), prefixes);
    }
    public Binding forColumn(CqlColumnDef def, Map<String,String> extra, String... prefixes) {
        for (BindingsLibrary library : libraries) {
            Optional<Binding> optionalBinding = switch (namingStyle) {
                case FullyQualified -> this.resolveFullyQualifiedBinding(def, extra);
                case SymbolicType -> this.resolveSymbolicBinding(def, extra);
                case CondensedKeyspace -> this.resolvedCondensedBinding(def, extra);
            };

            if (optionalBinding.isPresent()) {
                Binding binding = optionalBinding.get();

                if (prefixes.length>0) {
                    binding = binding.withPreFunctions(prefixes).withNameIncrement(++enumeration);
                    String extant = accumulatedByRecipe.get(binding.getRecipe());
                    if (extant!=null) {
                        binding= new Binding(extant,accumulated.get(extant));
                    }
                }

                registerBinding(binding);
                return binding;
            }
        }
        throw new UnresolvedBindingException(def);
    }


    private Optional<Binding> resolvedCondensedBinding(CqlColumnDef def, Map<String,String> extra) {
        throw new RuntimeException("Implement me!");
    }

    private Optional<Binding> resolveSymbolicBinding(CqlColumnDef def, Map<String,String> extra) {
        for (BindingsLibrary library : libraries) {
            Optional<Binding> binding = library.resolveBindingsFor(def);
            if (binding.isPresent()) {
                if (binding.get().getRecipe()==null) {
                    throw new RuntimeException("Binding returned from library " + library + "' was null, for def '" + def + "'");
                }
                return binding;
            }
        }
        return Optional.empty();

    }

    private Optional<Binding> resolveFullyQualifiedBinding(CqlColumnDef def, Map<String,String> extra) {
        for (BindingsLibrary library : libraries) {
            Optional<Binding> bindingRecipe = library.resolveBindingsFor(def);
            if (bindingRecipe.isPresent()) {
                Binding found = bindingRecipe.get();
                String name = namer.nameFor(def, extra);
                Binding renamedBinding = new Binding(name,found.getRecipe());
                return Optional.of(renamedBinding);
            }
        }
        return Optional.empty();
    }

    private void registerBinding(Binding newBinding) {
        String name = newBinding.getName();
        accumulated.put(name, newBinding.getRecipe());
        accumulatedByRecipe.put(newBinding.getRecipe(), name);
        counts.put(name, counts.get(name)==null? 1 : counts.get(name)+1);
    }

    public Map<String, String> getAccumulatedBindings() {
        return accumulated;
    }
}