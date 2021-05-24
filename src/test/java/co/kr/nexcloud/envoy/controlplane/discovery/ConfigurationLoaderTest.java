package co.kr.nexcloud.envoy.controlplane.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ConfigurationLoaderTest {
	@Autowired
	private ConfigurationLoader configLoader;
	
	private static final String TEST_RESOURCE_PATH = "src/test/resources/files";
	
	@BeforeEach
	void setUp() throws Exception {
		File origin = new File(TEST_RESOURCE_PATH + "/default-original.yml");
		File copy = new File(TEST_RESOURCE_PATH + "/default.yml");
		
		FileUtils.copyFile(origin, copy);
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	public void testRunConfigurationWatcher_ConfigFileModify_doLastModifiedChanged() {
		String file = "watcher-test";
				
		long lastModified = configLoader.lastConfigModified();
		
		try(
			FileOutputStream fos = new FileOutputStream(TEST_RESOURCE_PATH + "/" + file);
		) {
			IOUtils.write("set original", fos, "UTF-8");
		} catch(IOException e) {
			assertThat(e).as("IOException").isNull();
		}
		
		ReflectionTestUtils.invokeMethod(configLoader, "runConfigurationWatcher");
	
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		try(
			FileOutputStream fos = new FileOutputStream(TEST_RESOURCE_PATH + "/" + file);
		) {
			IOUtils.write("file changed", fos, "UTF-8");
		} catch(IOException e) {
			assertThat(e).as("IOException").isNull();
		}
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		
		assertThat(configLoader.lastConfigModified()).as("check last modified changed").isGreaterThan(lastModified);
	}

}
