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

  @youtube-audio-extract-fjCYYnfzRvI-whatsapp
  Scenario: Extract audio from YouTube video fjCYYnfzRvI with WhatsApp quality
    Given the YouTube video id "fjCYYnfzRvI"
    When I extract the audio for the YouTube video with "WHATSAPP" quality
    Then the extracted audio file should exist
    And the extracted audio filename should contain the video id
    And the extracted audio file should be non-empty

  @youtube-thumbnail-extract-B9j3pYC7Z20
  Scenario: Extract the high quality thumbnail image from YouTube video B9j3pYC7Z20
    Given the YouTube video id "B9j3pYC7Z20"
    When I extract the high quality thumbnail for the YouTube video
    Then the extracted thumbnail file should exist
    And the extracted thumbnail filename should contain the video id
    And the extracted thumbnail file should be non-empty

  @youtube-media-input-properties-driven
  Scenario Outline: Execute configured YouTube media feature from input properties
    Given the media input scenario "<scenarioKey>"
    When I execute the configured media feature
    Then all configured YouTube downloads should exist
    And the configured label should be applied to all downloaded files when provided

    @youtube-media-input-single-video
    Examples:
      | scenarioKey   |
      | single_video  |

    @youtube-media-input-playlist
    Examples:
      | scenarioKey |
      | playlist      |

    @youtube-media-input-thumbnail
    Examples:
      | scenarioKey |
      | thumbnail     |

    @youtube-media-input-extract-split
    Examples:
      | scenarioKey   |
      | extract_split |

  @youtube-audio-extract-B9j3pYC7Z20-compact
  Scenario: Extract audio from YouTube video B9j3pYC7Z20 with compact size quality
    Given the YouTube video id "B9j3pYC7Z20"
    When I extract the audio for the YouTube video with "COMPACT_SIZE" quality
    Then the extracted audio file should exist
    And the extracted audio filename should contain the video id
    And the extracted audio file should be non-empty

  @youtube-audio-extract-B9j3pYC7Z20-compact-speech
  Scenario: Extract audio from YouTube video B9j3pYC7Z20 with compact size speech quality
    Given the YouTube video id "B9j3pYC7Z20"
    When I extract the audio for the YouTube video with "COMPACT_SIZE_SPEECH" quality
    Then the extracted audio file should exist
    And the extracted audio filename should contain the video id
    And the extracted audio file should be non-empty

  @youtube-audio-extract-B9j3pYC7Z20-compact-music
  Scenario: Extract audio from YouTube video B9j3pYC7Z20 with compact size music quality
    Given the YouTube video id "B9j3pYC7Z20"
    When I extract the audio for the YouTube video with "COMPACT_SIZE_MUSIC" quality
    Then the extracted audio file should exist
    And the extracted audio filename should contain the video id
    And the extracted audio file should be non-empty

