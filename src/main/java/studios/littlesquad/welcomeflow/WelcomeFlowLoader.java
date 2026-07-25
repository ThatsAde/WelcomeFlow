package studios.littlesquad.welcomeflow;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Declares WelcomeFlow's runtime-only libraries to Paper's library loader.
 *
 * <p>Per the project's hard constraints, external libraries (sqlite-jdbc)
 * are injected at runtime via this mechanism instead of being shaded into
 * the plugin jar. This keeps the jar small and avoids relocation entirely
 * for the one library we need.
 *
 * <p>The coordinate is read from {@code libraries.properties}, which the
 * build generates from the same version the code is compiled against --
 * hardcoding it here would let the compile-time and runtime versions drift
 * apart silently.
 */
public final class WelcomeFlowLoader implements PluginLoader {

    private static final String LIBRARIES_RESOURCE = "/libraries.properties";
    private static final String SQLITE_COORDINATE_KEY = "sqlite-jdbc.coordinates";
    private static final String CENTRAL_REPOSITORY_URL = "https://repo1.maven.org/maven2/";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(new DefaultArtifact(sqliteCoordinate()), null));
        resolver.addRepository(
                new RemoteRepository.Builder("central", "default", CENTRAL_REPOSITORY_URL).build());
        classpathBuilder.addLibrary(resolver);
    }

    private String sqliteCoordinate() {
        Properties properties = new Properties();
        try (InputStream stream = WelcomeFlowLoader.class.getResourceAsStream(LIBRARIES_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        LIBRARIES_RESOURCE + " is missing from the plugin jar; the build is broken.");
            }
            properties.load(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + LIBRARIES_RESOURCE + " from the plugin jar.", e);
        }
        String coordinate = properties.getProperty(SQLITE_COORDINATE_KEY);
        if (coordinate == null || coordinate.isBlank()) {
            throw new IllegalStateException(
                    SQLITE_COORDINATE_KEY + " is absent from " + LIBRARIES_RESOURCE + "; the build is broken.");
        }
        return coordinate;
    }
}
