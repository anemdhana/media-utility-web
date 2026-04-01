@bdd @media-youtube
Feature: YouTube audio extraction
  As a media editor
  I want to extract audio from YouTube videos
  So that I can use them in playlists and media workflows

  @youtube-audio-extract-XxwTe1RhEZI
  Scenario: Extract audio from YouTube video XxwTe1RhEZI with default quality
    Given the YouTube video id "XxwTe1RhEZI"
    When I extract the audio for the YouTube video
    Then the extracted audio file should exist
    And the extracted audio filename should contain the video id
    And the extracted audio file should be non-empty

  @youtube-audio-extract-XxwTe1RhEZI-whatsapp
  Scenario: Extract audio from YouTube video XxwTe1RhEZI with WhatsApp quality
    Given the YouTube video id "XxwTe1RhEZI"
    When I extract the audio for the YouTube video with "WHATSAPP" quality
    Then the extracted audio file should exist
    And the extracted audio filename should contain the video id
    And the extracted audio file should be non-empty
