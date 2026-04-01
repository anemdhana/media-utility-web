package org.pssm.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Ensures required directories exist at application startup.
 */
@Component
public class MediaDirectoriesInitializer implements CommandLineRunner {

    @Value("${mediafiles_dir}")
    private String mediaFilesDir;

    @Value("${tools_location}")
    private String toolsLocation;

    @Override
    public void run(String... args) {
        createDirIfNotExists(mediaFilesDir);
        createDirIfNotExists(toolsLocation);
    }

    private void createDirIfNotExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("Created directory: " + path);
            } else {
                System.err.println("Failed to create directory: " + path);
            }
        }
    }
}
