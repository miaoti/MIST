package io.mist.core.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 
 * @author Sergio Segura
 */
public class PropertyManager {

	/**
	 * Legacy CWD-relative path. Kept as a last-resort fallback for environments
	 * that have explicitly mutated it, but the default load path is the classpath
	 * lookup below — the file ships at {@code mist-restest-adapter/src/main/resources/config.properties}
	 * after the Stage 1.C reactor split, and CWD when running from an IDE is the
	 * project root, where this relative path does not resolve.
	 */
	static String globalPropertyFilePath = "src/main/resources/config.properties";

	/**
	 * Classpath resource name for the bundled defaults. Resolved via the class's
	 * {@link ClassLoader} so it works regardless of CWD or jar packaging.
	 */
	private static final String GLOBAL_CLASSPATH_RESOURCE = "config.properties";

	static Properties globalProperties = null;
	static Properties userProperties = null;

	private static Map<String, Properties> userPropertiesMap= new HashMap<>();



	private static Logger logger = LogManager.getLogger(PropertyManager.class.getName());

	/**
	 * Reads a property from the global properties file
	 * @param name Property name
	 * @return
	 */
	static public String readProperty(String name) {

		if (globalProperties ==null) {
			 globalProperties = new Properties();
			 // Classpath first — works from any CWD, including IDE project-root runs
			 // and packaged jars. Falls through to the legacy file-path lookup only
			 // when the resource isn't bundled (defensive — should never happen
			 // for a built artifact).
			 try (InputStream cp = PropertyManager.class.getClassLoader()
					 .getResourceAsStream(GLOBAL_CLASSPATH_RESOURCE)) {
				 if (cp != null) {
					 globalProperties.load(cp);
				 } else {
					 // The legacy default config (src/main/resources/config.properties) is
					 // OPTIONAL: bundled on the classpath for normal runs, and simply absent
					 // when running from a packaged jar in an arbitrary cwd (e.g. a per-SUT
					 // .runtime/ dir). Only read it when present — otherwise use empty global
					 // properties silently (callers treat a missing key as "not set"). The
					 // previous unconditional FileInputStream threw FileNotFoundException on
					 // every single-arg readProperty call from such a cwd, spamming the log
					 // with a scary (but non-fatal) stack trace.
					 java.io.File defaultFile = new java.io.File(globalPropertyFilePath);
					 if (defaultFile.isFile()) {
						 try (FileInputStream defaultProperties = new FileInputStream(defaultFile)) {
							 globalProperties.load(defaultProperties);
						 }
					 } else {
						 logger.debug("No global properties on classpath or at {}; using empty defaults.", globalPropertyFilePath);
					 }
				 }
			 } catch (IOException e) {
				 logger.error("Error reading property file: {}", e.getMessage());
				 logger.error("Exception: ", e);
			 }
		}

		return globalProperties.getProperty(name);

	}

	/**
	 * Reads a property from the property file located in evalPropertiesFilePath
	 * @param evalPropertiesFilePath Path to the user properties file
	 * @param name Property name
	 * @return
	 */
	public static String readProperty(String evalPropertiesFilePath, String name) {

		Properties userProperties = userPropertiesMap.get(evalPropertiesFilePath);

		if (userProperties ==null) {
			userProperties = new Properties();
			try(FileInputStream experimentProperties = new FileInputStream(evalPropertiesFilePath)) {
				userProperties.load(experimentProperties);
				userPropertiesMap.put(evalPropertiesFilePath, userProperties);
			} catch (IOException e) {
				logger.error("Error reading property file: {}", e.getMessage());
				logger.error("Exception: ", e);
			}
		}

		return userProperties.getProperty(name);
	}

	// Setters

	public static void setUserPropertiesFilePath(String evalPropertiesFilePath) {
		Properties userProperties = new Properties();
		try (FileInputStream experimentProperties = new FileInputStream(evalPropertiesFilePath)) {
			userProperties.load(experimentProperties);
			userPropertiesMap.put(evalPropertiesFilePath, userProperties);
		} catch (IOException e) {
			logger.error("Error reading property file: {}", e.getMessage());
			logger.error("Exception: ", e);
		}
	}

}
