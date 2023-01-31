package org.qbicc.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.NativeImageSourceJarBuildItem;
import org.qbicc.plugin.initializationcontrol.QbiccFeature;
import org.qbicc.quarkus.spi.QbiccFeatureBuildItem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * Find GraalVM reflection metadata in jar files and convert into a form we can recognize
 */
public class GraalReachabilityMetadataProcessor {

    @BuildStep
    void importGraalConfiguration(NativeImageSourceJarBuildItem jarBuildItem,
                                  BuildProducer<QbiccFeatureBuildItem> qbiccFeatureBuildItemBuildProducer){
        HashSet<Path> visited = new HashSet<>();
        ArrayList<QbiccFeature.ReflectiveClass> reflectiveClasses = new ArrayList<>();
        ArrayList<QbiccFeature.Constructor> reflectiveConstructors = new ArrayList<>();
        ArrayList<QbiccFeature.Field> reflectiveFields = new ArrayList<>();
        ArrayList<QbiccFeature.Method> reflectiveMethods = new ArrayList<>();
        ArrayList<String> runtimeInitClasses = new ArrayList<>();

        searchJar(jarBuildItem.getPath(), visited, reflectiveClasses, reflectiveConstructors, reflectiveFields, reflectiveMethods, runtimeInitClasses);

        QbiccFeature qf = new QbiccFeature();
        qf.reflectiveClasses =  reflectiveClasses.toArray(QbiccFeature.ReflectiveClass[]::new);
        qf.reflectiveConstructors = reflectiveConstructors.toArray(QbiccFeature.Constructor[]::new);
        qf.reflectiveFields = reflectiveFields.toArray(QbiccFeature.Field[]::new);
        qf.reflectiveMethods = reflectiveMethods.toArray(QbiccFeature.Method[]::new);
        qf.initializeAtRuntime = runtimeInitClasses.toArray(String[]::new);
        qbiccFeatureBuildItemBuildProducer.produce(new QbiccFeatureBuildItem(qf));
        System.out.println(qf);
    }

    private void searchJar(final Path path, final Set<Path> visited,
                           ArrayList<QbiccFeature.ReflectiveClass> reflectiveClasses,
                           ArrayList<QbiccFeature.Constructor> reflectiveConstructors,
                           ArrayList<QbiccFeature.Field> reflectiveFields,
                           ArrayList<QbiccFeature.Method>  reflectiveMethods,
                           ArrayList<String> runtimeInitClasses) {
        if (!visited.add(path)) {
            return;
        }

        if (!Files.isDirectory(path)) {
            try (JarFile jar = new JarFile(path.toFile())) {
                // 1. Process files in META-INF/native-image
                jar.stream().filter(je -> je.getName().contains("META-INF/native-image")).forEach(entry -> {
                    if (entry.getName().endsWith("reflection-config.json")) {
                        System.out.println(entry.getName());
                    }
                    if (entry.getName().endsWith("native-image.properties")) {
                        try (InputStream is = jar.getInputStream(entry)) {
                            Properties props = new Properties();
                            props.load(is);
                            if (props.get("Args") instanceof String sa) {
                                for (String arg : sa.split(" ")) {
                                    if (arg.startsWith("--initialize-at-run-time=")) {
                                        String[] rcs = arg.substring("--initialize-at-run-time=".length()).split(",");
                                        for (String r: rcs) {
                                            runtimeInitClasses.add(r);
                                        }
                                    } else if (arg.startsWith("--initialize-at-build-time=")) {
                                        // nothing to do; this is what qbicc does by default
                                    } else if (arg.startsWith("-H:ReflectionConfigurationResources")) {
                                        if (arg.contains("/reflection-config.json")) {
                                            // expected; handled in the next loop
                                        } else {
                                            System.out.println("Unexpected name for reflection config: "+arg);
                                        }
                                    } else {
                                        System.out.println("Graal RMP: Unexpected argument: "+arg);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                });
                jar.stream().filter(je -> je.getName().contains("reflection-config.json")).forEach(entry -> {
                    System.out.println(entry.getName());
                });

                // 2. Recurse into dependent jars
                final String classPathAttribute = jar.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
                if (classPathAttribute != null) {
                    final String[] items = classPathAttribute.split(" ");
                    for (String item : items) {
                        searchJar(path.getParent().resolve(item), visited, reflectiveClasses, reflectiveConstructors, reflectiveFields, reflectiveMethods, runtimeInitClasses);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
