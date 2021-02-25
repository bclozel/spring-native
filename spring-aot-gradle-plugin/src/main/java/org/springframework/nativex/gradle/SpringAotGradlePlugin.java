/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.nativex.gradle;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;
import org.springframework.boot.gradle.tasks.bundling.BootJar;
import org.springframework.boot.gradle.tasks.bundling.BootWar;
import org.springframework.boot.gradle.tasks.run.BootRun;
import org.springframework.nativex.gradle.tasks.GenerateAotSources;

/**
 *
 */
public class SpringAotGradlePlugin implements Plugin<Project> {

	public static final String AOT_SOURCE_SET_NAME = "aot";

	public static final String GENERATE_TASK_NAME = "generateAot";

	public static final String AOT_TEST_SOURCE_SET_NAME = "aotTest";

	public static final String GENERATE_TEST_TASK_NAME = "generateTestAot";


	@Override
	public void apply(Project project) {

		project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {

			JavaPluginConvention java = project.getConvention().getPlugin(JavaPluginConvention.class);
			// build/generated/sources/aot/org/springframework/beans/Sample.java
			Path springAotSourcesPath = Paths.get(project.getBuildDir().getAbsolutePath(), "generated", "sources");
			// build/generated/resources/aot/sample.txt
			Path springAotResourcesPath = Paths.get(project.getBuildDir().getAbsolutePath(), "generated", "resources");

			/*
			 * MAIN
			 */
			SourceSet mainSourceSet = java.getSourceSets().findByName(SourceSet.MAIN_SOURCE_SET_NAME);
			File aotSourcesDirectory = springAotSourcesPath.resolve(AOT_SOURCE_SET_NAME).toFile();
			File aotResourcesDirectory = springAotResourcesPath.resolve(AOT_SOURCE_SET_NAME).toFile();
			SourceSet aotSourceSet = java.getSourceSets().create(AOT_SOURCE_SET_NAME);
			aotSourceSet.setCompileClasspath(mainSourceSet.getRuntimeClasspath());

			GenerateAotSources generateAotSources = project.getTasks().create(GENERATE_TASK_NAME, GenerateAotSources.class);
			generateAotSources.setClasspath(mainSourceSet.getRuntimeClasspath());
			generateAotSources.setResourceInputDirectories(mainSourceSet.getResources());
			generateAotSources.getSourcesOutputDirectory().set(aotSourcesDirectory);
			generateAotSources.getResourcesOutputDirectory().set(aotResourcesDirectory);
			
			project.getTasks().named(aotSourceSet.getCompileJavaTaskName(), JavaCompile.class, (aotCompileJava) -> {
				aotCompileJava.source(generateAotSources.getSourcesOutputDirectory());
			});
			
			project.getTasks().named(SpringBootPlugin.BOOT_JAR_TASK_NAME, BootJar.class, (bootJar) -> 
				bootJar.classpath(aotSourceSet.getRuntimeClasspath()));
			project.getTasks().named("bootRun", BootRun.class, (bootRun) -> 
				bootRun.classpath(aotSourceSet.getRuntimeClasspath()));	

			/*
			 * TESTS
			 */
			SourceSet testSourceSet = java.getSourceSets().findByName(SourceSet.TEST_SOURCE_SET_NAME);
			File aotTestSourcesDirectory = springAotSourcesPath.resolve(AOT_TEST_SOURCE_SET_NAME).toFile();
			File aotTestResourcesDirectory = springAotResourcesPath.resolve(AOT_TEST_SOURCE_SET_NAME).toFile();
			SourceSet aotTestSourceSet = java.getSourceSets().create(AOT_TEST_SOURCE_SET_NAME);
			aotTestSourceSet.setCompileClasspath(testSourceSet.getCompileClasspath().plus(testSourceSet.getOutput()));

			GenerateAotSources generateAotTestSources = project.getTasks().create(GENERATE_TEST_TASK_NAME, GenerateAotSources.class);

			generateAotTestSources.setClasspath(testSourceSet.getCompileClasspath().plus(testSourceSet.getOutput()));
			generateAotTestSources.setResourceInputDirectories(testSourceSet.getResources());
			generateAotTestSources.getSourcesOutputDirectory().set(aotTestSourcesDirectory);
			generateAotTestSources.getResourcesOutputDirectory().set(aotTestResourcesDirectory);

			aotTestSourceSet.getJava().srcDir(generateAotTestSources.getSourcesOutputDirectory());
			aotTestSourceSet.getResources().srcDir(generateAotTestSources.getResourcesOutputDirectory());

			project.getTasks().named(aotTestSourceSet.getCompileJavaTaskName()).configure(task -> task.dependsOn(generateAotTestSources));
			testSourceSet.setRuntimeClasspath(aotTestSourceSet.getOutput().minus(aotSourceSet.getOutput()).plus(testSourceSet.getRuntimeClasspath()));

		});
	}

}
