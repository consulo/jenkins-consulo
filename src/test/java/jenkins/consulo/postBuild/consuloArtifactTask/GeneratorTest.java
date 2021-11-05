/*
 * Copyright 2013-2018 must-be.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jenkins.consulo.postBuild.consuloArtifactTask;

import hudson.FilePath;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author VISTALL
 * @since 2018-03-31
 */
public class GeneratorTest
{
	@Test
	public void testWindows20191117() throws Exception
	{
		File rootDirectory = new File(GeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());

		final Path targetDirectory = Paths.get("result-directory");

		File localPath = new File(rootDirectory, "jenkins/consulo/postBuild/consuloArtifactTask/testWindows20191117");

		FilePath distDir = new FilePath(localPath);

		FilePath targetDir = new FilePath(targetDirectory.toFile());

		targetDir.deleteContents();

		Generator generator = new JRE11Generator(distDir, targetDir, 1, new DummyBuildListener());

		generator.buildDistributionInArchive("consulo-bundle-2-SNAPSHOT-win.zip", localPath.getPath() + "/jbrsdk-11_0_4-windows-x64-b304.77.tar.gz", "consulo-win64", ArchiveStreamFactory.ZIP);
		generator.buildDistributionInArchive("consulo-bundle-2-SNAPSHOT-win.zip", localPath.getPath() + "/jbr-11_0_4-windows-x64-b304.77.tar.gz", "consulo-win64-jre", ArchiveStreamFactory.ZIP);
	}

	@Test
	public void testMac20191117() throws Exception
	{
		File rootDirectory = new File(GeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());

		final Path targetDirectory = Paths.get("result-directory");

		File localPath = new File(rootDirectory, "jenkins/consulo/postBuild/consuloArtifactTask/testMac20191117");

		FilePath distDir = new FilePath(localPath);

		FilePath targetDir = new FilePath(targetDirectory.toFile());

		targetDir.deleteContents();

		Generator generator = new JRE11Generator(distDir, targetDir, 1, new DummyBuildListener());

		generator.buildDistributionInArchive("consulo-bundle-2-SNAPSHOT-mac.zip", localPath.getPath() + "/jdk-11.0.4+10_osx-x64_bin.tar.gz", "consulo-mac64", ArchiveStreamFactory.TAR);
		generator.buildDistributionInArchive("consulo-bundle-2-SNAPSHOT-mac.zip", localPath.getPath() + "/jbr-11_0_4-osx-x64-b304.77.tar.gz", "consulo-mac64-jbr", ArchiveStreamFactory.TAR);
		generator.buildDistributionInArchive("consulo-bundle-2-SNAPSHOT-mac.zip", localPath.getPath() + "/jbrsdk-11_0_4-osx-x64-b304.77.tar.gz", "consulo-mac64-jbsdk", ArchiveStreamFactory.TAR);
	}
}
