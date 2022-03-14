/*
 * Copyright 2013-2019 must-be.org
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
import hudson.model.BuildListener;
import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2019-11-17
 */
public class JRE11Generator extends Generator
{
	private static final String[] ourWinLinuxSkipListFromJre = {
			"jmods",
			"lib/src.zip"
	};

	private static final String[] ourMacSkipListFromJre = {
			"Contents/Home/jmods/",
			"Contents/Home/lib/src.zip",
	};

	public JRE11Generator(FilePath distPath, FilePath targetDir, FilePath jreDirectory, int buildNumber, BuildListener listener)
	{
		super(distPath, targetDir, jreDirectory, buildNumber, listener);
	}

	@Override
	protected void buildBundledJRE(FilePath jdkArchivePath, ArchiveStreamFactory factory, final String archiveOutType, final ArchiveOutputStream archiveOutputStream, final boolean isMac) throws Exception
	{
		final String[] rootDirectoryRef = new String[2];

		openAndProcessJreArchive(jdkArchivePath, factory, new ArchiveInputStreamProcessor()
		{
			@Override
			public void run(ArchiveInputStream stream) throws IOException
			{
				ArchiveEntry tempEntry = stream.getNextEntry();
				while(tempEntry != null)
				{
					final String name = skipStartEntry(tempEntry.getName());
					if(!name.isEmpty())
					{
						if(tempEntry.isDirectory())
						{
							rootDirectoryRef[0] = name;
							rootDirectoryRef[1] = tempEntry.getName();
							break;
						}
					}

					tempEntry = stream.getNextEntry();
				}
			}
		});

		if(rootDirectoryRef[0] == null)
		{
			throw new IOException("Can't find root directory");
		}

		// if jre archive is zip, path will be jdk-17/
		final String rootPath = rootDirectoryRef[0];
		// but if archive is tar, path will be ./jdk-17/, we need it for correct skipping
		final String originalRootPath = rootDirectoryRef[1];

		openAndProcessJreArchive(jdkArchivePath, factory, new ArchiveInputStreamProcessor()
		{
			@Override
			public void run(ArchiveInputStream ais) throws IOException
			{
				ArchiveEntry tempEntry = ais.getNextEntry();
				while(tempEntry != null)
				{
					final String name = skipStartEntry(tempEntry.getName());

					if(isUselessFile(name))
					{
						tempEntry = ais.getNextEntry();
						continue;
					}

					String[] skipSuffixes = isMac ? ourMacSkipListFromJre : ourWinLinuxSkipListFromJre;
					Set<String> skipPaths = new HashSet<>();
					for(String skip : skipSuffixes)
					{
						skipPaths.add(rootPath + skip);
					}

					if(isMac)
					{
						if(name.startsWith(rootPath))
						{
							String correctName = name.replace(rootPath, "jdk/");

							if(needAddToArchive(name, skipPaths))
							{
								final ArchiveEntryWrapper jdkEntry = createEntry(archiveOutType, "Consulo.app/Contents/platform/buildSNAPSHOT/jre/" + correctName, tempEntry);
								jdkEntry.setMode(extractMode(tempEntry));
								jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

								copyEntry(archiveOutputStream, ais, tempEntry, jdkEntry);
							}
						}
					}
					else
					{
						if(name.startsWith(rootPath))
						{
							if(needAddToArchive(name, skipPaths))
							{
								String correctName = name.replace(rootPath, "jre/");

								final ArchiveEntryWrapper jdkEntry = createEntry(archiveOutType, "Consulo/platform/buildSNAPSHOT/" + correctName, tempEntry);
								jdkEntry.setMode(extractMode(tempEntry));
								jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

								copyEntry(archiveOutputStream, ais, tempEntry, jdkEntry);
							}
						}
					}

					tempEntry = ais.getNextEntry();
				}
			}
		});
	}

	private static void openAndProcessJreArchive(FilePath jdkArchivePath, ArchiveStreamFactory factory, ArchiveInputStreamProcessor processor) throws IOException, ArchiveException, InterruptedException
	{
		if(jdkArchivePath.getName().endsWith(".zip"))
		{
			try (InputStream is = jdkArchivePath.read())
			{
				try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, is))
				{
					processor.run(ais);
				}
			}
		}
		else
		{
			try (InputStream is = jdkArchivePath.read())
			{
				try (GzipCompressorInputStream gz = new GzipCompressorInputStream(is))
				{
					try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.TAR, gz))
					{
						processor.run(ais);
					}
				}
			}
		}
	}

	private static String skipStartEntry(String name)
	{
		if(name.startsWith("./"))
		{
			return name.substring(2, name.length());
		}
		return name;
	}

	private static boolean isUselessFile(String path)
	{
		return path.endsWith(".pdb") || path.endsWith(".DS_Store") || path.contains(".dSYM/");
	}
}
