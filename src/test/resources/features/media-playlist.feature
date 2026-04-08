@bdd @media-playlist
Feature: Playlist management and ReplayGain reporting
  As a playlist editor
  I want playlist behaviour expressed in plain language
  So that the intent stays easy to follow

  @playlist-track-management
  Scenario: Create, update, and clear a playlist
    Given a playlist with one copied media track
    Then the playlist should contain 1 tracks
    And the playlist duration should be at least 0 seconds
    When I add another copied media track to the playlist
    Then the playlist should contain 2 tracks
    When I remove the extra track from the playlist
    Then the playlist should contain 1 tracks
    When I clear the playlist
    Then the playlist should contain 0 tracks

  @playlist-replaygain-report
  Scenario: Inspect ReplayGain details for a playlist
    Given a playlist with one copied audio track
    When I inspect the ReplayGain report for the playlist
    Then the ReplayGain report should reference the current playlist
    And the ReplayGain summary should report 1 tracks

  @playlist-apply-replaygain
  Scenario: Apply ReplayGain to a playlist when the tool is available
    Given the ReplayGain command line tool is available
    And a playlist with one copied audio track
    When I apply ReplayGain to the playlist
    And I inspect the ReplayGain report for the playlist
    Then the ReplayGain report should mark the playlist as fully normalized
    And the album volume gain should stay in a normal range across tracks without being too loud or too low

  @playlist-label-generation
  Scenario: Create playlist by label with label and total duration in filename
    Given a copied audio file labeled "heart-melting-tunes"
    When I create a playlist from label "heart-melting-tunes"
    Then the created label playlist filename should include the label and total duration
    And the created label playlist should contain at least 1 tracks

  @playlist-copy-to-target
  Scenario: Copy playlist and all tracks to a target folder
    Given a playlist with one copied audio track
    When I copy the playlist and tracks to a temporary target folder
    Then the copied playlist and all tracks should exist in the target folder
