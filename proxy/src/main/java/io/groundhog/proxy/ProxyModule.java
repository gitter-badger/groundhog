package io.groundhog.proxy;

import io.groundhog.capture.CaptureWriter;
import io.groundhog.har.HarFileCaptureWriter;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ProxyModule extends AbstractModule {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyModule.class);

  private static final int PARENT_LIMIT = 3;
  private static final String PROPERTIES_FILENAME = "conf/config.properties";

  @Override
  protected void configure() {
    Properties properties = new Properties();
    try {
      // Depending on the type of distribution, the conf directory can be one or two directories up
      File parentDir = new File(System.getProperty("user.dir"));
      Optional<File> configFile = findConfigInParent(parentDir, PARENT_LIMIT);
      File propertiesFile;
      if (!configFile.isPresent()) {
        // Attempt a fallback to the developer location. If launching from an IDE, be sure your working directory is proxy/
        propertiesFile = new File("src/dist", PROPERTIES_FILENAME);
        LOG.warn("Could not locate {} in current or parent directories up to {} levels deep. Falling back to developer config {}",
            PROPERTIES_FILENAME, PARENT_LIMIT, propertiesFile);
      } else {
        propertiesFile = configFile.get();
      }
      properties.load(new FileInputStream(propertiesFile));
    } catch (IOException e) {
      LOG.error("Failed to load properties file");
      Throwables.propagate(e);
    }

    Names.bindProperties(binder(), properties);

    String outputFilename = "out/recording.har";
    File outputFile = new File(outputFilename);
    String uploadDirectoryName = "uploads";
    File uploadLocation = new File(outputFile.getParentFile(), uploadDirectoryName);
    bind(File.class).annotatedWith(Names.named("UploadLocation")).toInstance(uploadLocation);

    CaptureWriter captureWriter = new HarFileCaptureWriter(outputFile, true, false, false);
    bind(CaptureWriter.class).toInstance(captureWriter);
  }

  private Optional<File> findConfigInParent(File parentDir, int limit) {
    File currentDir = parentDir;
    for (int i = 0; i < limit; i++) {
      if (null == currentDir) {
        break;
      }
      File propertiesFile = new File(currentDir, PROPERTIES_FILENAME);
      if (propertiesFile.exists()) {
        return Optional.of(propertiesFile);
      }
      currentDir = currentDir.getParentFile();
    }
    return Optional.absent();
  }
}
