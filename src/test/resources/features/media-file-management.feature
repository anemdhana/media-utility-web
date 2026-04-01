@bdd @media-files
Feature: Media file discovery and labels
  As a media curator
  I want to browse and label files in readable scenarios
  So that the behaviour is easy to understand

  @list-media-files
  Scenario: List all media files from the configured directory
    When I request all available media files
    Then a media list should be returned
    And the media list should not be empty

  @filter-media-by-name
  Scenario: Filter media files by a partial file name
    When I filter media files by the name fragment "inner"
    Then a media list should be returned

  @manage-media-labels
  Scenario: Add labels to a copied media file without duplicates
    Given a temporary media file copy for label management
    When I add the labels "label-test-1" and "label-test-2" to the copied media file
    And I add the label "label-test-1" again to the copied media file
    Then the copied media file should contain the labels "label-test-1" and "label-test-2"
    And the label "label-test-1" should only appear once
    And the copied media file should satisfy the label check for "label-test-1"
