/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.nativex.support;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.nativex.domain.init.InitializationDescriptor;
import org.springframework.nativex.domain.init.InitializationJsonMarshaller;
import org.springframework.nativex.type.Type;
import org.springframework.nativex.type.TypeSystem;

import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

/**
 * 
 * @author Andy Clement
 */
public class InitializationHandler extends Handler {
	
	private ImageClassLoader cl;
	private TypeSystem ts;

	InitializationHandler(ConfigurationCollector collector) {
		super(collector);
	}

	public InitializationDescriptor compute() {
		try {
			InputStream s = this.getClass().getResourceAsStream("/initialization.json");
			return InitializationJsonMarshaller.read(s);
		} catch (Exception e) {
			return null;
		}
	}

	public void register(BeforeAnalysisAccess access) {
		cl = ((BeforeAnalysisAccessImpl) access).getImageClassLoader();
		ts = TypeSystem.get(cl.getClasspath());
		InitializationDescriptor id = compute();
		System.out.println("Configuring initialization time for specific types and packages:");
		if (id == null) {
			throw new IllegalStateException("Unable to load initialization descriptor");
		}
//		List<Type> typenames = id.getBuildtimeClasses().stream()
//				.map(t -> ts.resolveDotted(t,true)/*access::findClassByName*/).filter(Objects::nonNull).collect(Collectors.toList());
//		collector.initializeAtBuildTime(typenames);
		List<Class<?>> typenames = id.getBuildtimeClasses().stream()
				.map(access::findClassByName).filter(Objects::nonNull).collect(Collectors.toList());
		RuntimeClassInitialization.initializeAtBuildTime(typenames.toArray(new Class[] {}));

		id.getRuntimeClasses().stream()
				.map(access::findClassByName).filter(Objects::nonNull)
				.forEach(RuntimeClassInitialization::initializeAtRunTime);
//				.map(t->ts.resolveDotted(t,true)/*access::findClassByName*/).filter(Objects::nonNull)
//				.forEach(collector::initializeAtRunTime);
		SpringFeature.log("Registering these packages for buildtime initialization: \n"+id.getBuildtimePackages());
//		collector.initializeAtBuildTimePackages(id.getBuildtimePackages().toArray(new String[] {}));
		RuntimeClassInitialization.initializeAtBuildTime(id.getBuildtimePackages().toArray(new String[] {}));
		SpringFeature.log("Registering these packages for runtime initialization: \n"+id.getRuntimePackages());
//		collector.initializeAtRunTimePackages(id.getRuntimePackages().toArray(new String[] {}));
		RuntimeClassInitialization.initializeAtRunTime(id.getRuntimePackages().toArray(new String[] {}));

		if (ConfigOptions.isVerifierOn()) {
			for (Map.Entry<String, List<String>> e : ts.getSpringClassesMakingIsPresentChecks().entrySet()) {
				String k = e.getKey();
				if (!id.getBuildtimeClasses().contains(k)) {
					System.out.println("[verification] The type " + k
							+ " is making isPresent() calls in the static initializer, could be worth specifying build-time-initialization. "
							+ "It appears to be making isPresent() checks on " + e.getValue());
				}
			}
		}
	}

	public void registerInitializationDescriptor(InitializationDescriptor initializationDescriptor) {
		List<String> buildtimeClasses = initializationDescriptor.getBuildtimeClasses();
		if (buildtimeClasses.size()!=0) {
			buildtimeClasses.stream()
//			.map(c -> ts.resolveDotted(c, true)/*cl.findClassByName(c, false)*/).filter(Objects::nonNull)
//			.forEach(collector::initializeAtBuildTime);
			.map(c -> cl.findClassByName(c, false)).filter(Objects::nonNull)
			.forEach(RuntimeClassInitialization::initializeAtBuildTime);
		}
		List<String> runtimeClasses = initializationDescriptor.getRuntimeClasses();
		if (runtimeClasses.size()!=0) {
			runtimeClasses.stream()
//			.map(c -> ts.resolveDotted(c,true)/*cl.findClassByName(c, false)*/).filter(Objects::nonNull)
//			.forEach(collector::initializeAtRunTime);
			.map(c -> cl.findClassByName(c, false)).filter(Objects::nonNull)
			.forEach(RuntimeClassInitialization::initializeAtRunTime);
		}
		List<String> buildtimePackages = initializationDescriptor.getBuildtimePackages();
		if (buildtimePackages.size()!=0) {
//			collector.initializeAtBuildTimePackages(buildtimePackages.toArray(new String[0]));
			RuntimeClassInitialization.initializeAtBuildTime(buildtimePackages.toArray(new String[0]));
		}
		List<String> runtimePackages = initializationDescriptor.getRuntimePackages();
		if (runtimePackages.size()!=0) {
//			collector.initializeAtRunTimePackages(runtimePackages.toArray(new String[0]));
			RuntimeClassInitialization.initializeAtRunTime(runtimePackages.toArray(new String[0]));
		}
	}
	
}
