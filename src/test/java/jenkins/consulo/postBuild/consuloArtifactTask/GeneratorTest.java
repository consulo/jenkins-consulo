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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import hudson.FilePath;
import hudson.console.ConsoleNote;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;

/**
 * @author VISTALL
 * @since 2018-03-31
 */
public class GeneratorTest
{
	@Test
	public void testMac20180331() throws Exception
	{
		File rootDirectory = new File(GeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());

		final Path targetDirectory = Files.createTempDirectory("temp");
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				FileUtils.deleteQuietly(targetDirectory.toFile());
			}
		}));

		File localPath = new File(rootDirectory, "jenkins/consulo/postBuild/consuloArtifactTask/testMac20180331");

		FilePath distDir = new FilePath(localPath);

		FilePath targetDir = new FilePath(targetDirectory.toFile());

		Generator generator = new Generator(distDir, targetDir, 1, new BuildListener()
		{
			private PrintStream myPrintStream = new PrintStream(new ByteArrayOutputStream());

			@Override
			public void started(List<Cause> list)
			{

			}

			@Override
			public void finished(Result result)
			{

			}

			@Override
			public PrintStream getLogger()
			{
				return myPrintStream;
			}

			@Override
			public void annotate(ConsoleNote consoleNote) throws IOException
			{

			}

			@Override
			public void hyperlink(String s, String s1) throws IOException
			{

			}

			@Override
			public PrintWriter error(String s)
			{
				return null;
			}

			@Override
			public PrintWriter error(String s, Object... objects)
			{
				return null;
			}

			@Override
			public PrintWriter fatalError(String s)
			{
				return null;
			}

			@Override
			public PrintWriter fatalError(String s, Object... objects)
			{
				return null;
			}
		});

		generator.buildDistributionInArchive("consulo-bundle-2-SNAPSHOT-mac.zip", localPath.getPath() + "/jbrex8u152b1136.28_osx_x64.tar.gz", "consulo-mac64", ArchiveStreamFactory.TAR);
	}
}
