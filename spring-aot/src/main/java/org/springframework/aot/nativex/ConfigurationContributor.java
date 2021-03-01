/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.aot.nativex;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aot.BootstrapContributor;
import org.springframework.aot.BuildContext;
import org.springframework.aot.ResourceFile;
import org.springframework.nativex.AotOptions;
import org.springframework.nativex.support.ConfigurationCollector;
import org.springframework.nativex.support.SpringAnalyzer;
import org.springframework.nativex.type.TypeSystem;

/**
 * Contributes the configuration files for native image construction. This includes the reflection,
 * resource and proxy configuration in addition to the native-image.properties file that includes
 * special args and initialization configuration.
 * 
 * @author Andy Clement
 */
public class ConfigurationContributor implements BootstrapContributor {
	
	private static Log logger = LogFactory.getLog(ConfigurationContributor.class);	
	
	@Override
	public void contribute(BuildContext context, AotOptions aotOptions) {
		TypeSystem typeSystem = TypeSystem.get(context.getClasspath());
		typeSystem.setAotOptions(aotOptions);
		SpringAnalyzer springAnalyzer = new SpringAnalyzer(typeSystem, aotOptions);
		springAnalyzer.analyze();
		ConfigurationCollector configurationCollector = springAnalyzer.getConfigurationCollector();
		context.describeReflection(reflect -> reflect.merge(configurationCollector.getReflectionDescriptor()));
		context.describeResources(resources -> resources.merge(configurationCollector.getResourcesDescriptors()));
		context.describeProxies(proxies -> proxies.merge(configurationCollector.getProxyDescriptors()));
		byte[] springComponentsFileContents = configurationCollector.getResources("META-INF/spring.components");
		if (springComponentsFileContents!=null) {
			logger.debug("Storing synthesized META-INF/spring.components");
			context.addResources(new ResourceFile() {
				@Override
				public void writeTo(Path resourcesPath) throws IOException {
					Path metaInfFolder = resourcesPath.resolve(Paths.get("META-INF"));
					Files.createDirectories(metaInfFolder);
					Path springComponentsFile = resourcesPath.resolve(ResourceFile.SPRING_COMPONENTS_PATH);
					try (FileOutputStream fos = new FileOutputStream(resourcesPath.resolve(ResourceFile.SPRING_COMPONENTS_PATH).toFile())) {
						fos.write(springComponentsFileContents);
					}
				}
			});
		}
		// Create native-image.properties
		context.addResources(new ResourceFile() {
			@Override
			public void writeTo(Path rootPath) throws IOException {
				Path nativeConfigFolder = rootPath.resolve(ResourceFile.NATIVE_CONFIG_PATH);
				Files.createDirectories(nativeConfigFolder);
				Path nativeImagePropertiesFile = nativeConfigFolder.resolve("native-image.properties");
				try (FileOutputStream fos = new FileOutputStream(nativeImagePropertiesFile.toFile())) {
					fos.write(configurationCollector.getNativeImagePropertiesContent().getBytes());
				}
			}
		});
	}
}
