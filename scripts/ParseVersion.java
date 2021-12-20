import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParseVersion {
    record Channel(String name, String ciUrlBase) {
        static final List<Channel> channels;

        static {
            try {
                Path path = Paths.get("channels.properties");
                if (Files.notExists(path)) {
                    throw new IllegalStateException(path + " does not exists");
                }

                Properties properties = new Properties();
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    properties.load(reader);
                }

                String[] names = properties.getProperty("names").split(",");

                ArrayList<Channel> cs = new ArrayList<>();
                for (String name : names) {
                    String urlBase = properties.getProperty(name + ".ci.url");
                    if (urlBase == null) {
                        urlBase = "https://ci.huangyuhui.net/job/HMCL-" + name;
                    }
                    cs.add(new Channel(name, urlBase));
                }
                channels = cs;
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        static Channel find(String name) {
            Objects.requireNonNull(name, "channel name is null");
            for (Channel channel : channels) {
                if (name.equals(channel.name)) {
                    return channel;
                }
            }
            throw new IllegalArgumentException("Bad channel name: " + name);
        }

        String artifactId() {
            return "hmcl-" + name;
        }
    }

    private static final Pattern PATTERN = Pattern.compile("^HMCL-(?<version>(?<major>[0-9]+)\\.(?<minor>[0-9]+)\\.(?<patch>[0-9]+)(\\.(?<build>[0-9]+))?)\\.(exe|jar)$");
    private static final Pattern CI_BUILD_NUMBER_PATTERN = Pattern.compile("^[0-9]+$");

    public static void main(String[] args) throws Exception {
        final Channel channel = Channel.find(System.getenv("HMCL_UPDATE_CHANNEL"));

        String ciBuildNumber = System.getenv("HMCL_CI_BUILD_NUMBER");
        if (!CI_BUILD_NUMBER_PATTERN.matcher(ciBuildNumber).matches()) {
            System.err.printf("Bad ci build number: '%s'%n", ciBuildNumber);
        }

        String fileName = args[0];

        Matcher matcher = PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            System.err.printf("Bad file name: '%s'%n", fileName);
            System.exit(-1);
        }

        String version = matcher.group("version");

        String majorVersion = matcher.group("major");
        String minorVersion = matcher.group("minor");
        String patchVersion = matcher.group("patch");
        String buildNumber = matcher.group("build");

        addEnv("HMCL_VERSION", version);
        /*
        addEnv("HMCL_MAJOR_VERSION", majorVersion);
        addEnv("HMCL_MINOR_VERSION", minorVersion);
        addEnv("HMCL_PATCH_VERSION", patchVersion);

        String npmVersion = "%s.%s.%s".formatted(majorVersion, minorVersion, patchVersion);

        if (buildNumber != null) {
            npmVersion = npmVersion + "-" + buildNumber;
            addEnv("HMCL_BUILD_NUMBER", buildNumber);
        }

        // addEnv("HMCL_NPM_VERSION", npmVersion);
         */

        addEnv("HMCL_CI_BUILD_NUMBER", ciBuildNumber);
        addEnv("HMCL_DOWNLOAD_BASE", "%s/%s/artifact/HMCL/build/libs".formatted(channel.ciUrlBase, ciBuildNumber));
    }

    private static final Path GITHUB_ENV_FILE = Paths.get(System.getenv("GITHUB_ENV"));

    private static void addEnv(String name, String value) throws Exception {
        Files.writeString(GITHUB_ENV_FILE, "%s=%s\n".formatted(name, value), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }
}