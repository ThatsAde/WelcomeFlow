package studios.littlesquad.welcomeflow;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Declares WelcomeFlow's runtime-only libraries to Paper's library loader.
 *
 * <p>Per the project's hard constraints, external libraries (sqlite-jdbc)
 * are injected at runtime via this mechanism instead of being shaded into
 * the plugin jar. This keeps the jar small and avoids relocation entirely
 * for the one library we need.
 */
public final class WelcomeFlowLoader implements PluginLoader {

    private static final String SQLITE_JDBC_COORDINATES = "org.xerial:sqlite-jdbc:3.46.1.3";
    private static final String CENTRAL_REPOSITORY_URL = "https://repo1.maven.org/maven2/";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(new DefaultArtifact(SQLITE_JDBC_COORDINATES), null));
        resolver.addRepository(
                new RemoteRepository.Builder("central", "default", CENTRAL_REPOSITORY_URL).build());
        classpathBuilder.addLibrary(resolver);
    }
}
