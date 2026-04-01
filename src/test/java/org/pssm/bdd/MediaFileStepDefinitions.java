package org.pssm.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.pssm.media.MediaFileUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MediaFileStepDefinitions extends MediaBddSupport {

    @Autowired
    private MediaFileUtils utils;

    private List<File> mediaFiles;
    private File copiedMediaFile;
    private List<String> labels;

    @When("I request all available media files")
    public void iRequestAllAvailableMediaFiles() throws Exception {
        mediaFiles = utils.listAllMediaFiles();
    }

    @When("I filter media files by the name fragment {string}")
    public void iFilterMediaFilesByTheNameFragment(String fragment) throws Exception {
        mediaFiles = utils.filterMediaFiles(fragment, (String) null);
    }

    @Then("a media list should be returned")
    public void aMediaListShouldBeReturned() {
        assertThat(mediaFiles).isNotNull();
    }

    @Then("the media list should not be empty")
    public void theMediaListShouldNotBeEmpty() {
        assertThat(mediaFiles).isNotEmpty();
    }

    @Given("a temporary media file copy for label management")
    public void aTemporaryMediaFileCopyForLabelManagement() throws Exception {
        copiedMediaFile = createLabelTestFileCopy();
        assertThat(copiedMediaFile).exists().isFile();
    }

    @When("I add the labels {string} and {string} to the copied media file")
    public void iAddTheLabelsAndToTheCopiedMediaFile(String firstLabel, String secondLabel) throws Exception {
        utils.addLabels(copiedMediaFile, List.of(firstLabel, secondLabel));
        labels = utils.getLabelsOfFile(copiedMediaFile);
    }

    @When("I add the label {string} again to the copied media file")
    public void iAddTheLabelAgainToTheCopiedMediaFile(String label) throws Exception {
        utils.addLabels(copiedMediaFile, List.of(label));
        labels = utils.getLabelsOfFile(copiedMediaFile);
    }

    @Then("the copied media file should contain the labels {string} and {string}")
    public void theCopiedMediaFileShouldContainTheLabelsAnd(String firstLabel, String secondLabel) {
        assertThat(labels).contains(firstLabel, secondLabel);
    }

    @Then("the label {string} should only appear once")
    public void theLabelShouldOnlyAppearOnce(String label) {
        assertThat(labels.stream().filter(label::equals).count()).isEqualTo(1L);
    }

    @Then("the copied media file should satisfy the label check for {string}")
    public void theCopiedMediaFileShouldSatisfyTheLabelCheckFor(String label) {
        assertThat(utils.containsLabels(copiedMediaFile, List.of(label))).isTrue();
        assertThat(utils.containsLabels(copiedMediaFile, List.of(label, "notpresent"))).isFalse();
    }
}
