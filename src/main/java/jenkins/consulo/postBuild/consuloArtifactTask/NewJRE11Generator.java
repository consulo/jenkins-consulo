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
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author VISTALL
 * @since 2019-11-17
 */
public class NewJRE11Generator extends Generator
{
	private static final String[] ourWinLinuxSkipListFromJre = {
			"jbrsdk/jmods",
			"jbrsdk/lib/src.zip"
	};

	private static final String[] ourMacSkipListFromJre = {
			"jdk/Contents/Home/jmods/",
			"jdk/Contents/Home/lib/src.zip",
	};

	public NewJRE11Generator(FilePath distPath, FilePath targetDir, int buildNumber, BuildListener listener)
	{
		super(distPath, targetDir, buildNumber, listener);
	}

	@Override
	public boolean isSupport32Bits()
	{
		return false;
	}

	@Override
	protected void buildBundledJRE(String jdkArchivePath, ArchiveStreamFactory factory, String archiveOutType, ArchiveOutputStream archiveOutputStream, boolean isMac) throws Exception
	{
		try (InputStream is = new FileInputStream(jdkArchivePath))
		{
			try (GzipCompressorInputStream gz = new GzipCompressorInputStream(is))
			{
				try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.TAR, gz))
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

						if(isMac)
						{
							// FIXME [VISTALL] we need change it if using another JRE
							if(name.startsWith("jdk-11.0.4.jdk/") || name.startsWith("jbr/") || name.startsWith("jbrsdk/"))
							{
								String correntName = remapMacPath(name);

								if(needAddToArchive(correntName, ourMacSkipListFromJre))
								{
									final ArchiveEntryWrapper jdkEntry = createEntry(archiveOutType, "Consulo.app/Contents/platform/buildSNAPSHOT/jre/" + correntName, tempEntry);
									jdkEntry.setMode(extractMode(tempEntry));
									jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

									copyEntry(archiveOutputStream, ais, tempEntry, jdkEntry);
								}
							}
						}
						else
						{
							// FIXME [VISTALL] we need change it if using another JRE
							if(name.startsWith("jbr/") || name.startsWith("jbrsdk/"))
							{
								// skip debug files
								if(needAddToArchive(name, ourWinLinuxSkipListFromJre))
								{
									String correntName = remapJetBrainsPath(name, "jre");

									final ArchiveEntryWrapper jdkEntry = createEntry(archiveOutType, "Consulo/platform/buildSNAPSHOT/" + correntName, tempEntry);
									jdkEntry.setMode(extractMode(tempEntry));
									jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

									copyEntry(archiveOutputStream, ais, tempEntry, jdkEntry);
								}
							}
						}

						tempEntry = ais.getNextEntry();
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

	private static String remapMacPath(String name)
	{
		if(name.startsWith("jdk-11.0.4.jdk/"))
		{
			return name.replaceAll("jdk-11.0.4.jdk", "jdk");
		}
		else if(name.startsWith("jbr/") || name.startsWith("jbrsdk/"))
		{
			return remapJetBrainsPath(name, "jdk");
		}
		return name;
	}

	private static String remapJetBrainsPath(String name, String to)
	{
		if(name.startsWith("jbr/"))
		{
			return to + "/" + name.substring(4, name.length());
		}
		else if(name.startsWith("jbrsdk/"))
		{
			return to + "/" + name.substring(7, name.length());
		}
		else
		{
			throw new UnsupportedOperationException(name);
		}
	}

	private static boolean isUselessFile(String path)
	{
		return path.endsWith(".pdb") || path.endsWith(".DS_Store") || path.contains(".dSYM/");
	}
}
