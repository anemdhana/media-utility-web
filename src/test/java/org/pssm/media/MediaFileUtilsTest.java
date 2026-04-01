
package org.pssm.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("BDD style media file utility checks")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MediaFileUtilsTest {

    @Autowired
    private MediaFileUtils utils;

    private File createLabelTestFileCopy() throws Exception {
        List<File> files = utils.listAllMediaFiles();
        assertThat(files).isNotEmpty();
        File source = files.stream()
                .filter(f -> f.getName().endsWith(".m4a") || f.getName().endsWith(".mp4"))
                .findFirst()
                .orElseThrow();

        String fileName = source.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String suffix = dotIndex >= 0 ? fileName.substring(dotIndex) : ".tmp";
        Path tempFile = Files.createTempFile("media-label-test-", suffix);
        Files.copy(source.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        tempFile.toFile().deleteOnExit();
        return tempFile.toFile();
    }

    @Test
    void given_the_spring_context_when_media_file_utils_are_loaded_then_they_are_available() {
        assertThat(utils).isNotNull();
    }

    @Test
    void given_media_files_when_i_list_them_then_a_result_collection_is_returned() throws Exception {
        List<File> files = utils.listAllMediaFiles();
        assertThat(files).isNotNull();
    }

    @Test
    void given_a_name_filter_when_i_search_media_files_then_matching_results_can_be_retrieved() throws Exception {
        List<File> files = utils.filterMediaFiles("inner", (String) null);
        assertThat(files).isNotNull();
    }


    @Test
    void given_a_media_file_copy_when_i_add_labels_then_they_are_saved_without_duplicates() throws Exception {
        File file = createLabelTestFileCopy();

        utils.addLabels(file, List.of("label-test-1", "label-test-2"));
        List<String> labels = utils.getLabelsOfFile(file);
        assertThat(labels).contains("label-test-1", "label-test-2");

        utils.addLabels(file, List.of("label-test-1"));
        labels = utils.getLabelsOfFile(file);
        assertThat(labels.stream().filter(l -> l.equals("label-test-1")).count()).isEqualTo(1);
    }

    @Test
    void given_a_labeled_media_file_when_i_check_for_labels_then_the_expected_match_result_is_returned() throws Exception {
        File file = createLabelTestFileCopy();

        utils.addLabels(file, List.of("label-test-2"));

        assertThat(utils.containsLabels(file, List.of("label-test-2"))).isTrue();
        assertThat(utils.containsLabels(file, List.of("label-test-1"))).isFalse();
        assertThat(utils.containsLabels(file, List.of("label-test-2", "notpresent"))).isFalse();
    }
}
