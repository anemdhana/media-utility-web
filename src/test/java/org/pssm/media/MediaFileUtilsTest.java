
package org.pssm.media;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MediaFileUtilsTest {

    @Autowired
    private MediaFileUtils utils;

    @Test
    void contextLoads() {
        assertThat(utils).isNotNull();
    }

    @Test
    void listAllMediaFiles_runs() throws Exception {
        List<File> files = utils.listAllMediaFiles();
        assertThat(files).isNotNull();
    }

    @Test
    void filterMediaFiles_runs() throws Exception {
        List<File> files = utils.filterMediaFiles("test", List.of(null));
        assertThat(files).isNotNull();
    }


    @Test
    void labelFeatures_workWithRealFiles() throws Exception {
        List<File> files = utils.listAllMediaFiles();
        assertThat(files).isNotEmpty();
        File file = files.stream().filter(f -> f.getName().endsWith(".m4a") || f.getName().endsWith(".mp4")).findFirst().orElseThrow();

        // Clear all labels
        utils.clearAllLabels(file);
        assertThat(utils.getLabelsOfFile(file)).isEmpty();

        // Add labels
        utils.addLabels(file, List.of("test1", "test2"));
        List<String> labels = utils.getLabelsOfFile(file);
        assertThat(labels).contains("test1", "test2");

        // Add duplicate label (should not duplicate)
        utils.addLabels(file, List.of("test1"));
        labels = utils.getLabelsOfFile(file);
        assertThat(labels.stream().filter(l -> l.equals("test1")).count()).isEqualTo(1);

        // Remove label
        utils.removeLabels(file, List.of("test1"));
        labels = utils.getLabelsOfFile(file);
        assertThat(labels).doesNotContain("test1");
        assertThat(labels).contains("test2");

        // Contains labels
        assertThat(utils.containsLabels(file, List.of("test2"))).isTrue();
        assertThat(utils.containsLabels(file, List.of("test1"))).isFalse();
        assertThat(utils.containsLabels(file, List.of("test2", "notpresent"))).isFalse();

        // Clear all
        utils.clearAllLabels(file);
        assertThat(utils.getLabelsOfFile(file)).isEmpty();
    }
}
