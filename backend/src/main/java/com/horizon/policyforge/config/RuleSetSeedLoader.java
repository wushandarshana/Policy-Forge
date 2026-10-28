package com.horizon.policyforge.config;

import com.horizon.policyforge.service.RuleSetService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * On startup, loads the shipped rule files ({@code rules/<slug>/v<N>.json}) into the rule-set table where
 * (productCode, version) is absent. Existing rows are never touched, so a restart changes nothing (NFR-05). The launch
 * products load first, in the catalog's order, then any other product folders alphabetically.
 */
@Component
public class RuleSetSeedLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RuleSetSeedLoader.class);
    private static final String PATTERN = "classpath*:rules/*/v*.json";
    private static final Pattern LOCATION = Pattern.compile("rules/([^/]+)/v(\\d+)\\.json$");
    private static final List<String> LAUNCH_ORDER = List.of("term-life", "motor", "household");

    private record SeedFile(String slug, int version, Resource resource) {
    }

    private final RuleSetService ruleSets;

    public RuleSetSeedLoader(RuleSetService ruleSets) {
        this.ruleSets = ruleSets;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        load();
    }

    /** Loads every seed file that is not stored yet. Returns how many rows were inserted. */
    public int load() throws IOException {
        int inserted = 0;
        for (SeedFile file : discover()) {
            String text = file.resource().getContentAsString(StandardCharsets.UTF_8);
            String source = "rules/" + file.slug() + "/v" + file.version() + ".json";
            if (ruleSets.registerSeed(source, file.slug(), file.version(), text)) {
                inserted++;
            }
        }
        LOG.info("Rule set seed load finished inserted={}", inserted);
        return inserted;
    }

    private static List<SeedFile> discover() throws IOException {
        List<SeedFile> files = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
            Matcher match = LOCATION.matcher(resource.getURI().toString());
            if (match.find()) {
                files.add(new SeedFile(match.group(1), Integer.parseInt(match.group(2)), resource));
            }
        }
        files.sort(Comparator.comparingInt((SeedFile f) -> launchRank(f.slug()))
                .thenComparing(SeedFile::slug)
                .thenComparingInt(SeedFile::version));
        return files;
    }

    private static int launchRank(String slug) {
        int index = LAUNCH_ORDER.indexOf(slug);
        return index >= 0 ? index : LAUNCH_ORDER.size();
    }
}
