package ca.digilogue.xp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
public class App {

    public static String version;

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(App.class, args);

        version = resolveVersion(ctx);

        log.info("xp-quotes-service is running @ version: {}", version);
    }

    private static String resolveVersion(ConfigurableApplicationContext ctx) {
        // Preferred: use build-info (packaged JAR / Docker)
        try {
            BuildProperties buildProperties = ctx.getBean(BuildProperties.class);
            return buildProperties.getVersion();
        } catch (NoSuchBeanDefinitionException ignored) {
            // Fall back to parsing pom.xml (e.g., when running from IDE)
        }

        return readVersionFromPom();
    }

    private static String readVersionFromPom() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("pom.xml"));

            boolean inParent = false;

            for (String line : lines) {
                String trimmed = line.trim();

                // Track whether we're inside the <parent>...</parent> block
                if (trimmed.startsWith("<parent>")) {
                    inParent = true;
                } else if (trimmed.startsWith("</parent>")) {
                    inParent = false;
                }

                // We only care about <version> tags OUTSIDE the <parent> section
                if (!inParent &&
                        trimmed.startsWith("<version>") &&
                        trimmed.endsWith("</version>")) {

                    return trimmed
                            .replace("<version>", "")
                            .replace("</version>", "")
                            .trim();
                }
            }
        } catch (Exception ignored) {
            // If something goes wrong reading pom.xml, fall through
        }

        // Last-resort fallback for local dev
        return "DEV";
    }
}
