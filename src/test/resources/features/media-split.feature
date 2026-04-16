@bdd @media-split
Feature: Media splitting
  As a user preparing clips
  I want to split audio and video in a readable way
  So that the resulting files are easy to verify

  @split-audio-basic
  Scenario: Split an audio clip into a new file
    Given an existing audio media file
    When I split the audio clip from "00:00:00" to "00:00:10" using "YOUTUBE_UPLOAD" quality
    Then the created audio clip should exist
    And the created audio clip should be non-empty

  @split-video-basic
  Scenario: Split a video clip into a new file
    Given an existing video media file
    When I split the video clip from "00:00:00" to "00:00:10" using "WHATSAPP" quality
    Then the created video clip should exist
    And the created video clip should be non-empty

  @split-jynedpzesle-whatsapp
  Scenario: Split the JyNedPZesLE media for WhatsApp sharing
    Given the media file for video id "JyNedPZesLE"
    When I split the selected media clip from "00:12:30" to "00:15:50" for "WHATSAPP" sharing
    Then the created audio clip should exist
    And the created audio clip should be non-empty

  @split-To0lu_BrXTk-whatsapp
  Scenario: Split the To0lu_BrXTk media for WhatsApp sharing
    Given the media file for video id "To0lu_BrXTk"
    When I split the selected media clip from "00:12:30" to "00:15:50" for "WHATSAPP" sharing
    Then the created audio clip should exist
    And the created audio clip should be non-empty

  @split-media-input-properties-driven
  Scenario Outline: Execute configured media split feature from input properties
    Given the media split input scenario "<scenarioKey>"
    When I execute the configured media split feature
    Then the configured media split output should exist
    And the configured media split output should be non-empty

    @split-media-input-whatsapp-compact-video
    Examples:
      | scenarioKey              |
      | whatsapp_compact_video   |
