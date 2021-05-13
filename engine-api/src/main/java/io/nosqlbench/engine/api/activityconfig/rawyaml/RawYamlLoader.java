package io.nosqlbench.engine.api.activityconfig.rawyaml;

import io.nosqlbench.engine.api.activityimpl.ActivityInitializationError;
import io.nosqlbench.nb.api.content.Content;
import io.nosqlbench.nb.api.content.NBIO;
import io.nosqlbench.nb.api.errors.BasicError;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.function.Function;

public class RawYamlLoader {

    public static String[] YAML_EXTENSIONS = new String[]{"yaml","yml"};

    List<Function<String, String>> stringTransformers = new ArrayList<>();
    private final ArrayList<Function<String,String>> transformers = new ArrayList<>();

    public void addTransformer(Function<String, String> newTransformer) {
        Collections.addAll(this.transformers, newTransformer);
    }

    public List<Map<String,Object>> loadString(Logger logger, String data) {

        try {
            if (logger != null) logger.trace("Applying string transformer to yaml data:" + data);
            for (Function<String, String> transformer : transformers) {
                data = transformer.apply(data);
            }
        } catch (Exception e) {
            RuntimeException t = new ActivityInitializationError("Error applying string transforms to input", e);
            throw t;
        }

        return parseYaml(logger, data);
    }

    public List<Map<String,Object>> loadPath(
            Logger logger,
            String path,
            String... searchPaths) {

        String data = null;
        try {
            Optional<Content<?>> oyaml = NBIO.all().prefix(searchPaths).name(path).extension(YAML_EXTENSIONS).first();
            data = oyaml.map(Content::asString).orElseThrow(() -> new BasicError("Unable to load " + path));
            return loadString(logger, data);
        } catch (Exception e) {
            throw new RuntimeException("error while reading file " + path, e);
        }
    }

    private List<Map<String,Object>> parseYaml(Logger logger, String data) {
        Yaml yaml = new Yaml();
        Iterable<Object> objects = yaml.loadAll(data);
        List<RawStmtsDoc> newDocList = new ArrayList<>();

        List<Map<String,Object>> maps = new ArrayList<>();

        for (Object object : objects) {
            if (object instanceof Map) {
                maps.add(new LinkedHashMap<>((Map<String,Object>)object));
            } else {
                throw new RuntimeException("Unable to coerce a non-map type to a statements yaml doc: " + object.getClass().getCanonicalName());
            }
        }
        return maps;
    }

    protected String applyTransforms(Logger logger, String data) {
        for (Function<String, String> xform : stringTransformers) {
            try {
                if (logger != null) logger.trace("Applying string transformer to yaml data:" + xform);
                data = xform.apply(data);
            } catch (Exception e) {
                RuntimeException t = new ActivityInitializationError("Error applying string transforms to input", e);
                throw t;
            }
        }
        return data;
    }

}
